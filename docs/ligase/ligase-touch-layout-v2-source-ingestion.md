# Ligase Touch Layout v2 — source ingestion contract draft

Status: **REVIEW DRAFT / NOT IMPLEMENTATION AUTHORITY**.

This Android-owned document defines how already-authoritative
`LayoutDescriptorV1` metadata and already-frozen Touch Layout v2 content may be
combined into a local catalog projection. It does not authorize runtime or
editor execution, v1 cutover, migration, Host Sync/public wire, content
download, publication, or community catalog features.

The frozen content contract remains
`LIGASE_TOUCH_LAYOUT_V2_DRAFT.md`. `LayoutDescriptorV1` remains the sole
authority for publication, compatibility, portable identity, instance binding,
and touch variant membership. Content bytes never create those facts.

The companion machine table is
`ligase-touch-layout-v2-source-ingestion-states.json`.

## 1. Single owner and dependency direction

`LayoutCatalogV2SourceRegistry` is the only owner allowed to assemble source
records. Activity, Compose, a Host adapter, and a filesystem repository MUST
NOT independently merge, rank, or project catalog entries.

The intended dependency direction is:

```text
Activity composition
  -> refresh coordinator
    -> source registry
      -> packaged reader / local generation repository / Host metadata adapter
        -> strict descriptor + content validators
          -> Core C catalog projection
```

The registry accepts immutable source snapshots. For each accepted record it
must have:

- a validated `LayoutDescriptorV1`;
- an origin (`PACKAGED_BUILT_IN`, `LOCAL_COPY`, or `HOST_CATALOG`);
- an explicit workspace state;
- an authorized content candidate only when that origin may provide local
  content.

An ingestion input never declares `availability`, `contentVerified`, or an
issue result. The registry derives all three after bounded reads and strict
validation. A source cannot self-report `READY`.

It publishes only typed safe state. Raw JSON, filesystem paths, content hashes,
exceptions, request identifiers, and Host transport details never enter UI
state, logs, `toString()`, accessibility semantics, or analytics.

## 2. Identity, deduplication, and source precedence

The canonical record key is `(layoutId, revision)`. `layoutId` is canonical
lowercase UUID D and `revision` is a safe integer. A source record failing
identity or descriptor validation is rejected before any path is formed.

The recommended deterministic merge rule is:

1. Validate every descriptor independently before grouping.
2. For one key, all descriptor values from every source must be structurally
   equal. Any difference is `CROSS_SOURCE_DESCRIPTOR_CONFLICT`; the whole key is
   fail-closed and no source silently wins.
3. An exact Host descriptor may confirm catalog presence but cannot provide
   local content. If an exact local record exists, its local origin and
   availability remain authoritative; Host presence is retained only in a
   source summary.
4. `PACKAGED_BUILT_IN` and `LOCAL_COPY` are expected to own distinct layout
   identities. If both claim the same key, even with equal descriptors or
   content, reject it as `LOCAL_ORIGIN_CONFLICT`. This prevents a user asset
   from shadowing an APK asset or vice versa.
5. Duplicate records from one origin must be byte/semantic duplicates after
   strict parsing. Otherwise reject the key as `DUPLICATE_SOURCE_RECORD`.
6. Different revisions coexist. The registry does not choose the highest
   revision, migrate a preference, or hide an older revision. A future product
   revision policy requires separate coordination.

No compatibility hint, physical screen property, DPI, reference resolution,
display name, filename, numeric app id, or source arrival order participates
in precedence.

These precedence rules are a review proposal and require coordination
acceptance before production implementation.

## 3. Packaged source

Packaged v2 content is an APK asset set rooted by one strict manifest. Trust is
anchored in the installed APK signature; the manifest and every referenced
descriptor/artifact are covered by that signature.

Each manifest entry must include:

- canonical `(layoutId, revision)`;
- a bounded relative descriptor asset name;
- a bounded relative content artifact asset name;
- SHA-256 of the exact descriptor bytes;
- SHA-256 of the exact artifact bytes.

