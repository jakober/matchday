"""Erzeugt das App-Icon in allen Formaten - aus einer Zeichnung, damit alle
gleich aussehen.

Motiv: Der Mittelkreis eines Spielfelds mit der Mittellinie, darin drei
ueberlappende Punkte - die Leute der Gruppe, wie in der Zusagezeile der App.
Farben der App (Hintergrund #0F1115, Gruen #37E27A, dazu die Avatarfarben).

Bewusst KEIN Fussball und KEIN Haken im Kreis: Google Play hat das vorige
Motiv im Oktober 2026 wegen angeblich fremder Inhalte abgelehnt. Ball mit
schwarzem Fuenfeck und gruener Haken-Kreis sind beide tausendfach vergeben;
Mittelkreis mit Gruppenpunkten ist eigen und sagt dasselbe.

Ausgabe:
  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
  composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml
  composeApp/src/androidMain/res/drawable/ic_launcher_monochrome.xml
  composeApp/src/androidMain/res/values/colors.xml (Hintergrundfarbe)
  store/out/play-icon-512.png, store/out/feature-graphic-1024x500.png

Aufruf: python store/icon.py  (aus dem Repo-Wurzelverzeichnis)
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = (15, 17, 21)
GREEN = (55, 226, 122)
WHITE = (255, 255, 255)
DARK = (22, 26, 33)
# Dieselben drei Farben wie die ersten Avatare in der App.
DOTS = ((55, 226, 122), (176, 107, 255), (255, 162, 62))


def pitch(draw, cx, cy, r, line, width):
    """Mittelkreis mit Mittellinie - links und rechts bis zum Rand."""
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=line, width=width)
    gap = r + width
    draw.line((cx - r * 1.62, cy, cx - gap, cy), fill=line, width=width)
    draw.line((cx + gap, cy, cx + r * 1.62, cy), fill=line, width=width)


def people(draw, cx, cy, r, ring):
    """Drei ueberlappende Punkte: die Leute der Gruppe."""
    step = r * 1.34
    # Von rechts nach links zeichnen: der linke Punkt liegt oben, wie in der
    # Avatarreihe der App.
    for i, color in reversed(list(enumerate(DOTS))):
        x = cx + (i - 1) * step
        # Ring in Hintergrundfarbe, damit sich die Punkte abheben - wie die
        # Avatarreihe in der Liste.
        draw.ellipse((x - r - ring, cy - r - ring, x + r + ring, cy + r + ring), fill=BG)
        draw.ellipse((x - r, cy - r, x + r, cy + r), fill=color)


def render(size, with_background=True):
    s = size
    img = Image.new("RGBA", (s, s), BG if with_background else (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = cy = s * 0.5
    pitch(draw, cx, cy, s * 0.30, GREEN, max(3, int(s * 0.042)))
    people(draw, cx, cy, s * 0.082, max(2, int(s * 0.020)))
    return img


def android_vector():
    """Adaptives Icon: 108dp Flaeche, Sicherheitszone 66dp um die Mitte."""
    cx = cy = 54.0
    r = 19.0          # Mittelkreis
    w = 2.6           # Strichstaerke
    dot = 5.2         # Punktradius
    ring = 1.4
    gap = r + w
    end = 32.5        # bleibt in der Sicherheitszone (54 +- 33)

    def circle(x, y, rad):
        return f"M{x:.2f},{y - rad:.2f} a{rad:.2f},{rad:.2f} 0 1,0 0,{2 * rad:.2f} a{rad:.2f},{rad:.2f} 0 1,0 0,{-2 * rad:.2f} Z"

    dots = []
    for i, color in enumerate(("#37E27A", "#B06BFF", "#FFA23E")):
        x = cx + (i - 1) * dot * 2.05
        dots.append(f'    <path android:fillColor="#0F1115" android:pathData="{circle(x, cy, dot + ring)}" />')
        dots.append(f'    <path android:fillColor="{color}" android:pathData="{circle(x, cy, dot)}" />')

    fg = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Mittelkreis eines Spielfelds, darin die Leute der Gruppe. Erzeugt von
  store/icon.py - dort aendern, nicht hier. Alles bleibt innerhalb der
  Sicherheitszone adaptiver Symbole (66 von 108 dp), sonst schneiden runde
  Launcher die Raender ab.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Mittelkreis und Mittellinie -->
    <path android:strokeColor="#37E27A" android:strokeWidth="{w}" android:pathData="{circle(cx, cy, r)}" />
    <path android:strokeColor="#37E27A" android:strokeWidth="{w}" android:strokeLineCap="round" android:pathData="M{cx - end:.2f},{cy} L{cx - gap:.2f},{cy}" />
    <path android:strokeColor="#37E27A" android:strokeWidth="{w}" android:strokeLineCap="round" android:pathData="M{cx + gap:.2f},{cy} L{cx + end:.2f},{cy}" />

    <!-- Die Leute der Gruppe -->
{chr(10).join(dots)}
</vector>
'''
    mono = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Einfarbige Fassung fuer thematisierte Symbole (Android 13+). Erzeugt von store/icon.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:strokeColor="#FFFFFF" android:strokeWidth="{w}" android:pathData="{circle(cx, cy, r)}" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="{w}" android:strokeLineCap="round" android:pathData="M{cx - end:.2f},{cy} L{cx - gap:.2f},{cy}" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="{w}" android:strokeLineCap="round" android:pathData="M{cx + gap:.2f},{cy} L{cx + end:.2f},{cy}" />
{chr(10).join(f'    <path android:fillColor="#000000" android:pathData="{circle(cx + (i - 1) * dot * 2.05, cy, dot + ring)}" />' + chr(10) + f'    <path android:fillColor="#FFFFFF" android:pathData="{circle(cx + (i - 1) * dot * 2.05, cy, dot)}" />' for i in range(3))}
</vector>
'''
    return fg, mono


def feature_graphic():
    """Play Store: 1024x500, Icon links, Name und Untertitel rechts."""
    from PIL import ImageFont
    img = Image.new("RGB", (1024, 500), BG)
    icon = render(360, with_background=False)
    img.paste(icon, (80, 70), icon)
    draw = ImageDraw.Draw(img)
    try:
        big = ImageFont.truetype("segoeuib.ttf", 92)
        small = ImageFont.truetype("segoeui.ttf", 44)
    except OSError:
        big = ImageFont.load_default()
        small = ImageFont.load_default()
    draw.text((470, 150), "Matchday", font=big, fill=WHITE)
    draw.text((472, 262), "Wer kommt?", font=small, fill=GREEN)
    draw.text((472, 322), "Spielplan teilen. Zusagen.", font=small, fill=(167, 173, 186))
    draw.text((472, 378), "Zusammen schauen.", font=small, fill=(167, 173, 186))
    return img


def main():
    out = os.path.join(ROOT, "store", "out")
    os.makedirs(out, exist_ok=True)

    ios = render(1024).convert("RGB")
    ios.save(os.path.join(ROOT, "iosApp", "iosApp", "Assets.xcassets", "AppIcon.appiconset", "AppIcon-1024.png"))
    render(512).convert("RGB").save(os.path.join(out, "play-icon-512.png"))
    render(1024).convert("RGB").save(os.path.join(out, "icon-1024.png"))
    feature_graphic().save(os.path.join(out, "feature-graphic-1024x500.png"))

    fg, mono = android_vector()
    res = os.path.join(ROOT, "composeApp", "src", "androidMain", "res")
    with open(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(fg)
    with open(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(mono)
    colors = os.path.join(res, "values", "colors.xml")
    with open(colors, encoding="utf-8") as f:
        text = f.read()
    text = text.replace('<color name="ic_launcher_background">#DC052D</color>', '<color name="ic_launcher_background">#0F1115</color>')
    with open(colors, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("Icons erzeugt:", out)


if __name__ == "__main__":
    main()
