# Ligase Touch Layout v2 — frozen content contract

Status: **FROZEN CONTENT CONTRACT / VALIDATION BASELINE**.

The schema, content model, canonicalization rules, and frozen fixtures are the
stable v2 validation baseline. Android Core A has independent authorization to
implement this validation baseline. This status does **not** authorize storage
cutover, editor or runtime execution, Host Sync or public wire integration,
publication, or download.

This document defines a new content artifact. The Android product does not
dual-read or dual-write TouchKit SharedPreferences/v1. Legacy input is accepted
only by the offline converter used to produce review fixtures.

## 1. Authority and identity

- `format` is exactly `ligase-touch-layout`; `schemaVersion` is exactly `2`.
- `layoutId`, `variantId`, and `elementId` are canonical lowercase UUID D
  strings. No RFC UUID version or variant-bit restriction is added. Version-0
  UUID D is intentionally valid.
- `revision` is a JSON integer in `1..9007199254740991`.
- `LayoutDescriptorV1` remains the only authority for publication status,
  compatibility, portable identity, instance binding, and variant resolution.
  This content artifact contains none of those fields.
- For one `(layoutId, revision)`, descriptor touch variants and content variants
  MUST have the exact same `variantId` set. Each pair MUST have equal
  `deviceClasses` and `orientations`. A content artifact alone is never
  `installed`, `published`, executable, or eligible.
- Content recommendations only filter or explain whether an already selected
  variant fits. They MUST NOT score or automatically choose a variant. The v1
  rule remains authoritative: exact local `preferredVariantId`, otherwise one
  eligible variant, otherwise explicit user selection.
- Canonical arrays: variants ascending by `variantId`; `deviceClasses` in
  `phone, tablet` order; `orientations` in `portrait, landscape` order;
  elements by `(zOrder, elementId)`. Duplicate set members are invalid.

## 2. Limits and strings

The raw artifact is read with a hard 1 MiB limit before UTF-8 decoding or JSON
parsing. Invalid UTF-8 and duplicate object keys at any depth are rejected.

- variants: `1..32`; elements per variant: `1..512`; root extensions: at most 32.
- reference canvas dimensions and reference resolution: `1..32768`.
- coordinates: `0..32767`; positive rect dimensions; the rect must fit canvas.
- `zOrder`: `-32768..32767`; opacity is integer `opacityPermille` in `0..1000`.
- JSON numbers are integers only. Identity/revision/extension integers use the
  JSON-safe range. Floats, NaN, infinities, and negative zero encodings are not
  accepted by the strict artifact parser. The raw integer-token hook rejects
  the exact JSON lexeme `-0` before value materialization, including nested
  extension values and every embedded legacy JSON document.
- All strings are Unicode scalar sequences: no unpaired surrogate and no C0,
  DEL, or C1 control. `displayName` max 80; label 32; description 80;
  `sourceReference` 160; extension key 64/value 256. Empty values are allowed
  only where the schema explicitly permits them.

## 3. Extensions and canonical hash

Core objects use `additionalProperties:false`. A future core field or kind is
fail-closed until a schema revision is reviewed.

The sole extension point is root `extensions`. Extension values are inert and
MUST NOT affect identity, descriptor matching, variant resolution, rendering,
or input execution. Values recursively allow only null, boolean, safe integer,
clean string, arrays of at most 32, and objects of at most 32 properties, with
maximum nesting depth 6. Floats and unsafe integers are rejected.

`contentHash` is `sha256:` plus lowercase SHA-256 of RFC 8785 JCS bytes after
removing `contentHash`. The v2 domain excludes floats, so its number
serialization is exact decimal safe integers. Object keys use RFC 8785 UTF-16
code-unit ordering; the non-BMP vector proves this differs from Unicode
code-point sorting. Semantic extension values are preserved; a parsed and
rewritten artifact is not claimed byte-equal. A repository may separately
retain the complete original artifact bytes.

## 4. Recommendation and variant eligibility

Recommendation fields are:

- non-empty canonical `deviceClasses` and `orientations`;
- reduced or non-reduced positive rational `min/preferred/maxAspectRatio`,
  satisfying `min <= preferred <= max`;
- positive `minShortestSideDp`, `minTouchTargetDp`;
- `safeAreaPolicy`: `videoContent` or `videoContentAndSystemInsets`;
- optional diagnostic `referenceDensityDpi`;
- diagnostic/preview `referenceResolution`.

The resolver uses the decoded video-content viewport and selected descriptor
variant. It MUST NOT use physical display pixel size, DPI, layout name, numeric
appid, or these diagnostics as an identity or automatic tie-break.

## 5. Integer viewport mapping

Decoded video content and system-safe rect use the same integer display
coordinate system. For `videoContent`, the video rect is selected. For
`videoContentAndSystemInsets`, select their intersection; empty or degenerate
intersection is `incompatibleSafeArea`.

