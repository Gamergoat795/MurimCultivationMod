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

## Extending it with a datapack

Realms, techniques, quests and sects are all datapack-driven. You don't need to compile
anything to rebalance the mod or add content — drop JSON into a datapack:

```
data/<your_pack>/murimcultivation/realm/my_realm.json
data/<your_pack>/murimcultivation/technique/my_technique.json
data/<your_pack>/murimcultivation/quest/my_quest.json
```

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
