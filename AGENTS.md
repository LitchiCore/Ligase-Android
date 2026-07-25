# Ligase Android workspace rules

These rules apply to the whole Android repository.

## Start from live state

- Read this file, `REQUEST.md`, relevant formal docs, the current branch, and
  `git status`/`git diff` before changing files.
- This is a shared checkout. Treat every unknown modification or untracked file
  as another owner's work. Establish an exact file allowlist and coordinate
  overlapping hunks before editing.
- Do not use an isolated worktree unless the user explicitly requests one.

## Toolchain and device windows

- Use the D-drive Android SDK and Gradle user home configured for this
  workstation. Keep machine-specific absolute paths out of committed source and
  documentation.
- Only one task may run Gradle, install an APK, or operate ADB at a time.
  Announce and release the window when collaborating.
- Prefer a connected physical device over an emulator. A device, Host, driver,
  permission, or installation gate that is unavailable must be reported as a
  precise `BLOCKED` or `SKIP`; never replace it with fabricated UI state,
  fixtures, database injection, or a different product path.

## Architecture boundaries

- `LigaseActivity` owns Android lifecycle and legacy composition bridges.
  `app/root` composes feature routes, and `app/navigation` is the single owner
  of product navigation state. Feature UI consumes immutable presentation state
  and typed actions.
- Do not create a second store, ViewModel, repository, session owner, or policy
  in Compose to work around a missing application seam. Report the required
  typed state/action contract first.
- TouchKit v1 runtime and persistence remain separate from Touch Layout v2
  catalog, journal, generation repository, and editor workspace. Do not
  dual-write, infer identity from names or paths, or route v2 through legacy
  SharedPreferences.
- Do not change pairing cryptography, Host protocol/wire DTOs, GameStream/RTSP,
  input dispatch, repository persistence, or machine-readable layout contracts
  from a frontend task unless that scope is explicitly authorized.
- Formal protocol/schema documents and their machine-readable assets are the
  authority. Link to them; do not create a second copied authority in app code
  or frontend docs.

## Safety and evidence

- Do not clear device data, delete paired computers, pair a device, start or
  stop a Host, launch a stream, mutate Host settings, or change user preferences
  unless the current task explicitly authorizes that action.
- Automated tests, compilation, R8, lint, and assembly prove only their own
  gates. They do not prove real pairing, network writes, streaming, accessibility
  speech, persistence across process death, or phone/tablet UI behavior.
- Device evidence must identify the exact frozen source/APK and distinguish
  completed checks from blocked or skipped checks.

## Git and reporting

- Every task final and commit handoff must declare
  `DOC_IMPACT=UPDATED|NONE` with a reason. Changes to user behavior or wording,
  architecture owners or dependency direction, storage/schema/protocol,
  Activity/lifecycle, permissions/security, build/install instructions, or
  device acceptance steps require the owner document in the same commit or a
  frozen documentation contract first. Only mechanical refactors and test-only
  strengthening normally qualify for `NONE`.
- Documentation must link to the machine authority instead of copying it.
  Planned work must not be described as implemented, and temporary SHAs or test
  counts do not belong in stable architecture documents.
- Preserve unrelated shared changes. Stage only the exact approved files;
  `git add .` and broad staging are prohibited.
- Keep structural moves, behavior changes, protocol/storage changes, and
  evidence-only documentation in separate focused commits.
- Before commit and push, run the proportional test gates, `git diff --check`,
  allowlist review, and a secret/absolute-path scan. Read back the remote SHA and
  report whether the worktree is clean for the submitted scope.
- Report each phase structurally: phase/conclusion, exact file scope, evidence,
  blockers or follow-up contract, external action required, commit/remote state,
  and whether a peer was already notified.