Asset names are manifest data, not derived from unvalidated identity. They must
be relative, normalized, unique, and confined to the fixed packaged asset
directory. Absolute paths, `..`, empty segments, backslashes, and aliases are
rejected.

Ingestion order is bounded raw read, exact byte hash, strict descriptor parse,
strict v2 parse/JCS/content-hash validation, and descriptor/content exact-set
alignment. Only then may availability become `READY`.

Application upgrade creates a new packaged snapshot. Removed packaged entries
disappear from that snapshot, but upgrade processing must not delete a local
copy, preference, quarantine record, or user export. A preference pointing to
an absent or changed revision becomes read-only invalid/stale state; it is not
automatically cleared or rewritten.

## 4. Local-copy source

A local-copy record may be created only by a future explicitly authorized v2
action. The current v1 editor, SharedPreferences catalog, offline converter,
filename discovery, and legacy source reference are not ingestion actions.

One local generation contains:

- the validated descriptor bytes;
- the validated artifact bytes;
- a generation metadata record binding their exact byte hashes and key.

Save is a single logical transaction:

1. validate the requested key before creating a path;
2. write descriptor, artifact, and generation metadata into a private staging
   directory;
3. flush and fsync all files;
4. read back exact bytes and rerun descriptor, content, hash, and alignment
   validation;
5. atomically publish one generation marker;
6. expose the record only after the marker points to the verified generation.

Failure leaves the prior visible generation byte-for-byte unchanged. A new
record failure leaves no visible record. Descriptor and artifact are never
published in separate generations.

No implicit import, conversion, copy-on-write, edit, preview, runtime load, or
delete action is authorized by this document.

## 5. Host-catalog source

The current Host H1 boundary is metadata-only. Android may ingest only an
authorized typed descriptor/index snapshot. It must project
`origin=HOST_CATALOG`, `availability=NOT_LOCAL`, and no content.

This contract defines no Host route, DTO, permission field, download,
authentication exchange, cache lifetime, or content bytes. Until a separate
cross-end contract is frozen:

- Host metadata cannot become `READY`;
- Host metadata cannot be previewed, edited, or executed;
- Host metadata cannot create a local file;
- refresh cannot be interpreted as download;
- public/Sync wire must not be guessed from this document.

Host disconnected, permission denied, an empty authorized catalog, and a
transport failure are different typed source states. UI must not infer them
from `NOT_LOCAL` or an empty item list.

## 6. Refresh state, last-success snapshot, and generation

Top-level state must separately expose:

- `initialLoading`;
- `refreshing`;
- `hasLoaded`;
- `stale`;
- typed `refreshOutcome`;
- typed `emptyReason`;
- per-origin `SourceSummary`;
- the current revision set and stored-preference validity;
- targeted preference-action result.

Refresh is non-destructive. If a last-success snapshot exists, loading retains
its items without marking them stale. A completed refresh failure retains
those items and marks the snapshot stale. It never flashes an empty catalog
merely because a poll started.

One refresh coordinator owns a monotonically increasing in-memory generation.
Every source read and validation result is tagged with the generation and
source identity. A late, cancelled, closed, or superseded result is discarded.
Observer count, recomposition, Activity recreation, and background/foreground
transitions must not multiply a refresh. Generation is internal and never
exposed to UI.

Each source publishes its complete current validated revision set. The
contract defines no active-revision owner, replacement relation, or revision
change event. UI repeatedly renders that set together with
the typed validity of a stored preference. It does not infer replacement,
select the greatest revision, depend on arrival order, carry a preference
forward, or trigger a write. Activity recreation and recomposition simply
render the same current state.

Lifecycle combinations are closed:

