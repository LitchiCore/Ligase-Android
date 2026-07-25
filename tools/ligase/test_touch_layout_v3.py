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


def verify_manifest(path: Path) -> None:
    for line in path.read_text(encoding="utf-8").splitlines():
        expected, relative = line.split("  ", 1)
        actual = hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()
        assert actual == expected, (relative, expected, actual)


def verify_cutover_state_table() -> None:
    value = strict_load(CUTOVER)
    assert set(value) == {
        "contract", "schemaVersion", "marker", "states", "quarantineTargets",
        "mustNotTouch", "negativeScenarios",
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
    assert len(cases) == 8
    assert len({item["id"] for item in cases}) == len(cases)


def main() -> None:
    verify_manifest(V3_MANIFEST)
    verify_cutover_state_table()
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
    v3_validator = Draft202012Validator(strict_load(SCHEMA))
    assert not list(v3_validator.iter_errors(strict_load(POSITIVE)))
    print("V3_CONTRACT_SELF_TEST_PASS")


if __name__ == "__main__":
    main()
