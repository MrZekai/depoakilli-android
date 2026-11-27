#!/usr/bin/env python3
"""
Faz 2: screenshots of the Pro screens (Y1, Y2, Y3, Y4, Y5, Y7) in every app
language, to check that no text overflows. Debug build only; the Play plans
come from the debug-only demo resource (ProDemo), never from real products.

Output: OUT/<locale>/<NN-name>.png
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "device-qa"))
import run as qa  # noqa: E402

PKG = os.environ.get("SHOT_PACKAGE", "com.mrzekai.depoakilli.debug")
qa.PKG = PKG
MEDIA = sys.argv[1]
OUT = sys.argv[2]
RES = "app/src/main/res"
LOCALES = [
    ("en-US", "en", "values"), ("tr-TR", "tr", "values-tr"), ("es-ES", "es", "values-es"),
    ("pt-BR", "pt-BR", "values-b+pt+BR"), ("de-DE", "de", "values-de"), ("fr-FR", "fr", "values-fr"),
    ("it-IT", "it", "values-it"), ("ru-RU", "ru", "values-ru"), ("id", "id", "values-in"), ("ar", "ar", "values-ar"),
]
only = os.environ.get("SHOT_LOCALES")
if only:
    LOCALES = [l for l in LOCALES if l[0] in only.split(",")]


def strings(values_dir):
    out = {}
    for d in ("values", values_dir):
        p = os.path.join(RES, d, "strings.xml")
        for el in ET.parse(p).getroot():
            if el.tag == "string" and el.text is not None:
                out[el.get("name")] = el.text.replace("\\'", "'").replace('\\"', '"').strip()
            if el.tag == "plurals":
                items = {i.get("quantity"): (i.text or "") for i in el}
                text = items.get("other") or next(iter(items.values()), "")
                out[el.get("name")] = text.replace("\\'", "'").replace('\\"', '"').strip()
                out[el.get("name") + "@all"] = [t.replace("\\'", "'").replace('\\"', '"').strip() for t in items.values()]
    return out


def rx(text, anchored=True):
    parts = re.split(r"%\d*\$?[sd]", text)
    body = ".+".join(re.escape(p) for p in parts)
    return "^" + body + "$" if anchored else body


def log(m):
    print(f"[pro-shots] {m}", flush=True)


def clear_system_dialogs():
    for _ in range(3):
        nodes = qa.dump()
        if not qa.find(r"isn.t responding|has stopped|keeps stopping", nodes):
            return
        btn = qa.find(r"^(Wait|Close app|OK)$", nodes)
        if btn:
            qa.tap(btn)
        else:
            qa.back()


def shot(locale_dir, name):
    clear_system_dialogs()
    path = os.path.join(locale_dir, name + ".png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    log(f"saved {path}")


def demo_status_bar():
    sh = qa.sh
    sh("settings put global sysui_demo_allowed 1")
    b = "am broadcast -a com.android.systemui.demo -e command"
    sh(f"{b} enter")
    sh(f"{b} clock -e hhmm 0941")
    sh(f"{b} battery -e level 100 -e plugged false")
    sh(f"{b} network -e wifi show -e level 4 -e fully true")
    sh(f"{b} notifications -e visible false")
    sh("settings put global hide_error_dialogs 1")


def push_media():
    qa.sh("rm -rf /sdcard/DCIM/Camera /sdcard/Pictures /sdcard/Download/* /sdcard/Movies/qa-* /sdcard/Android/media/com.whatsapp /sdcard/.SmartCleanerTrash")
    for top in ("DCIM", "Pictures", "Download", "Android"):
        src = os.path.join(MEDIA, top)
        if os.path.isdir(src):
            qa.adb("push", src, "/sdcard/", timeout=600)
    cmds = []
    for line in open(os.path.join(MEDIA, "manifest.tsv")):
        rel, size, mtime = line.rstrip("\n").split("\t")
        q = "'" + ("/sdcard/" + rel).replace("'", "'\\''") + "'"
        if int(size) > 0:
            cmds.append(f"truncate -s {size} {q}")
        cmds.append(f"touch -m -t {mtime} {q}")
    for i in range(0, len(cmds), 20):
        qa.sh("; ".join(cmds[i:i + 20]), timeout=120)
    # "My pick": old non-media downloads, 2.4 GB in all (sparse).
    old = time.strftime("%Y%m%d%H%M", time.gmtime(time.time() - 130 * 86400))
    qa.sh(f"truncate -s 1300M /sdcard/Download/backup-2025.zip; truncate -s 1100M /sdcard/Download/old-installer.bin; touch -m -t {old} /sdcard/Download/backup-2025.zip /sdcard/Download/old-installer.bin")
    # "I'll ask you": old mid-size videos shown one by one.
    older = time.strftime("%Y%m%d%H%M", time.gmtime(time.time() - 220 * 86400))
    qa.sh("mkdir -p /sdcard/Movies")
    qa.sh("; ".join(f"truncate -s {40 + i}M /sdcard/Movies/qa-trip-{i:02d}.mp4; touch -m -t {older} /sdcard/Movies/qa-trip-{i:02d}.mp4" for i in range(22)))
    qa.sh("content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1", timeout=120)


def write_prefs(first_used, decisions=0, month_bytes=0):
    day = int(qa.sh("date +%s").strip()) // 86400
    month = int(qa.sh("date +%Y%m").strip())
    xml = (
        '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map>'
        f'<boolean name="first_cleanup_used" value="{"true" if first_used else "false"}" />'
        f'<long name="decision_day" value="{day}" />'
        f'<int name="decision_count" value="{decisions}" />'
        '<long name="pro_card_hidden_until" value="0" />'
        f'<int name="pro_month" value="{month}" />'
        f'<long name="pro_month_bytes" value="{month_bytes}" />'
        "</map>"
    )
    with open("/tmp/pro_free_tier.xml", "w") as f:
        f.write(xml)
    tmp = "/data/local/tmp/pro_free_tier.xml"
    qa.adb("push", "/tmp/pro_free_tier.xml", tmp)
    qa.sh(f"run-as {PKG} mkdir -p shared_prefs")
    qa.sh(f"chmod 644 {tmp}; run-as {PKG} sh -c 'cat {tmp} > shared_prefs/pro_free_tier.xml'")


def fresh_app(app_locale):
    qa.sh(f"am force-stop {PKG}")
    qa.sh(f"pm clear {PKG}", timeout=120)
    time.sleep(2)
    qa.sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    qa.sh(f"appops set {PKG} GET_USAGE_STATS allow")
    qa.sh(f"cmd locale set-app-locales {PKG} --locales {app_locale}")


def start(demo=None, watch=None):
    extra = f" --es demo_pro {demo}" if demo else ""
    if watch:
        extra += f" --es demo_watch {watch}"
    qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity{extra}")
    time.sleep(4)
    clear_system_dialogs()


def scan(s):
    n = qa.wait_for(rx(s["home_cta_make_room"]) + "|" + rx(s["home_cta_clean"]), 40)
    if n and re.search(rx(s["home_cta_make_room"]), n.label):
        qa.tap(n)
    return qa.wait_for(rx(s["home_cta_clean"]), 240)


def open_settings_pro(s):
    gear = qa.wait_for("^" + re.escape(s["settings_title"]) + "$", 10)
    if not gear:
        return False
    qa.tap(gear)
    row = qa.wait_for(r"^Smart Cleaner Pro$", 10)
    if row:
        qa.tap(row)
        time.sleep(2)
    return bool(row)


def capture(listing, app_locale, values_dir):
    s = strings(values_dir)
    out = os.path.join(OUT, listing)
    os.makedirs(out, exist_ok=True)
    push_media()

    # Y3: before the free first cleanup is spent, Pro adds nothing.
    fresh_app(app_locale)
    start()
    if not scan(s):
        shot(out, "00-error-scan")
        return False
    if open_settings_pro(s):
        shot(out, "01-y3-honest")

    # Y1, Y2, Y7, Y4: first cleanup spent, 20 decisions used today, demo plans.
    fresh_app(app_locale)
    write_prefs(first_used=True, decisions=20)
    start("plans")
    if not scan(s):
        shot(out, "00-error-scan2")
        return False
    time.sleep(2)
    card = qa.scroll_to(rx(s["pro_card_title"]) + "|" + rx(s["paywall_title_one_tap"]), 4)
    shot(out, "02-y1-home-card")
    if card:
        tries = "|".join(rx(t) for t in s["pro_card_try@all"])
        cta = qa.scroll_to(tries + "|" + rx(s["pro_card_see"]), 4)
        if cta:
            qa.tap(cta)
            time.sleep(2)
            shot(out, "03-y2-paywall")
            qa.scroll_down()
            time.sleep(1)
            shot(out, "04-y2-paywall-bottom")
            qa.back()
            time.sleep(1)
    for _ in range(4):  # back to the top of Home (qa.to_home matches English only)
        qa.sh(f"input swipe {qa.W // 2} {int(qa.H * 0.3)} {qa.W // 2} {int(qa.H * 0.8)} 300")
    time.sleep(1)
    if qa.tap_text(rx(s["home_review_rest"]), 15):
        time.sleep(2)
        shot(out, "05-y7-plan")
        if qa.scroll_to(rx(s["plan_ask_one_by_one"]), 6):
            shot(out, "06-y7-plan-ask")
            qa.tap_text(rx(s["plan_ask_one_by_one"]), 5)
            time.sleep(2)
            shot(out, "07-y4-limit")
            qa.back()

    # Y5: Pro is on (demo), with this month's measured figure.
    qa.sh(f"am force-stop {PKG}")
    write_prefs(first_used=True, decisions=0, month_bytes=14_200_000_000)
    start("on")
    if open_settings_pro(s):
        shot(out, "08-y5-pro-on")

    # Faz 3A-1 Storage Watch: Z2 (off), then Z1 and Z3 with a demo week (Pro on), then notices.
    fresh_app(app_locale)
    start()
    if scan(s) and open_settings_row(s, s["watch_settings_row"]):
        time.sleep(2)
        shot(out, "12-z2-intro")
    fresh_app(app_locale)
    qa.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    start("on", watch="seed")
    if scan(s):
        time.sleep(3)
        qa.scroll_to(rx(s["watch_home_see_new"]), 4)
        shot(out, "09-z1-home")
        if open_settings_row(s, s["watch_settings_row"]):
            time.sleep(2)
            shot(out, "10-z3-watch")
            qa.scroll_down()
            time.sleep(1)
            shot(out, "11-z3-bottom")
    for topic in ("WEEKLY", "WHATSAPP", "THRESHOLD_90"):
        qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity --es demo_watch {topic}")
        time.sleep(3)
    qa.sh("cmd statusbar expand-notifications")
    time.sleep(2)
    shot(out, "13-notifications")
    qa.sh("cmd statusbar collapse")
    return True


def open_settings_row(s, title):
    qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity")
    time.sleep(1)
    gear = qa.wait_for("^" + re.escape(s["settings_title"]) + "$", 10)
    if not gear:
        return False
    qa.tap(gear)
    row = qa.scroll_to("^" + re.escape(title) + "$", 4)
    if row:
        qa.tap(row)
    return bool(row)


def main():
    qa.adb("wait-for-device", timeout=300)
    qa.W, qa.H = qa.screen_size()
    qa.sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0")
    time.sleep(15)
    demo_status_bar()
    ok = True
    for listing, tag, vdir in LOCALES:
        log(f"=== {listing}")
        done = False
        for attempt in (1, 2):
            try:
                done = capture(listing, tag, vdir)
            except Exception as e:
                log(f"{listing} attempt {attempt} failed: {e!r}")
            if done:
                break
        ok = done and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
