# What's left

Everything that is waiting on **you** — either something to do, or a decision only you can make.

Last updated against commit `36a0ec8`. This file goes stale; the two things that never lie are
`python3 tools/verify_sources.py` and the GitHub Actions run on your latest push.

## Where it stands

| | |
|---|---|
| Built and CI-green | Core data layer, cultivation loop, 8 martial arts, System window + quests, sects, alchemy, teaching NPC, attention minigames, honour/infamy, wandering warriors, duels |
| Tests | 254 across 25 files |
| Source files | 111 |
| Art | **Complete** for items and blocks. Both new mobs render on vanilla player models |
| Proven on a dedicated server | Yes — CI boots one on every push and waits for `Done (` |
| Never run anywhere | `./gradlew runClient` |
| Never played | All of it |

The gate reports nothing outstanding. That means every texture a model asks for exists and is
correctly shaped, every translation key resolves, and the datapack is internally consistent. Since the server smoke test landed it also means the mod
loads on a dedicated server — but nobody has yet opened a client and played it.

## Since you last read this

The four server-readiness fixes are done, plus a pass over the warrior and duel code that turned up
six real bugs.

- **The server actually starts.** CI now boots a headless dedicated server on every push and
  fails if it does not reach `Done (`. The first run reached it in under seven seconds — the first
  time anyone has seen this mod load on a server rather than inferred it.
- **The spirit-vein cache bug is fixed.** Two players at the same coordinates in different
  dimensions no longer share a vein count, singleplayer's two sides no longer share one, and loading
  an older world no longer serves stale counts forever.
- **`verify_sources` guards the dist boundary.** It fails on a client import in common code, and on
  the one-line "simplification" in `ModPayloads` that would kill every server.
- **Ordinary players can now use** `/murim info`, `realm get`, `chance`, `standing`, `quest list`,
  `technique list`, `sect list`, and **`sect join` / `sect leave`** — joining was operator-only, which
  meant non-op players could not join a sect at all. Join still applies every realm, allegiance and
  honour check.
- **Honour and infamy appear in the System window** (Status tab, under the stats). Before this the
  only way to read them was an operator command.
- **Six warrior/duel fixes:** saved warriors reloaded at 20 health regardless of realm; a duel never
  ended if your opponent left, died to something else or logged out, so the warrior refused everyone
  afterwards; ambushing with a bow or a martial art cost nothing; every hit during a truce counted as
  a separate ambush; warriors could fill the whole monster cap on a sunny day; and beating four
  members of a sect made you a member without ever joining.

New config value: `[warriors] maxNearby` (default 2) — a natural spawn is refused when that many
warriors are already within 48 blocks.

**One thing to know before you load an existing dev world:** the save format changed. Sect standing
moved inside a new `murim_standing` block, so an existing world loses its sect reputation and keeps
everything else. Nothing fails to load. Same break M4 made, and for the same reason — every field
is optional, and it is cheap now and expensive later.

---

# Do — needs your hands

## 1. Play it. This is now the biggest gap by far.

(The server half is covered: CI starts a dedicated server on every push.)

```
./gradlew runClient
```

**Start with the two things that were broken**, since fixing them is inferred from reading the code
and nothing more:

- Press `X` to attempt a breakthrough and confirm the bar survives all three sweeps. It never has.
- Learn an art, then press `C` to select and `G` to cast. The bar on the left prints the key that
  will fire each slot, so if it says nothing is bound, that is the bug and not you.

**Then the new content:**

- Give yourself a Wandering Warrior spawn egg from the creative tab. Its name states its tier and
  realm. Right-click to challenge it; fight it down and watch it yield rather than die.
- Challenge one far above you and confirm you are **spared** rather than killed. This is the single
  most important thing to check, because the whole fixed-difficulty design rests on it.
- Then ambush one without challenging — try it with a bow too, which used to cost nothing — and
  confirm the Status tab shows infamy rising and an orthodox sect subsequently refusing you.
