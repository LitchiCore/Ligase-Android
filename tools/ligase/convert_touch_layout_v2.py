#!/usr/bin/env python3
"""Offline, fail-closed TouchKit v1 to Ligase Touch Layout v2 converter."""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_touch_layout_v2 import (  # noqa: E402
    CONTROL, MAX_RAW_BYTES, UUID_D, content_hash, intersects, strict_load,
    strict_load_bytes, validate, validate_schema,
)

FORMAT = "ligase-touch-layout"
NAMESPACE = uuid.UUID("f798b97a-0cf9-4efc-b01e-e232cba3df71")
META = {
    "__touchkit_layout_format_version",
    "__touchkit_canvas_width",
    "__touchkit_canvas_height",
    "__touchkit_deleted_base_elements",
    "__touchkit_dynamic_elements",
    "__touchkit_authoritative_draw_order",
}
COMMON = {
    "LEFT", "TOP", "WIDTH", "HEIGHT", "ENABLED", "HIDDEN", "BACKGROUND_OPACITY",
    "LABEL", "DESCRIPTION", "BUTTON_SHAPE", "TRIGGER_MODE",
    "TIMED_HOLD_DURATION_MS", "EDITABLE_KEY_CODE", "SHOW_PHYSICAL_KEY_NAMES",
    "STICK_BINDINGS", "RADIAL_NAME", "RADIAL_SPEC", "SCROLL_STEP",
    "CONTINUOUS_SCROLL", "SCROLL_CADENCE_MS", "COMBO_SPEC",
    "GYRO_SENSITIVITY", "GYRO_DEADZONE", "GYRO_INVERT_X", "GYRO_INVERT_Y",
    "GYRO_TARGET", "GYRO_FRAME", "GYRO_AXES",
}
KEYS = {
    "A": 29, "B": 30, "C": 31, "D": 32, "E": 33, "F": 34, "G": 35,
    "H": 36, "I": 37, "J": 38, "K": 39, "L": 40, "M": 41, "N": 42,
    "O": 43, "P": 44, "Q": 45, "R": 46, "S": 47, "T": 48, "U": 49,
    "V": 50, "W": 51, "X": 52, "Y": 53, "Z": 54, "SPACE": 62,
    "LEFTALT": 57, "F1": 131, "F2": 132, "1": 8,
}


class ConversionError(ValueError):
    def __init__(self, reason, path="", detail=""):
        super().__init__(reason)
        self.reason, self.path, self.detail = reason, path, detail


def strict_nested(text, path):
    if not isinstance(text, str):
        raise ConversionError("malformedLegacyConfiguration", path)
    try:
        return strict_load_bytes(text.encode("utf-8"), max_bytes=256 * 1024)
    except Exception as exc:
        reason = getattr(exc, "code", "malformedLegacyConfiguration")
        raise ConversionError(reason, path) from exc


def pref_value(entry, path):
    if not isinstance(entry, dict) or set(entry) != {"type", "value"}:
        raise ConversionError("invalidLegacyPreference", path)
    value_type, value = entry["type"], entry["value"]
    if value_type == "int" and isinstance(value, int) and not isinstance(value, bool):
        return value
    if value_type == "string" and isinstance(value, str):
        return value
    raise ConversionError("unsupportedLegacyPreference", path)


def input_code(code):
    if isinstance(code, str):
        if code.startswith("0x"):
            code = int(code, 16)
        else:
            mapped = KEYS.get(code.strip().upper())
            if mapped is None:
                raise ConversionError("invalidLegacyBinding", "", code)
            code = mapped
    if isinstance(code, bool) or not isinstance(code, int) or not 0 <= code <= 65535:
        raise ConversionError("invalidLegacyBinding")
    return {"namespace": "androidKeyCode", "code": code}


def chord(spec):
    parts = [item.strip() for item in spec.split("+") if item.strip()]
    if not parts:
        raise ConversionError("invalidLegacyBinding")
    return [input_code(item) for item in parts]


