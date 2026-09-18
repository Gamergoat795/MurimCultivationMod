# Art manifest

Everything here is wired up already. **Drop a PNG at the listed path and it works** — no model
edit, no code change, no restart beyond relaunching the client.

`python3 tools/verify_sources.py` prints whatever is still missing at the end of its run, so the
list below stays honest on its own. When it prints nothing, the art is done.

## Rules that apply to every file

**Square, and a power of two.** This is the one that matters and the one that is easy to miss,
because a texture that breaks it still *loads* — it just renders wrong, so nothing errors and
nothing tells you.

- A sprite is mapped onto a square UV. A 1024×1536 image is drawn squashed into a square, not
  letterboxed.
- A size that is not a power of two costs mipmap levels, so the texture gets noisy at distance.
- Valid sizes are 16, 32, 64, 128. Not 548, not 577, not 1024×1028.

`python3 tools/verify_sources.py` now checks both and prints anything wrong at the end of its
run, so you do not have to remember this — but it is worth knowing *why*, because the fix is
re-exporting rather than resizing after the fact.

- **Format:** PNG, 32-bit RGBA. Transparency is supported and expected for items.
- **Size:** **32×32 for items**, 16×16 for blocks. 32 gives you double vanilla's detail while
  still stitching into a sane atlas; blocks stay at 16 because they sit next to vanilla blocks
  far more often than items do, and because the cauldron traces vanilla's own geometry.
- **Not 1024.** A 1024px sprite is 64× vanilla's resolution for something drawn at roughly 16
  pixels on screen. It bloats the item atlas and buys nothing visible.
- **Naming:** exact, lowercase, underscores. The filename *is* the reference; a typo shows up as
  a new line in the verify_sources report rather than as a crash.
- **No `.mcmeta` needed** unless you want an animated texture, in which case add
  `<name>.png.mcmeta` beside it and make the PNG a vertical strip of square frames.

## Items — `src/main/resources/assets/murimcultivation/textures/item/`

All eight exist. Seven need re-exporting at **32×32 square** — they were drawn at sizes between
548px and 1024×1536, so they currently render squashed and oversized. The art itself is fine; only
the export dimensions need to change.

| File | What it is | Status |
|---|---|---|
| `qi_gathering_manual.png` | Read this to awaken and sense Qi | ✅ 16×16, valid |
| `martial_manual.png` | The technique manual you read to learn an art | ⚠️ 1024×1536 — re-export 32×32 |
| `spirit_ginseng.png` | Herb → Qi Recovery Pill | ⚠️ 1024×1109 — re-export 32×32 |
| `blood_lotus.png` | Herb → Deviation Remedy Pill | ⚠️ 1024×1028 — re-export 32×32 |
| `jade_chrysanthemum.png` | Herb → Foundation Pill | ⚠️ 1024×1024 — square, but re-export at 32×32 |
| `qi_recovery_pill.png` | Restores 40% of Qi capacity | ⚠️ 548×545 — re-export 32×32 |
| `deviation_remedy_pill.png` | Clears a Qi Deviation | ⚠️ 577×577 — re-export 32×32 |
| `foundation_pill.png` | +5 purity | ⚠️ 578×578 — re-export 32×32 |

Pill and herb pairs read best when the pill picks up the herb's colour — it is the fastest way for
a player to learn which herb brews which pill without opening a wiki.

## Blocks — `src/main/resources/assets/murimcultivation/textures/block/`

**All five exist and work.** They are *generated placeholders*, not drawn art — produced by
`tools/generate_block_textures.py`, which is committed so the palette is one edit away from being
retuned and so it stays obvious that nobody painted these.

| File | What it is | Status |
|---|---|---|
| `pill_cauldron_top.png` | Top face — bronze rim with the mouth in the middle | 🔶 generated |
| `pill_cauldron_side.png` | Outer wall, banded. Also the particle texture when it breaks | 🔶 generated |
| `pill_cauldron_bottom.png` | Underside, with a foot ring | 🔶 generated |
| `pill_cauldron_inside.png` | Inner wall, darkening downward for depth | 🔶 generated |
| `spirit_vein.png` | All six faces — a jade crystal formation | 🔶 generated |

The cauldron is bronze rather than iron, so alchemy reads as worked metal and stays
distinguishable from vanilla's own grey cauldron beside it. The vein is jade, matching
`SystemTheme.TEXT_GOOD` so the block and the HUD reporting its effect share a colour.

To replace them with drawn art, just overwrite the PNGs — the models already point here and the
generator is not wired into the build. The cauldron uses vanilla's `minecraft:block/cauldron`
geometry, so the four faces map exactly the way vanilla's do; the quickest way to get proportions
right is to open vanilla's `cauldron_top` / `cauldron_side` / `cauldron_inner` / `cauldron_bottom`
as a reference layer and paint over them. Re-running the generator would overwrite your work, so
delete it once the real art lands.

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
