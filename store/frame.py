"""Rahmt rohe Screenshots fuer die Stores.

Legt jedes Bild auf dunklen Grund, skaliert es auf die Zielgroesse und setzt
oben eine Zeile Text. Rohe Screenshots nach store/screenshots/<plattform>/
legen, benannt 1.png ... 5.png; die Zeilen stehen unten in CAPTIONS.

Aufruf: python store/frame.py
Ausgabe: store/out/<plattform>/<n>-<sprache>.png
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = (15, 17, 21)
GREEN = (55, 226, 122)

# Zielgroessen: Apple 6,9" (Pflichtformat), Google beliebig im 9:16-Bereich.
TARGETS = {"ios": (1320, 2868), "android": (1080, 2340)}

CAPTIONS = {
    "de": [
        "Alle Spiele, alle Zusagen - auf einen Blick",
        "Der Monat im Überblick",
        "Zusagen oder absagen, mit einem Tipp",
        "Bundesliga, Premier League, NFL - suchen und hinzufügen",
        "Deine Gruppe, deine Leute",
    ],
    "en": [
        "Every match, every answer - at a glance",
        "The month at a glance",
        "In or out, with one tap",
        "Bundesliga, Premier League, NFL - search and add",
        "Your group, your people",
    ],
}


def font(size):
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def frame(src, target, caption):
    w, h = target
    canvas = Image.new("RGB", target, BG)
    shot = Image.open(src).convert("RGB")
    # Platz oben fuer die Zeile, unten etwas Luft; der Screenshot behaelt sein
    # Seitenverhaeltnis und bekommt runde Ecken.
    top = int(h * 0.12)
    avail_h = h - top - int(h * 0.04)
    scale = min((w * 0.86) / shot.width, avail_h / shot.height)
    shot = shot.resize((int(shot.width * scale), int(shot.height * scale)), Image.LANCZOS)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), radius=int(w * 0.05), fill=255)
    canvas.paste(shot, ((w - shot.width) // 2, top), mask)
    draw = ImageDraw.Draw(canvas)
    f = font(int(w * 0.045))
    tw = draw.textlength(caption, font=f)
    draw.text(((w - tw) / 2, top * 0.38), caption, font=f, fill=(255, 255, 255))
    return canvas


def main():
    for platform, target in TARGETS.items():
        src_dir = os.path.join(ROOT, "store", "screenshots", platform)
        if not os.path.isdir(src_dir):
            print(f"kein Ordner {src_dir} - uebersprungen")
            continue
        out_dir = os.path.join(ROOT, "store", "out", platform)
        os.makedirs(out_dir, exist_ok=True)
        for i in range(1, 6):
            src = os.path.join(src_dir, f"{i}.png")
            if not os.path.exists(src):
                continue
            for lang, captions in CAPTIONS.items():
                frame(src, target, captions[i - 1]).save(os.path.join(out_dir, f"{i}-{lang}.png"))
            print("gerahmt:", platform, i)


if __name__ == "__main__":
    main()
