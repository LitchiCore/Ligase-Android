#!/usr/bin/env python3
"""Mechanical converter/rollback checks for the v2 review snapshot."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from copy import deepcopy
from pathlib import Path

from convert_touch_layout_v2 import ConversionError, convert, run
from validate_touch_layout_v2 import ValidationError, strict_load, strict_load_bytes


ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "docs/ligase/ligase-touch-layout-v2.schema.json"
BUILTIN = ROOT / "tests/fixtures/touch-layout-v1-built-in-all-types.json"
BUILTIN_OUTPUT = ROOT / "tests/fixtures/ligase-touch-layout-v2-built-in-all-types.json"
BUILTIN_REPORT = ROOT / "tests/fixtures/ligase-touch-layout-v2-built-in-all-types.report.json"
GENSHIN = ROOT / "tests/fixtures/touch-layout-v1-genshin-impact-phone.json"
LAYOUT_ID = "00000000-0000-0000-0000-000000000001"
VARIANT_ID = "00000000-0000-0000-0000-000000000002"


def expect_conversion(reason, source):
    try:
        convert(source, LAYOUT_ID, VARIANT_ID)
    except ConversionError as exc:
        assert exc.reason == reason, (reason, exc.reason)
        return exc
    raise AssertionError(f"expected {reason}")


def main():
    with tempfile.TemporaryDirectory(prefix="ligase-layout-v2-") as directory:
        temp = Path(directory)
        output, report = temp / "layout.json", temp / "report.json"
        run(BUILTIN, output, report, SCHEMA, LAYOUT_ID, VARIANT_ID)
        assert output.read_bytes() == BUILTIN_OUTPUT.read_bytes()
        assert report.read_bytes() == BUILTIN_REPORT.read_bytes()
        first = (output.read_bytes(), report.read_bytes())
        run(BUILTIN, output, report, SCHEMA, LAYOUT_ID, VARIANT_ID)
        assert first == (output.read_bytes(), report.read_bytes())

        output.write_bytes(b"old-output-must-survive")
        report.write_bytes(b"old-report")
        old_output = output.read_bytes()
        command = [
            sys.executable,
            str(ROOT / "tools/ligase/convert_touch_layout_v2.py"),
            "--input", str(GENSHIN),
            "--output", str(output),
            "--report", str(report),
            "--schema", str(SCHEMA),
            "--layout-id", LAYOUT_ID,
            "--variant-id", VARIANT_ID,
        ]
        first_failure = subprocess.run(command, check=False, capture_output=True)
        assert first_failure.returncode == 2
        assert output.read_bytes() == old_output
        assert report.read_bytes() == (
            ROOT / "tests/fixtures/ligase-touch-layout-v2-genshin-impact-phone.report.json"
        ).read_bytes()
        first_failure_report = report.read_bytes()
        second_failure = subprocess.run(command, check=False, capture_output=True)
        assert second_failure.returncode == 2
        assert output.read_bytes() == b"old-output-must-survive"
        assert report.read_bytes() == first_failure_report

    builtin = strict_load(BUILTIN)
    nested_duplicate = deepcopy(builtin)
    nested_duplicate["preferences"]["__touchkit_dynamic_elements"]["value"] = (
        '[{"type":0,"type":0,"elementId":"duplicate"}]'
    )
    expect_conversion("duplicateKey", nested_duplicate)

    nested_negative_zero = deepcopy(builtin)
    nested_negative_zero["preferences"]["fixture_key"]["value"] = (
        nested_negative_zero["preferences"]["fixture_key"]["value"]
        .replace('"LEFT":20', '"LEFT":-0', 1)
    )
    expect_conversion("negativeZero", nested_negative_zero)

    unsupported_preference = deepcopy(builtin)
    unsupported_preference["preferences"]["unknown"] = {"type": "boolean", "value": True}
    expect_conversion("unsupportedLegacyPreference", unsupported_preference)

    orphan_preference = deepcopy(builtin)
    orphan_preference["preferences"]["orphan"] = {
        "type": "string",
        "value": "{\"LEFT\":1,\"TOP\":1,\"WIDTH\":1,\"HEIGHT\":1,\"ENABLED\":true,\"HIDDEN\":false,\"BACKGROUND_OPACITY\":1}",
    }
    expect_conversion("orphanLegacyDescriptor", orphan_preference)

    invalid_opacity = deepcopy(builtin)
    config = json.loads(invalid_opacity["preferences"]["fixture_key"]["value"])
    config["BACKGROUND_OPACITY"] = 101
    invalid_opacity["preferences"]["fixture_key"]["value"] = json.dumps(
        config, separators=(",", ":")
    )
    expect_conversion("invalidLegacyOpacity", invalid_opacity)

    unknown_trigger = deepcopy(builtin)
    config = json.loads(unknown_trigger["preferences"]["fixture_key"]["value"])
    config["TRIGGER_MODE"] = "MAGIC"
    unknown_trigger["preferences"]["fixture_key"]["value"] = json.dumps(
        config, separators=(",", ":")
    )
    expect_conversion("unknownLegacyEnum", unknown_trigger)

    try:
        strict_load_bytes(b" " * (1024 * 1024 + 1))
    except ValidationError as exc:
        assert exc.code == "fileTooLarge"
    else:
        raise AssertionError("oversized raw input accepted")

    print("CONVERTER_SELF_TEST_PASS")


if __name__ == "__main__":
    main()
