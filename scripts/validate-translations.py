#!/usr/bin/env python3
"""Validate complete language resources and Play listing character limits."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = [n for n in ET.parse(RES / "values/strings.xml").getroot()
          if n.get("translatable") != "false"]
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
BRANDS = re.compile(r"Smart Cleaner|Smart Clean(?!ing)|SMART CLEANER|SMART CLEAN(?!ING)|WhatsApp Business|WhatsApp|WHATSAPP|Google AdMob|Android/data|Android/obb|Android|APK|\b(?:MB|GB)\b")
QUANTITIES = {"de": {"one", "other"}, "fr": {"one", "many", "other"},
              "it": {"one", "many", "other"}, "ru": {"one", "few", "many", "other"},
              "in": {"other"}, "ar": {"zero", "one", "two", "few", "many", "other"}}
errors = []

def compare(source, target, label):
    for name, pattern in (("placeholders", FORMAT), ("brands/units", BRANDS),
                          ("markup", re.compile(r"</?[^>]+>")),
                          ("line escapes", re.compile(r"\\[nrt]"))):
        if Counter(pattern.findall(source)) != Counter(pattern.findall(target)):
            errors.append(f"{label}: {name} differ")
    if re.search(r"ZX[KQ]\d", target):
        errors.append(f"{label}: unresolved translation marker")

for locale, quantities in QUANTITIES.items():
    nodes = list(ET.parse(RES / f"values-{locale}/strings.xml").getroot())
    if [(n.tag, n.get("name")) for n in nodes] != [(n.tag, n.get("name")) for n in SOURCE]:
        errors.append(f"{locale}: keys, types or order differ")
        continue
    for source, target in zip(SOURCE, nodes):
        label = f"{locale}/{source.get('name')}"
        if source.tag == "string":
            compare(source.text or "", target.text or "", label)
        else:
            if {n.get("quantity") for n in target} != quantities:
                errors.append(f"{label}: incorrect plural quantities")
            original = {n.get("quantity"): n.text or "" for n in source}
            for item in target:
                compare(original.get(item.get("quantity"), original["other"]),
                        item.text or "", label + "/" + item.get("quantity"))
    print(f"values-{locale}: {len(nodes)} keys; XML, order, placeholders, brands, markup and plurals checked")

for locale in ("en", "tr", "es", "pt-BR", "de", "fr", "it", "ru", "id", "ar"):
    text = (ROOT / f"store-assets/listing-{locale}.txt").read_text(encoding="utf-8")
    fields = re.findall(r"(Title|Short description|Full description) \((\d+)\):\n(.*?)(?=\n\n(?:Short description|Full description)|\Z)", text, re.S)
    assert len(fields) == 3, locale
    counts = []
    for name, stated, value in fields:
        value = value.strip()
        count = len(value)
        counts.append(count)
        if count != int(stated) or count > {"Title": 30, "Short description": 80, "Full description": 4000}[name]:
            errors.append(f"listing-{locale}/{name}: invalid length/count {count}")
        if name == "Title" and value != "Smart Cleaner: Storage Manager":
            errors.append(f"listing-{locale}: title changed")
    print(f"listing-{locale}: title={counts[0]}, short={counts[1]}, full={counts[2]}")

if errors:
    raise SystemExit("\n".join(errors))
print("Translation and listing validation: PASS")
