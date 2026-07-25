# Android Touch Layout v3 implementation status

The machine authority for Touch Layout v3 is the frozen manifest
[`tests/fixtures/ligase-touch-layout-v3-sha256.txt`](../../tests/fixtures/ligase-touch-layout-v3-sha256.txt).
This file records implementation ownership and deliberately does not copy the
schema or geometry rules.

## Implemented in Core A

- independent Android v3 strict UTF-8 JSON materialization, typed content
  model, validator, RFC 8785 canonicalization, encoding, and content-hash
  verification;
- frozen anchor, signed partial-offscreen geometry, adaptive full-overlay
  mapping, unique layer, overlap, hit-test, and keyboard batch identity
  primitives;
- the one-time, exact-target v2 test-layout quarantine coordinator and the
  repository gate that remains closed until its atomic `V3_READY` marker is
  verified.

Production code does not load the review Python scripts. Frozen artifacts are
read only by tests.

## Implemented in Core B

- v3-only draft journal and atomic generation repository, both gated by the
  verified `V3_READY` cutover marker;
- the single editor session/workspace owner with process recovery, strict
  save/readback/registration, immutable authoritative resolved rectangles,
  anchor rebasing, signed move/resize/nudge, unique layer reordering, and
  generation-bound one-shot gesture commits;
- stable full-immersive target viewport blank creation without a Hall-side
  default canvas;
- the Activity-owner `NEW_V3` launch mode, which waits for stable immersive
  overlay bounds, checkpoints the new opaque draft identity, and resumes that
  journal after process recreation without creating a second Session;
- the complete generation-bound gesture-token facade plus the authoritative
  full-overlay pixel-to-canonical inverse mapper; UI preview remains
  non-persistent and release/cancel can consume a token at most once;
- the UI-safe forward editor mapper, which consumes immutable editor geometry
  without reconstructing protocol payloads and returns typed pixel, clip,
  draw-visibility, and hit-test projections;
- the single typed resize policy used by preview and persisted actions:
  keyboard/mouse circles use the dominant drag axis and always read back as a
  square, while rectangle shapes retain independent width and height;
- the atomic keyboard-key batch seam, including canonical key order, bounded
  cardinality, deterministic center stack/IDs/layers, and persisted
  `nextKeyboardBatchOrdinal`.
- the single local committed catalog projection: strict generations are
  re-read after save and process restart, projected without raw bytes or
  filesystem identity, and can be reopened through a typed Workspace action;
- the Compose/product Activity bridge and immersive editor shell. Saved local
  cards explicitly remain non-runtime until a separate runtime task.

## Residual cutover

The obsolete `feature/input/layout/v2` production and test trees, product
Activity/Root bindings, and manifest entry were removed after v3 became the
only content authority. They are not a fallback and are not read or written.
The exact cutover coordinator remains responsible for quarantining the
layout-owned v2 test-data targets before opening v3 repositories.

Preview/runtime rendering, Game/input dispatch, Host transport, Sync,
publication, download, and real-keyboard selection UI remain outside this
block. TouchKit v1 runtime and persistence remain physically separate and are
not deletion targets.