def trigger(config):
    raw = config.get("TRIGGER_MODE")
    if raw is None:
        raise ConversionError("missingLegacyField", "", "TRIGGER_MODE")
    mapped = {"HOLD": "hold", "TOGGLE": "toggle", "PRESS": "tap", "TIMED_HOLD": "timedHold"}.get(str(raw).upper())
    if mapped is None:
        raise ConversionError("unknownLegacyEnum", "", str(raw))
    timed = config.get("TIMED_HOLD_DURATION_MS")
    if mapped == "timedHold":
        if not isinstance(timed, int) or isinstance(timed, bool):
            raise ConversionError("missingLegacyField", "", "TIMED_HOLD_DURATION_MS")
    elif timed is not None:
        raise ConversionError("unsupportedLegacyField", "", "TIMED_HOLD_DURATION_MS")
    return mapped, timed


def appearance(config, descriptor):
    shape = config.get("BUTTON_SHAPE")
    if shape is None:
        raise ConversionError("missingLegacyField", "", "BUTTON_SHAPE")
    shape = str(shape).lower()
    if shape not in {"circle", "rectangle", "roundedrectangle"}:
        raise ConversionError("unknownLegacyEnum", "", shape)
    return {
        "label": str(config.get("LABEL", descriptor.get("name", ""))),
        "description": str(config.get("DESCRIPTION", descriptor.get("desc", ""))),
        "shape": shape,
        "showPhysicalKeyNames": bool(config.get("SHOW_PHYSICAL_KEY_NAMES", False)),
    }


def directional(spec):
    labels = {
        "上": "up", "下": "down", "左": "left", "右": "right", "中": "press",
        "UP": "up", "DOWN": "down", "LEFT": "left", "RIGHT": "right",
        "PRESS": "press",
    }
    result = {}
    for line in spec.splitlines():
        if not line.strip() or "=" not in line:
            raise ConversionError("invalidLegacyBinding")
        label, binding = line.split("=", 1)
        target = labels.get(label.strip().upper()) or labels.get(label.strip())
        if target is None or target in result:
            raise ConversionError("invalidLegacyBinding")
        result[target] = chord(binding)
    return result


