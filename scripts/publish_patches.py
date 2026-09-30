#!/usr/bin/env python3
"""Publish an immutable patch-mod JAR to its versioned GitHub prerelease.

Run only after the patch change has been merged to a clean, up-to-date main.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
OWNER = "TinorNoah"
REPO = "Lead-and-Leylines-Patches"
API = "https://api.github.com"
USER_AGENT = "TinorNoah/Lead-and-Leylines-Patches publisher"
VERSION_FILE = ROOT / "VERSION"


def load_local_env() -> None:
    path = ROOT / ".env"
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = value


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode:
        raise SystemExit(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def api_request(
    url: str,
    token: str,
    *,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    data: bytes | None = None,
    content_type: str | None = None,
    allow_not_found: bool = False,
) -> Any:
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        content_type = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            body = response.read()
            if not body:
                return None
            response_type = response.headers.get("Content-Type", "")
            if "json" in response_type or body[:1] in (b"{", b"["):
                return json.loads(body.decode("utf-8"))
            return body
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:2000]
        if allow_not_found and error.code == 404:
            return None
        raise SystemExit(f"GitHub API {method} failed with HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise SystemExit(f"GitHub API {method} failed: {error}") from error


def tag_commit_sha(tag: str, token: str) -> str | None:
    encoded_tag = urllib.parse.quote(tag, safe="")
    ref = api_request(
        f"{API}/repos/{OWNER}/{REPO}/git/ref/tags/{encoded_tag}",
        token,
        allow_not_found=True,
    )
    if not isinstance(ref, dict):
        return None
    target = ref.get("object")
    for _ in range(8):
        if not isinstance(target, dict):
            return None
        kind = target.get("type")
        sha = target.get("sha")
        if kind == "commit":
            return str(sha) if sha else None
        if kind != "tag" or not sha:
            return None
        tag_object = api_request(f"{API}/repos/{OWNER}/{REPO}/git/tags/{sha}", token)
        target = tag_object.get("object") if isinstance(tag_object, dict) else None
    raise SystemExit(f"Git tag {tag} contains too many annotated-tag references")


def artifact_details() -> tuple[str, str, Path, str]:
    version = VERSION_FILE.read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit(f"VERSION must be X.Y.Z, got {version!r}")
    filename = f"leylines-patches-{version}.jar"
    artifact = ROOT / "build" / "libs" / filename
    if not artifact.is_file() or artifact.stat().st_size == 0:
        raise SystemExit(f"patch artifact is missing; run scripts/build_patches.py: {artifact}")
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    return version, filename, artifact, digest


def main() -> None:
    load_local_env()
    token = os.environ.get("GH_TOKEN", "").strip()
    if not token:
        raise SystemExit("GH_TOKEN is required (set it in the environment or repository-root .env)")

    branch = git("branch", "--show-current")
    if branch != "main":
        raise SystemExit(f"refusing to publish from {branch!r}; check out main after merge")
    status = git("status", "--short")
    if status:
        raise SystemExit(f"refusing to publish with a dirty working tree:\n{status}")
    commit = git("rev-parse", "HEAD")

    version, filename, artifact, digest = artifact_details()
    tag = f"patches-v{version}"
    main_branch = api_request(f"{API}/repos/{OWNER}/{REPO}/branches/main", token)
    if not isinstance(main_branch, dict) or main_branch.get("commit", {}).get("sha") != commit:
        raise SystemExit("local main must exactly match the latest GitHub main commit before publishing")

    tag_path = urllib.parse.quote(tag, safe="")
    release = api_request(
        f"{API}/repos/{OWNER}/{REPO}/releases/tags/{tag_path}",
        token,
        allow_not_found=True,
    )
    created_release = release is None
    if release is None:
        ref = api_request(
            f"{API}/repos/{OWNER}/{REPO}/git/ref/tags/{tag_path}",
            token,
            allow_not_found=True,
        )
        if ref is not None:
            raise SystemExit(f"Git tag {tag} already exists without a matching release; refusing to retarget it")
        release = api_request(
            f"{API}/repos/{OWNER}/{REPO}/releases",
            token,
            method="POST",
            payload={
                "tag_name": tag,
                "target_commitish": commit,
                "name": f"Lead and Leylines Patches {version}",
                "body": (
                    "Project-owned compatibility patch artifact for Lead and Leylines. "
                    "See this repository's README.md for fix scope and regression notes."
                ),
                "draft": False,
                "prerelease": True,
            },
        )
        print(f"created prerelease {tag}")

    if not isinstance(release, dict) or release.get("tag_name") != tag:
        raise SystemExit(f"GitHub returned an unexpected release for {tag}")
    if release.get("draft") or not release.get("prerelease"):
        raise SystemExit(f"release {tag} exists but is not a published prerelease")

    for asset in release.get("assets", []):
        if not isinstance(asset, dict) or asset.get("name") != filename:
            continue
        download_url = asset.get("browser_download_url")
        if not download_url:
            raise SystemExit(f"existing release asset {filename} has no download URL")
        request = urllib.request.Request(str(download_url), headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                existing_digest = hashlib.sha256(response.read()).hexdigest()
        except (urllib.error.HTTPError, urllib.error.URLError) as error:
            raise SystemExit(f"could not verify existing {tag}/{filename}; refusing to replace it: {error}") from error
        if existing_digest != digest:
            raise SystemExit(
                f"{tag}/{filename} already exists with different bytes; increment VERSION instead"
            )
        print(f"already published {tag}/{filename} (SHA-256 {digest})")
        return

    if not created_release and tag_commit_sha(tag, token) != commit:
        raise SystemExit(
            f"release {tag} has no matching asset and its tag does not point at current main; refusing to attach bytes"
        )

    upload_url = (
        f"https://uploads.github.com/repos/{OWNER}/{REPO}/releases/"
        f"{release['id']}/assets?{urllib.parse.urlencode({'name': filename})}"
    )
    api_request(
        upload_url,
        token,
        method="POST",
        data=artifact.read_bytes(),
        content_type="application/java-archive",
    )
    print(f"published {tag}/{filename} (SHA-256 {digest})")


if __name__ == "__main__":
    main()
