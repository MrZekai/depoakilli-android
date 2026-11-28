#!/usr/bin/env python3
"""Faz 3B-2/3C/Sales shot set: the 3B-1 set (bursts, blurry, unrelated, 40+
days old) plus 10 dark photos and 12 document-like pictures (paper pages,
receipts). Built at run time; never stored in the repo."""
import os, random, subprocess, sys
from PIL import Image, ImageDraw, ImageEnhance, ImageFont

out = sys.argv[1]
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "gen_similar_set.py"), out, "0"], check=True)
random.seed(5)
lines = [l.split() for l in open(os.path.join(out, "manifest.txt")) if l.strip()]
t = min(int(e) for _, e in lines) - 30 * 3600  # older than the set: past the 30-day type rule
src = [n for n, _ in lines if n.startswith("IMG_O")]
extra = []
for i in range(10):
    t += 3600
    name = f"IMG_K{i:02d}.jpg"
    ImageEnhance.Brightness(Image.open(os.path.join(out, src[i])).convert("RGB")).enhance(0.07).save(os.path.join(out, name), quality=90)
    extra.append((name, t))
try:
    font = ImageFont.load_default(size=44)
except TypeError:
    font = ImageFont.load_default()
words = "invoice total amount date payment tax receipt order item price qty subtotal cash card thank you store".split()
for i in range(12):
    t += 3600
    receipt = i % 2 == 0
    w, h = (900, 2000) if receipt else (1500, 2000)
    im = Image.new("RGB", (w, h), (248, 247, 242))
    d = ImageDraw.Draw(im)
    y = 80
    while y < h - 100:
        txt = " ".join(random.choice(words) for _ in range(random.randint(2, 4 if receipt else 7)))
        if receipt:
            txt += f"   {random.randint(1, 99)}.{random.randint(10, 99)}"
        d.text((60, y), txt.upper() if receipt else txt, fill=(30, 30, 30), font=font)
        y += 70
    name = f"IMG_D{i:02d}.jpg"
    im.rotate(random.uniform(-3, 3), expand=False, fillcolor=(120, 110, 100)).save(os.path.join(out, name), quality=90)
    extra.append((name, t))
with open(os.path.join(out, "manifest.txt"), "a") as f:
    for n, e in extra:
        f.write(f"{n} {e}\n")
print(len(extra), "extra photos")