def payload(kind, element_id, descriptor, config):
    descriptor = descriptor or {}
    if kind == "keyboard":
        code = descriptor.get("code", config.get("EDITABLE_KEY_CODE"))
        if code is None:
            raise ConversionError("missingLegacyField", element_id, "key code")
        mode, timed = trigger(config)
        return {
            "payloadKind": kind, "inputCode": input_code(code),
            "appearance": appearance(config, descriptor), "trigger": mode,
            "timedHoldMs": timed,
        }
    if kind == "mouse":
        code = descriptor.get("code")
        buttons = {1: "primary", 2: "secondary", 3: "middle", 4: "back", 5: "forward"}
        if code not in buttons:
            raise ConversionError("unknownLegacyMouseButton", element_id, str(code))
        mode, timed = trigger(config)
        return {
            "payloadKind": kind, "button": buttons[code],
            "appearance": appearance(config, descriptor), "trigger": mode,
            "timedHoldMs": timed,
        }
    if kind in {"analog", "dpad"}:
        fields = {"up": "upCode", "down": "downCode", "left": "leftCode", "right": "rightCode"}
        if all(field in descriptor for field in fields.values()):
            values = {name: [input_code(descriptor[field])] for name, field in fields.items()}
            if "middleCode" in descriptor:
                values["press"] = [input_code(descriptor["middleCode"])]
        else:
            if "STICK_BINDINGS" not in config:
                raise ConversionError("missingLegacyField", element_id, "STICK_BINDINGS")
            values = directional(str(config["STICK_BINDINGS"]))
        if any(name not in values for name in fields):
            raise ConversionError("invalidLegacyBinding", element_id)
        result = {"payloadKind": kind, **values}
        result["diagonalPolicy"] = "vector" if kind == "analog" else "allowTwoDirections"
        return result
    if kind in {"customKeys", "combo"}:
        raw = descriptor.get("keys") if kind == "customKeys" else None
        keys = [input_code(item) for item in raw] if isinstance(raw, list) else chord(
            str(config.get("COMBO_SPEC", ""))
        )
        mode, timed = trigger(config)
        result = {
            "payloadKind": kind, "keys": keys, "pressOrder": "listed",
            "releaseOrder": "reverseListed", "trigger": mode, "timedHoldMs": timed,
            "appearance": appearance(config, descriptor),
        }
        if kind == "customKeys":
            result["sticky"] = bool(descriptor.get("sticky", False))
            if result["sticky"] and mode != "toggle":
                raise ConversionError("stickyTriggerConflict", element_id)
        return result
    if kind == "radial":
        if "RADIAL_SPEC" not in config:
            raise ConversionError("missingLegacyField", element_id, "RADIAL_SPEC")
        actions = []
        for line in str(config["RADIAL_SPEC"]).splitlines():
            if "=" not in line:
                raise ConversionError("invalidLegacyBinding", element_id)
            binding, label = line.split("=", 1)
            actions.append({"keys": chord(binding), "label": label})
        return {
            "payloadKind": kind, "label": str(config.get("RADIAL_NAME", descriptor.get("name", ""))),
            "startAngleMilliDegrees": -90000, "direction": "clockwise",
            "boundaryPolicy": "clockwiseInclusive", "actions": actions,
        }
    if kind == "scroll":
        raw = descriptor.get("scroll")
        direction = {1: "verticalPositive", -1: "verticalNegative"}.get(raw)
        if direction is None:
            raise ConversionError("unknownLegacyEnum", element_id, "scroll")
        mode, timed = trigger(config)
        continuous = config.get("CONTINUOUS_SCROLL")
        if not isinstance(continuous, bool):
            raise ConversionError("missingLegacyField", element_id, "CONTINUOUS_SCROLL")
        result = {
            "payloadKind": kind, "direction": direction, "stepUnit": "wheelDetent",
            "step": config.get("SCROLL_STEP"), "continuous": continuous,
            "cadenceMs": config.get("SCROLL_CADENCE_MS") if continuous else None,
            "appearance": appearance(config, descriptor), "trigger": mode,
            "timedHoldMs": timed,
        }
        return result
    if kind == "softKeyboard":
        return {"payloadKind": kind, "action": "openSystemIme", "executionScope": "androidLocal"}
    if kind == "gyro":
        required = {"GYRO_TARGET", "GYRO_FRAME", "GYRO_AXES", "GYRO_SENSITIVITY", "GYRO_DEADZONE"}
        missing = required - set(config)
        if missing:
            raise ConversionError("missingLegacyField", element_id, sorted(missing)[0])
        return {
            "payloadKind": kind, "target": config["GYRO_TARGET"],
            "frame": config["GYRO_FRAME"], "axes": config["GYRO_AXES"],
            "scaleUnit": "milliUnitsPerDegreePerSecond",
            "scale": int(config["GYRO_SENSITIVITY"]) * 1000,
            "deadzoneMilliDegreesPerSecond": int(config["GYRO_DEADZONE"]) * 1000,
            "invertX": bool(config.get("GYRO_INVERT_X", False)),
            "invertY": bool(config.get("GYRO_INVERT_Y", False)),
        }
    raise ConversionError("unknownLegacyControl", element_id)


def infer_kind(descriptor):
    mapping = {
        0: "keyboard", 1: "mouse", 2: "analog", 3: "dpad", 4: "customKeys",
        5: "radial", 6: "scroll", 7: "combo", 8: "softKeyboard", 9: "gyro",
    }
    kind = mapping.get(descriptor.get("type"))
    if kind is None:
        raise ConversionError("unknownLegacyControl", descriptor.get("elementId", ""))
    return kind


