#!/usr/bin/env python3
"""
Builds the demo media for Play Store screenshots on the CI runner.

Everything is synthetic: soft landscape gradients drawn here, no real photos,
no personal files. Videos are short real H.264 clips (so Android can draw a
thumbnail); the capture script later stretches them to large sizes on the
emulator with a sparse `truncate`, which costs no disk space.

Output: OUT/<relative path on /sdcard>  plus OUT/manifest.tsv
        (path<TAB>size_bytes_after_truncate<TAB>mtime 'YYYYMMDDhhmm')
"""
import os
import random
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else "store-media"
random.seed(51)
MB = 1024 * 1024

PALETTES = [
    ((255, 183, 120), (255, 120, 90), (60, 70, 110)),    # sunset
    ((150, 210, 255), (90, 160, 230), (40, 90, 60)),     # lake
    ((255, 225, 170), (240, 170, 110), (120, 80, 60)),   # desert
    ((190, 225, 255), (130, 190, 240), (70, 120, 90)),   # hills
    ((40, 50, 90), (90, 70, 140), (20, 25, 45)),         # night
    ((255, 240, 220), (250, 200, 160), (200, 120, 100)), # beach
]


def scene(w, h, palette, seed):
    rnd = random.Random(seed)
    sky_top, sky_low, ground = palette
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        c = tuple(int(sky_top[i] * (1 - t) + sky_low[i] * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    # sun / moon
    r = int(min(w, h) * rnd.uniform(0.06, 0.11))
    cx, cy = int(w * rnd.uniform(0.2, 0.8)), int(h * rnd.uniform(0.18, 0.35))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 245, 225))
    # hills
    for layer in range(3):
        base = int(h * (0.55 + layer * 0.12))
        shade = tuple(max(0, int(ground[i] * (0.75 + layer * 0.15))) for i in range(3))
        pts = [(0, h)]
        x = 0
        while x <= w:
            pts.append((x, base + int(rnd.uniform(-0.06, 0.06) * h)))
            x += w // 8
        pts.append((w, h))
        d.polygon(pts, fill=shade)
    return img.filter(ImageFilter.GaussianBlur(1.2))


def write_jpg(rel, w, h, seed, mtime, pal=None):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    scene(w, h, pal or PALETTES[seed % len(PALETTES)], seed).save(path, quality=88)
    return path


def write_video(rel, seed, target_bytes, mtime, vertical=False):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    w, h = (720, 1280) if vertical else (1280, 720)
    frame = path + ".png"
    scene(w, h, PALETTES[seed % len(PALETTES)], seed).save(frame)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-loop", "1", "-i", frame, "-t", "2",
         "-vf", "zoompan=z='min(zoom+0.002,1.2)':d=50:s=%dx%d,fps=25" % (w, h),
         "-c:v", "libx264", "-pix_fmt", "yuv420p", "-movflags", "+faststart", path],
        check=True,
    )
    os.remove(frame)
    manifest.append((rel, target_bytes, mtime))


manifest = []

# Camera: the "old videos" a user would think twice about (2024-2025).
for i, (day, size_gb) in enumerate([
    ("20240714", 2.4), ("20240802", 1.9), ("20240915", 1.6), ("20241103", 1.3),
    ("20241228", 1.1), ("20250219", 0.95), ("20250406", 0.8), ("20250611", 0.7),
]):
    write_video(f"DCIM/Camera/VID_{day}_1{i}4512.mp4", i, int(size_gb * 1024) * MB + i * 7919, day + "1830")
for i, day in enumerate(["20250103", "20250221", "20250517", "20250704", "20250812", "20250930"]):
    p = write_jpg(f"DCIM/Camera/IMG_{day}_0{i}2233.jpg", 1600, 1200, 20 + i, day + "1200")
    manifest.append((os.path.relpath(p, OUT), 0, day + "1200"))

# WhatsApp media (received videos and images).
wa = "Android/media/com.whatsapp/WhatsApp/Media"
for i, (day, mb) in enumerate([("20250210", 640), ("20250322", 410), ("20250508", 330), ("20250719", 260), ("20250901", 190)]):
    write_video(f"{wa}/WhatsApp Video/VID-{day}-WA00{i}{i}.mp4", 40 + i, mb * MB + i * 104729, day + "2010", vertical=True)
for i in range(8):
    day = f"2025{3 + i:02d}1{i}"
    p = write_jpg(f"{wa}/WhatsApp Images/IMG-{day}-WA00{i}7.jpg", 1080, 1350, 60 + i, day + "1015")
    manifest.append((os.path.relpath(p, OUT), 0, day + "1015"))

# Downloads: old installers, an archive, unfinished downloads, documents.
for name, mb, day in [
    ("Download/holiday-photos-2024.zip", 1850, "202409051140"),
    ("Download/Movie.Night.2023.1080p.mkv", 2300, "202402190930"),
    ("Download/presentation_final_v3.pdf", 38, "202501141015"),
    ("Download/invoice_2024_11.pdf", 2, "202411301700"),
]:
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(os.urandom(4096))
    manifest.append((name, mb * MB + 4093, day))
for i, (mb, day) in enumerate([(180, "202509011200"), (95, "202508141900")]):
    name = f"Download/update-package-{i + 1}.crdownload"
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(os.urandom(4096))
    manifest.append((name, mb * MB + 31 * i + 7, day))

# Exact duplicate photos: same bytes in two folders.
for i in range(4):
    src = write_jpg(f"Pictures/Trip/IMG_{2041 + i}.jpg", 2400, 1800, 80 + i, "202406221200")
    dup = os.path.join(OUT, f"Download/IMG_{2041 + i} (1).jpg")
    with open(src, "rb") as a, open(dup, "wb") as b:
        b.write(a.read())
    manifest.append((os.path.relpath(src, OUT), 0, "202406221200"))
    manifest.append((os.path.relpath(dup, OUT), 0, "202407011200"))

# Screenshots: a handful of plain, old phone screenshots.
for i in range(10):
    path = os.path.join(OUT, f"Pictures/Screenshots/Screenshot_2025030{i % 9 + 1}-1{i}2201.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = Image.new("RGB", (540, 1200), (245, 246, 248))
    d = ImageDraw.Draw(img)
    for row in range(8):
        d.rounded_rectangle([30, 120 + row * 130, 510, 220 + row * 130], 18, fill=(225 - row * 6, 230, 240))
    img.save(path)
    manifest.append((os.path.relpath(path, OUT), 0, f"2025030{i % 9 + 1}1200"))

with open(os.path.join(OUT, "manifest.tsv"), "w") as f:
    for rel, size, mtime in manifest:
        f.write(f"{rel}\t{size}\t{mtime}\n")
print(f"{len(manifest)} files")