| state | initialLoading | refreshing | hasLoaded | stale | items | emptyReason | refreshOutcome |
| --- | --- | --- | --- | --- | --- | --- | --- |
| idle | false | false | false | false | empty | null | `NOT_STARTED` |
| first load | true | true | false | false | empty | null | `IN_PROGRESS` |
| loaded, non-empty | false | false | true | false | current | null | `SUCCESS` or `PARTIAL_SUCCESS` |
| loaded, valid empty | false | false | true | false | empty | non-null source-derived reason | `SUCCESS` or `PARTIAL_SUCCESS` |
| refresh with cache | false | true | true | false | last success | retained from last success | `IN_PROGRESS` |
| failure without cache | false | false | true | false | empty | `REFRESH_FAILED_WITHOUT_CACHE` | `FAILED_NO_CACHE` |
| failure with cache | false | false | true | true | last success | retained from last success | `FAILED_USING_LAST_SUCCESS` |

`emptyReason` is null before the first completed load and whenever the rendered
items are non-empty. For a retained empty last-success snapshot it retains that
snapshot's closed empty reason. It is otherwise non-null only for a completed
empty snapshot or failure without cache. `retainedLastSuccess` is true while a
refresh renders a last-success snapshot and after
`FAILED_USING_LAST_SUCCESS`. Cancelled, closed, and superseded generations are
discarded without publishing a new lifecycle state or outcome.

## 7. Context unavailable is a third state

The Hall does not own an actual decoded video-content viewport. It must not use
physical screen dimensions, display DPI, `referenceDensityDpi`, or
`referenceResolution` as a substitute.

Without an authorized target stream viewport:

- catalog evaluation is `CONTEXT_UNAVAILABLE` with
  `NO_VIDEO_VIEWPORT`;
- variants are neither eligible nor ineligible;
- variants are read-only and cannot be selected;
- automatic selection, ranking, and preference writes are forbidden;
- selection code is `WAITING_FOR_CONTEXT_VALIDATION`; selection `variantId`
  and source are null, every variant row has `selected=false` and
  `selectEnabled=false`, and this context exposes neither select nor clear
  actions;
- a defensive select or clear call returns typed `CONTEXT_UNAVAILABLE` and
  performs zero writes;
- an existing stored preference may be displayed as
  `WAITING_FOR_CONTEXT_VALIDATION`, but is not applied, cleared, rewritten, or
  described as compatible/executable. The preferred row is presentation-only,
  not selected semantics.

This rule **supersedes the rejected pending-validation-preference proposal**.
There is no pending preference creation path.

Only a separately frozen owner of the actual target stream viewport may restore
eligibility evaluation. Runtime or launch must independently validate against
the actual decoded viewport and fail closed when incompatible; this document
does not authorize that runtime integration.

Retired, explicitly ineligible, `NOT_LOCAL`, and `INVALID` records remain
unselectable even when another context is available.

## 8. Typed safe projection

The source layer extends Core C state without exposing infrastructure:

- `CatalogLifecycle(initialLoading, refreshing, hasLoaded, stale)`;
- `RefreshOutcome(code, retainedLastSuccess)`;
- `SourceSummary(origin, phase, itemCount, stale, issueCode)`;
- `CatalogEmptyReason`;
- `RevisionSetState(layoutId, revisions, storedPreferenceValidity)`;
- targeted `PreferenceActionResult(layoutId, revision, variantId?, code)`.

Source phases are `UNAVAILABLE`, `LOADING`, `READY`, `PARTIAL`, and `FAILED`.
Typed empty reasons are `NO_AUTHORIZED_SOURCES`, `SOURCES_EMPTY`,
`ALL_ENTRIES_REJECTED`, and `REFRESH_FAILED_WITHOUT_CACHE`.

Host metadata state maps mechanically:

- `NOT_CONNECTED` -> phase `UNAVAILABLE`, issue `HOST_DISCONNECTED`;
- `AUTHORIZED_LOADING` -> phase `LOADING`, issue null;
- `AUTHORIZED_EMPTY` -> phase `READY`, item count `0`, issue
  `HOST_AUTHORIZED_EMPTY`;
