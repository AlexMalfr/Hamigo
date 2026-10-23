#!/usr/bin/env python3
"""Compose the launcher and Play icon from an actual PicoRenderer export.

Input: 560 px PNG, bounds (24, 24, 536, 536), HAPPY / WAVE, phase .25,
drawGroundShadow=false. Colour artwork keeps the native drawing, with cream
radio waves for contrast against the turquoise background.
Requires Pillow. Run from any directory: python tools/render_pico_icons.py.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import math

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
INK = (23, 63, 66)
TEAL = (8, 127, 130)
CREAM = (255, 236, 204)
ART_SIZE = 864  # 8 px for each dp of the 108 dp adaptive-icon layers.


def monochrome_source(source: Image.Image) -> Image.Image:
    """An open face and light enclosure outline keep the themed Pico friendly.

    The eyes, smile, cheeks, grille, antenna and arms come from native pixels.
    Only the outline of the native rounded enclosure and knob are stroked here.
    There is no filled body or facial knockout, unlike a silhouette conversion.
    """
    supersample = 4
    strokes = Image.new("L", (560 * supersample, 560 * supersample))
    draw = ImageDraw.Draw(strokes)
    bob = 1.4  # Native sin(.25 * 2pi) * 1.4; export uses scale 4 and padding 24.

    def xy(x: float, y: float) -> tuple[float, float]:
        return ((24 + x * 4) * supersample, (24 + (y + bob) * 4) * supersample)

    # Native legs start behind the filled enclosure. An open monochrome body
    # needs these same upper segments too, so the exported feet stay attached.
    for x, y, x2, y2 in ((38, 96, 33, 113), (88, 96, 95, 113)):
        draw.line((xy(x, y), xy(x2, y2)), fill=255, width=6 * 4 * supersample)
        for cx, cy in ((x, y), (x2, y2)):
            draw.ellipse((*xy(cx - 3, cy - 3), *xy(cx + 3, cy + 3)), fill=255)
    draw.rounded_rectangle((*xy(23, 38), *xy(105, 97)), radius=17 * 4 * supersample,
                           outline=255, width=round(2.8 * 4 * supersample))
    draw.ellipse((*xy(78, 50), *xy(96, 68)), outline=170, width=round(2 * 4 * supersample))
    alpha = strokes.resize(source.size, Image.Resampling.LANCZOS)

    def extract(channel: str, background: int, solid: int,
                area: tuple[float, float, float, float], opacity: float = 1) -> None:
        # Recover antialias coverage, retaining the actual exported curve rather
        # than drawing an approximation of the eyes, smile or grille.
        coverage = source.getchannel(channel).point(
            lambda value: round(max(0, min(1, (background - value) / (background - solid))) * 255 * opacity))
        left, top, right, bottom = area
        rectangle = (int(24 + left * 4), int(24 + (top + bob) * 4),
                     int(24 + right * 4), int(24 + (bottom + bob) * 4))
        alpha.paste(coverage.crop(rectangle), rectangle[:2])

    extract("R", 255, 23, (39, 56, 47, 64))
    extract("R", 255, 23, (58, 56, 66, 64))
    extract("R", 255, 23, (41, 69, 65, 82))
    extract("G", 255, 120, (31, 68, 39, 76), .45)
    extract("G", 255, 120, (66, 68, 74, 76), .45)
    extract("R", 255, 23, (78, 75, 96, 89))
    # The native antenna and raised hand preserve the exact original pose.
    native_alpha = source.getchannel("A")
    for area in ((140, 0, 420, 181), (444, 130, 559, 330), (0, 275, 116, 370),
                 (0, 439, 560, 560)):
        alpha.paste(native_alpha.crop(area), area[:2])
    result = Image.new("RGBA", source.size, (255, 255, 255, 0))
    result.putalpha(alpha)
    return result


def colour_source(source: Image.Image, for_store: bool = False) -> Image.Image:
    # Solid cream radio waves retain their native alpha/geometry and stay clear
    # on the turquoise icon background, without a second outline around them.
    result = source.copy()
    if not for_store:
        return result
    # The enclosure underside must contrast too: native teal becomes deep ink
    # only on the teal store background. Android colour layers remain native.
    pixels = result.load()
    for y in range(result.height):
        for x in range(result.width):
            r, g, b, a = pixels[x, y]
            if a and g-r > 65 and b-r > 65 and abs(g-b) < 30:
                pixels[x, y] = (*INK, a)
    waves = Image.new("RGBA", (560, 88), (*CREAM, 0))
    waves.putalpha(source.getchannel("A").crop((0, 0, 560, 88)))
    result.paste(waves, (0, 0))
    return result


def compose(source: Image.Image, angle: float = 14, face_x: float = 185,
            face_y: float = 290, character_width: int = 620) -> Image.Image:
    # Keep the selected close-up of the visible 72 dp area, with a slight tilt.
    # Keep the complete native drawing, including both arms and legs.
    # Only the final icon viewport crops it; no limb is erased or moved.
    ratio = 576 / 512
    side = round(character_width * ratio)
    character = source.resize((side, side), Image.Resampling.LANCZOS)
    character = character.rotate(angle, Image.Resampling.BICUBIC, expand=True)
    dx, dy = (232 - 280) * side / 560, (298 - 280) * side / 560
    radians = math.radians(angle)
    anchor_x = dx * math.cos(radians) + dy * math.sin(radians)
    anchor_y = -dx * math.sin(radians) + dy * math.cos(radians)
    center_x = 144 + face_x * ratio - anchor_x
    center_y = 144 + face_y * ratio - anchor_y
    result = Image.new("RGBA", (ART_SIZE, ART_SIZE))
    result.alpha_composite(character, (
        round(center_x - character.width / 2),
        round(center_y - character.height / 2),
    ))
    # A rectangular close-up, independent of the launcher's chosen shape.
    clip = Image.new("L", result.size)
    ImageDraw.Draw(clip).rectangle((144, 144, 719, 719), fill=255)
    result.putalpha(ImageChops.multiply(result.getchannel("A"), clip))
    return result


def preview_mask(size: int, shape: str) -> Image.Image:
    mask = Image.new("L", (size, size))
    draw = ImageDraw.Draw(mask)
    if shape == "circle":
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    elif shape == "squircle":
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=size * .28, fill=255)
    else:
        draw.rectangle((0, 0, size - 1, size - 1), fill=255)
    return mask


def make_previews(colour: Image.Image, mono: Image.Image, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    # Launchers normally expose the central 72 dp of the 108 dp layer.
    crop = (144, 144, 720, 720)
    modes = (
        ("Couleur / fond clair", colour, (250, 248, 242), None),
        ("Couleur / fond sombre", colour, INK, None),
        ("Theme clair", mono, (225, 242, 239), INK),
        ("Theme sombre", mono, (23, 63, 66), (154, 225, 216)),
    )
    sheet = Image.new("RGB", (3 * 272, len(modes) * 304), (250, 248, 242))
    text = ImageDraw.Draw(sheet)
    for row, (label, layer, background, tint) in enumerate(modes):
        for column, shape in enumerate(("circle", "squircle", "square")):
            art = layer.crop(crop).resize((224, 224), Image.Resampling.LANCZOS)
            if tint:
                alpha = art.getchannel("A")
                art = Image.new("RGBA", art.size, (*tint, 255))
                art.putalpha(alpha)
            frame = Image.new("RGBA", art.size, (*background, 255))
            frame.alpha_composite(art)
            frame.putalpha(preview_mask(224, shape))
            sheet.paste(frame, (column * 272 + 24, row * 304 + 40), frame)
            text.text((column * 272 + 24, row * 304 + 16), f"{label} / {shape}", fill=INK)
            frame.save(output / f"{row}-{shape}.png")
    sheet.save(output / "launcher-masks.png")
    # Native-size examples supplement the enlarged review sheet.
    small = Image.new("RGB", (len(modes) * 96, 96), (250, 248, 242))
    for index, (_, layer, background, tint) in enumerate(modes):
        art = layer.crop(crop).resize((48, 48), Image.Resampling.LANCZOS)
        if tint:
            alpha = art.getchannel("A")
            art = Image.new("RGBA", art.size, (*tint, 255))
            art.putalpha(alpha)
        frame = Image.new("RGBA", art.size, (*background, 255))
        frame.alpha_composite(art)
        frame.putalpha(preview_mask(48, "circle"))
        small.paste(frame, (index * 96 + 24, 24), frame)
    small.save(output / "launcher-48px.png")
    # Show the actual unmasked transparent foreground over a checkerboard too.
    # No launcher mask or coloured background is baked into the Android PNG.
    unmasked = Image.new("RGBA", (432, 432), (236, 236, 236, 255))
    checks = ImageDraw.Draw(unmasked)
    for y in range(0, 432, 24):
        for x in range(0, 432, 24):
            if (x // 24 + y // 24) % 2:
                checks.rectangle((x, y, x + 23, y + 23), fill=(255, 255, 255, 255))
    unmasked.alpha_composite(colour.resize((432, 432), Image.Resampling.LANCZOS))
    unmasked.save(output / "foreground-transparent.png")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "web/privacy/pico.png")
    parser.add_argument("--preview-dir", type=Path, default=ROOT / "output/playstore-56/icons")
    args = parser.parse_args()
    source = Image.open(args.source).convert("RGBA")
    if source.size != (560, 560):
        raise ValueError("Use the native 560 px Pico export described in the module docstring")
    foreground = compose(colour_source(source))
    mono_alpha = compose(monochrome_source(source)).getchannel("A")
    monochrome = Image.new("RGBA", (ART_SIZE, ART_SIZE), (255, 255, 255, 0))
    monochrome.putalpha(mono_alpha)
    drawables = ROOT / "app/src/main/res/drawable-nodpi"
    drawables.mkdir(parents=True, exist_ok=True)
    foreground.save(drawables / "pico_launcher_color.png", optimize=True)
    monochrome.save(drawables / "pico_launcher_monochrome.png", optimize=True)

    store = ROOT / "assets/store"
    store.mkdir(parents=True, exist_ok=True)
    # Square, fully opaque, no baked launcher mask or outer drop shadow.
    play = Image.new("RGBA", (576, 576), (*TEAL, 255))
    play.alpha_composite(compose(colour_source(source, for_store=True)).crop((144, 144, 720, 720)))
    play.convert("RGB").resize((512, 512), Image.Resampling.LANCZOS).save(store / "icon-512.png", optimize=True)
    make_previews(foreground, monochrome, args.preview_dir)
    print("Generated native Pico launcher layers and assets/store/icon-512.png")


if __name__ == "__main__":
    main()
