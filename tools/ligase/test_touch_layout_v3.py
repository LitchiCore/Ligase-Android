#!/usr/bin/env python3
"""Mechanical review gates for the Ligase Touch Layout v3 contract."""

from __future__ import annotations

import hashlib
import subprocess
import sys
import uuid
from pathlib import Path

from jsonschema import Draft202012Validator
from validate_touch_layout_v3 import strict_load, strict_load_vector


ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "docs/ligase/ligase-touch-layout-v3.schema.json"
POSITIVE = ROOT / "tests/fixtures/ligase-touch-layout-v3-positive.json"
NEGATIVE = ROOT / "tests/fixtures/ligase-touch-layout-v3-negative-vectors.json"
V3_MANIFEST = ROOT / "tests/fixtures/ligase-touch-layout-v3-sha256.txt"
CUTOVER = ROOT / "docs/ligase/ligase-touch-layout-v3-cutover-states.json"
API_REVIEW = ROOT / "docs/ligase/LIGASE_TOUCH_LAYOUT_V3_ANDROID_API_REVIEW.md"


def verify_manifest(path: Path) -> None:
    for line in path.read_text(encoding="utf-8").splitlines():
        expected, relative = line.split("  ", 1)
        actual = hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()
        assert actual == expected, (relative, expected, actual)


def verify_cutover_state_table() -> None:
    value = strict_load(CUTOVER)
    assert set(value) == {
        "contract", "schemaVersion", "marker", "states", "quarantineTargets",
        "preAuthorityV3TestDataTargets", "mustNotTouch", "negativeScenarios",
    }
    assert value["contract"] == "ligase-touch-layout-v3-cutover"
    assert value["schemaVersion"] == 1
    states = {item["state"]: item for item in value["states"]}
    assert set(states) == {
        "NOT_STARTED", "QUARANTINING", "VERIFYING", "FAILED_CLOSED", "V3_READY",
    }
    assert states["V3_READY"]["v3RepositoriesOpen"] is True
    assert all(
        item["v3RepositoriesOpen"] is False
        for name, item in states.items() if name != "V3_READY"
    )
    assert states["V3_READY"]["next"] == ["V3_READY"]
    declared = set(states)
    assert all(set(item["next"]) <= declared for item in states.values())
    targets = value["quarantineTargets"]
    assert targets["directories"] == sorted(targets["directories"])
    assert len(targets["directories"]) == len(set(targets["directories"]))
    assert targets["preferenceKeyPrefix"] == "preferred:"
    must_not_touch = set(value["mustNotTouch"])
    assert not set(targets["directories"]) & must_not_touch
    assert targets["sharedPreferencesFile"] not in must_not_touch
    cases = value["negativeScenarios"]
    assert value["marker"]["version"] == 2
    prior_v3 = value["preAuthorityV3TestDataTargets"]
    assert prior_v3 == {
        "directories": [
            "ligase-touch-layout-v3-drafts",
            "ligase-touch-layout-v3-generations",
        ],
        "acceptedPriorMarkerVersion": 1,
        "requiredReadyMarkerVersion": 2,
        "disposition": "quarantineAndRecreate",
    }
    assert not set(prior_v3["directories"]) & must_not_touch
    assert len(cases) == 10
    assert len({item["id"] for item in cases}) == len(cases)


