#!/usr/bin/env python3
"""
Faz 3B-1 threshold calibration (runs on CI, not in the app).
Synthetic set: 20 burst groups (3-6 frames each: small shifts, crops, light,
re-encoding), 20 blurry singles, 40 unrelated photos. Photos come from
picsum.photos at run time (never stored in the repo).
Computes dHash (9x8, same as the app) and MediaPipe Image Embedder cosine
(mobilenet_v3_small, the model bundled in the app) for every pair, and
reports the thresholds that keep unrelated photos apart and catch bursts.
"""
import io, itertools, json, os, random, sys, urllib.request
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
import mediapipe as mp

random.seed(7)
MODEL = sys.argv[1]
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)


def fetch(i):
    url = f"https://picsum.photos/id/{i}/1200/900"
    for _ in range(3):
        try:
            return Image.open(io.BytesIO(urllib.request.urlopen(url, timeout=30).read())).convert("RGB")
        except Exception:
            pass
    return None


ids = list(range(0, 1085))
random.shuffle(ids)
photos = []
pool = iter(ids)


def next_photo():
    while True:
        im = fetch(next(pool))
        if im is not None:
            return im


def jitter(im, k):
    w, h = im.size
    dx, dy = random.randint(-30, 30), random.randint(-20, 20)
    crop = random.uniform(0.0, 0.05)
    box = (int(w * crop) + max(dx, 0), int(h * crop) + max(dy, 0), int(w * (1 - crop)) + min(dx, 0), int(h * (1 - crop)) + min(dy, 0))
    out = im.crop(box).resize((w, h)).rotate(random.uniform(-2, 2))
    out = ImageEnhance.Brightness(out).enhance(random.uniform(0.85, 1.15))
    buf = io.BytesIO()
    out.save(buf, "JPEG", quality=random.randint(70, 95))
    return Image.open(buf).convert("RGB")


items = []  # (name, image, label, group)
for g in range(20):
    base = next_photo()
    for k in range(random.randint(3, 6)):
        items.append((f"burst{g:02d}_{k}", base if k == 0 else jitter(base, k), "burst", f"b{g}"))
for b in range(20):
    im = next_photo().filter(ImageFilter.GaussianBlur(random.uniform(4, 10)))
    items.append((f"blur{b:02d}", im, "blur", f"x{b}"))
for u in range(40):
    items.append((f"other{u:02d}", next_photo(), "other", f"o{u}"))
print(f"items: {len(items)}", flush=True)


def dhash(im):
    small = im.copy()
    small.thumbnail((256, 256))
    g = np.asarray(small.convert("L").resize((9, 8), Image.BILINEAR), dtype=np.int32)
    bits = 0
    for y in range(8):
        for x in range(8):
            bits = (bits << 1) | (1 if g[y, x] < g[y, x + 1] else 0)
    return bits


BaseOptions = mp.tasks.BaseOptions
ImageEmbedder = mp.tasks.vision.ImageEmbedder
opts = mp.tasks.vision.ImageEmbedderOptions(base_options=BaseOptions(model_asset_path=MODEL), l2_normalize=True, quantize=False)
emb = {}
hashes = {}
with ImageEmbedder.create_from_options(opts) as embedder:
    for name, im, label, group in items:
        thumb = im.copy(); thumb.thumbnail((256, 256))
        arr = np.asarray(thumb, dtype=np.uint8)
        res = embedder.embed(mp.Image(image_format=mp.ImageFormat.SRGB, data=arr))
        emb[name] = np.array(res.embeddings[0].embedding, dtype=np.float32)
        hashes[name] = dhash(im)
        thumb.save(os.path.join(OUT, name + ".jpg"), quality=80)

pairs = {"same": [], "diff": [], "blur_vs": []}
rows = []
for (a, _, la, ga), (b, _, lb, gb) in itertools.combinations(items, 2):
    cos = float(np.dot(emb[a], emb[b]))
    ham = bin(hashes[a] ^ hashes[b]).count("1")
    kind = "same" if ga == gb else ("blur_vs" if "blur" in (la, lb) else "diff")
    pairs[kind].append((cos, ham))
    rows.append((a, b, kind, cos, ham))

def stats(v):
    v = sorted(v)
    q = lambda p: v[min(len(v) - 1, int(p * len(v)))]
    return f"n={len(v)} min={v[0]:.3f} p05={q(0.05):.3f} p50={q(0.5):.3f} p95={q(0.95):.3f} max={v[-1]:.3f}"

for k, v in pairs.items():
    print(k, "cos", stats([c for c, _ in v]))
    print(k, "ham", stats([h for _, h in v]))

max_diff = max(c for c, _ in pairs["diff"] + pairs["blur_vs"])
print(f"highest cosine between different photos: {max_diff:.4f}")
for thr in [0.80, 0.82, 0.84, 0.85, 0.86, 0.88, 0.90, 0.92, 0.94]:
    caught = sum(1 for c, _ in pairs["same"] if c >= thr) / len(pairs["same"])
    false = sum(1 for c, _ in pairs["diff"] + pairs["blur_vs"] if c >= thr)
    # group-level: union-find over pairs >= thr
    parent = {n: n for n, *_ in items}
    def f(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]; x = parent[x]
        return x
    for a, b, kind, c, h in rows:
        if c >= thr:
            parent[f(a)] = f(b)
    clusters = {}
    for n, _, l, g in items:
        clusters.setdefault(f(n), set()).add(g)
    mixed = sum(1 for s in clusters.values() if len(s) > 1)
    bursts_found = sum(1 for g in range(20) if any({f"b{g}"} == s for s in clusters.values()) or
                       any(f"b{g}" in s and len(s) == 1 and sum(1 for n, _, l, gg in items if gg == f"b{g}" and f(n) in [k for k, ss in clusters.items() if ss == s]) >= 2 for s in clusters.values()))
    full = 0
    for g in range(20):
        members = [n for n, _, l, gg in items if gg == f"b{g}"]
        roots = {f(n) for n in members}
        if len(roots) == 1 and clusters[roots.pop()] == {f"b{g}"}:
            full += 1
    print(f"thr={thr:.2f} pair-recall={caught:.3f} false-pairs={false} mixed-clusters={mixed} bursts-fully-grouped={full}/20")
for dthr in [6, 8, 10, 12, 14]:
    same = sum(1 for _, h in pairs["same"] if h <= dthr) / len(pairs["same"])
    diff = sum(1 for _, h in pairs["diff"] + pairs["blur_vs"] if h <= dthr)
    print(f"dhash<={dthr}: same-recall={same:.3f} diff-pairs={diff}")
json.dump({"rows": rows}, open(os.path.join(OUT, "pairs.json"), "w"))
