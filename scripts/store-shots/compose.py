#!/usr/bin/env python3
"""
Turns raw emulator screenshots into Play Store phone screenshots (1080x1920,
9:16) with a short caption, plus a 1024x500 feature graphic per language.

python3 compose.py RAW_DIR OUT_DIR FONTS_DIR ICON_PNG
RAW_DIR/<locale>/<NN-name>.png  ->  OUT_DIR/<locale>/phone-<N>.png, feature-graphic.png
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAW, OUT, FONTS, ICON = sys.argv[1:5]
APP_FONT = os.path.join(os.path.dirname(__file__), "..", "..", "app/src/main/res/font/archivo_variable.ttf")

LIME = (43, 232, 120)
BLACK = (7, 9, 12)
WHITE = (247, 249, 255)
SOFT = (170, 180, 190)
W, H = 1080, 1920

# Order on the store page; the first three are what most people see.
SHOTS = [
    ("01-home-plan", "top"),
    ("02-confirm", "sheet"),
    ("03-findings", "top"),
    ("04-result", "top"),
    ("05-trash", "top"),
    ("06-tools", "top"),
]

# Captions avoid "free", rankings, prices and calls to action (Play metadata policy).
CAPTIONS = {
    "en-US": ["One scan shows how much room you can make", "Nothing goes without your OK", "Old videos, big files and chat media, sorted for you", "Changed your mind? Undo it", "Restore anything for 7 days", "Duplicates, screenshots and more tools"],
    "tr-TR": ["Tek tarama, ne kadar yer açılacağını gösterir", "Senin onayın olmadan hiçbir şey silinmez", "Eski videolar, büyük dosyalar, sohbet medyası: hepsi sıralı", "Fikrini mi değiştirdin? Geri al", "Her şeyi 7 gün içinde geri yükle", "Kopyalar, ekran görüntüleri ve daha fazlası"],
    "es-ES": ["Un análisis muestra cuánto espacio puedes recuperar", "Nada se borra sin tu permiso", "Videos antiguos, archivos grandes y medios de chats, ordenados", "¿Cambiaste de idea? Deshazlo", "Restaura lo que quieras durante 7 días", "Duplicados, capturas y más herramientas"],
    "pt-BR": ["Uma verificação mostra quanto espaço você pode recuperar", "Nada é apagado sem o seu OK", "Vídeos antigos, arquivos grandes e mídias de conversas, organizados", "Mudou de ideia? Desfaça", "Restaure o que quiser por 7 dias", "Duplicatas, capturas de tela e mais ferramentas"],
    "de-DE": ["Ein Scan zeigt, wie viel Platz du schaffen kannst", "Nichts wird ohne dein OK gelöscht", "Alte Videos, große Dateien und Chat-Medien, sortiert", "Doch nicht? Rückgängig machen", "Alles 7 Tage lang wiederherstellen", "Duplikate, Screenshots und mehr Werkzeuge"],
    "fr-FR": ["Une analyse montre la place que tu peux récupérer", "Rien n'est supprimé sans ton accord", "Vieilles vidéos, gros fichiers et médias de chats, triés", "Tu as changé d'avis ? Annule", "Restaure tout pendant 7 jours", "Doublons, captures d'écran et autres outils"],
    "it-IT": ["Una scansione mostra quanto spazio puoi recuperare", "Niente viene eliminato senza il tuo OK", "Video vecchi, file grandi e media delle chat, in ordine", "Hai cambiato idea? Annulla", "Ripristina tutto per 7 giorni", "Duplicati, screenshot e altri strumenti"],
    "ru-RU": ["Одно сканирование покажет, сколько места можно вернуть", "Ничего не удаляется без вашего согласия", "Старые видео, большие файлы и медиа из чатов — по полочкам", "Передумали? Отмените", "Восстановление в течение 7 дней", "Дубликаты, скриншоты и другие инструменты"],
    "id": ["Satu pemindaian menunjukkan berapa ruang yang bisa didapat", "Tidak ada yang dihapus tanpa izinmu", "Video lama, file besar, dan media chat, tersusun rapi", "Berubah pikiran? Batalkan", "Pulihkan apa saja selama 7 hari", "Duplikat, tangkapan layar, dan alat lainnya"],
    "ar": ["فحص واحد يوضح المساحة التي يمكنك استعادتها", "لا يُحذف شيء دون موافقتك", "مقاطع قديمة وملفات كبيرة ووسائط المحادثات، مرتبة لك", "غيّرت رأيك؟ تراجع", "استعد أي ملف خلال 7 أيام", "ملفات مكررة ولقطات شاشة وأدوات أخرى"],
}

FEATURE = {
    "en-US": ("Make room on your phone", "You review every file before it goes"),
    "tr-TR": ("Telefonunda yer aç", "Her dosyayı silinmeden önce sen incelersin"),
    "es-ES": ("Haz espacio en tu teléfono", "Revisas cada archivo antes de borrarlo"),
    "pt-BR": ("Abra espaço no seu celular", "Você revisa cada arquivo antes de apagar"),
    "de-DE": ("Schaff Platz auf deinem Handy", "Du prüfst jede Datei vor dem Löschen"),
    "fr-FR": ("Fais de la place sur ton téléphone", "Tu vérifies chaque fichier avant suppression"),
    "it-IT": ("Fai spazio sul tuo telefono", "Controlli ogni file prima di eliminarlo"),
    "ru-RU": ("Освободите место на телефоне", "Вы проверяете каждый файл перед удалением"),
    "id": ("Lapangkan ruang di ponselmu", "Kamu memeriksa setiap file sebelum dihapus"),
    "ar": ("وفّر مساحة على هاتفك", "تراجع كل ملف قبل حذفه"),
}


def font(locale, size, weight=900):
    if locale == "ar":
        return ImageFont.truetype(os.path.join(FONTS, "NotoSansArabic-Black.ttf" if weight >= 800 else "NotoSansArabic-Bold.ttf"), size)
    if locale == "ru-RU":
        return ImageFont.truetype(os.path.join(FONTS, "NotoSans-Black.ttf" if weight >= 800 else "NotoSans-Bold.ttf"), size)
    f = ImageFont.truetype(APP_FONT, size)
    f.set_variation_by_axes([weight])
    return f


def kw(locale):
    return {"direction": "rtl", "language": "ar"} if locale == "ar" else {}


def wrap(draw, text, fnt, max_w, locale):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=fnt, **kw(locale)) <= max_w or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    return lines


def fit_text(draw, text, locale, max_w, max_lines, start, weight=900):
    size = start
    while size > 30:
        fnt = font(locale, size, weight)
        lines = wrap(draw, text, fnt, max_w, locale)
        if len(lines) <= max_lines and all(draw.textlength(l, font=fnt, **kw(locale)) <= max_w for l in lines):
            return fnt, lines, size
        size -= 4
    fnt = font(locale, size, weight)
    return fnt, wrap(draw, text, fnt, max_w, locale), size


def rounded(img, r):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], r, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), mask)
    return out


def phone(raw_path, locale, caption, anchor, out_path):
    canvas = Image.new("RGB", (W, H), BLACK)
    d = ImageDraw.Draw(canvas)
    fnt, lines, size = fit_text(d, caption, locale, W - 140, 3, 84)
    lh = int(size * 1.18)
    top = 110
    for i, line in enumerate(lines):
        d.text((W // 2, top + i * lh), line, font=fnt, fill=LIME, anchor="ma", **kw(locale))
    card_top = top + len(lines) * lh + 70
    card_w = 900
    shot = Image.open(raw_path).convert("RGB")
    scale = card_w / shot.width
    visible = int((H - card_top + 60) / scale)
    if anchor == "sheet":
        # bottom sheet only: start just above it, card sits on the canvas bottom
        start = int(shot.height * 0.35)
        card_w = 960
        scale = card_w / shot.width
        crop = shot.crop((0, start, shot.width, shot.height))
        crop = crop.resize((card_w, int(crop.height * scale)), Image.LANCZOS)
        card_top = max(card_top, H - crop.height + 40)
        crop = crop.crop((0, 0, card_w, H - card_top + 60))
    elif anchor == "bottom":
        crop = shot.crop((0, shot.height - visible, shot.width, shot.height))
    else:
        crop = shot.crop((0, 0, shot.width, min(visible, shot.height)))
    if crop.width != card_w:
        crop = crop.resize((card_w, int(crop.height * scale)), Image.LANCZOS)
    card = rounded(crop, 56)
    glow = Image.new("RGBA", (card_w + 80, card.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle([40, 40, card_w + 40, card.height + 40], 56, fill=(43, 232, 120, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(26))
    x = (W - card_w) // 2
    canvas.paste(glow, (x - 40, card_top - 40), glow)
    canvas.paste(card, (x, card_top), card)
    canvas.save(out_path, optimize=True)


def feature(raw_home, locale, out_path):
    fw, fh = 1024, 500
    canvas = Image.new("RGB", (fw, fh), BLACK)
    d = ImageDraw.Draw(canvas)
    rtl = locale == "ar"
    # screenshot card bleeding off the bottom, on the trailing side
    shot = Image.open(raw_home).convert("RGB")
    card_w = 300
    scale = card_w / shot.width
    crop = shot.crop((0, int(shot.height * 0.115), shot.width, int(shot.height * 0.69)))
    ch = int(crop.height * scale)
    crop = crop.resize((card_w, ch), Image.LANCZOS)
    card = rounded(crop, 30)
    card_y = (fh - ch) // 2
    cx = 60 if rtl else fw - card_w - 60
    glow = Image.new("RGBA", (card_w + 60, ch + 60), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle([30, 30, card_w + 30, ch + 30], 30, fill=(43, 232, 120, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(20))
    canvas.paste(glow, (cx - 30, card_y - 30), glow)
    canvas.paste(card, (cx, card_y), card)
    # icon + texts on the leading side
    icon = Image.open(ICON).convert("RGBA").resize((96, 96), Image.LANCZOS)
    icon = rounded(icon, 22)
    text_x0 = fw - 60 if rtl else 60
    text_w = fw - card_w - 180
    canvas.paste(icon, ((fw - 60 - 96) if rtl else 60, 70), icon)
    name_f = font("en-US", 34, 800)
    d.text(((fw - 60 - 96 - 20) if rtl else (60 + 96 + 20), 118), "Smart Cleaner", font=name_f, fill=WHITE, anchor="rm" if rtl else "lm")
    head, sub = FEATURE[locale]
    hf, hl, hs = fit_text(d, head, locale, text_w, 2, 64)
    y = 205
    for line in hl:
        d.text((text_x0, y), line, font=hf, fill=LIME, anchor="ra" if rtl else "la", **kw(locale))
        y += int(hs * 1.15)
    sf, sl, ss = fit_text(d, sub, locale, text_w, 2, 30, 700)
    y += 16
    for line in sl:
        d.text((text_x0, y), line, font=sf, fill=SOFT, anchor="ra" if rtl else "la", **kw(locale))
        y += int(ss * 1.3)
    canvas.save(out_path, optimize=True)


def main():
    for locale in sorted(os.listdir(RAW)):
        src = os.path.join(RAW, locale)
        if not os.path.isdir(src) or locale not in CAPTIONS:
            continue
        dst = os.path.join(OUT, locale)
        os.makedirs(dst, exist_ok=True)
        n = 0
        for i, (name, anchor) in enumerate(SHOTS):
            raw = os.path.join(src, name + ".png")
            if os.path.exists(raw):
                n += 1
                phone(raw, locale, CAPTIONS[locale][i], anchor, os.path.join(dst, f"phone-{n}.png"))
        home = os.path.join(src, "01-home-plan.png")
        if os.path.exists(home):
            feature(home, locale, os.path.join(dst, "feature-graphic.png"))
        print(locale, n, "screens")


if __name__ == "__main__":
    main()
