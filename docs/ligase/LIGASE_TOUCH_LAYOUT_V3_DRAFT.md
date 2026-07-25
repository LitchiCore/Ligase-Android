# Ligase Touch Layout v3 — adaptive anchored fullscreen review draft

Status: **REVIEW_READY_5 / NOT FROZEN / NOT IMPLEMENTED**.

This document is the proposed replacement for the development-only
`ligase-touch-layout` schemaVersion 2 contract. It does not authorize Android
production parsing, editing, rendering, input dispatch, storage cutover, Host
wire, Sync, publication, or download.

On final acceptance, schemaVersion 3 becomes the sole current layout-content
authority. The development v2 authority is removed from the current authority
index; Git history is its only machine-readable archive. Production has no v2
reader, fallback, dual-read, alias, compatibility shim, legacy branch, or
re-create UX. Existing app-private v2 layout data is authorized test data and
is handled only by the one-time cutover state machine in the Android API review.

## 1. Identity and version boundary

- `format` remains exactly `ligase-touch-layout`.
- `schemaVersion` is exactly `3`.
- UUID, revision, ordering, string, extension, JCS, hash, typed kind,
  descriptor alignment, and recommendation rules are defined completely by
  the v3 schema, validator, and frozen vectors. No v2 document or fixture is a
  normative dependency.
- Production accepts only schemaVersion 3. Any other version fails closed as
  `UNSUPPORTED_SCHEMA` before content materialization.
- `LayoutDescriptorV1` remains the sole publication, compatibility, portable
  identity, binding, and variant-membership authority.

## 2. Signed geometry and visibility

Canvas width and height remain integers in `1..32768`.

Every element remains in the single canonical `elements` list. There is no
separate zone collection or identity. Position authority is:

- `anchorX`: exactly `LEFT|CENTER|RIGHT`;
- `anchorY`: exactly `TOP|BOTTOM`;
- `horizontalOffset`, `verticalOffset`: signed integers in `-32768..32767`;
- `width`, `height`: integers in `1..32768`.

`horizontalOffset` is the left gap for `LEFT`, the right gap for `RIGHT`, or
the signed element-center displacement from canvas center for `CENTER`.
`verticalOffset` is the top gap for `TOP` or bottom gap for `BOTTOM`. Negative
edge gaps intentionally encode partial-offscreen placement.
The v3 editor/application policy owns a versioned 40/20/40 horizontal zoning
rule. At pointer-up, it classifies the final resolved element center:

- LEFT when `centerX < 40% * CW`;
- CENTER when `40% * CW <= centerX <= 60% * CW`;
- RIGHT when `centerX > 60% * CW`.

It uses integer cross-products on `centerTwice=2*x+width`: LEFT iff
`centerTwice*5 < CW*4`, CENTER iff the first test is false and
`centerTwice*5 <= CW*6`, otherwise RIGHT. Thus exact 40% and 60% boundaries
belong to CENTER and partial-offscreen centers naturally classify LEFT/RIGHT.
The policy rebases `horizontalOffset` so resolving the new anchor yields the
identical canonical rect; any visual jump is a failure. Creation policy uses
the same rule after choosing its deterministic initial rect. Vertically,
`centerY < 50% * CH` selects TOP and `centerY >= 50% * CH` selects BOTTOM,
using `centerYTwice < CH`; exact 50% belongs to BOTTOM. It rebases
`verticalOffset` with the same zero-jump invariant. There is no manual anchor
override. Runtime consumes stored anchors and never reclassifies.

Resolve the source left coordinate as:

```
LEFT:   x = horizontalOffset
CENTER: x = roundHalfUp((CW - width) / 2) + horizontalOffset
RIGHT:  x = CW - horizontalOffset - width
```

Resolve `y=verticalOffset` for TOP and
`y=CH-verticalOffset-height` for BOTTOM. All products, sums, edges,
intersections, and areas use `Long` intermediates. Formal save accepts only
when the resolved rect intersects `[0,CW)×[0,CH)` by at least 1×1 canonical
unit. More than half, or all but one unit, may be outside. Zero-area
intersection is typed `RECT_NOT_VISIBLE`; there is no clamp or inferred repair.

## 3. Overlap, layers, clipping, and input

Positive-area overlap is valid in draft, formal save, and runtime. It produces
no collision issue or warning and never changes `candidateReady`. Every
element has a unique bounded integer `zOrder`; duplicate layers fail closed.
Canonical storage order is ascending `zOrder`. Add and batch actions allocate
deterministically increasing layers. A typed reorder action atomically produces
another unique canonical order or changes nothing.

