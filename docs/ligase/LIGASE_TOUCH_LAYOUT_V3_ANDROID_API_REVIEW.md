# Android Touch Layout v3 API review

Status: **REVIEW — proposed global-opacity and combo/radial API revision**.

The previously frozen API/validation baseline remains authoritative until this
fixed review snapshot completes independent review, explicit authorization,
and commit. No production implementation is authorized by this document.

The final production package is
`com.limelight.ligase.feature.input.layout.v3`. No v2 typealias, forwarding
class, reader, repository, preference adapter, reflection keep, or FQCN shim is
retained.

Proposed public application seam:

```text
LayoutV3EditorLaunchRequest =
  NewV3(displayName?)
  | ExistingV3(draftId)

LayoutV3EditorLaunchResult =
  AwaitingTargetViewport(creationNonce)
  | ResumeReady(draftId)
  | Rejected(INVALID_IDENTITY | ALREADY_OWNED |
             MISSING_DRAFT | STALE_GENERATION | CUTOVER_NOT_READY)

EditorTargetViewport(widthPx, heightPx, orientation, occlusionDiagnostics)

initializeNewV3(creationNonce, targetViewport) =
  Created(draftId, canvas)
  | Rejected(VIEWPORT_OUT_OF_RANGE | VIEWPORT_ORIENTATION_MISMATCH |
             STALE_GENERATION | ALREADY_CREATED | JOURNAL_WRITE_FAILED)
```

`widthPx` and `heightPx` are the stable immersive edge-to-edge
window/control-overlay bounds. `occlusionDiagnostics` may contain typed system
bar, cutout, rounded-corner, or hinge regions for warnings only. It is excluded
from identity, canvas dimensions, element validation, JCS/hash, selection, and
automatic movement. Video content and letterbox rectangles are not inputs to
this API.

Canonical editor state exposes one layout-level
`opacityPermille: Int (0..1000)`. The only opacity mutation is:

```text
setLayoutOpacityPermille(value) =
  Applied(updatedLayout)
  | Rejected(OUT_OF_RANGE | STALE_GENERATION | JOURNAL_WRITE_FAILED)
```

It atomically updates the complete candidate, journal, and authoritative
readback. `LayoutV3EditorElement` has no opacity property and there is no
`setOpacityPermille(elementId, value)` action.

Editor mutations retain the current typed element/property API, with:

```text
moveElement(elementId, signedHorizontalOffset, signedVerticalOffset)
resizeElement(elementId, positiveWidth, positiveHeight)

result =
  Applied
  | Rejected(RECT_NOT_VISIBLE | INVALID_DIMENSION |
             TARGET_NOT_VISIBLE |
             TARGET_BELOW_MINIMUM | UNKNOWN_ELEMENT |
             READ_ONLY_KIND | STALE_GENERATION)
```

The backend owns viewport validation, identity generation, exclusive lease,
strict geometry validation, explicit anchors, journal persistence, JCS/hash,
and atomic generation registration. `CanvasViewportMapperV3` exposes exact
rational `Sx`, `Sy`, and uniform `Ss`, resolved target rects, and typed mapping
failures. It never exposes DPI or video rectangles. Compose owns only local
per-frame gesture preview and submits one mutation at gesture completion.

The single element list uses the closed anchor combinations:

```text
anchorX = LEFT | CENTER | RIGHT
anchorY = TOP | BOTTOM
```

Creation policy chooses a deterministic initial rect and applies the same
versioned X 40/20/40 and Y 50/50 integer policies as pointer-up. Move commit
performs one typed anchor rebase that preserves the exact canonical rect.
There is no manual anchor action, duplicate zone field, or runtime
reclassification.

```text
addKeyboardKeys(keys: Set<InputCode>) =
  Applied(createdElementIds)
  | Rejected(EMPTY | TOO_MANY_KEYS | UNSUPPORTED_NAMESPACE |
             ID_EXHAUSTED | Z_ORDER_EXHAUSTED | STALE_GENERATION)

reorderElement(elementId, targetZOrder) =
  Applied(canonicalUniqueOrder)
  | Rejected(UNKNOWN_ELEMENT | OUT_OF_RANGE | STALE_GENERATION)
```

Batch input is de-duplicated and canonicalized by namespace then code. One
atomic mutation creates unique IDs and increasing unique layers at the exact
center stack. The default is a 96×96 circle with CENTER/BOTTOM anchors,
horizontal offset 0, and the exact centered bottom gap. IDs are UUIDv5 from
`layoutId` plus canonical content `nextKeyboardBatchOrdinal`, namespace, and
code; the action increments that content field atomically. Returned IDs
stay in canonical key order. Overlap is formal-valid. Renderer and hit-test
consumers use the unique z-order contract and never infer a tie-break from
element ID.

Formal save/reopen and export/import preserve the next ordinal through the
artifact and its JCS hash. Journal deletion cannot reset it. Repository
sidecars and UUID-scanning recovery are forbidden.

Closed editable payload actions additionally include:

