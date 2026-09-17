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
        f"{MODID}.quest.": datapack_ids("quest") + [f"{q}.description" for q in datapack_ids("quest")],
        f"{MODID}.title.": datapack_ids("title") + ["not_owned"],
        f"{MODID}.system.tab.": ["status", "meridians", "techniques", "quests"],
        f"{MODID}.stat.": [s for stat in ("body", "force", "meridian", "insight")
                           for s in (stat, f"{stat}.description")] + ["cannot_spend"],
        f"{MODID}.objective.": enum_ids("src/main/java/com/andymods/murimcultivation/system/ObjectiveKind.java"),
        f"{MODID}.reward.": enum_ids("src/main/java/com/andymods/murimcultivation/system/RewardKind.java"),
        f"{MODID}.quest_category.": enum_ids(
            "src/main/java/com/andymods/murimcultivation/system/QuestCategory.java"),
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

    for registry in ("realm", "technique", "quest", "title"):
        for path in glob.glob(f"{DATA_DIR}/{registry}/*.json"):
            document = json.load(open(path, encoding="utf-8"))
            referenced.add(document["translation_key"])
            if "description_key" in document:
                referenced.add(document["description_key"])

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

    check_quest_consistency(realms, techniques)


def check_quest_consistency(realms: list, techniques: dict) -> None:
    """Quest graph invariants that span files.

    A quest chain is a graph spread across one file per node, so the ways it breaks — a
    prerequisite that does not exist, a cycle, an objective naming a technique that was
    renamed, a gate a prerequisite can never satisfy — are all invisible from any single file.
    """
    quests = {os.path.basename(p)[:-len(".json")]: json.load(open(p, encoding="utf-8"))
              for p in glob.glob(f"{DATA_DIR}/quest/*.json")}
    titles = set(datapack_ids("title"))
    if not quests:
        return

    def local(identifier: str) -> str:
        return identifier.split(":", 1)[-1]

    realm_tiers = {}
    for realm in realms:
        realm_tiers[local(realm["translation_key"].rsplit(".", 1)[-1])] = realm["tier"]

    for name, quest in sorted(quests.items()):
        # Prerequisites must exist, or the quest is permanently unreachable.
        for prerequisite in quest.get("prerequisites", []):
            if local(prerequisite) not in quests:
                fail(f"quest {name}: prerequisite does not exist: {prerequisite}")

        # A prerequisite gated above this quest makes this quest's own gate a lie.
        for prerequisite in quest.get("prerequisites", []):
            parent = quests.get(local(prerequisite))
            if parent and parent.get("required_realm_tier", 0) > quest.get("required_realm_tier", 0):
                fail(f"quest {name}: gated at realm tier {quest.get('required_realm_tier', 0)} but "
                     f"its prerequisite {local(prerequisite)} needs tier "
                     f"{parent.get('required_realm_tier', 0)}")

        for objective in quest["objectives"]:
            target = objective.get("target")
            if target is None:
                continue
            kind = objective["kind"]
            if kind in ("learn_technique", "cast_technique", "master_technique"):
                if local(target) not in techniques:
                    fail(f"quest {name}: objective {kind} names an unknown technique: {target}")
            elif kind == "reach_realm" and local(target) not in realm_tiers:
                fail(f"quest {name}: objective reach_realm names an unknown realm: {target}")

        for reward in quest.get("rewards", []):
            target = reward.get("target")
            if target is None:
                continue
            if reward["kind"] == "technique" and local(target) not in techniques:
                fail(f"quest {name}: reward names an unknown technique: {target}")
            if reward["kind"] == "title" and local(target) not in titles:
                fail(f"quest {name}: reward names an unknown title: {target}")

    # A cycle makes every quest in it unreachable, and nothing in one file reveals it.
    visiting: set = set()
    done: set = set()

    def walk(node: str, trail: list) -> None:
        if node in done:
            return
        if node in visiting:
            fail(f"quest prerequisites form a cycle: {' -> '.join(trail + [node])}")
            return
        visiting.add(node)
        for prerequisite in quests.get(node, {}).get("prerequisites", []):
            parent = local(prerequisite)
            if parent in quests:
                walk(parent, trail + [node])
        visiting.discard(node)
        done.add(node)

    for name in sorted(quests):
        walk(name, [])

    # A new cultivator must have something to do immediately.
    lowest_tier = min(realm["tier"] for realm in realms)
    openers = [name for name, quest in quests.items()
               if not quest.get("prerequisites") and quest.get("required_realm_tier", 0) <= lowest_tier]
    if not openers:
        fail("no quest is available at the starting realm with no prerequisites; a new "
             "cultivator would open the System window to an empty list")


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