Let selected safe size be `(SW, SH)` and canvas `(CW, CH)`. Choose the uniform
contain scale `p/q = min(SW/CW, SH/CH)` by integer cross multiplication.
`round(n/d)` is nearest integer with exact half rounded toward positive
infinity: `(2*n+d)//(2*d)` for non-negative values.

Mapped canvas size is `(round(CW*p/q), round(CH*p/q))`. Residual is safe size
minus mapped canvas size. Per element, horizontal offset is `0`,
`round(residualX/2)`, or `residualX` for left/center/right; vertical offset is
the corresponding top/center/bottom value. Rect left/top and right/bottom
edges are independently scaled with the same rounding, then width/height are
edge differences. A zero final dimension is incompatible. This algorithm maps
to the actual decoded video rect/letterbox, never the whole physical screen.

## 6. Geometry, overlap, and dispatch

`rect` is an integer reference-canvas rectangle. Anchors are explicit; no
legacy 45/55-position heuristic exists.

Two active (`enabled=true`) visible (`hidden=false`) controls whose positive
area intersects MUST have different `zOrder`. Touching boundaries are not an
intersection. Disabled or hidden controls are ignored by collision validation.
Runtime dispatch among intersecting controls selects the greatest `zOrder`.
`elementId` is only the canonical storage tie-break and is never an executable
same-z overlap rule.

Editor save and runtime load call the same scene validator.

## 7. Input codes and typed kinds

Every keyboard-like binding uses:

```json
{"namespace":"androidKeyCode","code":29}
```

Allowed namespaces are `androidKeyCode` and `usbHidKeyboardUsage`; code is
`0..65535`. The legacy converter emits only `androidKeyCode` and never guesses
HID. A runtime that cannot execute a namespace fails closed for that control.

Mouse buttons are semantic `primary|secondary|middle|back|forward`; legacy
numeric 9/10/11 remapping is forbidden.

The closed v2 kinds and semantics are:

- `keyboard`: one inputCode and trigger.
- `analog`: up/down/left/right and optional press chords; simultaneous axes
  form a normalized vector (`diagonalPolicy=vector`).
- `dpad`: four chords; `allowTwoDirections` presses both axes, while
  `dominantAxis` keeps the first axis until it returns to neutral.
- `customKeys` and `combo`: keys press in listed order and release in reverse
  order. `sticky=true` is valid only with `toggle`; it is not combined with
  hold, tap, or timed hold.
- `radial`: first sector starts at -90 degrees; actions proceed clockwise;
  the clockwise boundary is inclusive and the counter-clockwise boundary is
  exclusive (`clockwiseInclusive`).
- `scroll`: direction is semantic; step unit is one wheel detent. Continuous
  scroll repeats every `cadenceMs`; non-continuous scroll MUST have null
  cadence. Trigger semantics still govern initial activation.
- `softKeyboard`: `openSystemIme`, Android-local execution. Host validates and
  stores but never executes it.
- `gyro`: target `mouse|rightStick`, frame `device|screen`, axes
  `yawPitch|yawPitchRoll`, and integer scale in
  `milliUnitsPerDegreePerSecond`. Deadzone uses
  milli-degrees-per-second; invert flags apply after frame transformation.

Trigger is `hold|toggle|tap|timedHold`. Only `timedHold` has a non-null
`timedHoldMs` (`1..60000`); all other triggers require null.

## 8. Runtime architecture

The intended product path is:

`immutable LayoutScene -> descriptor-governed variant selection -> viewport
mapper -> renderer/input binding`.

Editor and runtime share schema, cross-field, collision, namespace, and hash
validation. Neither directly edits SharedPreferences. This draft authorizes no
runtime/editor/Sync/public payload implementation.

## 9. Offline converter

The converter uses bounded raw UTF-8 and duplicate-detecting parsing for the
outer legacy artifact and every embedded JSON string. It rejects unknown
preference types, unconsumed preferences, orphan descriptors, unknown enum,
missing binding, out-of-range geometry/opacity, truncation, clamping, and any
unsupported field. It supplies only defaults proven and listed by the
conversion contract; review fixture metadata gives an explicit authoritative
draw order.

Before replacement, generated content passes Draft 2020-12 schema,
cross-field, collision, JCS, and hash validation. Success uses a same-directory
temporary file, flush/fsync, and atomic replace. Failure creates no output or
leaves an existing output byte-for-byte unchanged. The diagnostic conversion
report is a separate atomic output: on failure it is created or replaced with
the deterministic structured failure report for that attempt. No contract
claims that a pre-existing diagnostic report remains unchanged.

The real Genshin 2312x1080 sample contains 19 active overlap pairs and no
authoritative draw order. It deterministically fails `collisionDetected`; no
`zOrder` is inferred from `legacyId`, object order, or filename. The built-in
all-types fixture has explicit auditable draw-order metadata and is the sole
positive conversion fixture.

## 10. Descriptor integration gate

A future integration validator must receive both `LayoutDescriptorV1` and this
content. It verifies exact `(layoutId, revision)`, exact touch `variantId` set,
and equal device-class/orientation sets. It does not derive publication,
compatibility, binding, or installed/executable readiness from content bytes.
