#!/usr/bin/env python3
"""
Captures raw Play Store screenshots of the debug build on an emulator, in
every app language, with demo data only (see make_media.py), a clean demo
status bar and no ads (the app's own 60-minute ad-free window is switched on
through run-as, the same state a rewarded ad grants).

Output: OUT/<locale>/<NN-name>.png  (full 1080x2400 screens)
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "device-qa"))
import run as qa  # noqa: E402  (adb helpers: dump/find/tap/wait_for/...)

PKG = os.environ.get("SHOT_PACKAGE", "com.mrzekai.depoakilli.debug")
qa.PKG = PKG
MEDIA = sys.argv[1]
OUT = sys.argv[2]
RES = "app/src/main/res"
LOCALES = [  # (Play listing code, app locale tag, values dir)
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
    return out


def rx(text):
    """Exact-label regex from a string resource; %1$s and friends match anything."""
    parts = re.split(r"%\d*\$?[sd]", text)
    return "^" + ".+".join(re.escape(p) for p in parts) + "$"


def log(m):
    print(f"[shots] {m}", flush=True)


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
    sh(f"{b} network -e mobile show -e datatype none -e level 4 -e fully true")
    sh(f"{b} notifications -e visible false")
    sh("cmd overlay enable com.android.internal.systemui.navbar.gestural")
    # No system "isn't responding" dialogs over the app (the emulator's own launcher is slow).
    sh("settings put global hide_error_dialogs 1")


def push_media():
    qa.sh("rm -rf /sdcard/DCIM/Camera /sdcard/Pictures /sdcard/Download/* /sdcard/Android/media/com.whatsapp /sdcard/.SmartCleanerTrash")
    for top in ("DCIM", "Pictures", "Download", "Android"):
        src = os.path.join(MEDIA, top)
        if os.path.isdir(src):
            qa.adb("push", src, "/sdcard/", timeout=600)
    cmds = []
    for line in open(os.path.join(MEDIA, "manifest.tsv")):
        rel, size, mtime = line.rstrip("\n").split("\t")
        p = "/sdcard/" + rel
        q = "'" + p.replace("'", "'\\''") + "'"
        if int(size) > 0:
            cmds.append(f"truncate -s {size} {q}")
        cmds.append(f"touch -m -t {mtime} {q}")
    for i in range(0, len(cmds), 20):
        qa.sh("; ".join(cmds[i:i + 20]), timeout=120)
    qa.sh("content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1", timeout=120)


def ad_free_window():
    up = float(qa.sh("cat /proc/uptime").split()[0])
    now = int(time.time() * 1000)
    hour = 3600 * 1000
    xml = (
        '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map>'
        f"<long name=\"expiry_wall_millis\" value=\"{now + hour}\" />"
        f"<long name=\"remaining_at_checkpoint_millis\" value=\"{hour}\" />"
        f"<long name=\"checkpoint_elapsed_millis\" value=\"{int(up * 1000)}\" />"
        "</map>"
    )
    qa.sh(f"run-as {PKG} mkdir -p shared_prefs")
    tmp = "/data/local/tmp/adfree.xml"
    with open("/tmp/adfree.xml", "w") as f:
        f.write(xml)
    qa.adb("push", "/tmp/adfree.xml", tmp)
    qa.sh(f"chmod 644 {tmp}; run-as {PKG} sh -c 'cat {tmp} > shared_prefs/rewarded_ad_free_window.xml'")
    log("ad-free prefs: " + qa.sh(f"run-as {PKG} cat shared_prefs/rewarded_ad_free_window.xml")[:200])


def fresh_app(app_locale):
    qa.sh(f"am force-stop {PKG}")
    log("pm clear: " + qa.sh(f"pm clear {PKG}", timeout=120).strip())
    time.sleep(2)
    qa.sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    qa.sh(f"appops set {PKG} GET_USAGE_STATS allow")
    qa.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS 2>/dev/null")
    qa.sh(f"cmd locale set-app-locales {PKG} --locales {app_locale}")
    ad_free_window()


def capture(listing, app_locale, values_dir):
    s = strings(values_dir)
    out = os.path.join(OUT, listing)
    os.makedirs(out, exist_ok=True)
    push_media()
    fresh_app(app_locale)
    qa.launch()
    clear_system_dialogs()
    qa.dismiss_ads()

    make_room = qa.wait_for(rx(s["home_cta_make_room"]) + "|" + rx(s["home_cta_clean"]), 40)
    if make_room and re.search(rx(s["home_cta_make_room"]), make_room.label):
        qa.tap(make_room)
    if not qa.wait_for(rx(s["home_cta_clean"]), 240):
        log(f"{listing}: plan did not appear: " + " | ".join(qa.texts())[:600])
        shot(out, "00-error")
        return False
    time.sleep(2)
    shot(out, "01-home-plan")

    if qa.tap_text(rx(s["home_review_rest"]), 15):
        time.sleep(2)
        qa.dismiss_ads()
        shot(out, "03-findings")
        qa.back()
        time.sleep(1)

    qa.tap_text(rx(s["home_cta_clean"]), 15)
    if qa.wait_for(rx(s["confirm_yes_clean"]), 20):
        time.sleep(1)
        shot(out, "02-confirm")
        qa.tap_text(rx(s["confirm_yes_clean"]), 5)
        if qa.wait_for(rx(s["result_free_now_bytes"]), 90):
            time.sleep(2)
            shot(out, "04-result")
        qa.tap_text(rx(s["result_done"]), 10)
        qa.dismiss_ads()
    time.sleep(2)
    if qa.tap_text(rx(s["tab_trash"]), 10):
        time.sleep(2)
        shot(out, "05-trash")
    if qa.tap_text(rx(s["tab_tools"]), 10):
        time.sleep(2)
        shot(out, "06-tools")
    return True


def k3_copies():
    """K3: "Select all" on possible duplicates in three cases, with a
    screenshot of the message: all different / some exact / all exact.
    1.1 GB sparse files stay "possible" after the scan (the automatic check
    stops at 1 GB), and the plan lists them (groups over 50 MB)."""
    s = strings("values")
    out = os.path.join(OUT, "k3")
    os.makedirs(out, exist_ok=True)
    d = "/sdcard/Download"
    mb10 = 10 * 1024 * 1024

    def base(name):
        qa.sh(f"truncate -s 1100M {d}/{name}.zip")

    def exact(name):
        qa.sh(f"truncate -s 1100M '{d}/{name} (1).zip'")

    def different(name):  # same size and same sampled blocks; one byte differs at 10 MB
        exact(name)
        qa.sh(f"printf X | dd of='{d}/{name} (1).zip' bs=1 seek={mb10} conv=notrunc 2>/dev/null", timeout=120)

    cases = (
        ("1-all-different", [("alpha", different)], r"None of these are exact copies"),
        ("2-some-exact", [("alpha", different), ("beta", exact)], r"^1 exact copy selected$"),
        ("3-all-exact", [("beta", exact), ("delta", exact)], r"^2 exact copies selected$"),
    )
    for case, files, expect in cases:
        qa.sh("rm -rf /sdcard/DCIM/Camera /sdcard/Pictures /sdcard/Download/* /sdcard/Android/media/com.whatsapp /sdcard/.SmartCleanerTrash")
        for name, kind in files:
            base(name)
            kind(name)
        qa.sh(f"touch -m -t 202403011200 {d}/*")
        qa.sh("content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1", timeout=120)
        fresh_app("en")
        qa.launch()
        clear_system_dialogs()
        n = qa.wait_for(rx(s["home_cta_make_room"]) + "|" + rx(s["home_cta_clean"]) + "|" + rx(s["home_review_rest"]), 40)
        if n and re.search(rx(s["home_cta_make_room"]), n.label):
            qa.tap(n)
        if not qa.wait_for(rx(s["home_review_rest"]), 240):
            qa.launch()  # a cold emulator can still be on "starting…"
            clear_system_dialogs()
            n = qa.wait_for(rx(s["home_cta_make_room"]) + "|" + rx(s["home_review_rest"]), 60)
            if n and re.search(rx(s["home_cta_make_room"]), n.label):
                qa.tap(n)
        if not qa.wait_for(rx(s["home_review_rest"]), 240):
            log(f"k3 {case}: no findings link: " + " | ".join(qa.texts())[:500])
            shot(out, case + "-no-findings")
            continue
        qa.tap_text(rx(s["home_review_rest"]), 10)
        time.sleep(2)
        row = qa.scroll_to(rx(s["plan_group_duplicate_unverified"]) + "|^" + re.escape(s["plan_group_duplicate_unverified"]), 10)
        if not row:
            log(f"k3 {case}: no possible-duplicates row: " + " | ".join(qa.texts())[:500])
            shot(out, case + "-no-row")
            continue
        qa.tap(row)
        time.sleep(2)
        shot(out, case + "-before")
        if not qa.tap_text(rx(s["smart_detail_select_all"]), 10):
            log(f"k3 {case}: no select all: " + " | ".join(qa.texts())[:500])
        msg = None
        end = time.time() + 300
        while time.time() < end and not msg:
            msg = qa.find(expect)
            if not msg:
                time.sleep(0.3)
        time.sleep(4)  # let the snackbar go so the screenshot shows the on-page result
        shot(out, case)
        log(f"k3 {case}: expected /{expect}/ -> {msg.label if msg else None}; visible: " + " | ".join(qa.texts())[:300])


def main():
    qa.adb("wait-for-device", timeout=300)
    qa.W, qa.H = qa.screen_size()
    qa.sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0")
    time.sleep(15)
    qa.dismiss_ads()
    demo_status_bar()
    ok = True
    for listing, tag, vdir in LOCALES:
        log(f"=== {listing}")
        done = False
        for attempt in (1, 2):  # a slow emulator sometimes ignores the first reset
            try:
                done = capture(listing, tag, vdir)
            except Exception as e:  # keep going with the next language
                log(f"{listing} attempt {attempt} failed: {e!r}")
            if done:
                break
        ok = done and ok
    if os.environ.get("K3", "1") == "1":
        try:
            k3_copies()
        except Exception as e:
            log(f"k3 failed: {e!r}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