Renderer output is clipped first to canonical canvas and then to the full
immersive virtual-control overlay bounds. Hit testing and dispatch operate only on that same
visible intersection. Runtime must never emit an off-canvas input coordinate.
Renderer traversal is bottom-to-top. For each pointer, hit testing traverses
top-to-bottom and selects the first enabled, non-hidden element whose clipped
visible region contains the pointer. That pointer never falls through to lower
elements. Multiple pointers resolve independently. Hidden and disabled
elements are skipped.

This review freezes runtime behavior but does not implement a runtime.

### 3.1 Planned atomic keyboard batch mutation

The future editor application API exposes
`addKeyboardKeys(Set<InputCode>)`. It accepts only the existing closed keyboard
namespaces, de-duplicates input, enforces a maximum of 32 keys, and sorts by
namespace order `androidKeyCode` then `usbHidKeyboardUsage`, then integer code.
Click order is never identity or z-order authority.

One successful action creates one unique canonical element identity per key,
all with the same deterministic 96×96 circle, `trigger=hold`,
`timedHoldMs=null`, and exact canvas-center rect. Its stored geometry is
`anchorX=CENTER`, `horizontalOffset=0`, `anchorY=BOTTOM`,
`verticalOffset=roundHalfUp((canvasHeight-96)/2)`, width/height 96. The
resolved rect is
`x=roundHalfUp((canvasWidth-96)/2)`,
`y=canvasHeight-verticalOffset-96`.

Canonical v3 content persists required positive safe integer
`nextKeyboardBatchOrdinal`, initially 1. It participates in strict schema,
JCS, content hash, export, import, atomic generation save, and readback.
A batch atomically consumes its current value and increments the content field
by one. Exhaustion fails without mutation. Draft journal carries the complete
candidate content, but is not a second owner of the ordinal.

Element ID is RFC 4122 UUIDv5
using `layoutId` as namespace and UTF-8 name
`keyboard-batch:<batchOrdinal>:<namespace>:<code>`. Canonical result order is
the canonical key order, not UUID order. Consecutive unique z-order values
start at `max(existing zOrder)+1` and follow that result order, so the last
item is initially topmost. Ordinal, IDs, elements, content, and journal update commit
atomically. Invalid input, ID exhaustion, z-order overflow, or structural
failure creates zero elements and does not consume the ordinal. Formal save,
journal deletion, process restart, LOCAL_COPY reopen, export/import, and new
generation registration recover the next value from verified content;
repositories must not keep a sidecar or derive it by scanning UUIDs.

The exact center stack is formal-valid and may be saved immediately. Process
recovery preserves canonical element and stack order; users may drag items
apart or use typed layer actions later. Visibility and typed payload rules
remain strict.

## 4. Adaptive anchored fullscreen mapper

The reference canvas is the complete immersive overlay bounds captured when
the variant is created. For target full-overlay dimensions `TW×TH` and
reference `RW×RH`, compute exact rational scales:

```
Sx = TW / RW
Sy = TH / RH
Ss = Sy
```

Horizontal offsets use `Sx`; vertical offsets use `Sy`. Width and height use the
single `Ss=Sy`, so a square/circle never stretches into a rectangle/ellipse.
Every multiplication uses a `Long` intermediate and the same
round-half-up operation; no floating point, DPI, dp, or separate width/height
size scaling is allowed.

`roundHalfUp(numerator/denominator)` is exactly
`floor(numerator/denominator + 1/2)` for positive denominator, including signed
numerators. Therefore `-0.5→0`, `-1.5→-1`, `0.5→1`, and `1.5→2`. Kotlin must
implement the same integer rule with checked `Long` intermediates; truncation
toward zero, platform `round`, floating point, and sign-dependent alternatives
are forbidden. Frozen signed half-tie vectors are the cross-language authority.

For RIGHT, for example:

```
targetRightGap = roundHalfUp(horizontalOffset * Sx)
targetWidth    = roundHalfUp(width * Ss)
targetX        = TW - targetRightGap - targetWidth
```

