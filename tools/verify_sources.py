#!/usr/bin/env python3
"""Static checks that run without a NeoForge artifact on the classpath.

These exist because the interesting failure modes in a Minecraft mod are not compile errors.
A misspelled resource directory, a translation key that no lang file defines, or a datapack
file whose numbers contradict another datapack file all compile perfectly and fail silently at
runtime — which is exactly how this mod originally shipped an item with no texture and a
keybind that did nothing.

Run from the repository root:  python3 tools/verify_sources.py
"""

from __future__ import annotations

import glob
import json
import os
import re
import sys

MODID = "murimcultivation"
JAVA_ROOTS = ("src/main/java/", "src/test/java/")
LANG_FILE = f"src/main/resources/assets/{MODID}/lang/en_us.json"
DATA_DIR = f"src/main/resources/data/{MODID}/{MODID}"
INTERNAL_PACKAGE = "com.andymods.murimcultivation."

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def strip_code(source: str) -> str:
    """Remove comments, then string and char literals.

    Order matters: an apostrophe in a comment ("the player's Qi") opens a bogus char literal
    that swallows everything up to the next apostrophe, including real code.
    """
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    source = re.sub(r"//[^\n]*", " ", source)
    source = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', source)
    source = re.sub(r"'(?:\\.|[^'\\\n])*'", "''", source)
    return source


def java_files() -> list[str]:
    return sorted(f for root in JAVA_ROOTS for f in glob.glob(root + "**/*.java", recursive=True))


def package_of(path: str) -> str:
    for root in JAVA_ROOTS:
        if path.startswith(root):
            return os.path.dirname(path[len(root):]).replace("/", ".")
    return ""


def check_structure() -> None:
    """Package declarations, internal imports, unused imports, bracket balance, type names."""
    files = java_files()
    known = {}
    for path in files:
        for root in JAVA_ROOTS:
            if path.startswith(root):
                known[path[len(root):-len(".java")].replace("/", ".")] = path

    for path in files:
        raw = open(path, encoding="utf-8").read()
        body = strip_code(re.sub(r"^(package|import)[^\n]*\n", "", raw, flags=re.M))
        stripped = strip_code(raw)

        declared = re.search(r"^package\s+([\w.]+);", raw, re.M)
        expected = package_of(path)
        if not declared:
            fail(f"{path}: no package declaration")
        elif declared.group(1) != expected:
            fail(f"{path}: package {declared.group(1)} does not match directory {expected}")

        for imported in re.findall(r"^import\s+(?:static\s+)?([\w.]+);", raw, re.M):
            simple = imported.rsplit(".", 1)[-1]
            if imported.startswith(INTERNAL_PACKAGE) and imported not in known:
                fail(f"{path}: import does not resolve to a source file: {imported}")
            if not re.search(rf"\b{re.escape(simple)}\b", body):
                fail(f"{path}: unused import {imported}")

        for opening, closing, label in (("{", "}", "braces"), ("(", ")", "parentheses")):
            if stripped.count(opening) != stripped.count(closing):
                fail(f"{path}: unbalanced {label}")

        stem = os.path.basename(path)[:-len(".java")]
        if not re.search(rf"\b(class|enum|record|interface)\s+{re.escape(stem)}\b", raw):
            fail(f"{path}: no top-level type named {stem}")


def enum_ids(path: str) -> list[str]:
    """The serialized names of a StringRepresentable enum, e.g. LUNG("lung") -> lung."""
    return re.findall(r'^\s+[A-Z][A-Z0-9_]*\("([a-z_]+)"', open(path, encoding="utf-8").read(), re.M)


def datapack_ids(registry: str) -> list[str]:
    return sorted(os.path.basename(p)[:-len(".json")]
                  for p in glob.glob(f"{DATA_DIR}/{registry}/*.json"))