- Save and quit next to a high-realm warrior, reload, and check it still has its full health bar.
- Kill one that has already yielded. It should be the worst outcome available to you.

**Then meditation**, as before. Read a Qi gathering manual to awaken, then press `B` to meditate. A
bar sweeps bottom-centre every 18–45 seconds; press `B` again inside the lit window.

**Nothing about how this feels has been verified.** The timings below are my guesses. A 2.5-second
sweep with a 22% window might be trivially easy or genuinely annoying, and no test can tell me
which.

These live under `[focus]` in the mod's server config. It is registered as
`ModConfig.Type.SERVER`, which means NeoForge stores it **per world**, not in the global `config/`
folder — look in `saves/<world>/serverconfig/murimcultivation-server.toml` in single-player, or
`world/serverconfig/` on a dedicated server. Being server-side is deliberate: it makes the server
authoritative and syncs the values to joining clients, so the HUD can show odds the server will
actually honour.

| Setting | Default | Turn it… |
|---|---|---|
| `sweepSeconds` | 2.5 | down to make it harder, up to make it forgiving |
| `windowWidth` | 0.22 | down to make it harder |
| `promptMinSeconds` / `promptMaxSeconds` | 18 / 45 | up if three prompts a minute feels like a chore |
| `missPenalty` | 0.34 | **0 disables the whole mechanic**, restoring the old behaviour exactly |
| `latencyTolerance` | 0.18 | up if honest play on a bad connection gets rejected |
| `breakthroughSweeps` | 3 | 1 for a single window instead of three tightening ones |
| `breakthroughBonus` | 0.15 | how much a flawless circulation adds to breakthrough odds |

`latencyTolerance` is the one I would expect to be wrong first. It is the line between "laggy but
honest" and "rejected", and I had no real players to calibrate it against.

### The new sections, same place in the file

`[standing]`, `[warriors]` and the duel keys inside `[warriors]` are all in that same per-world
`serverconfig` file. The ones most likely to be wrong:

| Setting | Default | What it decides |
|---|---|---|
| `warriors.naturalSpawns` | `true` | **Set to `false` to keep warriors out of worldgen entirely.** The spawn egg still works |
| `warriors.neutralPressure` | 20 | Danger floor in an ordinary biome. Raise it and the world gets harder everywhere |
| `warriors.maxNaturalRealmTier` | 5 | Highest realm a wild warrior may roll. 5 is Transcendent |
| `warriors.maxRealmGap` | 2 | How far apart two cultivators may be and still duel. Lower it and challenges get refused more |
| `warriors.yieldHealthFraction` | 0.2 | When a duellist yields. This is what makes losing survivable, so do not set it near zero |
| `warriors.sectChance` | 35 | Percentage of warriors carrying a sect allegiance |
| `warriors.maxNearby` | 2 | Natural spawns refused once this many warriors are within 48 blocks |
| `standing.honourPerHonourableWin` | 4 | Five clean wins reaches the orthodox threshold |
| `standing.orthodoxHonourRequired` | 20 | Honour an orthodox sect wants |
| `standing.demonicInfamyRequired` | 20 | Infamy a demonic sect wants. Four ambushes reaches it |

**How often** warriors spawn is a datapack weight rather than a config value, in
`data/murimcultivation/neoforge/biome_modifier/wandering_warriors.json` — a biome modifier cannot
read config. It ships at weight 6; raise it if the roads feel empty, and raise `maxNearby` too or the
local cap will absorb the change.

Every amount in `[standing]` can be set to 0 to disable honour and infamy entirely, which leaves
sect joining behaving exactly as it did before this pass. A test asserts that.

## 2. Two clients, once.

Two things can only be checked with two players connected at once, and both are the kind of bug
that is invisible in single-player:

