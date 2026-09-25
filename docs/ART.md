# Art manifest

Everything here is wired up already. **Drop a PNG at the listed path and it works** — no model
edit, no code change, no restart beyond relaunching the client.

`python3 tools/verify_sources.py` prints whatever is still missing at the end of its run, so the
list below stays honest on its own. When it prints nothing, the art is done.

## Rules that apply to every file

- **Format:** PNG, 32-bit RGBA. Transparency is supported and expected for items.
- **Size:** 16×16 for everything listed here. Larger is allowed — Minecraft scales any
  power-of-two square — but 16×16 is what the vanilla sprites next to yours will be, and mixing
  resolutions looks worse than a consistent low one.
- **Naming:** exact, lowercase, underscores. The filename *is* the reference; a typo shows up as
  a new line in the verify_sources report rather than as a crash.
- **No `.mcmeta` needed** unless you want an animated texture, in which case add
  `<name>.png.mcmeta` beside it and make the PNG a vertical strip of frames.

## Items — `src/main/resources/assets/murimcultivation/textures/item/`

| File | What it is | Notes for drawing it |
|---|---|---|
| `qi_gathering_manual.png` | ✅ **Already exists** | The one piece of real art in the repo |
| `martial_manual.png` | The technique manual you read to learn an art | Was rendering as an identical copy of the Qi gathering manual — these are different items and need to look different. Highest priority on this list |
| `spirit_ginseng.png` | Herb → Qi Recovery Pill | Root vegetable, pale//ginseng-coloured |
| `blood_lotus.png` | Herb → Deviation Remedy Pill | Flower, deep red. Used to cure Qi Deviation, so it should read as medicinal-but-ominous |
| `jade_chrysanthemum.png` | Herb → Foundation Pill | Flower, jade green/white. The rarest of the three |
| `qi_recovery_pill.png` | Restores 40% of Qi capacity | Pills are small — consider a 2- or 3-pill cluster so the sprite is not one lonely dot |
| `deviation_remedy_pill.png` | Clears a Qi Deviation | Should read as related to the blood lotus |
| `foundation_pill.png` | +5 purity | Should read as related to the jade chrysanthemum, and as the most precious of the three |

Each herb sprite does double duty: it is also what the herb looks like planted in the world
(an X-shaped plant for ginseng and chrysanthemum, lying flat on the water for the blood lotus).
Draw it as the whole plant, not a cut stem, and leave the background transparent.

Pill and herb pairs read best when the pill picks up the herb's colour — it is the fastest way for
a player to learn which herb brews which pill without opening a wiki.

## Blocks — `src/main/resources/assets/murimcultivation/textures/block/`

| File | What it is |
|---|---|
| `pill_cauldron_top.png` | Top face — the rim, with the opening in the middle |
| `pill_cauldron_side.png` | Outer wall. Also used as the particle texture (what you see when it breaks) |
| `pill_cauldron_bottom.png` | Underside |
| `pill_cauldron_inside.png` | Inner wall, seen down through the opening |
| `spirit_vein.png` | All six faces of the spirit vein block — a crystal formation that raises ambient Qi |

The cauldron uses vanilla's `minecraft:block/cauldron` geometry, so these four faces map exactly
the way the vanilla cauldron's do. The quickest way to get the proportions right is to open
vanilla's `cauldron_top.png` / `cauldron_side.png` / `cauldron_inner.png` / `cauldron_bottom.png`
as a reference layer and paint over them.

## What is deliberately *not* here

- **Entity models and animations.** The martial artist NPC renders on the vanilla player model
  with the default Steve skin (`client/render/MartialArtistRenderer.java`). There is no custom
  model, no rig and no animation anywhere in the mod.
- **Technique animations.** The eight martial arts are particles plus a sound. Nobody swings,
  steps or strikes a pose — animating the *player* is not possible without a third-party library,
  since vanilla exposes no API for it.
- **The realm aura.** Currently server-side particles rather than a render layer, so the
  `aura_colour` in each realm's JSON is not yet used for a glow around the player.

These are all live options, just not this pass. See the plan file for what each would cost.
