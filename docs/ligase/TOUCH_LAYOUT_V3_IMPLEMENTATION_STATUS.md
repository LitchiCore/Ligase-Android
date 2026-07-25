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
- the atomic keyboard-key batch seam, including canonical key order, bounded
  cardinality, deterministic center stack/IDs/layers, and persisted
  `nextKeyboardBatchOrdinal`.

## Not implemented by Core A or Core B

Compose UI, Manifest/product Activity integration, catalog and source-registry
replacement, preview/runtime rendering, Game/input dispatch, Host transport,
Sync, publication, and download remain outside this block.

The existing `feature/input/layout/v2` production tree is transitional only.
It is not a fallback and is not read or written by v3. Later compile-safe
cutover blocks must replace and then remove its catalog/source registry,
Activity/editor symbols, tests, manifest entry, and storage references. Core B
does not read, write, or migrate v2 generations or journals. TouchKit v1 runtime
and persistence remain physically separate and are not deletion targets.
