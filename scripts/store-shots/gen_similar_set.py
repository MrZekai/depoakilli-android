#!/usr/bin/env python3
"""Faz 3B-1 device set: 20 bursts (3-6 frames, seconds apart), 20 blurry, 40
unrelated photos (hours apart), from picsum.photos at run time; plus N filler
photos for the speed run. Writes JPEGs and a manifest 'name epoch_seconds'."""
import io, os, random, sys, time, urllib.request
from PIL import Image, ImageEnhance, ImageFilter
random.seed(11)
out, fillers = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 0
os.makedirs(out, exist_ok=True)
fill_dir = out + "-fill"
os.makedirs(fill_dir, exist_ok=True)
ids = list(range(0, 1085)); random.shuffle(ids); it = iter(ids)
def photo():
    while True:
        try:
            return Image.open(io.BytesIO(urllib.request.urlopen(f"https://picsum.photos/id/{next(it)}/2000/1500", timeout=30).read())).convert("RGB")
        except Exception:
            pass
def jitter(im):
    w, h = im.size
    c = random.uniform(0, 0.05); dx, dy = random.randint(-40, 40), random.randint(-30, 30)
    o = im.crop((int(w*c)+max(dx,0), int(h*c)+max(dy,0), int(w*(1-c))+min(dx,0), int(h*(1-c))+min(dy,0))).resize((w, h)).rotate(random.uniform(-2, 2))
    return ImageEnhance.Brightness(o).enhance(random.uniform(0.85, 1.15))
now = int(time.time()) - 40 * 86400
manifest = []
t = now
for g in range(20):
    base = photo(); t += 3 * 3600
    for k in range(random.randint(3, 6)):
        name = f"IMG_B{g:02d}_{k}.jpg"
        (base if k == 0 else jitter(base)).save(os.path.join(out, name), quality=90)
        manifest.append((name, t + k * random.randint(1, 4)))
for b in range(20):
    t += 3 * 3600
    name = f"IMG_X{b:02d}.jpg"
    photo().filter(ImageFilter.GaussianBlur(random.uniform(4, 10))).save(os.path.join(out, name), quality=90)
    manifest.append((name, t))
for u in range(40):
    t += 3 * 3600
    name = f"IMG_O{u:02d}.jpg"
    photo().save(os.path.join(out, name), quality=90)
    manifest.append((name, t))
with open(os.path.join(out, "manifest.txt"), "w") as f:
    for n, e in manifest:
        f.write(f"{n} {e}\n")
# Speed-run fillers: distinct procedural pictures, one per 10 minutes.
for i in range(fillers):
    im = Image.effect_mandelbrot((800, 600), (random.uniform(-2, 0), random.uniform(-1.2, 0), random.uniform(0.2, 1), random.uniform(0.2, 1.2)), random.randint(20, 200)).convert("RGB")
    im = ImageEnhance.Color(im.convert("RGB")).enhance(1.0)
    im.save(os.path.join(fill_dir, f"FILL_{i:05d}.jpg"), quality=70)
print(len(manifest), "set photos,", fillers, "fillers")