```text
replaceComboChord(elementId, keys: List<InputCode>) =
  Applied(canonicalChord)
  | Rejected(EMPTY_CHORD | TOO_MANY_KEYS | DUPLICATE_KEY |
             UNSUPPORTED_INPUT_CODE | UNKNOWN_ELEMENT | WRONG_KIND |
             STALE_GENERATION | JOURNAL_WRITE_FAILED)

addRadialAction(elementId, label?, keys: List<InputCode>) =
  Applied(generatedActionId, canonicalRadial)
  | Rejected(EMPTY_CHORD | TOO_MANY_KEYS | UNSUPPORTED_INPUT_CODE |
             DUPLICATE_KEY | ID_GENERATION_FAILED | ACTION_LIMIT |
             INVALID_LABEL | UNKNOWN_ELEMENT | WRONG_KIND |
             STALE_GENERATION | JOURNAL_WRITE_FAILED)

removeRadialAction(elementId, actionId)
  = Applied(canonicalRadial)
  | Rejected(UNKNOWN_ACTION | MINIMUM_ACTIONS | UNKNOWN_ELEMENT |
             WRONG_KIND | STALE_GENERATION | JOURNAL_WRITE_FAILED)

reorderRadialAction(elementId, actionId, targetOrder)
  = Applied(canonicalRadial)
  | Rejected(UNKNOWN_ACTION | OUT_OF_RANGE | UNKNOWN_ELEMENT |
             WRONG_KIND | STALE_GENERATION | JOURNAL_WRITE_FAILED)

replaceRadialActionLabel(elementId, actionId, label?)
  = Applied(canonicalRadial)
  | Rejected(UNKNOWN_ACTION | INVALID_LABEL | UNKNOWN_ELEMENT |
             WRONG_KIND | STALE_GENERATION | JOURNAL_WRITE_FAILED)

replaceRadialActionChord(elementId, actionId, keys: List<InputCode>)
  = Applied(canonicalRadial)
  | Rejected(EMPTY_CHORD | TOO_MANY_KEYS | UNSUPPORTED_INPUT_CODE |
             DUPLICATE_KEY | UNKNOWN_ACTION | UNKNOWN_ELEMENT |
             WRONG_KIND | STALE_GENERATION | JOURNAL_WRITE_FAILED)
```

Every successful radial mutation produces unique stable action IDs and
contiguous canonical order. Each mutation is one journal state transition;
failure is zero-write. Combo and radial properties are closed typed editor
unions rather than raw maps. Runtime execution remains unavailable and
fail-closed.

The application owner obtains UUID D candidates from an injected identity
source during `addRadialAction`. UI never supplies or generates persistent
identity. The owner attempts at most three candidates: the initial candidate
plus two collision retries. Invalid candidates or three collisions return
`ID_GENERATION_FAILED` with zero mutation. There is no derivation from index,
label, time, content hash, or UI state. Applied IDs persist unchanged through
journal recovery, save/reopen, and reorder.

Chord requests are bounded typed lists so duplicate caller values remain
observable. The owner validates empty/count/namespace/code and duplicate
identity before canonical sorting. Any failure is zero-write. Strict
artifact/import validation independently rejects duplicate list entries before
materialization.

The action validation order is:

```text
EMPTY_CHORD -> TOO_MANY_KEYS -> UNSUPPORTED_INPUT_CODE -> DUPLICATE_KEY
-> INVALID_LABEL -> canonical sort
-> (radial add only) request UUID candidate
```

Radial add consumes zero identity candidates when chord, target, or optional
label validation fails. Radial chord replacement uses the same
`List<InputCode>` validation and never relies on a caller-side set.
Count is checked before items, unsupported code before duplicate identity, and
duplicate identity before label bounds. Existing layout/action identity and
stale-generation gates remain the outer application checks.

The shared keyboard picker consumes a safe readable-key presentation derived
from canonical `InputCode`. It returns a typed set to the owning action.
Presentation labels never enter namespace/code identity, JCS, or hash.

No API in this review authorizes runtime execution, preview execution, Host
transport, v1 migration, or deletion outside the exact v2 test-layout cutover
targets below.

## One-time test-data cutover

The sole owner is `LayoutV3CutoverCoordinator`. Before any v3 catalog, journal,
editor, preference, or generation repository opens, it reads one app-private
marker and executes the closed state table in
`ligase-touch-layout-v3-cutover-states.json`.

The allowlisted v2-owned targets are:

- directory `ligase-touch-layout-v2-generations`;
- directory `ligase-touch-layout-v2-drafts`;
- directory `ligase-touch-layout-v2`;
- SharedPreferences file `ligase_touch_layout_v2_preferences`.

The implementation must first verify every resolved target is app-private and
exactly allowlisted. It moves existing directories/files to one app-private
quarantine generation, fsyncs the parent where supported, verifies original
targets are absent, then atomically writes the v3 marker. Preference cleanup
accepts only keys with exact prefix `preferred:` followed by a canonical layout
UUID in the named layout-owned preference file. When all keys match, the
complete file is quarantined. Any unknown key fails closed, preserves the file,
and prevents the marker.

Host identity/certificates, pairing, computers database, library, streaming,
input mode, TouchKit v1 preferences, and every unrecognized file/key are
must-not-touch. Failure leaves the marker unset and v3 closed. A later launch
retries the same idempotent quarantine. Quarantine is recoverable development
data and is not automatically garbage-collected in this phase.

After marker state `V3_READY`, only v3 repositories may open. There is no mixed
mode. The final implementation gate scans production and tests for
`layout.v2`, `LayoutV2`, `layout-v2`, v2 storage names, old manifest Activity
FQCN, and v2 Proguard/serialization keeps; expected production count is zero.

A change from element opacity to root-only opacity changes accepted v3 bytes.
Because the product is unpublished, any older app-private v3 generation,
journal, or catalog entry is test data and must be quarantined/re-created by
an explicitly versioned, layout-owned cutover gate before repositories open.
It is not migrated, dual-read, assigned a guessed default, or retained through
a compatibility branch. The quarantine allowlist remains restricted to
layout-owned v3 storage and must-not-touch every Host, pairing, library,
streaming, input-mode, and TouchKit v1 asset.