- `AUTHORIZED_METADATA_READY` -> phase `READY` when every authorized
  descriptor validates, otherwise `PARTIAL` when at least one validates and at
  least one is rejected; its issue is null for `READY` and the applicable safe
  descriptor issue for `PARTIAL`;
- `PERMISSION_DENIED` -> phase `FAILED`, issue `PERMISSION_DENIED`;
- `FAILED` -> phase `FAILED`, issue `HOST_SOURCE_FAILED`.

At minimum, safe source issue codes cover:

- `NO_VIDEO_VIEWPORT`;
- `SOURCE_UNAVAILABLE`;
- `HOST_DISCONNECTED`;
- `HOST_AUTHORIZED_EMPTY`;
- `HOST_SOURCE_FAILED`;
- `PERMISSION_DENIED`;
- `STORAGE_FAILURE`;
- `INVALID_MANIFEST`;
- `INVALID_DESCRIPTOR`;
- `CONTENT_HASH_MISMATCH`;
- `CONTENT_REJECTED`;
- `DESCRIPTOR_CONTENT_MISMATCH`;
- `DUPLICATE_SOURCE_RECORD`;
- `CROSS_SOURCE_DESCRIPTOR_CONFLICT`;
- `LOCAL_ORIGIN_CONFLICT`;
- `READBACK_FAILED`;
- `ROLLBACK_FAILED`.

`STALE_RESULT` and `CANCELLED` are internal coordinator diagnostics only. A
late, cancelled, closed, or superseded generation is discarded before
projection, so those codes MUST NOT appear in `SourceSummary`, UI state,
accessibility semantics, or the public safe source-issue set. A future
current-generation partial-source cancellation would require a separately
named and frozen projected code; it must not reuse these diagnostics.

UI must not infer loading, authorization, emptiness, failure, or staleness from
`items.isEmpty`, item availability, or issue-list shape.

## 9. Quarantine, rollback, and garbage collection

Rejected candidates never replace a visible verified generation. Quarantine
stores only bounded app-private diagnostic metadata required to explain the
failure: source, safe key when valid, typed code, and local observation time.
It must not be exposed to UI or logs as a path, raw body, descriptor, artifact,
hash, or exception.

Automatic garbage collection may delete:

- abandoned staging directories not referenced by a generation marker;
- temporary files belonging to a failed or cancelled generation;
- a rebuildable `TRANSIENT_FETCH_CACHE` that is not a committed Host snapshot,
  is not referenced by current state, and has no in-flight reader.

It must not automatically delete or rewrite:

- a visible or previous valid local-copy generation;
- any user-created/imported artifact;
- a preference, including stale or invalid preference;
- an export;
- packaged authority metadata;
- any committed Host snapshot;
- the `CURRENT_AUTHORIZED_HOST_SNAPSHOT`.

User-asset deletion requires a separately authorized explicit product action.

## 10. Review and implementation gates

Before implementation, review must freeze:

1. packaged manifest schema, asset root, and descriptor byte format;
2. local generation metadata and atomic marker format;
3. the source precedence proposal in section 2;
4. descriptor revision presentation/selection policy;
5. the owner and lifecycle of an actual target stream viewport;
6. Host metadata fields, authorization, empty state, and cache lifetime.

The minimum future implementation should remain under
`feature/input/layout/v2/{application,data,domain}` with only a narrow Activity
composition update. It must not modify Compose, v1 repositories, TouchKit,
runtime input, Host wire, Sync, or public APIs in the same commit.

Required tests include manifest traversal/hash failures, descriptor/content
atomic pairing, readback rollback, duplicate/cross-source conflicts,
last-success preservation, per-source partial failure, generation cancellation,
late results, context-unavailable zero writes, stored-preference zero cleanup,
quarantine bounds, and garbage collection that never deletes user assets.
