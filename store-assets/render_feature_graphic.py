from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent.parent
OUT = Path(__file__).resolve().parent / "feature-graphic-1024x500.png"
canvas = Image.new("RGB", (1024, 500), "#10292b")
draw = ImageDraw.Draw(canvas)
draw.rounded_rectangle((52, 72, 350, 428), radius=42, fill="#1b4445")
icon = Image.open(ROOT / "app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground_art.png").convert("RGBA")
icon.thumbnail((292, 292), Image.Resampling.LANCZOS)
canvas.paste(icon, (55 + (292 - icon.width) // 2, 104 + (292 - icon.height) // 2), icon)
font_dir = Path("C:/Windows/Fonts")
title_font = ImageFont.truetype(str(font_dir / "segoeuib.ttf"), 56)
subtitle_font = ImageFont.truetype(str(font_dir / "segoeui.ttf"), 25)
draw.text((390, 154), "Free up storage", font=title_font, fill="#ffffff")
draw.text((390, 223), "safely", font=title_font, fill="#ffffff")
draw.text((390, 322), "Junk • Duplicates • Large files", font=subtitle_font, fill="#9de2d5")
draw.text((390, 357), "WhatsApp media", font=subtitle_font, fill="#9de2d5")
canvas.save(OUT)
