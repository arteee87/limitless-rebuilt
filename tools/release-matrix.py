#!/usr/bin/env python3
"""Builds the release matrix from the repository's tags, and checks the tags against the sources.

Releases on GitHub are keyed by tag, not by branch, so a repository carrying one branch per Minecraft
version still shows every release in a single list. The tag name is therefore the only place the pair
(mod version, Minecraft version) is recorded, and this script turns those tags into the table that
belongs in the README.

Two modes:

    ./tools/release-matrix.py            print the markdown table for the README
    ./tools/release-matrix.py --check    verify the tags against gradle.properties

``--check`` is the one worth running before a release. The version in ``gradle.properties`` is only
bumped at release time, so a forgotten bump, or a typo in a tag, is otherwise invisible until somebody
downloads the wrong jar.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict

TAG = re.compile(r"^v(?P<mod>\d+\.\d+\.\d+)\+mc(?P<mc>\d+\.\d+(?:\.\d+)?)$")
REPO_URL = "https://github.com/arteee87/limitless-rebuilt"
PROPERTIES = "gradle.properties"


def git(*args: str, check: bool = True) -> str:
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=check
    ).stdout.strip()


def tags() -> list[tuple[str, str, str]]:
    """Every release tag as (tag, mod version, minecraft version), newest line last."""
    found = []

    for raw in git("tag", "-l", "v*").splitlines():
        match = TAG.match(raw)

        if match is None:
            print(f"skipping tag {raw!r}: not shaped v<mod>+mc<minecraft>", file=sys.stderr)
            continue

        found.append((raw, match.group("mod"), match.group("mc")))

    return sorted(found, key=lambda entry: (entry[2], version_key(entry[1])))


def version_key(version: str) -> tuple[int, int, int]:
    major, minor, patch = version.split(".")
    return int(major), int(minor), int(patch)


def properties(ref: str) -> dict[str, str]:
    """Key=value pairs of gradle.properties as it stood at that ref, or none if it has no such file.

    A branch that only holds documentation, such as a hub branch, legitimately has no
    gradle.properties; treating that as an error would make the check unusable.
    """
    text = git("show", f"{ref}:{PROPERTIES}", check=False)

    if not text:
        return {}

    values = {}

    for line in text.splitlines():
        line = line.strip()

        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()

    return values


def table(releases: list[tuple[str, str, str]]) -> str:
    grouped: dict[str, list[tuple[str, str]]] = defaultdict(list)

    for tag, mod, minecraft in releases:
        grouped[minecraft].append((mod, tag))

    if not grouped:
        return "_No releases yet._"

    rows = ["| Minecraft | Mod versions | Releases |", "|---|---|---|"]

    for minecraft in sorted(grouped, key=version_key):
        entries = grouped[minecraft]
        mods = ", ".join(mod for mod, _ in entries)
        links = ", ".join(f"[{tag}]({REPO_URL}/releases/tag/{tag})" for _, tag in entries)
        rows.append(f"| {minecraft} | {mods} | {links} |")

    return "\n".join(rows)


def check(releases: list[tuple[str, str, str]]) -> int:
    """Compares every tag against the properties file committed next to it."""
    problems = 0

    for tag, mod, minecraft in releases:
        values = properties(tag)

        if mod_version := values.get("mod_version"):
            if mod_version != mod:
                print(f"{tag}: tag says {mod}, {PROPERTIES} says {mod_version}")
                problems += 1
        else:
            print(f"{tag}: no mod_version in {PROPERTIES}")
            problems += 1

        if mc_version := values.get("minecraft_version"):
            if mc_version != minecraft:
                print(f"{tag}: tag says mc{minecraft}, {PROPERTIES} says {mc_version}")
                problems += 1
        else:
            print(f"{tag}: no minecraft_version in {PROPERTIES}")
            problems += 1

    # A release is cut from a commit whose version has to match the tag, so the tip of every branch
    # that already has a release should either be ahead of it or still carry that exact version.
    for branch in git("branch", "-r", "--format=%(refname:short)").splitlines():
        if not branch.startswith("origin/") or "HEAD" in branch:
            continue

        minecraft = current_minecraft(branch)
        line = [(tag, mod) for tag, mod, mc in releases if mc == minecraft]

        if not minecraft:
            print(f"{branch}: no {PROPERTIES}, nothing to check here")
            continue

        if not line:
            print(f"{branch}: mc{minecraft} has no releases yet")
            continue

        tag, mod = line[-1]
        tip = properties(branch)

        if tip.get("mod_version") == mod:
            print(f"{branch}: at {mod}, matching the newest release {tag}")
        elif version_key(tip.get("mod_version", "0.0.0")) > version_key(mod):
            print(f"{branch}: {tip.get('mod_version')} is ahead of {tag}, tag it when you release")
        else:
            print(f"{branch}: {tip.get('mod_version')} is behind {tag}, did the bump get lost?")
            problems += 1

    return problems


def current_minecraft(branch: str) -> str:
    return properties(branch).get("minecraft_version", "")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify tags against gradle.properties")
    arguments = parser.parse_args()

    releases = tags()

    if arguments.check:
        problems = check(releases)
        print("checked" if not problems else f"{problems} problem(s) found")
        return 1 if problems else 0

    print(table(releases))
    return 0


if __name__ == "__main__":
    sys.exit(main())