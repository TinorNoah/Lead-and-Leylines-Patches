# Agent instructions

This repository contains the source and release tooling for the small, project-owned NeoForge patch mod used by the Lead and Leylines modpack. It is a narrowly scoped compatibility layer, not a general-purpose addon or a dumping ground for every crash involving a mod in the pack.

## Read before editing

1. Read [`README.md`](README.md) for the current fix ledger and published validation status.
2. Read [`docs/MAINTAINING.md`](docs/MAINTAINING.md) for the required investigation, implementation, testing, documentation, and release process.
3. When a proposed fix depends on the pack, inspect the current `pack/pack.toml` and exact mod manifests in [the pack repository](https://github.com/TinorNoah/Lead-and-Leylines). Do not rely on remembered versions or old logs as proof of current compatibility.

## Mission and boundaries

- Fix a demonstrated, pack-relevant incompatibility with the smallest maintainable code change.
- Prefer an upstream repair, supported configuration or datapack option, or existing compatible addon when one safely solves the issue. Keep code here only when those options are insufficient for the reproduced problem.
- Keep patch source, build tooling, and its detailed ledger in this repository. The pack repository owns packwiz TOML, pack configuration, mod catalog, smoke-test harness, and server overlay. Do not copy pack files or third-party JARs here.
- Treat every Mixin as a compatibility contract with a specific trigger, target, dependency/version boundary, affected side, and regression test.
- Do not claim a crash is fixed merely because code compiles, a server boots, or a release asset exists. Report exactly which client and server paths were tested and which remain unverified.

## Before proposing or changing behavior

1. Establish evidence: reproduce the failure, or use a complete crash report/log and a minimal reliable reproduction. Identify the first relevant stack frame, calling side/thread, target class/method, input state, and exact mod/loader versions.
2. Verify the affected bytecode/API and currently supported versions from reliable sources. Search for upstream fixes and inspect existing configuration/data hooks. Old notes are leads, not current compatibility evidence.
3. Describe the root cause and at least the reasonable alternatives. Define exactly what the patch changes and what it deliberately does not change.
4. Decide the client/server/common scope before coding. Check dedicated-server classloading for every client reference.
5. If the cause, intended behavior, fallback semantics, affected versions, or acceptable risk is uncertain, stop and ask the project owner. Do not silently choose the most convenient interpretation.

Ask before widening the supported version range, changing gameplay or data semantics, adding an invasive workaround, creating a framework for hypothetical future fixes, removing a shipped fix, or publishing an artifact when publication was not explicitly part of the request.

## Implementation rules

- Prefer a small, descriptive class/Mixin per independently reproduced problem. Avoid global hooks, broad redirects, catch-all exception suppression, reflection-based guessing, and unrelated cleanup.
- Preserve valid inputs and existing behavior. Intervene only for the demonstrated invalid/missing state; use the live level/server registry or service that owns the data. Never substitute a static/global registry for a data-driven registry.
- Declare an exact dependency/version constraint when the code relies on a specific class or bytecode signature. Re-check the target before widening that boundary. Do not claim compatibility outside the tested range.
- Use optional-target mechanisms only when a dependency is genuinely optional. Required dependencies belong in mod metadata; optional classes must not break discovery when absent.
- Keep client-only code in client-only Mixin configuration. A dedicated server must not resolve client classes. Keep the pack integration side-correct; this mod is currently distributed as `both` so connected clients and servers use identical bytes.
- Explain non-obvious injection points and safety decisions, but do not add comments that merely restate code.
- Never add generated JARs, Gradle caches, downloaded mod files, launcher instances, credentials, or private server details to Git.

## Required change workflow

1. Work from an up-to-date feature branch and open a pull request; do not edit `main` directly.
2. In the PR description, include the trigger, root cause, affected versions/sides, target signature, rejected alternatives, behavior change, and regression plan.
3. Build against the versions read from the pack configuration with `python3 scripts/build_patches.py --pack-root <pack-repository>`. Inspect the resulting artifact and Mixin/refmap output; do not commit the JAR.
4. For server-relevant changes, run the pack repository's documented local smoke test and inspect injection/load logs. Add a focused probe for each target where practical.
5. For client or connected-client symptoms, test with a matching client joined to the dedicated server. A server-only smoke test is not an adequate substitute.
6. Update this repository's README fix ledger and [`docs/MAINTAINING.md`](docs/MAINTAINING.md) when policy or workflow changes. Record validation truthfully, including explicit unverified paths.
7. Run relevant checks, review the complete diff, and report command results and any remaining uncertainty.

## Versioning and publication

- `VERSION` is the patch-artifact version; it is independent of the pack version.
- Published artifacts are immutable prereleases tagged `patches-vX.Y.Z`. Never replace an existing asset with different bytes; increment `VERSION` for new bytes.
- Publish only from a clean local `main` that exactly matches GitHub, using `scripts/publish_patches.py`. Do not publish from a feature branch or before its patch-source PR is merged.
- Publication is externally visible. Do it only when the user explicitly requests or approves it; if the task wording is ambiguous, ask first.
- After a published artifact is intended for the pack, coordinate the pack-side manifest URL and SHA-256 update in the separate pack repository and run `packwiz refresh` there. Do not create a pack release or publish to a mod store unless that is separately requested.

## When to ask instead of guessing

Ask the project owner when evidence does not isolate the cause; two plausible fixes have materially different behavior; you cannot determine the correct side or live data source; version compatibility is ambiguous; a change may alter gameplay/world data; a manual regression path is unavailable but resolution or publication is being considered; or the request does not clearly authorize a release. State what is known, what is uncertain, the safe options, and your recommendation. Do not present an inference as a verified fact.
