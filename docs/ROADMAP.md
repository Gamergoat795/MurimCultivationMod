# Roadmap — what this mod should become

A phase-by-phase picture of the finished mod: what a player experiences at each stage, what gets
built to make that true, and how to know a phase is done. `docs/STATUS.md` is the short list of what
is waiting on you right now. This is the long view.

**The fantasy, in one line:** you are a *Murim Login* / *Nano Machine* protagonist. You climb a long
realm ladder, open meridians one at a time, risk crippling breakthroughs, master martial arts that
cost Qi, and live in a world that remembers how you fight. A System window only you can see tells
you what to do next.

**Three rules every phase keeps:**

1. **The server decides; the client asks.** Every number that matters is computed server-side, and
   the client only ever sends intent. This is why the anti-AFK minigames cannot be trivially cheated
   and why the mod runs on a dedicated server.
2. **Balance lives in data and `Tuning` records.** Realms, arts, quests, sects, pills and loot are
   JSON a datapack can change. Formulas take their numbers as parameters so they can be unit-tested
   without a world.
3. **Conduct, not outcome.** Honour, infamy and sect standing measure *how* you fight. Losing
   honestly costs nothing; winning dishonourably costs a great deal.

---

## Phase 0 — The foundation (built)

What exists today and runs on a dedicated server (CI starts one on every push):

| System | What the player does |
|---|---|
| Cultivation | Read a manual to awaken, meditate to gain progress and purity, climb nine realms × four substages |
| Meridians | Open 12 primary and 8 extraordinary vessels; each costs progress and can backfire |
| Breakthroughs | A three-sweep circulation minigame, then a roll that can end in Qi Deviation |
| Attention | A breath-rhythm prompt during meditation, so leaving the game running earns nothing |
| Martial arts | Eight arts with Qi cost, cooldowns, weapon rules and mastery; `C` selects, `G` casts |
| System window | `K`: status, stats, titles, meridian grid, techniques, quests; toasts for events |
| Quests | A story chain from awakening to First-Rate, plus dailies |
| Alchemy | A cauldron, three herbs, three pills |
| Standing | Honour and infamy (0–100 each), shown in the Status tab |
| Sects | Three sects, five ranks, alignment gates; envoys who recruit and teach |
| Wanderers | Neutral warriors at a realm rolled from biome and distance, names coloured by tier |
| Duels | Challenge, fight to a yield, spare or kill; sects react to what you did; spoils for mercy |
| Bounty hunters | High infamy sends a hunter matched to your realm |
| Rankings | `/murim rankings` — the ten highest cultivators online |

**How sects react to a fight with one of their members:**

| | Their sect | Other sects | Other demonic sects |
|---|---|---|---|
| Duel, spared | +small | — | — |
| Duel, killed after yielding | −small | +small | +small-medium |
| Ambush, killed | −large | −minimal | +medium |
| Ambush, spared | — | — | — |

---

## Phase 1 — Prove it in play

**The biggest gap is that nobody has played it.** Everything above is verified by 270+ unit tests and
a server that boots, but nobody has yet felt whether any of it is fun. This phase comes first because
every later phase builds on numbers that are currently guesses.

**What the player experiences:** the same features, but tuned. First-Rate takes an evening, not a
minute or a week. Duels feel winnable at your realm and terrifying two above it. The roads have a
stranger on them, not a crowd.

**Work:**
- A full `runClient` pass using the checklist in `STATUS.md`, plus a two-client LAN pass.
- **Balance targets**, measured rather than assumed:
  - Third-Rate → First-Rate in 2–3 hours of active play.
  - A same-realm duel won roughly 60% of the time by a player using their arts.
  - A wanderer every few minutes of walking; an envoy every hour or so of exploring.
  - A hunter roughly every 15 minutes once you are wanted.
- Tune the config (`[focus]`, `[warriors]`, `[standing]`, `[sectConduct]`, `[bounty]`) and the
  biome-modifier weights from what is observed.
- Fix whatever the playtest finds, before anything new is added.

**Done when:** someone has played from awakening to First-Rate, and the numbers in the configs were
chosen by watching rather than guessing.

---

## Phase 2 — A murim full of people

**What the player experiences:** the world reacts to you. Rivals recur. Sects patrol and fight each
other. Warriors use real martial arts. Your sect gives you things to do.

**Work, in order of how much each reuses:**
1. **Warriors that use martial arts.** Experts and Masters cast Sword Qi, Divine Palm or Shadowless
   Step at you, using the existing behaviours with an entity as caster. Duels go from trading blows
   to reading and dodging.
2. **Sect missions.** Repeatable quests from an envoy: gather herbs, defeat a rival sect's member
   honourably, deliver a message. Uses the quest engine as it is; a mission is a quest with a sect
   id. Gives sect rank a steady source besides fighting.
3. **Rivals.** A named warrior generated near your realm who reappears as you climb, remembers
   whether you fought fair, and in the end becomes an ally or a nemesis. The strongest "protagonist"
   moment for the least new code.
4. **Sect patrols.** Groups of two or three members who are hostile to the opposing alignment, so
   swearing to a sect has a cost as well as a benefit.
5. **Player-vs-player duels.** The same challenge-and-yield rules between players, with honour on
   the line and no deaths.

**Decision needed:** whether a bounty hunter that beats you kills you (as now) or captures you —
for example taking a fine in items and teleporting you to spawn.

**Done when:** a player can spend a session only on the people of the world — duelling, running
missions, dodging hunters — and keep progressing.

