#!/usr/bin/env python3
"""Strict validator and deterministic vectors for Ligase Touch Layout v3."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from copy import deepcopy
from datetime import datetime
from fractions import Fraction
from pathlib import Path

MAX_RAW_BYTES = 1024 * 1024
SAFE_INTEGER = 9007199254740991
UUID_D = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)
CONTROL = re.compile(r"[\x00-\x1f\x7f-\x9f]")
INPUT_NAMESPACES = {"androidKeyCode", "usbHidKeyboardUsage"}
INPUT_NAMESPACE_ORDER = {"androidKeyCode": 0, "usbHidKeyboardUsage": 1}
DEVICE_ORDER = {"phone": 0, "tablet": 1}
ORIENTATION_ORDER = {"portrait": 0, "landscape": 1}
KINDS = {
    "keyboard", "mouse", "analog", "dpad", "customKeys", "radial",
    "scroll", "combo", "softKeyboard", "gyro",
}


class ValidationError(ValueError):
    def __init__(self, code: str, path: str = "", detail: str = ""):
        super().__init__(f"{code}:{path}:{detail}")
        self.code, self.path, self.detail = code, path, detail


def fail(code: str, path: str = "", detail: str = "") -> None:
    raise ValidationError(code, path, detail)


def _reject_duplicate(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail("duplicateKey", "", key)
        result[key] = value
    return result


def _parse_integer(lexeme: str) -> int:
    if re.fullmatch(r"-0", lexeme):
        fail("negativeZero", "", lexeme)
    return int(lexeme)


def strict_load_bytes(raw: bytes, *, max_bytes: int = MAX_RAW_BYTES):
    if len(raw) > max_bytes:
        fail("fileTooLarge")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        fail("invalidUtf8", "", str(exc.start))
    try:
        return json.loads(
            text,
            object_pairs_hook=_reject_duplicate,
            parse_int=_parse_integer,
            parse_float=lambda value: fail("fractionNotAllowed", "", value),
            parse_constant=lambda value: fail("invalidNumber", "", value),
        )
    except ValidationError:
        raise
    except json.JSONDecodeError as exc:
        fail("invalidJson", "", str(exc.pos))


def strict_load(path: Path):
    with path.open("rb") as stream:
        raw = stream.read(MAX_RAW_BYTES + 1)
    return strict_load_bytes(raw)


def strict_load_vector(path: Path):
    raw = path.read_bytes()
    if len(raw) > MAX_RAW_BYTES:
        fail("fileTooLarge")
    try:
        return json.loads(
            raw.decode("utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate,
            parse_int=_parse_integer,
            parse_constant=lambda value: fail("invalidNumber", "", value),
        )
    except UnicodeDecodeError:
        fail("invalidUtf8")


def valid_scalar_string(value, *, maximum: int, path: str):
    if not isinstance(value, str) or not 1 <= len(value) <= maximum:
        fail("invalidString", path)
    if CONTROL.search(value) or any(0xD800 <= ord(ch) <= 0xDFFF for ch in value):
        fail("invalidString", path)
    return value


def _utf16_key(value: str) -> bytes:
    return value.encode("utf-16-be", errors="strict")


def _jcs_string(value: str) -> str:
    if len(value) > 4096 or any(
        0xD800 <= ord(ch) <= 0xDFFF for ch in value
    ):
        fail("invalidString")
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def jcs_text(value) -> str:
    """RFC 8785 for the v3 value domain (no floats; safe integers only)."""
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, int) and not isinstance(value, bool):
        if not -SAFE_INTEGER <= value <= SAFE_INTEGER:
            fail("unsafeInteger")
        return str(value)
    if isinstance(value, float):
        fail("fractionNotAllowed")
    if isinstance(value, str):
        return _jcs_string(value)
    if isinstance(value, list):
        return "[" + ",".join(jcs_text(item) for item in value) + "]"
    if isinstance(value, dict):
        keys = sorted(value, key=_utf16_key)
        return "{" + ",".join(
            _jcs_string(key) + ":" + jcs_text(value[key]) for key in keys
        ) + "}"
    fail("unsupportedJcsType")


def jcs(value) -> bytes:
    return jcs_text(value).encode("utf-8")


def validate_extension(value, path="/extensions", depth=0):
    if value is None or isinstance(value, bool):
        return
    if isinstance(value, int) and not isinstance(value, bool):
        if not -SAFE_INTEGER <= value <= SAFE_INTEGER:
            fail("unsafeInteger", path)
        return
    if isinstance(value, float):
        fail("fractionNotAllowed", path)
    if isinstance(value, str):
        valid_scalar_string(value, maximum=256, path=path)
        return
    if isinstance(value, list):
        if depth > 6:
            fail("extensionDepthExceeded", path)
        if len(value) > 32:
            fail("extensionItemsExceeded", path)
        for index, item in enumerate(value):
            validate_extension(item, f"{path}/{index}", depth + 1)
        return
    if isinstance(value, dict):
        if depth > 6:
            fail("extensionDepthExceeded", path)
        if len(value) > 32:
            fail("extensionPropertiesExceeded", path)
        for key, item in value.items():
            valid_scalar_string(key, maximum=64, path=path)
            validate_extension(item, f"{path}/{key}", depth + 1)
        return
    fail("unsupportedExtensionValue", path)


def validate_input_code(value, path):
    if not isinstance(value, dict) or set(value) != {"namespace", "code"}:
        fail("invalidInputCode", path)
    if value["namespace"] not in INPUT_NAMESPACES:
        fail("unsupportedInputNamespace", path)
    code = value["code"]
    if isinstance(code, bool) or not isinstance(code, int) or not 0 <= code <= 65535:
        fail("invalidInputCode", path)


def validate_chord(values, path):
    if not isinstance(values, list) or not 1 <= len(values) <= 16:
        fail("invalidChord", path)
    for index, value in enumerate(values):
        validate_input_code(value, f"{path}/{index}")
    identities = [(value["namespace"], value["code"]) for value in values]
    expected = sorted(
        set(identities),
        key=lambda item: (INPUT_NAMESPACE_ORDER[item[0]], item[1]),
    )
    if identities != expected:
        fail("nonCanonicalChord", path)


def validate_trigger(payload, path):
    trigger = payload.get("trigger")
    if trigger not in {"hold", "toggle", "tap", "timedHold"}:
        fail("invalidTrigger", path + "/trigger")
    timed = payload.get("timedHoldMs")
    if trigger == "timedHold":
        if isinstance(timed, bool) or not isinstance(timed, int) or not 1 <= timed <= 60000:
            fail("invalidTimedHold", path + "/timedHoldMs")
    elif timed is not None:
        fail("unexpectedTimedHold", path + "/timedHoldMs")


def validate_payload(kind, payload, path):
    if not isinstance(payload, dict) or payload.get("payloadKind") != kind:
        fail("payloadKindMismatch", path)
    if kind == "keyboard":
        validate_input_code(payload.get("inputCode"), path + "/inputCode")
        validate_trigger(payload, path)
    elif kind == "mouse":
        if payload.get("button") not in {"primary", "secondary", "middle", "back", "forward"}:
            fail("invalidMouseButton", path + "/button")
        validate_trigger(payload, path)
    elif kind in {"analog", "dpad"}:
        for name in ("up", "down", "left", "right"):
            values = payload.get(name)
            if not isinstance(values, list) or not 1 <= len(values) <= 8:
                fail("invalidDirectionalBinding", path + "/" + name)
            for index, value in enumerate(values):
                validate_input_code(value, f"{path}/{name}/{index}")
        if kind == "analog":
            if payload.get("diagonalPolicy") != "vector":
                fail("invalidDiagonalPolicy", path + "/diagonalPolicy")
            press = payload.get("press")
            if press is not None:
                for index, value in enumerate(press):
                    validate_input_code(value, f"{path}/press/{index}")
        elif payload.get("diagonalPolicy") not in {"allowTwoDirections", "dominantAxis"}:
            fail("invalidDiagonalPolicy", path + "/diagonalPolicy")
    elif kind in {"customKeys", "combo"}:
        keys = payload.get("keys")
        validate_chord(keys, path + "/keys")
        if payload.get("pressOrder") != "listed" or payload.get("releaseOrder") != "reverseListed":
            fail("invalidChordOrder", path)
        validate_trigger(payload, path)
        if kind == "customKeys":
            sticky = payload.get("sticky")
            if not isinstance(sticky, bool):
                fail("invalidSticky", path + "/sticky")
            if sticky and payload.get("trigger") != "toggle":
                fail("stickyTriggerConflict", path)
    elif kind == "radial":
        if payload.get("startAngleMilliDegrees") != -90000 or payload.get("direction") != "clockwise":
            fail("invalidRadialGeometry", path)
        if payload.get("boundaryPolicy") != "clockwiseInclusive":
            fail("invalidRadialBoundary", path)
        actions = payload.get("actions")
        if not isinstance(actions, list) or not 2 <= len(actions) <= 16:
            fail("invalidRadialActions", path + "/actions")
        action_ids = [action.get("actionId") for action in actions]
        if any(not UUID_D.fullmatch(value or "") for value in action_ids):
            fail("invalidRadialActionId", path + "/actions")
        if len(action_ids) != len(set(action_ids)):
            fail("duplicateRadialActionId", path + "/actions")
        if [action.get("order") for action in actions] != list(range(len(actions))):
            fail("nonCanonicalRadialActionOrder", path + "/actions")
        for ai, action in enumerate(actions):
            if "label" in action:
                valid_scalar_string(
                    action["label"],
                    maximum=32,
                    path=f"{path}/actions/{ai}/label",
                )
            validate_chord(action.get("keys"), f"{path}/actions/{ai}/keys")
    elif kind == "scroll":
        if payload.get("direction") not in {"verticalPositive", "verticalNegative", "horizontalPositive", "horizontalNegative"}:
            fail("invalidScrollDirection", path)
        if payload.get("stepUnit") != "wheelDetent":
            fail("invalidScrollUnit", path)
        step = payload.get("step")
        if isinstance(step, bool) or not isinstance(step, int) or not 1 <= step <= 120:
            fail("invalidScrollStep", path)
        continuous = payload.get("continuous")
        if not isinstance(continuous, bool):
            fail("invalidContinuous", path)
        cadence = payload.get("cadenceMs")
        if continuous:
            if isinstance(cadence, bool) or not isinstance(cadence, int) or not 16 <= cadence <= 1000:
                fail("invalidScrollCadence", path)
        elif cadence is not None:
            fail("unexpectedScrollCadence", path)
        validate_trigger(payload, path)
    elif kind == "softKeyboard":
        if payload.get("action") != "openSystemIme" or payload.get("executionScope") != "androidLocal":
            fail("invalidSoftKeyboard", path)
    elif kind == "gyro":
        if payload.get("target") not in {"mouse", "rightStick"}:
            fail("invalidGyroTarget", path)
        if payload.get("frame") not in {"device", "screen"}:
            fail("invalidGyroFrame", path)
        if payload.get("axes") not in {"yawPitch", "yawPitchRoll"}:
            fail("invalidGyroAxes", path)
        if payload.get("scaleUnit") != "milliUnitsPerDegreePerSecond":
            fail("invalidGyroScale", path)
        for field, minimum, maximum in (
            ("scale", 1, 100000), ("deadzoneMilliDegreesPerSecond", 0, 100000)
        ):
            value = payload.get(field)
            if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
                fail("invalidGyroRange", path + "/" + field)


def intersects(left, right):
    return (
        min(left["x"] + left["width"], right["x"] + right["width"])
        > max(left["x"], right["x"])
        and min(left["y"] + left["height"], right["y"] + right["height"])
        > max(left["y"], right["y"])
    )


def _round_half_up(numerator: int, denominator: int) -> int:
    if denominator <= 0:
        fail("invalidScale")
    return (2 * numerator + denominator) // (2 * denominator)


def _anchor_origin(span: int, size: int, offset: int, anchor: str) -> int:
    if anchor in {"LEFT", "TOP"}:
        return offset
    if anchor == "CENTER":
        return _round_half_up(span - size, 2) + offset
    if anchor in {"RIGHT", "BOTTOM"}:
        return span - offset - size
    fail("invalidAnchor")


def resolve_element_rect(canvas, element):
    rect = element["rect"]
    return {
        "x": _anchor_origin(
            canvas["width"], rect["width"], rect["horizontalOffset"], element["anchorX"]
        ),
        "y": _anchor_origin(
            canvas["height"], rect["height"], rect["verticalOffset"], element["anchorY"]
        ),
        "width": rect["width"],
        "height": rect["height"],
    }


def rebase_anchor(canvas, resolved_rect):
    center_twice = 2 * resolved_rect["x"] + resolved_rect["width"]
    width = canvas["width"]
    if center_twice * 5 < width * 4:
        anchor = "LEFT"
        offset = resolved_rect["x"]
    elif center_twice * 5 <= width * 6:
        anchor = "CENTER"
        offset = resolved_rect["x"] - _round_half_up(
            width - resolved_rect["width"], 2
        )
    else:
        anchor = "RIGHT"
        offset = width - resolved_rect["x"] - resolved_rect["width"]
    center_y_twice = 2 * resolved_rect["y"] + resolved_rect["height"]
    if center_y_twice < canvas["height"]:
        anchor_y = "TOP"
        vertical_offset = resolved_rect["y"]
    else:
        anchor_y = "BOTTOM"
        vertical_offset = (
            canvas["height"] - resolved_rect["y"] - resolved_rect["height"]
        )
    return {
        "anchorX": anchor,
        "anchorY": anchor_y,
        "horizontalOffset": offset,
        "verticalOffset": vertical_offset,
    }


def _visible(rect, bounds):
    return (
        min(rect["x"] + rect["width"], bounds["x"] + bounds["width"])
        - max(rect["x"], bounds["x"]) >= 1
        and min(rect["y"] + rect["height"], bounds["y"] + bounds["height"])
        - max(rect["y"], bounds["y"]) >= 1
    )


def map_element(canvas, element, overlay_bounds, orientation):
    if overlay_bounds["width"] <= 0 or overlay_bounds["height"] <= 0:
        fail("invalidOverlayBounds")
    if orientation not in {"landscape", "portrait"}:
        fail("invalidViewportOrientation")
    scale_x_num, scale_x_den = overlay_bounds["width"], canvas["width"]
    scale_y_num, scale_y_den = overlay_bounds["height"], canvas["height"]
    size_num, size_den = scale_y_num, scale_y_den
    rect = element["rect"]
    width = _round_half_up(rect["width"] * size_num, size_den)
    height = _round_half_up(rect["height"] * size_num, size_den)
    offset_x = _round_half_up(rect["horizontalOffset"] * scale_x_num, scale_x_den)
    offset_y = _round_half_up(rect["verticalOffset"] * scale_y_num, scale_y_den)
    if width < 1 or height < 1:
        fail("targetBelowMinimum")
    left = overlay_bounds["x"] + _anchor_origin(
        overlay_bounds["width"], width, offset_x, element["anchorX"]
    )
    top = overlay_bounds["y"] + _anchor_origin(
        overlay_bounds["height"], height, offset_y, element["anchorY"]
    )
    mapped = {"x": left, "y": top, "width": width, "height": height}
    if not _visible(mapped, overlay_bounds):
        fail("targetNotVisible")
    return mapped


def map_scene(canvas, elements, overlay_bounds, orientation, minimum_target_px=1):
    mapped = []
    for element in elements:
        rect = map_element(canvas, element, overlay_bounds, orientation)
        if rect["width"] < minimum_target_px or rect["height"] < minimum_target_px:
            fail("targetBelowMinimum")
        mapped.append((element, rect))
    return [rect for _, rect in mapped]


def hit_test(elements, point):
    for element in sorted(elements, key=lambda item: item["zOrder"], reverse=True):
        if not element.get("enabled", True) or element.get("hidden", False):
            continue
        rect = element["visibleRect"]
        if (
            rect["x"] <= point["x"] < rect["x"] + rect["width"]
            and rect["y"] <= point["y"] < rect["y"] + rect["height"]
        ):
            return element["elementId"]
    return None


def create_variant_canvas(target):
    width = target.get("widthPx")
    height = target.get("heightPx")
    orientation = target.get("orientation")
    if (
        isinstance(width, bool) or isinstance(height, bool)
        or not isinstance(width, int) or not isinstance(height, int)
        or not 1 <= width <= 32768 or not 1 <= height <= 32768
    ):
        fail("viewportOutOfRange")
    if orientation not in {"portrait", "landscape"}:
        fail("invalidViewportOrientation")
    actual = "landscape" if width >= height else "portrait"
    if orientation != actual:
        fail("viewportOrientationMismatch")
    return {"width": width, "height": height}


def _canonical_set(values, order, path):
    if len(values) != len(set(values)) or values != sorted(values, key=order.__getitem__):
        fail("nonCanonicalSetOrder", path)


def validate(document):
    if document.get("format") != "ligase-touch-layout" or document.get("schemaVersion") != 3:
        fail("invalidFormat")
    if not UUID_D.fullmatch(document.get("layoutId", "")):
        fail("invalidLayoutId")
    revision = document.get("revision")
    if isinstance(revision, bool) or not isinstance(revision, int) or not 1 <= revision <= SAFE_INTEGER:
        fail("invalidRevision")
    next_batch = document.get("nextKeyboardBatchOrdinal")
    if (
        isinstance(next_batch, bool)
        or not isinstance(next_batch, int)
        or not 1 <= next_batch <= SAFE_INTEGER
    ):
        fail("invalidNextKeyboardBatchOrdinal")
    valid_scalar_string(document.get("displayName"), maximum=80, path="/displayName")
    opacity = document.get("opacityPermille")
    if (
        isinstance(opacity, bool)
        or not isinstance(opacity, int)
        or not 0 <= opacity <= 1000
    ):
        fail("invalidLayoutOpacity", "/opacityPermille")
    variants = document.get("variants")
    if not isinstance(variants, list) or not 1 <= len(variants) <= 32:
        fail("invalidVariantCount")
    variant_ids = [variant.get("variantId") for variant in variants]
    if (
        any(not UUID_D.fullmatch(item or "") for item in variant_ids)
        or len(set(variant_ids)) != len(variant_ids)
    ):
        fail("invalidVariantId")
    if variant_ids != sorted(variant_ids):
        fail("nonCanonicalVariantOrder")
    extensions = document.get("extensions", {})
    if len(extensions) > 32:
        fail("extensionPropertiesExceeded", "/extensions")
    validate_extension(extensions)
    for vi, variant in enumerate(variants):
        device_classes = variant["deviceClasses"]
        orientations = variant["orientations"]
        _canonical_set(device_classes, DEVICE_ORDER, f"/variants/{vi}/deviceClasses")
        _canonical_set(orientations, ORIENTATION_ORDER, f"/variants/{vi}/orientations")
        canvas = variant["canvas"]
        width, height = canvas["width"], canvas["height"]
        recommendation = variant["recommendation"]
        ratios = [
            Fraction(recommendation[name]["numerator"], recommendation[name]["denominator"])
            for name in ("minAspectRatio", "preferredAspectRatio", "maxAspectRatio")
        ]
        if not ratios[0] <= ratios[1] <= ratios[2]:
            fail("invalidAspectRange", f"/variants/{vi}/recommendation")
        elements = variant["elements"]
        if not 1 <= len(elements) <= 512:
            fail("invalidElementCount", f"/variants/{vi}/elements")
        ids = [element["elementId"] for element in elements]
        if len(ids) != len(set(ids)):
            fail("duplicateElementId", f"/variants/{vi}/elements")
        z_orders = [element["zOrder"] for element in elements]
        if len(z_orders) != len(set(z_orders)):
            fail("duplicateZOrder", f"/variants/{vi}/elements")
        expected = sorted(elements, key=lambda item: (item["zOrder"], item["elementId"]))
        if elements != expected:
            fail("nonCanonicalElementOrder", f"/variants/{vi}/elements")
        for ei, element in enumerate(elements):
            path = f"/variants/{vi}/elements/{ei}"
            if not UUID_D.fullmatch(element["elementId"]):
                fail("invalidElementId", path + "/elementId")
            if element["kind"] not in KINDS:
                fail("unknownKind", path + "/kind")
            resolved = resolve_element_rect(canvas, element)
            if not _visible(resolved, {"x": 0, "y": 0, "width": width, "height": height}):
                fail("rectNotVisible", path + "/rect")
            validate_payload(element["kind"], element["payload"], path + "/payload")
            if "sourceReference" in element:
                valid_scalar_string(element["sourceReference"], maximum=160, path=path + "/sourceReference")
    material = deepcopy(document)
    actual = material.pop("contentHash", None)
    expected_hash = "sha256:" + hashlib.sha256(jcs(material)).hexdigest()
    if actual != expected_hash:
        fail("contentHashMismatch")
    return document


def validate_descriptor_alignment(content, descriptor):
    if set(descriptor) != {"layoutId", "revision", "variants"}:
        fail("invalidDescriptorProjection")
    if descriptor["layoutId"] != content["layoutId"] or descriptor["revision"] != content["revision"]:
        fail("descriptorIdentityMismatch")
    touch = [item for item in descriptor["variants"] if item.get("inputProfile") == "touch"]
    if any(
        set(item) != {"variantId", "inputProfile", "deviceClasses", "orientations"}
        for item in descriptor["variants"]
    ):
        fail("invalidDescriptorProjection")
    touch_ids = [item["variantId"] for item in touch]
    if len(touch_ids) != len(set(touch_ids)):
        fail("duplicateDescriptorVariant")
    content_by_id = {item["variantId"]: item for item in content["variants"]}
    if set(touch_ids) != set(content_by_id):
        fail("descriptorVariantSetMismatch")
    for item in touch:
        content_variant = content_by_id[item["variantId"]]
        if (
            item["deviceClasses"] != content_variant["deviceClasses"]
            or item["orientations"] != content_variant["orientations"]
        ):
            fail("descriptorEligibilityMismatch", item["variantId"])


def content_hash(document):
    material = deepcopy(document)
    material.pop("contentHash", None)
    return "sha256:" + hashlib.sha256(jcs(material)).hexdigest()


def validate_schema(schema_path: Path, documents):
    try:
        from jsonschema import Draft202012Validator
    except ImportError as exc:
        raise SystemExit("jsonschema 4.x is required") from exc
    schema = strict_load(schema_path)
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for path, value in documents:
        errors = sorted(validator.iter_errors(value), key=lambda error: list(error.path))
        if errors:
            fail("schemaRejected", str(path), errors[0].message)


def run_jcs_vectors(path: Path):
    vectors = strict_load_vector(path)
    for case in vectors["jcsCases"]:
        actual = jcs(case["input"]).decode("utf-8")
        if actual != case["expected"]:
            fail("jcsMismatch", case["id"], actual)


def run_rounding_vectors(path: Path):
    vectors = strict_load_vector(path)
    for case in vectors["roundingCases"]:
        actual = _round_half_up(case["numerator"], case["denominator"])
        if actual != case["expected"]:
            fail("roundingMismatch", case["id"], str(actual))


def run_viewport_vectors(path: Path):
    vectors = strict_load_vector(path)
    for case in vectors["viewportCases"]:
        try:
            actual = map_element(
                case["canvas"], case["element"], case["overlayBounds"], case["orientation"]
            )
        except ValidationError as exc:
            if case.get("expectedError") != exc.code:
                fail("wrongViewportResult", case["id"], exc.code)
        else:
            if actual != case.get("expectedRect"):
                fail("viewportMismatch", case["id"], str(actual))

    for case in vectors["anchorRebaseCases"]:
        actual = rebase_anchor(case["canvas"], case["resolvedRect"])
        if actual != case["expected"]:
            fail("anchorRebaseMismatch", case["id"], str(actual))
        rebased = {
            "rect": {
                "horizontalOffset": actual["horizontalOffset"],
                "verticalOffset": actual["verticalOffset"],
                "width": case["resolvedRect"]["width"],
                "height": case["resolvedRect"]["height"],
            },
            "anchorX": actual["anchorX"],
            "anchorY": actual["anchorY"],
        }
        if resolve_element_rect(case["canvas"], rebased) != case["resolvedRect"]:
            fail("anchorRebaseMoved", case["id"])

    for case in vectors["targetSceneCases"]:
        try:
            actual = map_scene(
                case["canvas"],
                case["elements"],
                case["overlayBounds"],
                case["orientation"],
                case.get("minimumTargetPx", 1),
            )
        except ValidationError as exc:
            if case.get("expectedError") != exc.code:
                fail("wrongTargetSceneResult", case["id"], exc.code)
        else:
            if actual != case.get("expectedRects"):
                fail("targetSceneMismatch", case["id"], str(actual))

    for case in vectors["hitTestCases"]:
        actual = hit_test(case["elements"], case["point"])
        if actual != case["expectedElementId"]:
            fail("hitTestMismatch", case["id"], str(actual))

    for case in vectors["variantCreationCases"]:
        try:
            actual = create_variant_canvas(case["targetViewport"])
        except ValidationError as exc:
            if case.get("expectedError") != exc.code:
                fail("wrongVariantCreationResult", case["id"], exc.code)
        else:
            if actual != case.get("expectedCanvas"):
                fail("variantCreationMismatch", case["id"], str(actual))


def run_descriptor_vectors(path: Path, base):
    vectors = strict_load_vector(path)
    for case in vectors["descriptorCases"]:
        try:
            validate_descriptor_alignment(base, case["descriptor"])
        except ValidationError as exc:
            if case["expected"] != exc.code:
                fail("wrongDescriptorResult", case["id"], exc.code)
        else:
            if case["expected"] != "accepted":
                fail("descriptorNegativeAccepted", case["id"])


def run_kind_vectors(path: Path, base, schema_validator):
    vectors = strict_load_vector(path)
    for case in vectors["kindCases"]:
        candidate = deepcopy(base)
        payload_path = f"/variants/0/elements/{case['elementIndex']}/payload"
        mutation = case["mutation"]
        target = payload_path + mutation["path"]
        if mutation.get("op", "replace") == "delete":
            pointer_delete(candidate, target)
        else:
            pointer_set(candidate, target, deepcopy(mutation["value"]))
        if case.get("rehash"):
            candidate["contentHash"] = content_hash(candidate)
        errors = list(schema_validator.iter_errors(candidate))
        expected = case["expected"]
        if expected == "schemaRejected":
            if not errors:
                fail("kindNegativeAccepted", case["id"])
            continue
        if errors:
            fail("unexpectedSchemaRejection", case["id"], errors[0].message)
        try:
            validate(candidate)
        except ValidationError as exc:
            if exc.code != expected:
                fail("wrongKindResult", case["id"], exc.code)
        else:
            fail("kindNegativeAccepted", case["id"])


def _editor_chord_error(keys):
    if not isinstance(keys, list) or len(keys) == 0:
        return "EMPTY_CHORD"
    if len(keys) > 16:
        return "TOO_MANY_KEYS"
    for index, key in enumerate(keys):
        try:
            validate_input_code(key, f"/editorChord/{index}")
        except ValidationError:
            return "UNSUPPORTED_INPUT_CODE"
    identities = [(item["namespace"], item["code"]) for item in keys]
    if len(identities) != len(set(identities)):
        return "DUPLICATE_KEY"
    return None


def _canonical_editor_chord(keys):
    return sorted(
        keys,
        key=lambda item: (
            INPUT_NAMESPACE_ORDER[item["namespace"]],
            item["code"],
        ),
    )


def _assert_zero_write(case):
    if (
        case["mutationApplied"] is not False
        or case.get("writes") != 0
        or case.get("stateBytesUnchanged") is not True
    ):
        fail("editorMutationWasNotAtomic", case["id"])


def run_editor_mutation_vectors(path: Path):
    vectors = strict_load_vector(path)
    common_errors = set(vectors["commonEditorActionErrors"])
    declared_errors = {
        operation: set(errors)
        for operation, errors in vectors["editorActionErrorSets"].items()
    }
    observed_errors = {operation: set() for operation in declared_errors}
    for case in vectors["editorMutationCases"]:
        operation = case["operation"]
        if operation not in declared_errors:
            fail("unknownEditorMutationVector", case["id"], operation)
        expected_error = case.get("expectedError")
        if expected_error is not None:
            if (
                expected_error not in declared_errors[operation]
                and expected_error not in common_errors
            ):
                fail("undeclaredEditorActionError", case["id"], expected_error)
            if expected_error in declared_errors[operation]:
                observed_errors[operation].add(expected_error)
        if operation == "replaceComboChord":
            keys = case["inputKeys"]
            error = _editor_chord_error(keys)
            if error is not None:
                if case.get("expectedError") != error:
                    fail("wrongEditorMutationResult", case["id"], error)
                _assert_zero_write(case)
            else:
                canonical = _canonical_editor_chord(keys)
                if canonical != case.get("expectedCanonicalKeys"):
                    fail("editorMutationCanonicalMismatch", case["id"])
                if case["mutationApplied"] is not True:
                    fail("editorMutationWasNotApplied", case["id"])
        elif operation == "addRadialAction":
            if "inputKeys" in case:
                error = _editor_chord_error(case["inputKeys"])
                if error is None and "label" in case:
                    try:
                        valid_scalar_string(
                            case["label"], maximum=32, path="/editorMutation/label"
                        )
                    except ValidationError:
                        error = "INVALID_LABEL"
                if error is not None:
                    if case.get("expectedError") != error:
                        fail("wrongEditorMutationResult", case["id"], error)
                    if case.get("candidateIdsConsumed") != 0:
                        fail("radialIdentityConsumedBeforeValidation", case["id"])
                    _assert_zero_write(case)
                    continue
            if case.get("radialActionCount", 0) >= 16:
                if case.get("expectedError") != "ACTION_LIMIT":
                    fail("wrongEditorMutationResult", case["id"], "ACTION_LIMIT")
                if case.get("candidateIdsConsumed") != 0:
                    fail("radialIdentityConsumedBeforeValidation", case["id"])
                _assert_zero_write(case)
                continue
            existing = set(case["existingActionIds"])
            accepted = None
            attempts = 0
            for candidate in case["generatedCandidates"][:3]:
                attempts += 1
                if not UUID_D.fullmatch(candidate):
                    break
                if candidate not in existing:
                    accepted = candidate
                    break
            if attempts != case["attempts"]:
                fail("radialIdentityAttemptMismatch", case["id"])
            if accepted is None:
                if (
                    case.get("expectedError") != "ID_GENERATION_FAILED"
                ):
                    fail("radialIdentityFailureMismatch", case["id"])
                _assert_zero_write(case)
            elif (
                accepted != case.get("expectedActionId")
                or case["mutationApplied"] is not True
            ):
                fail("radialIdentitySuccessMismatch", case["id"])
            if "saveReopenActionId" in case and case["saveReopenActionId"] != accepted:
                fail("radialIdentityReopenMismatch", case["id"])
        elif operation == "replaceRadialActionChord":
            if "inputKeys" in case:
                error = _editor_chord_error(case["inputKeys"])
                if case.get("expectedError") != error:
                    fail("wrongEditorMutationResult", case["id"], str(error))
                _assert_zero_write(case)
            elif case.get("expectedError") == "UNKNOWN_ACTION":
                _assert_zero_write(case)
            else:
                fail("radialTargetedFailureMismatch", case["id"])
        elif operation in {
            "removeRadialAction",
            "reorderRadialAction",
            "replaceRadialActionLabel",
        }:
            _assert_zero_write(case)
    for operation, declared in declared_errors.items():
        missing = declared - observed_errors[operation]
        if missing:
            fail(
                "unreachableDeclaredEditorActionError",
                operation,
                ",".join(sorted(missing)),
            )


def run_negative_vectors(schema_path: Path, base_path: Path, vectors_path: Path):
    try:
        from jsonschema import Draft202012Validator
    except ImportError as exc:
        raise SystemExit("jsonschema 4.x is required") from exc
    schema_validator = Draft202012Validator(strict_load(schema_path))
    base = strict_load(base_path)
    vectors = strict_load_vector(vectors_path)
    run_jcs_vectors(vectors_path)
    run_rounding_vectors(vectors_path)
    run_viewport_vectors(vectors_path)
    run_descriptor_vectors(vectors_path, base)
    run_kind_vectors(vectors_path, base, schema_validator)
    run_editor_mutation_vectors(vectors_path)
    for case in vectors["documentCases"]:
        candidate = deepcopy(base)
        for mutation in case.get("mutations", []):
            operation = mutation.get("op", "replace")
            replacement = (
                deepcopy(pointer_get(candidate, mutation["valueFrom"]))
                if "valueFrom" in mutation
                else deepcopy(mutation.get("value"))
            )
            if operation == "replace":
                pointer_set(candidate, mutation["path"], replacement)
            elif operation == "append":
                pointer_get(candidate, mutation["path"]).append(replacement)
            elif operation == "repeatAppend":
                target = pointer_get(candidate, mutation["path"])
                for index in range(mutation["count"]):
                    item = deepcopy(replacement)
                    if isinstance(item, dict) and "elementId" in item:
                        item["elementId"] = f"00000000-0000-0000-0001-{index:012x}"
                    if isinstance(item, dict) and "variantId" in item:
                        item["variantId"] = f"00000000-0000-0000-0002-{index:012x}"
                    target.append(item)
            elif operation == "fillObject":
                pointer_set(
                    candidate,
                    mutation["path"],
                    {f"x{index:02d}": replacement for index in range(mutation["count"])},
                )
            elif operation == "fillArray":
                pointer_set(
                    candidate,
                    mutation["path"],
                    [deepcopy(replacement) for _ in range(mutation["count"])],
                )
            elif operation == "delete":
                pointer_delete(candidate, mutation["path"])
            else:
                fail("invalidVectorOperation", case["id"], operation)
        if case.get("rehash"):
            candidate["contentHash"] = content_hash(candidate)
        schema_errors = list(schema_validator.iter_errors(candidate))
        expected = case["expected"]
        if expected == "accepted":
            if schema_errors:
                fail("unexpectedSchemaRejection", case["id"], schema_errors[0].message)
            validate(candidate)
        elif expected == "schemaRejected":
            if not schema_errors:
                fail("negativeAccepted", case["id"])
        else:
            if schema_errors:
                fail("unexpectedSchemaRejection", case["id"], schema_errors[0].message)
            try:
                validate(candidate)
            except ValidationError as exc:
                if exc.code != expected:
                    fail("wrongNegativeResult", case["id"], exc.code)
            else:
                fail("negativeAccepted", case["id"])
    for case in vectors["rawCases"]:
        raw = bytes.fromhex(case["hex"])
        try:
            strict_load_bytes(raw)
        except ValidationError as exc:
            if exc.code != case["expected"]:
                fail("wrongRawResult", case["id"], exc.code)
        else:
            fail("negativeAccepted", case["id"])


def pointer_set(value, pointer, replacement):
    tokens = pointer.strip("/").split("/")
    parent = value
    for token in tokens[:-1]:
        token = token.replace("~1", "/").replace("~0", "~")
        parent = parent[int(token)] if isinstance(parent, list) else parent[token]
    final = tokens[-1].replace("~1", "/").replace("~0", "~")
    if isinstance(parent, list):
        parent[int(final)] = replacement
    else:
        parent[final] = replacement


def pointer_get(value, pointer):
    for token in pointer.strip("/").split("/") if pointer else []:
        token = token.replace("~1", "/").replace("~0", "~")
        value = value[int(token)] if isinstance(value, list) else value[token]
    return value


def pointer_delete(value, pointer):
    tokens = pointer.strip("/").split("/")
    parent = value
    for token in tokens[:-1]:
        token = token.replace("~1", "/").replace("~0", "~")
        parent = parent[int(token)] if isinstance(parent, list) else parent[token]
    final = tokens[-1].replace("~1", "/").replace("~0", "~")
    if isinstance(parent, list):
        del parent[int(final)]
    else:
        del parent[final]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("documents", nargs="*", type=Path)
    parser.add_argument("--schema", required=True, type=Path)
    parser.add_argument("--negative-vectors", type=Path)
    parser.add_argument("--base", type=Path)
    args = parser.parse_args()
    values = [(path, strict_load(path)) for path in args.documents]
    validate_schema(args.schema, values)
    for path, value in values:
        validate(value)
        print(f"PASS {path} {hashlib.sha256(path.read_bytes()).hexdigest()}")
    if args.negative_vectors:
        if not args.base:
            raise SystemExit("--negative-vectors requires --base")
        run_negative_vectors(args.schema, args.base, args.negative_vectors)
        print("NEGATIVE_VECTORS_PASS")


if __name__ == "__main__":
    main()
