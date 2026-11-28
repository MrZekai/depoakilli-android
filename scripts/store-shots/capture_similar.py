#!/usr/bin/env python3
"""
Faz 3B-1 on a real emulator (debug build, demo Pro: no Play products):
1. the device set (20 bursts / 20 blurry / 40 unrelated) -> groups from
   logcat, checked: no group mixes two subjects, >= 80 % of bursts found;
2. list + group screenshots in every app language;
3. one group to the app's Trash and back;
4. speed run over set + fillers (time, memory peak).
Usage: capture_similar.py <set dir> <fill dir> <out dir>
"""
import os, re, subprocess, sys, threading, time
import xml.etree.ElementTree as ET
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "device-qa"))
import run as qa  # noqa: E402

PKG = os.environ.get("SHOT_PACKAGE", "com.mrzekai.depoakilli.debug")
qa.PKG = PKG
SET, FILL, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
RES = "app/src/main/res"
LOCALES = [("en-US", "en", "values"), ("tr-TR", "tr", "values-tr"), ("es-ES", "es", "values-es"), ("pt-BR", "pt-BR", "values-b+pt+BR"),
           ("de-DE", "de", "values-de"), ("fr-FR", "fr", "values-fr"), ("it-IT", "it", "values-it"), ("ru-RU", "ru", "values-ru"),
           ("id", "id", "values-in"), ("ar", "ar", "values-ar")]
CAM = "/sdcard/DCIM/Camera"
results = []


def log(m):
    print(f"[similar] {m}", flush=True)


def check(name, ok, note=""):
    results.append((name, bool(ok)))
    log(("PASS " if ok else "FAIL ") + name + (f" — {note}" if note else ""))


def unescape(t):
    return (t or "").replace("\\'", "'").replace('\\"', '"').strip()


def strings(vdir):
    out = {}
    for d in ("values", vdir):
        for el in ET.parse(os.path.join(RES, d, "strings.xml")).getroot():
            if el.tag == "string" and el.text is not None:
                out[el.get("name")] = unescape(el.text)
    return out


def rx(text):
    return "^" + ".+".join(re.escape(p) for p in re.split(r"%\d*\$?[sd]", text)) + "$"


def shot(d, name):
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name + ".png"), "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def scan():
    qa.sh("content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1", timeout=300)


def clear_system_dialogs():
    """A slow emulator shows "Pixel Launcher isn't responding" over everything; not our app."""
    for _ in range(3):
        nodes = qa.dump()
        if not qa.find(r"isn.t responding|has stopped|keeps stopping", nodes):
            return
        btn = qa.find(r"^(Wait|Close app|OK)$", nodes)
        if btn:
            qa.tap(btn)
        else:
            qa.back()


def launch(locale_tag):
    qa.sh("settings put global hide_error_dialogs 1")
    clear_system_dialogs()
    qa.sh(f"am force-stop {PKG}")
    qa.sh(f"cmd locale set-app-locales {PKG} --locales {locale_tag}")
    qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity --es demo_pro on --es watch_target similar")
    time.sleep(3)
    clear_system_dialogs()


def wait_log(pattern, timeout):
    end = time.time() + timeout
    while time.time() < end:
        out = qa.adb("logcat", "-d", "-v", "raw", "SmartCleanerPhotos:W", "*:S")
        m = re.findall(pattern, out)
        if m:
            return out
        time.sleep(3)
    return None