LEFT uses the scaled gap directly. CENTER uses the target canvas center plus
the signed offset scaled by `Sx`, then subtracts half the uniformly scaled
width using the same round-half-up rule. TOP maps its gap directly.
BOTTOM computes `targetY=TH-roundHalfUp(verticalOffset*Sy)-targetHeight`.
Signed negative gaps remain signed. Because vertical offset and size share
`Sy`, TOP/BOTTOM preserve their semantic edge with deterministic rounding; the
frozen vectors cover exact and one-unit rounding boundaries.

This is not scene contain/letterbox: the full target window is used, edge
controls stay at their semantic edges, and the center region absorbs aspect
change. CENTER is valid for radial, soft-keyboard, touchpad/mouse, combo, and
menu controls. A non-blocking center
occlusion warning may be projected, but cannot reject save or runtime.

Raw pointer samples are clamped to the full overlay before inverse mapping.
Element endpoints are never clamped. After mapping, the validator rechecks
positive visibility, integer overflow, and the typed minimum target size.
Overlap remains valid. Failures are `TARGET_NOT_VISIBLE` or
`TARGET_BELOW_MINIMUM`; they require an explicit suitable variant and never
cause squeezing, auto movement, anchor changes, or preference writes.

Decoded video content, video letterbox, and video-touch coordinate mapping are
orthogonal axes. They never shrink, offset, or select the virtual-control
layout canvas. Accordingly, v3 removes the v2 recommendation field
`safeAreaPolicy`; no replacement content field controls overlay clipping.

Editor move and resize preview may update every frame locally. A typed mutation
is committed exactly once at pointer-up or cancellation. Move accepts signed
origins. Resize requires positive dimensions. Both reject `RECT_NOT_VISIBLE`
without modifying the draft or journal.

## 5. Target-viewport variant creation

A new v3 blank is not pre-created in Hall and does not use a fixed 1920×1080
canvas. Hall launches the editor in `NEW_V3` mode without content bytes.

After the sensor-landscape Activity is immersive edge-to-edge and its complete
window/control-overlay bounds are stable, the Activity supplies:

```
EditorTargetViewport(widthPx, heightPx, orientation)
```

`widthPx` and `heightPx` are the complete bounds and are not reduced by status
bar, navigation bar, cutout, rounded-corner, hinge, or safe-drawing insets.
They must each be in `1..32768`. Orientation is
`portrait|landscape` and must agree with the dimensions; square is
deterministically `landscape`. The creation policy copies these two dimensions
exactly into the new variant canvas. It does not use DPI, whole physical
display size, device name, reference density, reference resolution, or a
rounded aspect bucket.

System bars appearing or disappearing never changes the persisted canvas.
Cutout, rounded-corner, hinge, and system-bar regions may be projected as typed
diagnostic warnings, but do not crop canvas, move controls, or change formal
save visibility. Users may place controls there. Tool overlay chrome may use
safe-drawing/cutout insets to remain operable; opening, closing, or offsetting
that chrome never changes canvas measurement or element mapping.

The application creates a canonical new `layoutId`, `variantId`, revision 1,
draft journal, and exclusive generation only after this validation. A
nonce/generation gate makes repeated layout callbacks idempotent and prevents
double creation. Process death before creation repeats viewport initialization;
after creation recovery uses only opaque `draftId`. Rotation or another device
uses this adaptive anchored mapper and never rewrites the persisted canvas.

Existing v3 draft launch is `EXISTING_V3(draftId)`. Intents contain only mode,
opaque canonical draft identity where applicable, and a non-secret generation
token; never raw content, path, hash, payload, or preference.

## 6. Authority and accepted-byte impact

- The v3 manifest contains only v3 authority assets.
- Existing v2 docs, schema, tools, fixtures, reports, and manifest are removed
  from the current tree in the later contract-freeze commit. They are not
  copied, regenerated, linked as an alternative authority, or installed.
- No v2 artifact is converted. Existing v2 app-private test data is never
  parsed as v3 and never contributes identity, elements, preferences, or
  descriptor state.
- The original Genshin input is not a v3 fixture. Git history retains the prior
  conversion evidence without making it a current authority.

## 7. Required implementation gates after acceptance

Future Android implementation must rename the complete production
`feature/input/layout/v2` tree, FQCNs, types, storage names, tests, and imports
to v3; update strict domain models, codec/encoder, validator, catalog, source
registry, generation/journal repository, workspace, editor creation policy,
session mutations, viewport mapper, and renderer contract tests; and leave no
v2 production symbol or path. It must not modify v1 TouchKit runtime
persistence or Game input dispatch in the contract commit.