- One player's breath-rhythm prompt must never appear on the other's screen.
- Two players meditating side by side must get independently-timed prompts, not the same one.

The code is built so this cannot go wrong — every network payload in the mod is point-to-point,
there is no broadcast anywhere. But that is an argument, not a test.

## 3. Optional: replace the block textures.

The five block textures are **generated, not drawn** — `tools/generate_block_textures.py` makes
them from a flat palette. They are correct and they work; they are not art. Overwrite the PNGs
whenever you feel like it and delete the generator. `docs/ART.md` says which vanilla textures to
trace for the cauldron proportions.

---

# Approve — needs a decision from you

## 1. Bounty hunters and sect patrols — the other half of the mob pass

Wandering warriors, duels and honour are built. The two mob types that hang off infamy are not:

- **Bounty hunters** that spawn to come after you once your infamy is high. Right now infamy gates
  which sects will take you and makes wanderers attack rather than parley, which is real but
  passive — nothing yet comes looking.
- **Sect patrols**, groups hostile to the opposing alignment, which is what would make an
  allegiance cost something rather than only unlocking teachers.

Deliberately held back so the duel mechanic gets playtested before two more mob types are built on
top of it. If duels feel wrong, I would rather change them now than in three places.

## 2. M7 — models and animations

Planned in full. Needs your go-ahead, and it is the only thing in this list that **costs your
players something**: two mods they must install (Player Animation Library, and Zigy's Player
Animator API for server-side triggering).

Split so the risk is isolated:

- **M7a** — NPC model and animation. Zero dependencies. Also folds in the realm aura, which has
  been a particle stand-in since M3.
- **M7b** — the dependency and the pipeline, proven end-to-end with one deliberately crude
  animation and two clients.
- **M7c** — the nine real animations, once the pipeline is proven.

**M7a needs one texture from you** (`textures/entity/martial_artist.png`, 64×64) and **M7c needs
nine animations authored in Blockbench**. I can build the loading, triggering and schema; I cannot
author animation, because hand-writing keyframes without a preview produces motion that is
technically valid and looks wrong.

## 3. M6 — polish

Never started, and the only milestone with no plan written yet. Contents as sketched: advancements
mirroring the realm ladder, datagen for models and tags, a balance pass, and documentation for
datapack authors.

## 4. The balance pass, specifically

Worth separating from the rest of M6 because it is the one that decides whether the mod is fun.
The stated target — First-Rate in roughly two to three hours — has **never been measured**. Nobody
has cultivated for two hours to find out. Until someone does, the whole progression curve is a
set of numbers I picked that are internally consistent and externally unvalidated.

This is the item I would put first if you want the mod to be *good* rather than merely correct.

---

# Gaps nobody has closed

Not decisions, just things that do not exist yet:

- No advancements.
- No documentation for datapack authors, despite every realm, technique, quest, title, sect and
  pill formula being datapack-driven and designed for exactly that.
- No NPC entity texture. Both mobs render on vanilla player skins — the teacher as Steve, a
  wandering warrior as Alex — so they are told apart by their names rather than by looking
  different. Only fixed by M7a.
- No "new quest available" toast. The notification and its text exist; nothing sends it.
- Warrior spawn density is a guess. The weight (6) and the local cap (2) need someone to walk
  around for ten minutes and say whether it feels like a road or a crowd.
- Nothing distinguishes a Thug from a Master visually. The name states the tier and realm, which
  works, but you have to look at one to know whether to run.

---

# How to check any of this yourself

```
python3 tools/verify_sources.py          # structure, unresolved type names, JSON, translation
                                         # keys, datapack consistency, texture dimensions, art
./gradlew build                          # compile + 254 tests (this is what CI runs)
./gradlew runServer                      # CI does this too, on every push
./gradlew runClient                      # the only way to know how any of it feels
```

The plan file carries the full reasoning for every milestone, including the ones not yet built,
and the record of every bug found and how it was fixed.
