# Ligase Touch Layout v2 conversion report — review draft 3

This report is candidate evidence, not a frozen protocol or runtime admission.

## Inputs and roles

| Input | Role in REVIEW_READY_3 | Result |
|---|---|---|
| `touch-layout-v1-built-in-all-types.json` | Auditable synthetic legacy fixture covering dynamic types 0–9 | Converted |
| `touch-layout-v1-genshin-impact-phone.json` | Real 2312x1080 adversarial migration sample | Rejected: `collisionDetected`, 19 active overlap pairs |

The Genshin source remains byte-preserved as a fixture. No v2 artifact is
produced for it. It lacks authoritative legacy draw-order evidence, so assigning
`zOrder` from JSON order, preference name, or legacy ID would change topmost
input semantics.

## Positive conversion

- canonical version-0 UUID D is deliberately used for layout/variant identity;
- all ten kinds are emitted with typed payloads;
- every key is `androidKeyCode`; no HID inference occurs;
- draw order comes from explicit fixture metadata;
- anchors are `explicitLeftTopNoInference`;
- converter output passes strict schema, cross-field, collision, JCS, and
  content-hash validation before atomic replacement;
- two consecutive conversions are byte-identical to the checked fixture and
  report.

## Fail-closed and rollback evidence

Mechanical self-tests cover:

- duplicate outer and nested JSON keys, invalid UTF-8, raw negative-zero tokens
  at artifact/extension/embedded-legacy depth, and pre-parse >1 MiB;
- unknown/non-string preference types, unconsumed preference, orphan
  descriptor, unknown enum, invalid opacity, and missing required binding;
- formal CLI failure leaves a pre-existing layout artifact byte-for-byte
  unchanged while atomically replacing the diagnostic report with the
  deterministic failure result; two identical failures produce identical
  report bytes and exit code 2;
- 513 elements, 33 variants, 33 extensions, unsafe/fraction extension values,
  excessive extension depth, invalid canonical arrays and ratios;
- every kind's missing/unknown/type/range or mutual-exclusion boundary;
- active same-z overlap rejection, boundary-touch and disabled exceptions;
- safe-area intersection/degeneration and deterministic integer mapping;
- UTF-16 non-BMP JCS object-key ordering and content-hash mismatch.

## Explicit non-claims

- There is no product runtime/editor v2 implementation.
- There is no SharedPreferences fallback or dual-read/dual-write path.
- Content bytes do not establish publication, compatibility, binding,
  installation, or executable readiness.
- Host remains metadata-only until a later cross-platform content contract is
  accepted and separately implemented.
