#!/usr/bin/env python3
"""
Faz 3A-2: screenshots of Z5 (list), Z6 (progress), Z7 (watch both) and Z8
(result) in every app language, from a real shrink on the emulator. Debug
build only; Pro is the debug-only demo state (no Play products, no price).

Usage: capture_video.py <video.mp4> <out dir>   ->   <out>/<locale>/<NN-name>.png
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
VIDEO = os.path.abspath(sys.argv[1])
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
FOLDER = "/sdcard/DCIM/Camera"


def unescape(text):
    return (text or "").replace("\\'", "'").replace('\\"', '"').strip()


def strings(values_dir):
    out = {}
    for d in ("values", values_dir):
        for el in ET.parse(os.path.join(RES, d, "strings.xml")).getroot():
            if el.tag == "string" and el.text is not None:
                out[el.get("name")] = unescape(el.text)
            if el.tag == "plurals":
                out[el.get("name") + "@all"] = [unescape(i.text) for i in el]
    return out


def rx(text, anchored=True):
    parts = re.split(r"%\d*\$?[sd]", text)
    body = ".+".join(re.escape(p) for p in parts)
    return "^" + body + "$" if anchored else body


def any_rx(texts):
    return "|".join(rx(t) for t in texts)


def log(m):
    print(f"[video-shots] {m}", flush=True)


def shot(locale_dir, name):
    path = os.path.join(locale_dir, name + ".png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    log(f"saved {path}")


def demo_status_bar():
    b = "am broadcast -a com.android.systemui.demo -e command"
    qa.sh("settings put global sysui_demo_allowed 1")
    qa.sh(f"{b} enter")
    qa.sh(f"{b} clock -e hhmm 0941")
    qa.sh(f"{b} battery -e level 100 -e plugged false")
    qa.sh(f"{b} network -e wifi show -e level 4 -e fully true")
    qa.sh(f"{b} notifications -e visible false")
    qa.sh("settings put global hide_error_dialogs 1")


def capture(listing, app_locale, values_dir, index):
    s = strings(values_dir)
    out = os.path.join(OUT, listing)
    os.makedirs(out, exist_ok=True)
    # One fresh video per language; the earlier ones are shrunk and in the Trash.
    dev = f"{FOLDER}/trip-{index:02d}.mp4"
    qa.sh(f"mkdir -p {FOLDER}")
    qa.adb("push", VIDEO, dev, timeout=300)
    stamp = time.strftime("%Y%m%d%H%M", time.gmtime(time.time() - (40 + index) * 86400))
    qa.sh(f"touch -m -t {stamp} {dev}")
    qa.sh("content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1", timeout=120)
    qa.sh(f"am force-stop {PKG}")
    qa.sh(f"cmd locale set-app-locales {PKG} --locales {app_locale}")
    qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity --es demo_pro on --es watch_target videos")
    button = qa.wait_for(any_rx(s["video_shrink_button@all"]), 90, dismiss=False)
    if not button:
        log("Z5 not shown: " + " | ".join(qa.texts()[:15]))
        return False
    shot(out, "01-z5-list")
    qa.tap(button)
    if qa.wait_for(rx(s["video_progress_label"]), 30, dismiss=False):
        time.sleep(3)
        shot(out, "02-z6-progress")
    compare = qa.wait_for(rx(s["video_compare_title"]), 900, dismiss=False)
    if not compare:
        log("Z7 not shown: " + " | ".join(qa.texts()[:15]))
        log("video log: " + " | ".join(qa.adb("logcat", "-d", "SmartCleanerVideo:V", "TransformerInternal:V", "MediaCodec:E", "*:S").splitlines()[-30:])[:4000])
        return False
    shot(out, "03-z7-compare")
    play = qa.find(rx(s["video_play"].replace("%1$s", s["video_smaller"]))) or qa.find("^" + re.escape(s["video_play"].split("%")[0]) + ".*" + re.escape(s["video_smaller"]) + "$")
    if play:
        qa.tap(play)
        time.sleep(5)
    approve = qa.scroll_to(rx(s["video_approve"]), 4)
    if not approve:
        log("approve not found")
        return False
    shot(out, "04-z7-approve")
    qa.tap(approve)
    if not qa.wait_for(rx(s["video_result_title"], anchored=False), 30, dismiss=False):
        log("Z8 not shown: " + " | ".join(qa.texts()[:15]))
        return False
    shot(out, "05-z8-result")
    qa.tap_text(rx(s["video_done"]), 10)
    return True


def main():
    qa.adb("wait-for-device", timeout=300)
    qa.W, qa.H = qa.screen_size()
    qa.sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0")
    time.sleep(15)
    qa.sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    qa.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    demo_status_bar()
    ok = True
    for index, (listing, tag, vdir) in enumerate(LOCALES):
        log(f"=== {listing}")
        try:
            done = capture(listing, tag, vdir, index)
        except Exception as e:  # keep going: one language must not stop the rest
            log(f"{listing} failed: {e!r}")
            done = False
        ok = done and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
