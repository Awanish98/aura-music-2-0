#!/usr/bin/env python3
"""Assemble chunked APK/AAB release assets inside GitHub Actions.

The local network cannot reliably push a single 150-190 MB asset to the GitHub
upload endpoint. Small `apk.part-NNN` / `aab.part-NNN` assets are uploaded to
the draft release instead. This script runs on a GitHub runner, where the
GitHub-to-GitHub transfer is fast, verifies the original SHA-256 digests,
publishes the final assets under the names used by website/config.js, then
removes the temporary chunks.
"""

import hashlib
import json
import os
import re
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "Awanish98/aura-music-2-0")
TAG = os.environ.get("RELEASE_TAG", "v1.4.6")
TOKEN = os.environ.get("GITHUB_TOKEN", "")

TARGETS = (
    {
        "prefix": "apk",
        "name": "AURA-Music-UniversalGms-Release.apk",
        "sha256": os.environ.get("APK_SHA256", "F46917BAE7299546ECD01815728F9DE783F641A9988F56C05D3C97158148AB16").lower(),
    },
    {
        "prefix": "aab",
        "name": "AURA-Music-UniversalGms-Release.aab",
        "sha256": os.environ.get("AAB_SHA256", "6C9F33EF5B55B53B4C4A8B136601357D1CF45F8AFF9FC1451AE78F55C07305D3").lower(),
    },
)


def api(url, method="GET", body=None, accept="application/vnd.github+json"):
    if not TOKEN:
        raise RuntimeError("GITHUB_TOKEN is required")
    request = urllib.request.Request(url, data=body, method=method)
    request.add_header("Authorization", f"Bearer {TOKEN}")
    request.add_header("Accept", accept)
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    if body is not None:
        request.add_header("Content-Type", "application/octet-stream")
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            raw = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")[:500]
        raise RuntimeError(f"{method} {url} failed: HTTP {error.code} {detail}") from error
    return json.loads(raw) if raw and accept == "application/vnd.github+json" else raw


def load_release():
    return api(f"{API}/repos/{REPO}/releases/tags/{TAG}")


def chunk_list(release, prefix):
    pattern = re.compile(rf"^{re.escape(prefix)}\.part-(\d+)$")
    found = []
    for asset in release["assets"]:
        match = pattern.match(asset["name"])
        if match:
            found.append((int(match.group(1)), asset))
    found.sort(key=lambda item: item[0])
    expected = list(range(1, len(found) + 1))
    actual = [number for number, _ in found]
    if actual != expected:
        raise RuntimeError(f"{prefix}: missing chunks, expected {expected}, found {actual}")
    if not found:
        raise RuntimeError(f"{prefix}: no chunks found on release {TAG}")
    return [asset for _, asset in found]


def download(asset, path):
    request = urllib.request.Request(asset["url"])
    request.add_header("Authorization", f"Bearer {TOKEN}")
    request.add_header("Accept", "application/octet-stream")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    with urllib.request.urlopen(request, timeout=300) as response, path.open("wb") as output:
        shutil.copyfileobj(response, output, 1024 * 1024)


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest().lower()


def delete_asset(asset):
    api(f"{API}/repos/{REPO}/releases/assets/{asset['id']}", method="DELETE")
    print(f"deleted chunk {asset['name']}")


def upload_final(release, name, path):
    for asset in release["assets"]:
        if asset["name"] == name:
            delete_asset(asset)
    upload_url = release["upload_url"].split("{", 1)[0]
    url = upload_url + "?name=" + urllib.parse.quote(name)
    body = path.read_bytes()
    api(url, method="POST", body=body)
    print(f"uploaded {name} ({len(body)} bytes)")


def assemble(release, target, workdir):
    prefix = target["prefix"]
    chunks = chunk_list(release, prefix)
    merged = workdir / target["name"]
    with merged.open("wb") as output:
        for asset in chunks:
            part = workdir / asset["name"]
            print(f"downloading {asset['name']} ({asset['size']} bytes)")
            download(asset, part)
            with part.open("rb") as source:
                shutil.copyfileobj(source, output, 1024 * 1024)
            part.unlink()
    actual = digest(merged)
    if actual != target["sha256"]:
        raise RuntimeError(f"{target['name']}: SHA-256 mismatch: {actual} != {target['sha256']}")
    print(f"{target['name']}: SHA-256 verified {actual}")
    upload_final(release, target["name"], merged)
    for asset in chunks:
        delete_asset(asset)


def main():
    workdir = Path(os.environ.get("RUNNER_TEMP", ".")) / "aura-release-assembly"
    workdir.mkdir(parents=True, exist_ok=True)
    release = load_release()
    print(f"release {TAG}: {release['id']}, draft={release['draft']}, assets={len(release['assets'])}")
    for target in TARGETS:
        assemble(release, target, workdir)
    shutil.rmtree(workdir, ignore_errors=True)
    print("release assets assembled and verified")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # noqa: BLE001 - surface a clear CI failure
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
