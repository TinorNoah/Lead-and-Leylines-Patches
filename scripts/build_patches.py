#!/usr/bin/env python3
"""Build the project-owned NeoForge compatibility patch mod."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import textwrap
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK_REPOSITORY_NAME = "Lead-and-Leylines"
PATCH_REPOSITORY = "TinorNoah/Lead-and-Leylines-Patches"
VERSION_FILE = ROOT / "VERSION"


def patch_version() -> str:
    version = VERSION_FILE.read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit(f"VERSION must be X.Y.Z, got {version!r}")
    return version


def gradle_command() -> str:
    configured = os.environ.get("GRADLE", "").strip()
    candidates = [configured, shutil.which("gradle") or ""]
    if sys.platform == "darwin":
        candidates.extend(
            [
                "/opt/homebrew/opt/gradle@8/bin/gradle",
                "/opt/homebrew/bin/gradle",
            ]
        )
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    raise SystemExit("Gradle 8 or newer is required; install it or set GRADLE to its executable")


def java21_home() -> Path:
    configured_home = os.environ.get("JAVA_HOME", "").strip()
    candidates: list[Path] = []
    if configured_home:
        candidates.append(Path(configured_home))
    java = shutil.which("java")
    if java:
        candidates.append(Path(java).resolve().parent.parent)
    if sys.platform == "darwin":
        candidates.append(Path("/opt/homebrew/opt/openjdk@21"))

    checked: set[Path] = set()
    for home in candidates:
        home = home.expanduser().resolve()
        if home in checked:
            continue
        checked.add(home)
        executable = home / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not executable.is_file():
            continue
        result = subprocess.run(
            [str(executable), "-version"],
            check=False,
            capture_output=True,
            text=True,
        )
        version_text = f"{result.stderr}\n{result.stdout}"
        match = re.search(r'version "(\d+)', version_text)
        if result.returncode == 0 and match and int(match.group(1)) == 21:
            return home

    raise SystemExit("Java 21 is required to build patches; set JAVA_HOME to its installation")


def resolve_versions(args: argparse.Namespace) -> tuple[str, str]:
    minecraft_version = args.minecraft_version
    neoforge_version = args.neoforge_version
    pack_root = args.pack_root

    if pack_root is None:
        sibling_pack = ROOT.parent / PACK_REPOSITORY_NAME
        if (sibling_pack / "pack" / "pack.toml").is_file():
            pack_root = sibling_pack

    if pack_root:
        pack_file = pack_root.expanduser().resolve() / "pack" / "pack.toml"
        if not pack_file.is_file():
            raise SystemExit(f"pack configuration not found: {pack_file}")
        versions = tomllib.loads(pack_file.read_text(encoding="utf-8")).get("versions", {})
        pack_minecraft = versions.get("minecraft")
        pack_neoforge = versions.get("neoforge")
        if not pack_minecraft or not pack_neoforge:
            raise SystemExit(f"pack/pack.toml is missing [versions].minecraft or [versions].neoforge: {pack_file}")
        if minecraft_version and minecraft_version != pack_minecraft:
            raise SystemExit("--minecraft-version conflicts with pack/pack.toml")
        if neoforge_version and neoforge_version != pack_neoforge:
            raise SystemExit("--neoforge-version conflicts with pack/pack.toml")
        minecraft_version = pack_minecraft
        neoforge_version = pack_neoforge

    if not minecraft_version or not neoforge_version:
        raise SystemExit(
            "Pass --pack-root or both --minecraft-version and --neoforge-version"
        )
    return minecraft_version, neoforge_version


def expected_pack_metadata(version: str, digest: str) -> str:
    filename = f"leylines-patches-{version}.jar"
    url = (
        "https://github.com/"
        f"{PATCH_REPOSITORY}/releases/download/patches-v{version}/{filename}"
    )
    return textwrap.dedent(
        f'''\
        name = "Lead and Leylines Patches"
        filename = "{filename}"
        side = "both"

        [download]
        url = "{url}"
        hash-format = "sha256"
        hash = "{digest}"
        '''
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--pack-root",
        type=Path,
        help="pack repository root; versions are read from pack/pack.toml",
    )
    parser.add_argument("--minecraft-version", help="Minecraft version when not using --pack-root")
    parser.add_argument("--neoforge-version", help="NeoForge version when not using --pack-root")
    parser.add_argument(
        "--pack-manifest",
        type=Path,
        help="optional packwiz .pw.toml path to generate from the built JAR digest",
    )
    args = parser.parse_args()

    version = patch_version()
    minecraft_version, neoforge_version = resolve_versions(args)
    gradle = gradle_command()
    java_home = java21_home()
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = os.pathsep.join(
        [str(java_home / "bin"), env.get("PATH", "/usr/bin:/bin")]
    )
    result = subprocess.run(
        [
            gradle,
            "--no-daemon",
            f"-PminecraftVersion={minecraft_version}",
            f"-PneoForgeVersion={neoforge_version}",
            "build",
        ],
        cwd=ROOT,
        env=env,
        check=False,
    )
    if result.returncode:
        raise SystemExit(f"Gradle build failed with exit {result.returncode}")

    jar = ROOT / "build" / "libs" / f"leylines-patches-{version}.jar"
    if not jar.is_file() or jar.stat().st_size == 0:
        raise SystemExit(f"Gradle did not produce a non-empty artifact: {jar}")
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()

    if args.pack_manifest:
        manifest = args.pack_manifest.expanduser().resolve()
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text(expected_pack_metadata(version, digest), encoding="utf-8")
        print(f"updated pack manifest {manifest}")

    print(f"built {jar.relative_to(ROOT)}")
    print(f"sha256 {digest}")


if __name__ == "__main__":
    main()