def check_translation_keys() -> None:
    """Every key the code or datapack content references must exist, and vice versa.

    Keys are built three ways — as literals, by concatenating a prefix with an enum's
    serialized name, and by concatenating a prefix with a datapack id — so all three are
    resolved here rather than only the easy one.
    """
    if not os.path.exists(LANG_FILE):
        fail(f"missing lang file {LANG_FILE}")
        return

    defined = set(json.load(open(LANG_FILE, encoding="utf-8")))
    referenced: set[str] = set()

    for path in glob.glob("src/main/java/**/*.java", recursive=True):
        source = open(path, encoding="utf-8").read()
        for match in re.finditer(r'"((?:' + MODID + r'|key|itemGroup)\.[A-Za-z0-9_.]+[A-Za-z0-9_])"', source):
            referenced.add(match.group(1))

    # Prefixes completed at runtime. Each maps to the set of suffixes it can produce.
    suffix_sources = {
        f"{MODID}.meridian.": enum_ids("src/main/java/com/andymods/murimcultivation/cultivation/Meridian.java"),
        f"{MODID}.substage.": enum_ids("src/main/java/com/andymods/murimcultivation/cultivation/Substage.java"),
        f"{MODID}.deviation.": enum_ids(
            "src/main/java/com/andymods/murimcultivation/cultivation/DeviationSeverity.java"),
        f"{MODID}.qi_density.": enum_ids("src/main/java/com/andymods/murimcultivation/cultivation/QiDensity.java"),
        f"{MODID}.hand_requirement.": enum_ids(
            "src/main/java/com/andymods/murimcultivation/technique/HandRequirement.java"),
        f"{MODID}.technique.refused.hand.": enum_ids(
            "src/main/java/com/andymods/murimcultivation/technique/HandRequirement.java"),
        # MartialManualItem derives a technique's name from its id.
        f"{MODID}.technique.": datapack_ids("technique"),
        # One key per loadout slot, numbered from 1.
        "key." + MODID + ".technique_slot_": [str(i) for i in range(1, 5)],
    }

    seen_prefixes = set()
    for path in glob.glob("src/main/java/**/*.java", recursive=True):
        source = open(path, encoding="utf-8").read()
        for match in re.finditer(r'"((?:' + MODID + r'|key)\.[A-Za-z0-9_.]*[._])"\s*\+', source):
            seen_prefixes.add(match.group(1))

    for prefix in sorted(seen_prefixes):
        suffixes = suffix_sources.get(prefix)
        if suffixes is None:
            fail(f"translation prefix built at runtime but not covered by this check: {prefix!r}"
                 f" (add it to suffix_sources in tools/verify_sources.py)")
            continue
        referenced.discard(prefix)  # the bare prefix is not itself a key
        for suffix in suffixes:
            referenced.add(prefix + suffix)

    for registry in ("realm", "technique"):
        for path in glob.glob(f"{DATA_DIR}/{registry}/*.json"):
            referenced.add(json.load(open(path, encoding="utf-8"))["translation_key"])

    for key in sorted(referenced - defined):
        fail(f"translation key referenced but not defined: {key}")
    # Item and creative-tab keys are resolved by vanilla from the registry name.
    for key in sorted(defined - referenced):
        if not key.startswith(("item.", "itemGroup.")):
            fail(f"translation key defined but never referenced: {key}")


def check_json_parses() -> None:
    for path in sorted(glob.glob("src/main/resources/**/*.json", recursive=True)
                       + glob.glob("src/main/resources/**/*.mcmeta", recursive=True)):
        try:
            json.load(open(path, encoding="utf-8"))
        except Exception as error:
            fail(f"{path}: invalid JSON: {error}")


def check_datapack_consistency() -> None:
    """Cross-file invariants that no single file's schema can express.

    A technique is gated twice: by its own required_realm_tier and by the cap the realm places
    on what a body can channel. Those live in different files, and when they disagree the
    effective gate silently differs from the declared one.
    """
    realms = [json.load(open(p, encoding="utf-8")) for p in glob.glob(f"{DATA_DIR}/realm/*.json")]
    techniques = {os.path.basename(p)[:-len(".json")]: json.load(open(p, encoding="utf-8"))
                  for p in glob.glob(f"{DATA_DIR}/technique/*.json")}
    if not realms or not techniques:
        fail("expected realm and technique datapack content to exist")
        return

    realms.sort(key=lambda realm: realm["tier"])

    tiers = [realm["tier"] for realm in realms]
    if len(set(tiers)) != len(tiers):
        fail(f"realm tiers are not unique: {tiers}")

    for name, technique in sorted(techniques.items()):
        eligible = [r for r in realms if r["tier"] >= technique["required_realm_tier"]]
        if not eligible:
            fail(f"technique {name}: no realm reaches required_realm_tier "
                 f"{technique['required_realm_tier']}")
            continue
        first = eligible[0]
        if first.get("technique_tier", 0) < technique["tier"]:
            fail(f"technique {name}: declares required_realm_tier "
                 f"{technique['required_realm_tier']}, but the first realm meeting that "
                 f"(tier {first['tier']}) caps technique tier at "
                 f"{first.get('technique_tier', 0)} while the technique is tier "
                 f"{technique['tier']} — the real gate is higher than declared")

    starting = realms[0]
    castable = [name for name, technique in techniques.items()
                if technique["required_realm_tier"] <= starting["tier"]
                and technique["tier"] <= starting.get("technique_tier", 0)]
    if len(castable) < 2:
        fail(f"the starting realm (tier {starting['tier']}) can cast only {castable}; "
             f"a new cultivator should have at least two usable arts")


def main() -> int:
    check_structure()
    check_json_parses()
    check_translation_keys()
    check_datapack_consistency()

    java_count = len(java_files())
    if failures:
        print(f"verify_sources: {len(failures)} problem(s) across {java_count} Java files\n")
        for problem in failures:
            print(f"  {problem}")
        return 1

    print(f"verify_sources: OK — {java_count} Java files, structure, JSON, "
          f"translation keys and datapack consistency all clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