def convert(source, layout_id, variant_id):
    if source.get("format") != "artemis-touchkit-layout" or source.get("version") != 1:
        raise ConversionError("invalidLegacyEnvelope")
    if set(source) != {"format", "version", "name", "sourceLayoutId", "preferences"}:
        raise ConversionError("unsupportedLegacyField")
    prefs_raw = source.get("preferences")
    if not isinstance(prefs_raw, dict):
        raise ConversionError("invalidLegacyEnvelope", "/preferences")
    prefs = {key: pref_value(value, f"/preferences/{key}") for key, value in prefs_raw.items()}
    width, height = prefs.get("__touchkit_canvas_width"), prefs.get("__touchkit_canvas_height")
    if not all(isinstance(value, int) and not isinstance(value, bool) for value in (width, height)):
        raise ConversionError("invalidLegacyGeometry")
    dynamic = strict_nested(prefs.get("__touchkit_dynamic_elements"), "/preferences/__touchkit_dynamic_elements")
    deleted = strict_nested(prefs.get("__touchkit_deleted_base_elements"), "/preferences/__touchkit_deleted_base_elements")
    draw_order_raw = prefs.get("__touchkit_authoritative_draw_order")
    draw_order = strict_nested(draw_order_raw, "/preferences/__touchkit_authoritative_draw_order") if draw_order_raw is not None else None
    if not isinstance(dynamic, list) or not isinstance(deleted, list):
        raise ConversionError("malformedLegacyConfiguration")
    descriptors = {}
    descriptor_fields = {
        0: {"type", "name", "code", "desc", "elementId"},
        1: {"type", "name", "code", "desc", "elementId"},
        2: {"type", "name", "upCode", "downCode", "leftCode", "rightCode", "middleCode", "elementId"},
        3: {"type", "name", "upCode", "downCode", "leftCode", "rightCode", "elementId"},
        4: {"type", "name", "keys", "sticky", "desc", "elementId"},
        5: {"type", "name", "elementId"},
        6: {"type", "name", "scroll", "desc", "elementId"},
        7: {"type", "name", "desc", "elementId"},
        8: {"type", "name", "elementId"},
        9: {"type", "name", "elementId"},
    }
    for descriptor in dynamic:
        if not isinstance(descriptor, dict) or not isinstance(descriptor.get("elementId"), str):
            raise ConversionError("malformedLegacyConfiguration")
        element_id = descriptor["elementId"]
        if element_id in descriptors:
            raise ConversionError("duplicateLegacyElement", element_id)
        allowed = descriptor_fields.get(descriptor.get("type"))
        if allowed is None or set(descriptor) - allowed:
            raise ConversionError("unsupportedLegacyField", element_id)
        descriptors[element_id] = descriptor
    config_keys = [
        key for key, value in prefs.items()
        if key not in META and key not in deleted and isinstance(value, str)
    ]
    # A legacy artifact without authoritative draw-order cannot be converted
    # when active controls overlap. Detect this before interpreting control
    # kinds so the real Genshin sample has one stable, security-relevant result.
    if draw_order is None:
        legacy_rects = []
        for legacy_id in config_keys:
            config = strict_nested(prefs[legacy_id], f"/preferences/{legacy_id}")
            required = {"LEFT", "TOP", "WIDTH", "HEIGHT", "ENABLED", "HIDDEN"}
            if not isinstance(config, dict) or required - set(config):
                raise ConversionError("missingLegacyField", legacy_id)
            if config["ENABLED"] and not config["HIDDEN"]:
                legacy_rects.append((
                    legacy_id,
                    {
                        "x": config["LEFT"], "y": config["TOP"],
                        "width": config["WIDTH"], "height": config["HEIGHT"],
                    },
                ))
        overlap_count = sum(
            1
            for index, (_, left) in enumerate(legacy_rects)
            for _, right in legacy_rects[index + 1:]
            if intersects(left, right)
        )
        if overlap_count:
            raise ConversionError("collisionDetected", "", str(overlap_count))
    if set(config_keys) != set(descriptors):
        raise ConversionError("orphanLegacyDescriptor")
    if draw_order is not None and (
        not isinstance(draw_order, list)
        or len(draw_order) != len(set(draw_order))
        or set(draw_order) != set(config_keys)
    ):
        raise ConversionError("invalidLegacyDrawOrder")
    order = draw_order if draw_order is not None else sorted(config_keys)
    elements = []
    for legacy_id in order:
        config = strict_nested(prefs[legacy_id], f"/preferences/{legacy_id}")
        if not isinstance(config, dict) or set(config) - COMMON:
            raise ConversionError("unsupportedLegacyField", legacy_id)
        required = {"LEFT", "TOP", "WIDTH", "HEIGHT", "ENABLED", "HIDDEN", "BACKGROUND_OPACITY"}
        if required - set(config):
            raise ConversionError("missingLegacyField", legacy_id)
        rect = {
            "x": config["LEFT"], "y": config["TOP"],
            "width": config["WIDTH"], "height": config["HEIGHT"],
        }
        if (
            any(isinstance(value, bool) or not isinstance(value, int) for value in rect.values())
            or rect["x"] < 0 or rect["y"] < 0 or rect["width"] <= 0 or rect["height"] <= 0
            or rect["x"] + rect["width"] > width or rect["y"] + rect["height"] > height
        ):
            raise ConversionError("invalidLegacyGeometry", legacy_id)
        opacity = config["BACKGROUND_OPACITY"]
        if isinstance(opacity, bool) or not isinstance(opacity, int) or not 0 <= opacity <= 100:
            raise ConversionError("invalidLegacyOpacity", legacy_id)
        descriptor = descriptors[legacy_id]
        kind = infer_kind(descriptor)
        elements.append({
            "elementId": str(uuid.uuid5(NAMESPACE, f"{layout_id}:{variant_id}:{legacy_id}")),
            "kind": kind, "rect": rect, "horizontalAnchor": "left",
            "verticalAnchor": "top", "zOrder": order.index(legacy_id),
            "enabled": config["ENABLED"], "hidden": config["HIDDEN"],
            "opacityPermille": opacity * 10,
            "payload": payload(kind, legacy_id, descriptor, config),
            "sourceReference": legacy_id,
        })
    active = [item for item in elements if item["enabled"] and not item["hidden"]]
    overlaps = [
        (left["sourceReference"], right["sourceReference"])
        for index, left in enumerate(active) for right in active[index + 1:]
        if intersects(left["rect"], right["rect"])
    ]
    if overlaps and draw_order is None:
        raise ConversionError("collisionDetected", "", str(len(overlaps)))
    elements.sort(key=lambda item: (item["zOrder"], item["elementId"]))
    document = {
        "format": FORMAT, "schemaVersion": 2, "layoutId": layout_id, "revision": 1,
        "displayName": source["name"], "extensions": {},
        "variants": [{
            "variantId": variant_id, "deviceClasses": ["phone"],
            "orientations": ["landscape"],
            "recommendation": {
                "preferredAspectRatio": {"numerator": width, "denominator": height},
                "minAspectRatio": {"numerator": 4, "denominator": 3},
                "maxAspectRatio": {"numerator": 32, "denominator": 9},
                "minShortestSideDp": 320, "minTouchTargetDp": 48,
                "safeAreaPolicy": "videoContent",
                "referenceResolution": {"width": width, "height": height},
            },
            "canvas": {"width": width, "height": height},
            "elements": elements,
        }],
        "contentHash": "",
    }
    document["contentHash"] = content_hash(document)
    report = {
        "result": "converted", "sourceFormat": source["format"],
        "legacySourceReference": source["sourceLayoutId"], "layoutId": layout_id,
        "revision": 1, "variantId": variant_id, "elementCount": len(elements),
        "contentHash": document["contentHash"],
        "anchorConversion": "explicitLeftTopNoInference",
        "drawOrderEvidence": "authoritativeLegacyMetadata" if draw_order is not None else "noActiveOverlap",
    }
    return document, report