def main():
    qa.adb("wait-for-device", timeout=300)
    qa.W, qa.H = qa.screen_size()
    time.sleep(10)
    qa.sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    qa.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    # 1. device set
    qa.sh(f"mkdir -p {CAM}")
    qa.adb("push", SET + "/.", CAM + "/", timeout=600)
    lines = open(os.path.join(SET, "manifest.txt")).read().split("\n")
    script = "; ".join(f"touch -m -d @{e} {CAM}/{n}" for n, e in (l.split() for l in lines if l.strip()))
    qa.sh(script, timeout=120)
    qa.sh(f"rm -f {CAM}/manifest.txt")
    scan()
    qa.adb("logcat", "-c")
    launch("en")
    out = wait_log(r"similar: looked=", 600)
    check("benzer: tarama bitti", out is not None)
    if out:
        summary = re.findall(r"similar: (.+)", out)[-1]
        log("run: " + summary)
        groups = [g.split(",") for g in re.findall(r"similar group: (.+)", out)]
        mixed = [g for g in groups if len({re.sub(r"_\d+\.jpg$", "", n) if n.startswith("IMG_B") else n for n in g}) > 1]
        found = {re.match(r"IMG_B(\d+)_", n).group(1) for g in groups for n in g if n.startswith("IMG_B")}
        check("benzer: iki farklı konu aynı gruba düşmedi", not mixed, note=str(mixed[:3]))
        check("benzer: seri çekimlerin en az %80'i bulundu", len(found) >= 16, note=f"{len(found)}/20 seri, {len(groups)} grup")
        check("benzer: bulanık/alakasız tekler grupta yok", not any(n.startswith(("IMG_X", "IMG_O")) for g in groups for n in g))
    # 2. screenshots in every language
    for listing, tag, vdir in ([] if os.environ.get("SPEED_ONLY") else LOCALES):
        s = strings(vdir)
        launch(tag)
        first = qa.wait_for(rx(s["similar_open_first"]), 120, dismiss=False) or qa.scroll_to(rx(s["similar_open_first"]), 6)
        d = os.path.join(OUT, listing)
        qa.sh("input swipe 500 600 500 1600 200")
        time.sleep(1)
        shot(d, "01-similar-list")
        first = qa.scroll_to(rx(s["similar_open_first"]), 6)
        if first:
            shot(d, "02-similar-list-bottom")
            qa.tap(first)
            time.sleep(3)
            shot(d, "03-similar-group")
        else:
            log(f"{listing}: list not shown: " + " | ".join(qa.texts()[:12]))
    # 3. one group to the Trash and back (English)
    s = strings("values")
    launch("en")
    first = qa.wait_for(r"^.+ · \d+ frames$", 120, dismiss=False)
    check("grup: bir grup kartı bulundu", first, note="" if first else " | ".join(qa.texts()[:15]))
    if first:
        qa.tap(first)
        time.sleep(2)
        before = qa.sh(f"ls {CAM} | wc -l").strip()
        move = qa.scroll_to(r"^Move \d+ frames? to Trash · .+$", 6)
        check("grup: 'Çöp'e taşı' düğmesi var", move)
        if move:
            qa.tap(move)
            time.sleep(4)
            after = qa.sh(f"ls {CAM} | wc -l").strip()
            check("grup: kareler Çöp'e taşındı (klasörde azaldı)", int(after) < int(before), note=f"{before} -> {after}")
            check("grup: en iyisi yerinde kaldı", int(after) > 0)
    # 4. speed run: set + fillers, fresh cache
    n_fill = len([f for f in os.listdir(FILL) if f.endswith(".jpg")])
    if n_fill:
        qa.sh(f"mkdir -p /sdcard/Pictures/Fill")
        qa.adb("push", FILL + "/.", "/sdcard/Pictures/Fill/", timeout=1800)
        scan()
        qa.sh(f"run-as {PKG} rm -f files/photo_features.bin")
        qa.adb("logcat", "-c")
        peak = [0]
        stop = [False]
        def sampler():
            while not stop[0]:
                m = re.search(r"TOTAL PSS:\s+(\d+)|TOTAL\s+(\d+)", qa.sh(f"dumpsys meminfo {PKG}", timeout=60))
                if m:
                    peak[0] = max(peak[0], int(m.group(1) or m.group(2)))
                time.sleep(2)
        th = threading.Thread(target=sampler, daemon=True); th.start()
        launch("en")
        out = None
        for minute in range(40):
            out = wait_log(r"similar: looked=", 60)
            if out:
                break
            pid = qa.sh(f"pidof {PKG}").strip()
            top = qa.sh("dumpsys activity activities | grep -m1 topResumedActivity").strip()
            tail = qa.adb("logcat", "-d", "-v", "brief", "SmartCleanerPhotos:W", "SmartCleaner:W", "AndroidRuntime:E", "ActivityManager:E", "*:S")[-300:]
            log(f"speed {minute + 1} min: pid={pid or '-'} top={top[-80:]} log={tail!r}")
            clear_system_dialogs()
        stop[0] = True
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(OUT, "speed-crash.txt"), "w") as f:
            f.write(qa.adb("logcat", "-d", "-v", "threadtime", "AndroidRuntime:E", "SmartCleanerPhotos:W", "*:S", timeout=120))
        check("hız: ilk tarama 40 dk içinde bitti", out is not None, note="" if out else qa.adb("logcat", "-d", "-v", "raw", "SmartCleanerPhotos:W", "*:S")[-600:])
        if out:
            line = re.findall(r"similar: (.+)", out)[-1]
            log(f"speed run ({n_fill} fillers + set, first run = newest 3000): {line}; memory peak PSS {peak[0] // 1024} MB")
            check("hız: ilk tarama bitti", True, note=line)
    ok = all(r for _, r in results)
    log(f"{sum(1 for _, r in results if r)}/{len(results)} PASS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
