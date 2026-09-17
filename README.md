# Murim Cultivation

A Minecraft mod about being the protagonist of a Murim cultivation manwha. Gather Qi, open
your meridians one by one, survive breakthroughs that can cripple you, learn martial arts
from manuals, and climb the nine realms from a nobody Third-Rate to the Realm of Life and Death.

- **Minecraft** 1.21.1
- **Loader** NeoForge 21.1.x
- **Java** 21

## Building

```bash
./gradlew build          # compile, run unit tests, produce build/libs/murimcultivation-<version>.jar
./gradlew runClient      # launch a dev client
./gradlew runServer      # launch a dev dedicated server
./gradlew runData        # regenerate datagen output into src/generated/resources
```

Everything about the platform — Minecraft version, NeoForge version, mod id, version,
description — lives in `gradle.properties`. `build.gradle` and `neoforge.mods.toml` read from
it, so the two can never disagree. If you want a newer NeoForge, bump `neo_version` there and
nothing else.

> The pinned `neo_version` is a known-good floor, not necessarily the newest. Latest 21.1.x
> builds: <https://projects.neoforged.net/neoforged/neoforge>

## Core systems

### The three numbers

The thing that makes the progression work is that these are *separate*:

| Value | What it is | Spent? |
|---|---|---|
| **Qi** / capacity | The resource your techniques burn. Regenerates passively. | Yes |
| **Progress** | Long-term advancement toward your next realm. | Never |
| **Purity** | Quality of your foundation, 0–100. Gates breakthrough odds. | Slowly lost by rushing |

Cultivating raises Progress and Purity. Fighting spends Qi. Breaking through spends Progress
and *tests* Purity.

### The nine realms

Each realm has four substages — Early, Mid, Late, Peak — so there are 36 steps from nobody to
peak of the Murim.

| # | Realm | Hangul |
|---|---|---|
| 1 | Third-Rate | 삼류 |
| 2 | Second-Rate | 이류 |
| 3 | First-Rate | 일류 |
| 4 | Peak | 절정 |
| 5 | Transcendent | 초절정 |
| 6 | Flower Realm | 화경 |
| 7 | Profound Realm | 현경 |
| 8 | Realm of Life and Death | 생사경 |
| 9 | Beyond Heaven | 천외천 |

### Meridians

Twelve primary meridians and eight extraordinary vessels, twenty nodes total. Opening one costs
Progress and a consumable, and carries its own risk of Qi Deviation. Each grants a passive — Qi
capacity, regeneration, an extra technique slot, reduced technique cost — and higher realms
refuse to admit you until the required ones are open.

### Breakthrough and Qi Deviation

Breaking through is an event, not a button. You need enough Progress, the required meridians
open, Qi above a threshold, and Purity above the realm's floor. Success chance scales with
Purity, technique mastery, pills consumed, and how Qi-dense your location is.

Fail and you suffer **Qi Deviation** in one of three severities — Minor Blockage, Reverse Flow,
or Shattered Meridian — which applies debuffs, can slam a meridian shut, and drains Progress.
It clears with recovery pills, time, or a healer.

## Martial arts

Techniques are learned from manuals and improve with use. Mastery runs 0–100 with diminishing
returns: the first uses of a new art teach a lot, the last few percent are a grind. Mastery
makes an art cheaper in Qi and quicker to come round again, and how hard it hits scales at a
rate each art declares for itself.

Arts need the right thing in your hands. Sword arts require a sword; palm arts require hands
free of a weapon; footwork and internal arts work regardless — so a swordsman and a palm
artist play differently at the same realm.

| Art | Hangul | Tier | Realm | Grip |
|---|---|---|---|---|
| Internal Healing | 내공치료 | 1 | Third-Rate | any |
| Qinggong | 경공 | 1 | Third-Rate | any |
| Divine Palm | 장법 | 2 | Second-Rate | hands free |
| Water Walking | 수상보행 | 2 | Second-Rate | any |
| Shadowless Step | 무영보 | 3 | First-Rate | any |
| Sword Qi | 검기 | 3 | First-Rate | sword |
| Iron Body | 첨신공 | 4 | Peak | any |
| Sword Force | 검강 | 5 | Transcendent | sword |