def encoded_json(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")


def atomic_replace_bytes(path: Path, raw: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def run(input_path, output_path, report_path, schema_path, layout_id, variant_id):
    source = strict_load(input_path)
    document, report = convert(source, layout_id, variant_id)
    validate_schema(schema_path, [(output_path, document)])
    validate(document)
    # Only validated bytes reach atomic replacement.
    atomic_replace_bytes(output_path, encoded_json(document))
    atomic_replace_bytes(report_path, encoded_json(report))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--schema", required=True, type=Path)
    parser.add_argument("--layout-id", required=True)
    parser.add_argument("--variant-id", required=True)
    args = parser.parse_args()
    if not UUID_D.fullmatch(args.layout_id) or not UUID_D.fullmatch(args.variant_id):
        raise SystemExit("layoutId/variantId must be canonical lowercase UUID D")
    try:
        run(args.input, args.output, args.report, args.schema, args.layout_id, args.variant_id)
    except Exception as exc:
        report = {
            "result": "failed",
            "reason": getattr(exc, "reason", getattr(exc, "code", type(exc).__name__)),
            "path": getattr(exc, "path", ""),
            "detail": getattr(exc, "detail", ""),
        }
        atomic_replace_bytes(args.report, encoded_json(report))
        raise SystemExit(2)


if __name__ == "__main__":
    main()
