# Lead and Leylines Patches

A small, project-owned NeoForge mod containing narrowly scoped compatibility fixes for the [Lead and Leylines modpack](https://github.com/TinorNoah/Lead-and-Leylines). The patch JAR is distributed as an immutable prerelease asset; this repository is the source of truth for its code, build, release process, and regression ledger.

This is not a general-purpose mod or a place for speculative workarounds. Prefer an upstream fix, a supported configuration/datapack override, or an existing compatible addon. Keep a project-owned patch only when those options do not solve a reproduced pack issue.

## Current fixes

| Fix | Trigger and cause | Patch behavior | Sides | Compatibility boundary |
|---|---|---|---|---|
| Weapons of Miracles × Modern Industrialization drill enchantment lookup | Weapons of Miracles requests enchantment levels while Modern Industrialization constructs the synthetic enchantments for a steam drill or fueled diesel tool. MI 2.5.8 dereferences a null enchantment-registry lookup on that path. The reported client symptom is a crash while opening Creative Search on a connected dedicated server; the same unsafe code path exists for the diesel tool. | Before either MI `getAllEnchantments` method runs, preserve a non-null lookup. If it is null, use the active server registry on the logical server or the connected client level's registry on the client. If no world/server registry is available, leave it null rather than inventing a global registry or masking the absence of a game context. | Both | MI `2.5.8` is an exact required dependency in the mod metadata. Targets are `SteamDrillItem` and `DieselToolItem`; re-check their signatures before widening the pin. |

Implementation:

- `src/main/java/dev/leadandleylines/patches/mixin/ModernIndustrializationEnchantmentLookupMixin.java` — server/common registry fallback.
- `src/main/java/dev/leadandleylines/patches/mixin/ClientModernIndustrializationEnchantmentLookupMixin.java` — connected-client registry fallback.
- `src/main/resources/leylines_patches.mixins.json` — common and client Mixin registration.
- `src/main/resources/META-INF/neoforge.mods.toml` — Minecraft, NeoForge, and exact MI version requirements.

### Regression checks

A successful Gradle build or clean server boot proves neither that Mixin applied to both targets nor that Creative Search no longer crashes. Validate in layers:

1. Build with `python3 scripts/build_patches.py --pack-root <path-to-pack-repository>`. Inspect the output for Mixin/refmap or target errors.
2. Run `python3 scripts/smoke_test.py` from the pack repository (the default is its full benchmark with an 8 GiB server heap). Confirm the server loads the patch and MI without an injection error.
3. With a client and dedicated server using the identical patch JAR, join the server, open Creative Search, and search for the steam drill and diesel tool. Confirm the client remains connected and the creative item list populates.
4. Exercise the server-side path with both tool types. From the server console, spawn zombies holding the items (the item names below match the current MI pin):

   ```text
   summon minecraft:zombie 0 100 0 {HandItems:[{id:"modern_industrialization:steam_mining_drill",Count:1b},{}],PersistenceRequired:1b}
   summon minecraft:zombie 4 100 0 {HandItems:[{id:"modern_industrialization:diesel_mining_drill",Count:1b},{}],PersistenceRequired:1b}
   ```

   Confirm no null lookup exception appears in the server log and both entities remain alive.
5. Record exact client/server mod versions and the result in the PR or a dated regression note. Do not describe the client Creative Search check as passed until it has been manually performed; a server smoke test is not a substitute.

### Validation status — 2026-09-30

- Gradle build passed and the generated JAR contains the mod and Mixin metadata.
- The default 8 GiB full pack smoke-test benchmark passed. A separate boot with Mixin debug export confirmed the dedicated-server `SteamDrillItem` and `DieselToolItem` classes were transformed; `javap` confirmed both injected server lookup calls.
- The zombie-held-item server regression probe passed for both `modern_industrialization:steam_mining_drill` and `modern_industrialization:diesel_mining_drill` on MI 2.5.8 / WOM 2.0.178.
- Creative Search has not yet been manually tested from a matching client connected to the dedicated server. That client behavior remains **unverified**; do not mark the user-reported crash fully resolved until it passes.

## Adding a future fix

Before writing code:

1. Reproduce the issue on the current pack, or preserve a precise crash/log and minimal reproduction. Record the trigger, stack frame, affected side(s), and exact versions.
2. Check the affected mod's current release, issue tracker, configuration/data hooks, and available compatible fixes. Do not add a patch merely because two mods sound like likely suspects.
3. State why the patch belongs here, the behavior it changes, and the compatibility boundary. Ask for approval before broadening the module's role or introducing an invasive, behavior-changing workaround.
4. Decide whether it is client-only, server-only, or both. For gameplay behavior used in singleplayer and multiplayer, keep both sides in parity unless there is a tested reason not to.

Implementation rules:

- Prefer one small class/Mixin per independent issue, with a descriptive name. Avoid a central all-mods hook or generic interception framework.
- Guard optional mod targets with the appropriate optional-target pattern, and add explicit dependency/version metadata only when the code truly requires those classes or bytecode signatures. Pin narrowly when method semantics are version-specific.
- Use the live level/server registry or service that owns the data. Do not substitute static registries for data-driven registries, and do not silently swallow exceptions or change unrelated behavior.
- Keep client classes in client-only Mixins; dedicated-server loading must not resolve client-only classes.
- Explain non-obvious injection points and fallback semantics in code only where needed. Put diagnosis, alternatives, version matrix, and regression details here.
- Do not commit generated JAR files, Gradle caches, instance folders, or downloaded third-party JARs. `build/` and `.gradle/` are local build output.
- Add a row to **Current fixes** for every shipped tweak, documenting trigger, cause, exact target method/resource, behavior, side, and dependency/version boundary. Update regression steps and checks. Keep resolved/removed fixes in a dated history section rather than deleting the rationale.

## Build

Requirements: Java 21 and Gradle 8 or newer. The build needs the Minecraft and NeoForge versions from the pack's `pack/pack.toml`. When the pack repository is checked out beside this repository, the script can read it automatically; otherwise pass `--pack-root` explicitly. Alternatively, supply `--minecraft-version` and `--neoforge-version`.

From this repository's root:

```sh
python3 scripts/build_patches.py --pack-root ../Lead-and-Leylines
```

The script builds `build/libs/leylines-patches-X.Y.Z.jar` using `VERSION`, prints its SHA-256, and can also update the packwiz manifest if given `--pack-manifest`:

```sh
python3 scripts/build_patches.py \
  --pack-root ../Lead-and-Leylines \
  --pack-manifest ../Lead-and-Leylines/pack/mods/leylines-patches.pw.toml
```

After changing the manifest, run `packwiz refresh` from the pack repository's `pack/` directory. The manifest uses `side = "both"` so server and client/singleplayer installations use identical bytes. The manifest URL points at a versioned release asset, so it becomes downloadable only after that artifact has been published.

## Artifact publication

Patch artifacts use immutable prerelease tags `patches-vX.Y.Z`, separate from the pack's `vX.Y.Z` releases. After the patch change is merged to this repository's clean, up-to-date `main`, set `GH_TOKEN` in the environment (or a gitignored root `.env`) and run:

```sh
python3 scripts/publish_patches.py
```

The publisher requires a clean `main` that exactly matches GitHub, verifies the artifact against its version, and refuses to replace different bytes at an existing release tag. A pre-existing tag/release that does not match the immutable artifact is a hard error; increment `VERSION` for new bytes. Never publish an artifact from a feature branch. Keep mod-browser release channels limited to pack tags `vX.Y.Z`.
