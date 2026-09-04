"""Erzeugt das App-Icon in allen Formaten - aus einer Zeichnung, damit alle
gleich aussehen.

Motiv: Fussball mit gruenem Zusage-Haken auf dunklem Grund - die Farben der
App (Hintergrund #0F1115, Gruen #37E27A). Der Ball sagt Fussball, der Haken
"Wer kommt?".

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


def ball(draw, cx, cy, r, ink, paper, seam):
    """Klassischer Ball: weisse Kugel, dunkles Fuenfeck in der Mitte, fuenf Naehte."""
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=paper)
    pr = r * 0.34
    pent = [(cx + pr * math.sin(2 * math.pi * i / 5), cy - pr * math.cos(2 * math.pi * i / 5)) for i in range(5)]
    draw.polygon(pent, fill=ink)
    for (x, y) in pent:
        # Naht vom Eck des Fuenfecks nach aussen, auf derselben Strahlrichtung
        dx, dy = x - cx, y - cy
        n = math.hypot(dx, dy)
        ex, ey = cx + dx / n * r * 0.97, cy + dy / n * r * 0.97
        draw.line((x, y, ex, ey), fill=ink, width=seam)


def check(draw, cx, cy, r, color, width):
    """Haken in einem Kreis."""
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color)
    p1 = (cx - r * 0.45, cy + r * 0.02)
    p2 = (cx - r * 0.12, cy + r * 0.36)
    p3 = (cx + r * 0.5, cy - r * 0.34)
    draw.line((p1, p2), fill=WHITE, width=width)
    draw.line((p2, p3), fill=WHITE, width=width)
    for p in (p1, p2, p3):
        draw.ellipse((p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2), fill=WHITE)


def render(size, with_background=True):
    s = size
    img = Image.new("RGBA", (s, s), BG if with_background else (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = s * 0.47
    cy = s * 0.50
    r = s * 0.30
    ball(draw, cx, cy, r, DARK, WHITE, max(2, int(s * 0.028)))
    # Der Haken sitzt unten rechts auf dem Ball, mit dunklem Rand als Trennung.
    bx, by, br = s * 0.70, s * 0.70, s * 0.145
    draw.ellipse((bx - br - s * 0.018, by - br - s * 0.018, bx + br + s * 0.018, by + br + s * 0.018), fill=BG)
    check(draw, bx, by, br, GREEN, max(3, int(s * 0.05)))
    return img


def android_vector():
    """Adaptives Icon: 108dp Flaeche, Sicherheitszone 66dp um die Mitte."""
    cx, cy, r = 51.0, 54.0, 20.0
    pr = r * 0.34
    pent = [(cx + pr * math.sin(2 * math.pi * i / 5), cy - pr * math.cos(2 * math.pi * i / 5)) for i in range(5)]
    pent_path = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pent) + " Z"
    seams = []
    for (x, y) in pent:
        dx, dy = x - cx, y - cy
        n = math.hypot(dx, dy)
        ex, ey = cx + dx / n * r * 0.97, cy + dy / n * r * 0.97
        seams.append(f'    <path android:strokeColor="#161A21" android:strokeWidth="1.9" android:strokeLineCap="round" android:pathData="M{x:.2f},{y:.2f} L{ex:.2f},{ey:.2f}" />')
    bx, by, br = 66.5, 66.5, 9.5
    p1 = (bx - br * 0.45, by + br * 0.02)
    p2 = (bx - br * 0.12, by + br * 0.36)
    p3 = (bx + br * 0.5, by - br * 0.34)
    fg = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Fussball mit gruenem Zusage-Haken. Erzeugt von store/icon.py - dort
  aendern, nicht hier. Alles bleibt innerhalb der Sicherheitszone adaptiver
  Symbole (66 von 108 dp), sonst schneiden runde Launcher die Raender ab.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Ball -->
    <path android:fillColor="#FFFFFF" android:pathData="M{cx},{cy - r} a{r},{r} 0 1,0 0,{2 * r} a{r},{r} 0 1,0 0,{-2 * r} Z" />
    <path android:fillColor="#161A21" android:pathData="{pent_path}" />
{chr(10).join(seams)}

    <!-- Haken, mit dunklem Rand als Trennung vom Ball -->
    <path android:fillColor="#0F1115" android:pathData="M{bx},{by - br - 1.2} a{br + 1.2},{br + 1.2} 0 1,0 0,{2 * (br + 1.2)} a{br + 1.2},{br + 1.2} 0 1,0 0,{-2 * (br + 1.2)} Z" />
    <path android:fillColor="#37E27A" android:pathData="M{bx},{by - br} a{br},{br} 0 1,0 0,{2 * br} a{br},{br} 0 1,0 0,{-2 * br} Z" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="2.6" android:strokeLineCap="round" android:strokeLineJoin="round"
        android:pathData="M{p1[0]:.2f},{p1[1]:.2f} L{p2[0]:.2f},{p2[1]:.2f} L{p3[0]:.2f},{p3[1]:.2f}" />
</vector>
'''
    mono = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Einfarbige Fassung fuer thematisierte Symbole (Android 13+). Erzeugt von store/icon.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M{cx},{cy - r} a{r},{r} 0 1,0 0,{2 * r} a{r},{r} 0 1,0 0,{-2 * r} Z" />
    <path android:fillColor="#000000" android:pathData="{pent_path}" />
    <path android:fillColor="#FFFFFF" android:pathData="M{bx},{by - br} a{br},{br} 0 1,0 0,{2 * br} a{br},{br} 0 1,0 0,{-2 * br} Z" />
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
