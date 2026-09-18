#!/usr/bin/env python3
"""
Generates the mod's block textures.

These are **placeholders, generated rather than drawn** — flat colours and simple shapes, so the
cauldron and the spirit vein read correctly in-game without waiting on hand-painted art. Keeping
the generator in the repo rather than only its output means the palette is one edit away from
being retuned, and it is obvious to the next reader that nobody painted these.

Written with no image library on purpose: this environment has neither PIL nor ImageMagick, and a
16x16 RGBA PNG needs only zlib, which is in the standard library.

Run from the repository root:

    python3 tools/generate_block_textures.py

Everything here is deterministic. The same run produces byte-identical files, so regenerating
never shows up as a spurious diff.
"""

import os
import struct
import zlib

SIZE = 16
OUT = "src/main/resources/assets/murimcultivation/textures/block"

# --- palette ---------------------------------------------------------------------------
# Bronze for the cauldron: alchemy reads as worked metal rather than iron, and it stays
# distinguishable from vanilla's own grey cauldron sitting next to it.
BRONZE_DARK = (0x3E, 0x2B, 0x19, 255)
BRONZE_MID = (0x6E, 0x50, 0x2D, 255)
BRONZE_LIGHT = (0x9C, 0x72, 0x40, 255)
BRONZE_HIGH = (0xC2, 0x95, 0x57, 255)
VOID = (0x14, 0x0F, 0x0C, 255)

# Jade for the spirit vein, matching SystemTheme.TEXT_GOOD (0xA5D6A7) so the block and the HUD
# that reports its effect share a colour.
JADE_DARK = (0x27, 0x4E, 0x38, 255)
JADE_MID = (0x4A, 0x91, 0x67, 255)
JADE_LIGHT = (0x7F, 0xCF, 0x9C, 255)
JADE_HIGH = (0xC4, 0xF2, 0xD2, 255)


def grain(x: int, y: int, spread: int = 3) -> int:
    """
    A deterministic per-pixel wobble, so a flat fill still reads as a surface.

    Not random: a hash of the coordinates. Two runs produce identical files, and the pattern has
    no visible tiling seam at 16x16 because the multipliers are coprime with the size.
    """
    h = (x * 73_856_093) ^ (y * 19_349_663)
    h = (h >> 5) & 0xFFFF
    return (h % (spread * 2 + 1)) - spread


def shade(colour, amount: int):
    """Lightens or darkens a colour, clamped, leaving alpha alone."""
    r, g, b, a = colour
    return (
        max(0, min(255, r + amount)),
        max(0, min(255, g + amount)),
        max(0, min(255, b + amount)),
        a,
    )


def blank(colour=(0, 0, 0, 0)):
    return [[colour for _ in range(SIZE)] for _ in range(SIZE)]


def write_png(path: str, pixels) -> None:
    """Writes 8-bit RGBA. Filter type 0 on every scanline — no prediction, simplest correct form."""
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)
    print(f"  {SIZE}x{SIZE}  {len(png):>5} B  {path}")


# --- the textures ----------------------------------------------------------------------

def cauldron_side():
    """Outer wall: banded metal, darker at the base, with a lip along the top."""
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            if y <= 1:
                base = BRONZE_HIGH            # the rim, catching light
            elif y >= 14:
                base = BRONZE_DARK            # in shadow where it meets the ground
            elif y % 5 == 2:
                base = BRONZE_LIGHT           # raised bands, so the wall is not a flat slab
            else:
                base = BRONZE_MID
            px[y][x] = shade(base, grain(x, y))
    # Vertical seam, so the four faces read as a wrapped vessel rather than a printed cube.
    for y in range(2, 14):
        px[y][0] = shade(BRONZE_DARK, grain(0, y, 2))
    return px


def cauldron_top():
    """The rim seen from above, with the opening in the middle."""
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            if edge <= 1:
                base = BRONZE_HIGH            # outer lip
            elif edge == 2:
                base = BRONZE_LIGHT
            else:
                base = VOID                   # the mouth
            px[y][x] = shade(base, grain(x, y, 2 if edge > 2 else 4))
    return px


def cauldron_inside():
    """Inner wall, seen down through the mouth: the same metal, lit far less."""
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            # Darkens toward the bottom, which is what makes the vessel read as having depth.
            base = shade(BRONZE_MID, -18 - y * 2)
            px[y][x] = shade(base, grain(x, y, 2))
    return px


def cauldron_bottom():
    """Underside: plain, unlit, with a foot ring."""
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            base = BRONZE_DARK if edge <= 1 else shade(BRONZE_MID, -24)
            px[y][x] = shade(base, grain(x, y, 2))
    return px


def spirit_vein():
    """
    A jade crystal formation, one texture for all six faces.

    Facets are drawn as diagonal bands rather than a scatter of lighter pixels, because a
    diagonal reads as a cut crystal face at 16x16 while noise just reads as static.
    """
    px = blank()
    for y in range(SIZE):
        for x in range(SIZE):
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            diagonal = (x + y) % 8
            reverse = (x - y) % 8

            if edge == 0:
                base = JADE_DARK              # a border, so the blocks tile with visible seams
            elif diagonal < 2 or reverse < 2:
                base = JADE_LIGHT             # facet edges catching light
            elif diagonal == 4:
                base = JADE_HIGH              # the brightest vein through the middle of a facet
            else:
                base = JADE_MID
            px[y][x] = shade(base, grain(x, y))
    return px


TEXTURES = {
    "pill_cauldron_side.png": cauldron_side,
    "pill_cauldron_top.png": cauldron_top,
    "pill_cauldron_inside.png": cauldron_inside,
    "pill_cauldron_bottom.png": cauldron_bottom,
    "spirit_vein.png": spirit_vein,
}


def main() -> int:
    print(f"generating {len(TEXTURES)} block textures into {OUT}/")
    for name, build in sorted(TEXTURES.items()):
        write_png(os.path.join(OUT, name), build())
    print("done — these are generated placeholders; replace them with drawn art when you have it")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