def main() -> None:
    verify_manifest(V3_MANIFEST)
    verify_cutover_state_table()
    api_review = API_REVIEW.read_text(encoding="utf-8")
    assert "INVALID_CHORD" not in api_review
    add_radial = api_review.split("addRadialAction(", 1)[1].split(
        "removeRadialAction(", 1
    )[0]
    replace_radial_chord = api_review.split(
        "replaceRadialActionChord(", 1
    )[1].split("```", 1)[0]
    for code in (
        "EMPTY_CHORD", "TOO_MANY_KEYS", "UNSUPPORTED_INPUT_CODE", "DUPLICATE_KEY",
    ):
        assert code in add_radial
        assert code in replace_radial_chord
    assert "INVALID_LABEL" in add_radial
    assert "INVALID_LABEL" not in replace_radial_chord
    command = [
        sys.executable,
        str(ROOT / "tools/ligase/validate_touch_layout_v3.py"),
        "--schema", str(SCHEMA),
        "--negative-vectors", str(NEGATIVE),
        "--base", str(POSITIVE),
        str(POSITIVE),
    ]
    first = subprocess.run(command, check=True, capture_output=True, text=True)
    second = subprocess.run(command, check=True, capture_output=True, text=True)
    assert first.stdout == second.stdout
    assert strict_load(POSITIVE)["schemaVersion"] == 3
    vectors = strict_load_vector(NEGATIVE)
    viewport_ids = {item["id"] for item in vectors["viewportCases"]}
    assert {
        "phone-to-tablet-left-top-40-30",
        "phone-to-tablet-left-bottom-40-30",
        "phone-to-tablet-center-top-40-30",
        "phone-to-tablet-center-bottom-40-30",
        "phone-to-tablet-right-top-40-30",
        "phone-to-tablet-right-bottom-40-30",
        "negative-right-bottom-gaps-partial-offscreen",
        "negative-top-gap-partial-offscreen",
        "circle-invariant-wide-tablet",
        "transient-system-bars-do-not-change-map",
        "cutout-diagnostic-does-not-change-map",
        "video-letterbox-does-not-change-map",
    } <= viewport_ids
    assert {
        item["id"] for item in vectors["anchorRebaseCases"]
    } == {
        "outside-left-top",
        "exact-x40-center",
        "signed-center-top",
        "exact-x60-center",
        "outside-right-bottom",
        "y-below-half-top",
        "y-exact-half-bottom",
    }
    assert {item["id"] for item in vectors["targetSceneCases"]} == {
        "narrow-aspect-overlap-valid",
        "minimum-target-fail-closed",
    }
    element_schema = strict_load(SCHEMA)["$defs"]["element"]
    schema = strict_load(SCHEMA)
    positive = strict_load(POSITIVE)
    assert "opacityPermille" in schema["required"]
    assert schema["properties"]["opacityPermille"] == {
        "type": "integer", "minimum": 0, "maximum": 1000,
    }
    assert "opacityPermille" not in element_schema["required"]
    assert "opacityPermille" not in element_schema["properties"]
    assert positive["opacityPermille"] == 420
    assert all(
        "opacityPermille" not in element
        for variant in positive["variants"]
        for element in variant["elements"]
    )
    assert element_schema["properties"]["anchorX"]["enum"] == [
        "LEFT", "CENTER", "RIGHT",
    ]
    assert element_schema["properties"]["anchorY"]["enum"] == ["TOP", "BOTTOM"]
    batch = vectors["keyboardBatchCases"][0]
    namespace_order = {"androidKeyCode": 0, "usbHidKeyboardUsage": 1}
    canonical = sorted(
        {tuple((item["namespace"], item["code"])) for item in batch["input"]},
        key=lambda item: (namespace_order[item[0]], item[1]),
    )
    assert [
        {"namespace": namespace, "code": code} for namespace, code in canonical
    ] == [item["inputCode"] for item in batch["expected"]]
    namespace = uuid.UUID(batch["layoutId"])
    assert [
        str(
            uuid.uuid5(
                namespace,
                "keyboard-batch:"
                f"{batch['batchOrdinal']}:{item['inputCode']['namespace']}:"
                f"{item['inputCode']['code']}",
            )
        )
        for item in batch["expected"]
    ] == [item["elementId"] for item in batch["expected"]]
    assert [item["zOrder"] for item in batch["expected"]] == [10, 11, 12]
    assert all(
        item["anchorX"] == "CENTER"
        and item["anchorY"] == "BOTTOM"
        and item["horizontalOffset"] == 0
        and item["verticalOffset"] == 492
        and item["resolvedRect"] == {"x": 912, "y": 492, "width": 96, "height": 96}
        for item in batch["expected"]
    )
    assert batch["formalSave"] == "accepted"
    assert batch["initialNextKeyboardBatchOrdinal"] == 1
    assert batch["resultNextKeyboardBatchOrdinal"] == 2
    reopened = vectors["keyboardBatchCases"][1]
    assert reopened["journalDeleted"] is True
    assert reopened["reopenedContentNextKeyboardBatchOrdinal"] == 2
    assert reopened["resultNextKeyboardBatchOrdinal"] == 3
    reopened_ids = [
        str(
            uuid.uuid5(
                namespace,
                "keyboard-batch:"
                f"2:{item['inputCode']['namespace']}:{item['inputCode']['code']}",
            )
        )
        for item in reopened["expected"]
    ]
    assert reopened_ids == [item["elementId"] for item in reopened["expected"]]
    assert not set(reopened_ids) & {item["elementId"] for item in batch["expected"]}
    assert vectors["keyboardBatchCases"][2]["count"] == 33
    assert vectors["keyboardBatchCases"][3]["createdCount"] == 0
    assert vectors["keyboardBatchCases"][3]["ordinalConsumed"] is False
    editor_cases = {
        item["id"]: item for item in vectors["editorMutationCases"]
    }
    declared_editor_errors = {
        operation: set(errors)
        for operation, errors in vectors["editorActionErrorSets"].items()
    }
    common_editor_errors = set(vectors["commonEditorActionErrors"])
    observed_editor_errors = {
        operation: {
            item["expectedError"]
            for item in vectors["editorMutationCases"]
            if item["operation"] == operation
            and "expectedError" in item
            and item["expectedError"] not in common_editor_errors
        }
        for operation in declared_editor_errors
    }
    assert observed_editor_errors == declared_editor_errors
    assert editor_cases["combo-input-order-canonicalized"][
        "expectedCanonicalKeys"
    ] == [
        {"namespace": "androidKeyCode", "code": 57},
        {"namespace": "usbHidKeyboardUsage", "code": 5},
    ]
    assert editor_cases["combo-duplicate-input-zero-write"]["expectedError"] == (
        "DUPLICATE_KEY"
    )
    assert editor_cases["combo-empty-input-zero-write"]["expectedError"] == "EMPTY_CHORD"
    assert editor_cases["combo-too-many-input-zero-write"]["expectedError"] == (
        "TOO_MANY_KEYS"
    )
    assert editor_cases["combo-unsupported-input-zero-write"][
        "expectedError"
    ] == "UNSUPPORTED_INPUT_CODE"
    assert editor_cases["radial-owner-generated-first-candidate"][
        "saveReopenActionId"
    ] == editor_cases["radial-owner-generated-first-candidate"][
        "expectedActionId"
    ]
    assert editor_cases["radial-first-collision-second-candidate"][
        "attempts"
    ] == 2
    assert editor_cases["radial-three-collisions-zero-write"][
        "expectedError"
    ] == "ID_GENERATION_FAILED"
    assert editor_cases["radial-three-collisions-zero-write"][
        "mutationApplied"
    ] is False
    assert editor_cases["radial-add-too-many-before-item-validation"][
        "expectedError"
    ] == "TOO_MANY_KEYS"
    assert editor_cases["radial-add-unsupported-before-duplicate"][
        "expectedError"
    ] == "UNSUPPORTED_INPUT_CODE"
    assert editor_cases["radial-add-duplicate-before-invalid-label"][
        "expectedError"
    ] == "DUPLICATE_KEY"
    assert editor_cases["radial-add-capacity-before-id"]["expectedError"] == (
        "ACTION_LIMIT"
    )
    for case_id in (
        "radial-add-empty-chord-before-id",
        "radial-add-too-many-before-item-validation",
        "radial-add-duplicate-chord-before-id",
        "radial-add-unsupported-chord-before-id",
        "radial-add-unsupported-before-duplicate",
        "radial-add-duplicate-before-invalid-label",
        "radial-add-invalid-label-before-id",
    ):
        assert editor_cases[case_id]["candidateIdsConsumed"] == 0
        assert editor_cases[case_id]["writes"] == 0
        assert editor_cases[case_id]["stateBytesUnchanged"] is True
    for case_id, expected in (
        ("radial-replace-empty-chord-zero-write", "EMPTY_CHORD"),
        ("radial-replace-too-many-chord-zero-write", "TOO_MANY_KEYS"),
        ("radial-replace-duplicate-chord-zero-write", "DUPLICATE_KEY"),
        ("radial-replace-unsupported-chord-zero-write", "UNSUPPORTED_INPUT_CODE"),
    ):
        assert editor_cases[case_id]["expectedError"] == expected
        assert editor_cases[case_id]["writes"] == 0
        assert editor_cases[case_id]["stateBytesUnchanged"] is True
    assert editor_cases["radial-unknown-action-zero-write"][
        "mutationApplied"
    ] is False
    assert editor_cases["radial-stale-generation-zero-write"][
        "mutationApplied"
    ] is False
    combo = positive["variants"][0]["elements"][7]["payload"]
    assert combo["payloadKind"] == "combo"
    assert combo["keys"] == [
        {"namespace": "androidKeyCode", "code": 8},
        {"namespace": "androidKeyCode", "code": 57},
    ]
    radial = positive["variants"][0]["elements"][5]["payload"]
    assert radial["payloadKind"] == "radial"
    assert [item["order"] for item in radial["actions"]] == [0, 1]
    assert len({item["actionId"] for item in radial["actions"]}) == 2
    assert radial["actions"][1]["keys"] == [
        {"namespace": "androidKeyCode", "code": 57},
        {"namespace": "usbHidKeyboardUsage", "code": 5},
    ]
    negative_ids = {item["id"] for item in vectors["documentCases"]}
    assert {
        "missing-layout-opacity",
        "layout-opacity-below-range",
        "layout-opacity-above-range",
        "legacy-element-opacity-rejected",
        "combo-duplicate-chord-key",
        "combo-noncanonical-chord-order",
        "radial-duplicate-action-id",
        "radial-noncanonical-action-order",
    } <= negative_ids
    v3_validator = Draft202012Validator(strict_load(SCHEMA))
    assert not list(v3_validator.iter_errors(strict_load(POSITIVE)))
    print("V3_CONTRACT_SELF_TEST_PASS")


if __name__ == "__main__":
    main()