---

## Phase 3 — Your foundation

**What the player experiences:** two players at the same realm cultivate differently. Choices made
early shape the whole climb.

**Work:**
1. **Cultivation manuals** (a new `qi_technique` datapack registry). The manual you cultivate sets
   your gain rate, purity ceiling and a personality: demonic arts gain fast but cap purity; orthodox
   ones are slow with a high ceiling; hidden ones do something stranger. Switching costs purity, so
   finding a better manual is a decision.
2. **Talent at awakening.** A rolled constitution or bloodline — faster gain with a lower ceiling,
   or slow with an extra meridian. Makes a second playthrough different. This is the genre's own
   premise.
3. **Lingering injuries.** A fourth deviation outcome: a crippled meridian that stays shut until a
   rare pill or a healer fixes it. Gives failure a memory and alchemy a reason to exist.
4. **Alchemy depth.** More herbs, pill grades (impure → perfect), and failure that wastes
   ingredients.

**Needs:** one new field in the save data. The codec has two spare slots, so this fits, but it is
the moment to plan the next nested group.

**Done when:** a player can explain *why* their character cultivates the way it does.

---

## Phase 4 — The world itself

**What the player experiences:** places worth finding. A sect has a gate you walk up to. Manuals sit
in hidden caves. A good cultivation spot is discovered, not built.

**Work:**
1. **Sect compounds** — one structure per sect, with envoys, an elder and a training yard. Envoys
   move in, and their wild spawning can be turned down.
2. **Hidden caves** holding cultivation manuals and martial manuals — the found-manual fantasy that
   Phase 3 sets up.
3. **Natural spirit-vein formations** generated in Qi-rich biomes.
4. **Murim towns** — a market for herbs and pills, a notice board for bounties *you* can take.

**Needs:** structure templates (NBT, built in-game) and jigsaw pools. Some of this is building, not
code.

**Done when:** exploring finds something new for the first several hours.

---

## Phase 5 — Minigames with depth

**What the player experiences:** each kind of attention tests something different, at a pace that
matches how often you do it.

**Work:**
- **Meditation:** alternate a *breath cycle* (hold to inhale, release on the beat) with *stillness*
  (distractions appear; do not press). Low friction, since it happens constantly.
- **Meridian opening:** *force vs. finesse* — hold to build pressure, release to push through;
  overshoot causes the deviation opening already risks.
- **Breakthrough:** *Qi pressure* — a rising bar you vent by tapping; too much is deviation, too
  little is failure.
- **Multi-beat rhythm everywhere:** a pulse travels your open meridians and you tap as it passes each
  node, 3–8 beats by realm. Makes the meridian board matter beyond its stats.

Same architecture as today: the server generates and judges, the client only draws and reports a
position. The judge stays pure and tested.

**Done when:** a long session never feels like the same prompt twice in a row.

---

## Phase 6 — The high realms

**What the player experiences:** the top of the ladder is a series of events, not bigger numbers.

**Work:**
1. **Tribulations.** From Transcendent up, a successful breakthrough summons something to survive —
   lightning, a heavenly beast, your own shadow.
2. **Inner demons.** A gate where the obstacle is your own mind, played as the stillness minigame
   under pressure. Failing is a deviation.
3. **Technique creation.** Two arts at full mastery fuse into a personal art you name. An endgame
   reward no one else has.
4. **The System levels with you.** The window gains tabs as you advance — a map, a bestiary, an
   appraisal tool. In *Murim Login* the interface is the gift; today it arrives complete.

**Done when:** reaching each of the last four realms is a story a player would tell.

---

## Phase 7 — Look and feel

**What the player experiences:** martial arts look like martial arts.

**Work:**
- **NPC model and animation** (no new dependency): robes, a sash, a topknot; idle breathing; a bow.
  Plus the realm-coloured aura, which every realm's JSON already defines.
- **Player animation** via Player Animation Library: one animation per art, plus a meditation pose.
  This adds dependencies your players must install, so it waits for your approval.
- **Qi-sense:** a toggle that tints nearby spirit veins and shows other cultivators' realms as auras.
- Sounds for each art, and a distinct sound for each deviation tier.

**Needs:** a 64×64 NPC texture and nine animations made in Blockbench. I can build the loading and
triggering; the art has to be made by eye.

**Done when:** a screenshot alone tells you this is a cultivation mod.

---

## Phase 8 — Ready to publish

**Work:**
- Advancements mirroring the realm ladder and the honour path.
- Data generation for models, tags and recipes.
- A guide for datapack authors — every realm, art, quest, title, sect, pill, loot table and spawn
  weight is data and was designed for it.
- A config reference, a changelog, and a CurseForge/Modrinth page.
- A save-format version number, so future changes can migrate old worlds rather than resetting them.

**Done when:** a stranger can install it, play it, and write a datapack for it without asking you
anything.

---

## Suggested order, and why

1. **Phase 1** first, always: it makes every later number real.
2. **Phase 2** next: the people of the world are what turn honour and sects from numbers into play,
   and nearly all of it reuses what exists.
3. **Phase 3** then **Phase 4**: manuals need somewhere to be found, but the manual system should
   exist before the places that hold them.
4. **Phase 5** whenever the attention prompts start to feel repetitive in playtests.
5. **Phase 6** once players are actually reaching the high realms.
6. **Phase 7** can run alongside any of the above once art is available.
7. **Phase 8** last.
