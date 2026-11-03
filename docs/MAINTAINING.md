# Maintaining Lead and Leylines Patches

This guide is the detailed working agreement for humans and coding agents contributing to this repository. [`AGENTS.md`](../AGENTS.md) contains the short, binding instructions agents must follow. [`README.md`](../README.md) is the public-facing fix ledger and build/release entry point. Keep all three aligned when the project’s purpose, current fixes, or workflow changes.

## 1. Purpose and main idea

Lead and Leylines Patches is a small NeoForge mod owned by the Lead and Leylines pack project. Its job is to bridge a **confirmed incompatibility that matters to this pack** when there is no suitably narrow upstream, configuration, datapack, or existing-addon solution.

The design principle is:

> Diagnose first. Patch the smallest invalid interaction. Preserve everything else. Bound the compatibility claim. Test each affected side. Ask rather than invent behavior when evidence is incomplete.

This project is not:

- a fork of the mods it interoperates with;
- a general compatibility layer for arbitrary mod combinations;
- an alternative place to put pack configuration, datapacks, or user content;
- a home for performance optimizations or gameplay rebalancing;
- a collection of speculative Mixins added “just in case”; or
- a substitute for reporting a reproducible upstream bug to its owner.

A project-owned fix should have a clear reason to exist here, a defined removal path when upstream resolves it, and evidence that the chosen injection point and behavior are correct. More code is not automatically more reliable. Avoid architecture for hypothetical future conflicts: add the next fix only when there is a distinct, demonstrated need.

## 2. Repository boundaries

This repository owns:

- Java source for the patch mod;
- Mixin and NeoForge metadata;
- Gradle build configuration and build/publish helpers;
- the patch artifact version in `VERSION`;
- fix rationale, compatibility boundaries, and regression procedures; and
- immutable patch prerelease assets attached to `patches-vX.Y.Z` tags.

