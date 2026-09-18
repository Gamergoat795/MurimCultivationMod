# What's left

Everything that is waiting on **you** — either something to do, or a decision only you can make.

Last updated against commit `9f2cbbd`. This file goes stale; the two things that never lie are
`python3 tools/verify_sources.py` and the GitHub Actions run on your latest push.

## Where it stands

| | |
|---|---|
| Built and CI-green | Core data layer, cultivation loop, 8 martial arts, System window + quests, sects, alchemy, teaching NPC, attention minigames, honour/infamy, wandering warriors, duels |
| Tests | 245 across 24 files |
| Source files | 110 |
| Art | **Complete** for items and blocks. Both new mobs render on vanilla player models |
| Never run anywhere | `./gradlew runServer`, `./gradlew runClient` |
| Never played | All of it |

The gate reports nothing outstanding. That means every texture a model asks for exists and is
correctly shaped, every translation key resolves, and the datapack is internally consistent — it
does **not** mean the mod has been seen working, because nothing here has ever been launched.

## Since you last read this

Your playtest notes are done, plus the first half of the content pass:

- **Both bugs fixed.** The breakthrough minigame had never worked for anyone — the prompt bar was
  being wiped within a second of appearing, on every attempt, by a sync that fires once a second.
  Techniques did nothing because three of the four slot keys shipped with no key bound and the
  fourth held whatever art the registry yielded first. `C` now selects and `G` casts, so two keys
  reach all four arts.
- **Honour and infamy** are real values, 0–100 each, and they gate which sects will take you.
- **Wandering warriors** spawn across the Overworld at a realm rolled from the local Qi-richness
  and the distance from world spawn. They are neutral and never pick a fight.
- **Duels.** Right-click to challenge. They accept, refuse, or attack if your name is bad enough.
  A duel ends on a yield and cannot kill anyone, in either direction.

**One thing to know before you load an existing dev world:** the save format changed. Sect standing
moved inside a new `murim_standing` block, so an existing world loses its sect reputation and keeps
everything else. Nothing fails to load. Same break M4 made, and for the same reason — every field
is optional, and it is cheap now and expensive later.

---

# Do — needs your hands

## 1. Start a server. Fifteen seconds, and it is the one gate CI cannot provide.

```
./gradlew runServer
```

It either reaches `Done (n.nns)! For help, type help` or it throws
`NoClassDefFoundError: net/minecraft/client/...`. That single line is the whole test.

Why it matters more than it looks: a Minecraft mod can compile perfectly and still be unable to
start a dedicated server, because a client-only class reached from common code only fails at
*class-load* time. CI runs `build`, which compiles and tests — it never starts a server. Every
claim that this mod is "dist-clean" is an inference from reading the code. This makes it an
observation.

## 2. Play it. This is now the biggest gap by far.

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
- Then ambush one without challenging, and confirm `/murim standing` shows infamy rising and an
  orthodox sect subsequently refusing you.
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

## 3. Two clients, once.

Two things can only be checked with two players connected at once, and both are the kind of bug
that is invisible in single-player:

- One player's breath-rhythm prompt must never appear on the other's screen.
- Two players meditating side by side must get independently-timed prompts, not the same one.

The code is built so this cannot go wrong — every network payload in the mod is point-to-point,
there is no broadcast anywhere. But that is an argument, not a test.

## 4. Optional: replace the block textures.

The five block textures are **generated, not drawn** — `tools/generate_block_textures.py` makes
them from a flat palette. They are correct and they work; they are not art. Overwrite the PNGs
whenever you feel like it and delete the generator. `docs/ART.md` says which vanilla textures to
trace for the cauldron proportions.

---

# Approve — needs a decision from you

## 1. The four server-readiness fixes

Planned in full, not built. I have just re-verified all four are still outstanding. **One is a
real bug**, not a cleanup:

- **`QiSources` cache key omits the dimension.** Two players at the same coordinates in different
  dimensions share a cached spirit-vein count, so one player's veins inflate the other's Qi
  density — and with it their meditation gain, insight odds and breakthrough chance. There is a
  second, nastier half: the freshness check compares against `level.getGameTime()`, which is
  per-level, so a colliding entry from a level with a higher game time makes the subtraction
  negative, reads as fresh, and serves a stale count indefinitely.
- `invalidate()` has no callers despite a javadoc saying it runs on world unload.
- `verify_sources` has no dist checks — the discipline keeping `runServer` alive is enforced by a
  code comment.
- The whole `/murim` tree sits behind one op-level gate, so read-only queries are admin-only. You
  already chose to split read from write here.

Say the word and I'll do all four.

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
- No NPC entity texture (only needed if M7a happens).
- `isNearVein` in `QiSources` is dead public API with zero callers.

---

# How to check any of this yourself

```
python3 tools/verify_sources.py          # structure, JSON, translation keys, datapack
                                         # consistency, texture dimensions, missing art
./gradlew build                          # compile + 188 tests (this is what CI runs)
./gradlew runServer                      # the gate CI cannot provide
./gradlew runClient                      # the only way to know how any of it feels
```

The plan file carries the full reasoning for every milestone, including the ones not yet built,
and the record of every bug found and how it was fixed.
