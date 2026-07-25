# Android Touch Layout v3 API review

Status: **REVIEW_READY_5 / DESIGN ONLY**.

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