Sustained arts — Sword Force, Iron Body, Qinggong, Water Walking — run for a duration and are
tracked as transient state, so a crash or a death can never leave you permanently buffed. Iron
Body charges Qi every tick it is held and collapses when you run dry.

Four techniques can be bound at once. Slot 1 defaults to `R`; slots 2–4 ship unbound so you
can assign keys that do not clash with your other mods, and `C` cycles the selected slot if
you would rather use one cast key.

## The System window

Press `K`. A window only you can see, in four tabs:

- **Status** — realm and substage, the three numbers as bars, the four stats with a spend
  button each, and the titles you have earned. Click a title to wear it; click it again to take
  it off.
- **Meridians** — the twenty channels as a grid, twelve primary above and eight extraordinary
  vessels below. Hovering shows the exact progress cost and deviation risk, computed by the same
  functions the server charges with. Click a sealed channel to force it open.
- **Arts** — what you have learned, its mastery, and which four are bound. Click to bind or
  unbind a slot.
- **Quests** — what the System is asking for, with live per-objective progress.

Stat points come from clearing substages and breaking through realms. Body raises health, Force
raises damage, Meridian widens your Qi capacity, and Insight makes you cultivate faster — a bet
that the run is long enough to pay it back.

Quests are datapack content, in two kinds. The story chain runs from awakening to First-Rate.
Dailies reset each in-game day by default; because a Minecraft day is twenty minutes, that is
configurable via `dailyResetIntervalDays`, and daily rewards are deliberately smaller than story
rewards.

## Keybinds

| Key | Action |
|---|---|
| `B` | Meditate |
| `X` | Attempt breakthrough |
| `M` | Open the next sealed meridian |
| `K` | Open the System window |
| `R` | Use technique slot 1 |
| unbound | Technique slots 2–4 — assign them yourself |
| `C` | Cycle the selected technique slot |

## Extending it with a datapack

Realms, techniques, quests and sects are all datapack-driven. You don't need to compile
anything to rebalance the mod or add content — drop JSON into a datapack:

```
data/<your_pack>/murimcultivation/realm/my_realm.json
data/<your_pack>/murimcultivation/technique/my_technique.json
data/<your_pack>/murimcultivation/quest/my_quest.json
data/<your_pack>/murimcultivation/title/my_title.json
```

A technique names a *behaviour* that the mod implements in code, so a pack can ship "Greater
Sword Qi" as the `murimcultivation:sword_qi` behaviour with bigger numbers and a higher realm
gate, but cannot author new behaviour without Java.

## Checks

```bash
python3 tools/verify_sources.py   # structure, JSON, translation keys, datapack consistency
./gradlew build                   # compile and unit tests
```

`verify_sources.py` needs no NeoForge artifact, so it runs anywhere. It catches the failure
modes that compile perfectly and break silently: a misspelled resource directory, a
translation key nothing defines, or two datapack files whose numbers contradict each other —
for instance a technique whose declared realm requirement is lower than the realm that can
actually channel it.

## Debug commands

```
/murim realm get|set <realm> [substage]
/murim qi <amount>
/murim progress <amount>
/murim meridian open|close <meridian>
/murim technique grant <technique>
/murim reset
```

## Project layout

```
cultivation/  Qi, realms, meridians, breakthrough, Qi Deviation
technique/    martial arts: definitions, mastery, loadout, effects
system/       the System window's data: quests, titles, stat points
network/      CustomPacketPayload records, client handling isolated to Dist.CLIENT
client/       HUD layers, the System screen, aura rendering, keybinds
registry/     every DeferredRegister and the datapack registry keys
sect/ npc/ alchemy/ world/   skeletons for a later milestone
```

## License

MIT — see [LICENSE](LICENSE).