The separate [Lead and Leylines pack repository](https://github.com/TinorNoah/Lead-and-Leylines) owns:

- `pack/pack.toml`, the current Minecraft/NeoForge selection, and the pack version;
- packwiz mod metadata, including `pack/mods/leylines-patches.pw.toml`;
- the installed-mod catalog, pack configuration, datapacks, and server overlay; and
- the pack-side smoke-test harness and its reports.

Do not move those pack files into this repository. Do not change the pack repository as an incidental side effect of a source-only patch task. When the requested work includes making an artifact available to the pack, coordinate the two repositories deliberately: publish the patch artifact from this repository, update the manifest and index in the pack repository, then validate the pack integration there. A patch-source PR and a pack-integration change are different review scopes even if they are part of the same user request.

## 3. Current shipped fix and its boundary

The current patch addresses the Weapons of Miracles × Modern Industrialization drill enchantment lookup:

- MI 2.5.8 can receive a null enchantment `RegistryLookup` in `SteamDrillItem.getAllEnchantments` or `DieselToolItem.getAllEnchantments` during the affected Weapons of Miracles path.
- On the logical server, the common Mixin supplies the active server's live enchantment registry only when the incoming lookup is null.
- On the client, a client-only Mixin supplies the connected level's live registry only when the incoming lookup is null.
- If no server or client level exists, the patch preserves null. It does not construct a static registry, guess the correct data pack state, or suppress a subsequent failure.
- The required MI dependency is pinned exactly in `src/main/resources/META-INF/neoforge.mods.toml`; do not widen it just because the class names still exist. Reinspect the method signature and bytecode/API behavior first.
- Both the steam drill and diesel tool matter. Do not test only one target and infer the other passes.

The reported symptom was a client crash while opening Creative Search while connected to a dedicated server. The server-side transformation and server-held-item probes have passed, but the matching-client Creative Search reproduction has not yet been manually verified. This is a known validation gap, not proof that the client fix works or fails. Keep that status visible until an actual connected-client test is recorded.

The complete current-fix table and validation notes live in [`README.md`](../README.md). Update it whenever a fix ships, its compatibility boundary changes, a test result changes, or an upstream fix makes it obsolete.

## 4. Investigation before implementation

Do not start by writing a Mixin. First establish enough evidence to know what code must change.

### Evidence checklist

Capture or determine:

- the full relevant crash report and stack trace, not just the final exception line;
- the user action and exact reproduction sequence;
- whether the failure occurs on the client, logical server, dedicated server, or more than one;
- the calling thread and whether client-only code is involved;
- the target class, method, relevant argument/state, and the first frame that identifies the faulty interaction;
- the exact Minecraft, NeoForge, and relevant-mod versions, read from the live pack metadata or launcher/logs;
- whether the failure is deterministic and whether it reproduces without the proposed patch; and
- whether existing configs, datapacks, upstream releases, or another compatible mod already address it.

Logs, remembered version numbers, class names from an older build, and plausible-sounding mod interactions are leads. They are not sufficient proof by themselves. If a cause is not isolated, request the missing crash report, versions, reproduction, or target information. Do not code against an assumed target signature.

### Alternatives to evaluate

Check, in order:

1. Is there a current upstream fix or a supported update that resolves the exact reproduction?
2. Can a documented mod configuration, datapack, or pack setting safely avoid the trigger?
3. Is there an existing compatible addon that owns this integration?
4. If not, can a narrow pack-owned shim preserve the expected behavior without taking ownership of the other mod's implementation?

Record the alternatives and why they are insufficient in the PR. Do not ship a workaround solely to reduce log noise if it leaves the user-visible failure unchanged or hides a deeper issue.

## 5. Designing a narrow fix

Before editing, write down a one-sentence behavioral contract: **when condition X occurs on side Y for versions Z, provide behavior W; otherwise preserve existing behavior**. If you cannot write that sentence confidently, ask for clarification.

For each proposed patch, define:

- the exact target class, method, field, or resource;
- the exact injection point and why it is stable enough for the pinned versions;
- which values/states should pass through unchanged;
- the exceptional or missing state being repaired;
- the source of any fallback data and why it is authoritative at that time;
- the behavior when that source is unavailable;
- target mod and loader version boundaries; and
- tests for both the patched condition and unaffected behavior.

Favor a small class/Mixin per independent issue. Avoid a shared interception framework, universal registry utility, global event bus hook, blanket redirects, or a long list of unrelated targets. A common helper is justified only when multiple real fixes already share the same invariant and the helper does not obscure their distinct boundaries.

### Safe Mixin practices

- Confirm the actual target bytecode/signature for the supported dependency. Check the argument order, return type, invocation timing, and whether the method is static or instance-based.
- Use the narrowest injection point that preserves the normal path. Avoid altering every invocation when only one method-entry argument is unsafe.
- Preserve non-null/valid inputs exactly. Change only the specific demonstrated bad state.
- Use the live world/server-owned registry or data service. Never substitute a static/default registry for data-driven data.
- Do not catch and ignore exceptions merely to make the crash disappear. Do not return fake defaults unless the owner explicitly approves the semantic change and a test proves it is safe.
- Use optional-target handling only when the target dependency is truly optional. Required target dependencies must be accurately declared in `neoforge.mods.toml`.
- Keep client API references out of common/server classes and common Mixin lists. Verify the dedicated server can discover and load the mod without loading client-only classes.
- If a target method changes and the injection no longer matches, fail clearly rather than silently claiming the fix still applies.

## 6. Side and compatibility policy

Classify a fix as client, server, common, or both based on where the faulty call executes—not on which screen or user action exposed it. A client action can trigger a server failure, and a server action can send data that later crashes a client.

For each side:

- identify where the relevant state lives and which runtime owns it;
- avoid resolving client classes from dedicated-server code;
- test the exact side-specific code path, including registry/context absence where relevant; and
- keep client/server artifacts identical where the pack needs the mod on both sides.

Compatibility is the intersection of tested target signatures and declared dependency ranges. A broad Maven/loader range is not evidence of broad compatibility. Recheck code before widening any dependency pin. If the target supports multiple incompatible signatures, ask whether to split the code path or keep the narrower pin rather than adding speculative reflection.

Minecraft and NeoForge versions are sourced from the pack's `pack/pack.toml`; do not add copied version values to documentation or workflow inputs. The build helper reads those values from the pack checkout or accepts explicit version arguments for an isolated build. Keep the patch `VERSION` separate from the pack's version.

## 7. Required validation

Validation is layered. State the result of each layer separately; do not promote a lower-level pass into a higher-level claim.

### Build and artifact

From this repository, with the pack repository checked out beside it or passed explicitly:

```sh
python3 scripts/build_patches.py --pack-root ../Lead-and-Leylines
```

The helper reads the pack's current Minecraft and NeoForge versions, runs the Gradle build using Java 21, checks that the expected versioned JAR exists, and prints its SHA-256. Inspect Gradle output for compile errors, Mixin annotation/refmap problems, and target warnings. The JAR is generated under `build/`; never commit it.

The Gradle archive is configured for stable ordering and timestamps. If a reproducibility concern or build configuration change arises, perform clean builds and compare the resulting digest. A build pass proves only compilation and packaging; it does not prove that Mixin applies at runtime or that the target crash is fixed.

### Pack-side server smoke test

After a pack-facing mod or config change, follow the pack repository's [`local-smoke-test` procedure](https://github.com/TinorNoah/Lead-and-Leylines/blob/main/.agents/skills/local-smoke-test/SKILL.md). Its default is the full benchmark using the configured 8 GiB server limit. Do not delete the pack's mod caches to force downloads. Inspect the server output for mod-load errors and Mixin injection failures. The smoke test is not a client test.

### Focused runtime regression

For the existing MI issue, test both items in the server-held-item WOM call path. The known commands are in [`README.md`](../README.md). Verify no lookup exception appears and that the entities remain alive. If code or target versions change, first reconfirm item IDs and method signatures instead of assuming these commands remain valid.

### Connected-client regression

For the reported symptom, use a client and dedicated server with the exact same patch JAR and compatible pack files. Join the server, open Creative Search, search for the steam drill and diesel tool, and confirm the client remains connected and the creative listing populates. Record client and server versions, steps, result, and relevant log evidence.

If no matching client is available, state **not tested** and ask whether to wait for client verification or proceed with a clearly labeled prerelease. Never infer a connected-client pass from server-side bytecode inspection, zombie probes, a local singleplayer world, or server smoke results.

### Reporting test outcomes

Use precise language:

- “build passed” means compilation and artifact generation passed;
- “server injection passed” means runtime logs/debug export show the specific target transformed;
- “server regression passed” means the focused server reproduction passed;
- “connected-client regression passed” requires the exact manual client/server reproduction; and
- “unverified” means no evidence either way for that path.

Include failed or skipped tests, reasons, and any known limitation. Do not call a patch “fixed” without naming the tested scope.

## 8. Documentation and review checklist

For every fix that is proposed for shipping, update the README fix table and add enough detail to answer:

- What user-visible problem does it fix?
- What is the confirmed root cause?
- Which precise target is changed?
- What behavior changes, and what remains unchanged?
- Which side(s) and exact dependency/signature boundaries apply?
- Which tests were run, and which are still unverified?
- What upstream fix or event would allow removal?

For a removed or superseded patch, move its explanation to a dated history section. Do not erase why it was added; future contributors need to know which regression must not be reintroduced.

A good PR includes:

1. Short problem statement and reproducible trigger.
2. Evidence and root-cause analysis.
3. Alternatives researched and reason for choosing a patch.
4. Scope/side/version contract.
5. Minimal implementation description.
6. Build, server, and client test results—each separately.
7. Remaining risks, manual work, and follow-up/upstream status.

Review the diff for accidental broad scope, missing client isolation, metadata pins that disagree with source assumptions, generated files, stale docs, unverified claims, and secrets.

## 9. Versioning and immutable artifact lifecycle

`VERSION` is the JAR release version. It does not track the pack version. A release tag is `patches-v<VERSION>` and its asset is `leylines-patches-<VERSION>.jar`.

Publication rules:

1. Merge the source change to this repository's `main` through a reviewed PR.
2. Check out a clean, up-to-date `main`; build the exact source that is committed there.
3. Review test evidence and ask the owner if a material client path is unverified or publication was not expressly requested.
4. Set `GH_TOKEN` in the environment or a gitignored root `.env`. Never print, paste, or commit the token.
5. Run `python3 scripts/publish_patches.py`. It verifies the clean branch/remote commit and refuses to overwrite different bytes for an existing release/tag.
6. Verify the public download asset and SHA-256. If the asset/tag already exists with different bytes, do not delete or retarget it—increment `VERSION` for a new artifact.
7. If this version is meant to be consumed by the pack, update the pack repository's `.pw.toml` URL and SHA-256, run `packwiz refresh` from its `pack/` directory, validate the catalog/integration, and run its local smoke test.

Do not publish from a feature branch. Do not use pack release tags `vX.Y.Z` for patch artifacts. Do not publish a CurseForge/Modrinth store release or full pack release from this repository. A publicly available prerelease is not evidence that the client regression passed.

The current published patch artifact is `patches-v0.1.0`; its bytes are immutable. New bytes require a new `VERSION`, even if the source change seems small.

## 10. Security, privacy, and pack safety

- Never hardcode or commit GitHub tokens, panel credentials, private hostnames, player data, or unredacted logs that contain secrets/personal data.
- Keep crash logs only when they are needed and sanitized. Do not upload unrelated user logs to a public issue/PR.
- Do not commit third-party mod JARs, generated patch JARs, Gradle caches, launcher folders, or pack exports.
- Do not alter the pack's world data, configs, dependency selection, release channel, or server deployment as a side effect of patch-source work.
- Do not run panel/server deployment or publish a pack release from this repository.
- Preserve the pack's existing artifact caches; use the pack's documented cache path rather than wiping and refetching dependencies.

## 11. When to stop and ask

Ask the project owner before proceeding if any of these is unclear:

- the report does not identify one credible root cause;
- the target method/argument or version boundary cannot be verified;
- the correct fallback data source or no-context behavior is uncertain;
- the fix could change gameplay, saved-world data, or item/enchantment semantics;
- client-only versus server/common ownership is ambiguous;
- alternatives differ materially in risk or behavior;
- the only available tests do not cover the side that crashed, but someone wants to claim resolution or publish a release;
- a patch is proposed for removal or dependency/version pins need broadening; or
- the user’s request could mean either code changes, a public release, or pack deployment.

A useful clarification request says:

> I can verify **[known facts]**, but **[specific uncertainty]** is not established. The safe choices are **A** and **B**; I recommend **A** because **reason**. Do you want me to proceed with that scope, gather more evidence, or leave the code unchanged?

Do not ask questions whose answer is already present in current source, logs, pack metadata, or authoritative documentation. Inspect first; ask only for a decision or evidence that is genuinely unavailable.

## 12. Definition of done

A patch change is ready for review when the cause and behavior contract are documented, implementation scope matches it, side isolation and version metadata are correct, focused tests are run or explicitly marked unavailable, README/ledger details are current, and the PR clearly lists what is not verified.

A patch is ready to be described as resolving a user-visible crash only after the relevant client/server reproduction succeeds. A patch artifact is ready for pack consumption only after it is published immutably, the pack manifest/hash is updated and refreshed, and the pack-side validation required for the change is reported. If either criterion cannot be met, say what remains and ask the owner how to proceed.
