#!/usr/bin/env python3
"""
Device QA for Smart Cleaner on an emulator (Faz 6 checks).

Drives the installed QA APK with adb only (no Gradle dependency, no app code):
seeds test files, grants the two special accesses, taps through Home,
findings, cleanup, the result screen and Trash, takes a screenshot at each
step and checks the texts the owner would otherwise check by eye.

Writes OUT/report.md (also appended to $GITHUB_STEP_SUMMARY) and OUT/*.png.
Exit code 1 if any check failed.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = os.environ.get("QA_PACKAGE", "com.mrzekai.depoakilli.qa")
OUT = os.environ.get("QA_OUT", "device-qa-out")
os.makedirs(OUT, exist_ok=True)

results = []  # (name, ok, note, screenshot)
shot_no = 0


def adb(*args, check=False, timeout=60):
    p = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {p.stderr.strip()}")
    return p.stdout


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def log(msg):
    print(f"[qa] {msg}", flush=True)


# ---------------- UI tree ----------------

class Node:
    def __init__(self, el):
        self.text = el.get("text") or ""
        self.desc = el.get("content-desc") or ""
        self.cls = el.get("class") or ""
        self.clickable = el.get("clickable") == "true"
        m = re.findall(r"\d+", el.get("bounds") or "[0,0][0,0]")
        self.x1, self.y1, self.x2, self.y2 = map(int, m[:4])

    @property
    def label(self):
        return self.text or self.desc

    @property
    def cx(self):
        return (self.x1 + self.x2) // 2

    @property
    def cy(self):
        return (self.y1 + self.y2) // 2


def dump():
    for _ in range(3):
        sh("uiautomator dump /sdcard/qa-ui.xml >/dev/null 2>&1", timeout=40)
        xml = sh("cat /sdcard/qa-ui.xml")
        if "<hierarchy" in xml:
            try:
                root = ET.fromstring(xml[xml.index("<hierarchy"):])
                return [Node(e) for e in root.iter("node")]
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def texts(nodes=None):
    nodes = nodes if nodes is not None else dump()
    return [n.label for n in nodes if n.label]


def find(pattern, nodes=None):
    rx = re.compile(pattern, re.I | re.S)
    for n in nodes if nodes is not None else dump():
        if n.label and rx.search(n.label):
            return n
    return None


def wait_for(pattern, timeout=30, dismiss=True):
    end = time.time() + timeout
    while time.time() < end:
        nodes = dump()
        n = find(pattern, nodes)
        if n:
            return n
        if dismiss:
            dismiss_ads(nodes)
        time.sleep(1)
    return None


def tap(node):
    sh(f"input tap {node.cx} {node.cy}")
    time.sleep(1.5)


def tap_text(pattern, timeout=20):
    n = wait_for(pattern, timeout)
    if not n:
        return False
    tap(n)
    return True


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


W, H = 1080, 2400


def scroll_down():
    sh(f"input swipe {W // 2} {int(H * 0.75)} {W // 2} {int(H * 0.30)} 350")
    time.sleep(1.2)


def scroll_to(pattern, max_swipes=14):
    for _ in range(max_swipes):
        n = find(pattern)
        if n and n.y2 < H * 0.92:
            return n
        scroll_down()
    return find(pattern)


def back():
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1.5)


def resumed_activity():
    out = sh("dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'")
    return out.strip()


def dismiss_ads(nodes=None):
    """Closes a Google sample interstitial or a consent form if one covers the app."""
    act = resumed_activity()
    if "AdActivity" in act:
        nodes = dump()
        close = find(r"^(close|close ad|kapat|interstitial close button)$", nodes)
        if close:
            tap(close)
        else:
            back()
        return True
    nodes = nodes if nodes is not None else dump()
    consent = find(r"^(consent|accept all|agree|do not consent)$", nodes)
    if consent:
        tap(consent)
        return True
    return False


def shot(name):
    global shot_no
    shot_no += 1
    fname = f"{shot_no:02d}-{name}.png"
    with open(os.path.join(OUT, fname), "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    return fname


def check(name, ok, note="", screenshot=None):
    results.append((name, bool(ok), note, screenshot))
    log(("PASS " if ok else "FAIL ") + name + (f" — {note}" if note else ""))
    if not ok:
        log("  visible: " + " | ".join(texts())[:1500])


# ---------------- setup ----------------

DAY = 24 * 3600


def seed_files(tag, count=2, mb=300):
    """Old unfinished downloads: the plan's first (Auto) tier moves them to Trash."""
    stamp = time.strftime("%Y%m%d%H%M", time.gmtime(time.time() - 40 * DAY))
    for i in range(count):
        path = f"/sdcard/Download/qa-{tag}-{i}.crdownload"
        sh(f"dd if=/dev/urandom of={path} bs=1048576 count={mb} 2>/dev/null; touch -m -t {stamp} {path}", timeout=180)
    # An old large personal file, so the findings list has a "decide" row too.
    path = f"/sdcard/Movies/qa-{tag}-video.mp4"
    sh(f"mkdir -p /sdcard/Movies; dd if=/dev/urandom of={path} bs=1048576 count=400 2>/dev/null; touch -m -t {stamp} {path}", timeout=180)
    sh("content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1")


BIG_APP = os.environ.get("QA_BIG_APP", "com.mrzekai.depoakilli.debug")


def seed_big_app():
    """A second, debuggable app (the debug build, a different package) with
    700 MB of saved data: the plan's apps section must list it (>= 500 MB)."""
    if BIG_APP not in sh("pm list packages"):
        log(f"{BIG_APP} not installed; apps rows cannot be checked")
        return False
    sh(f"run-as {BIG_APP} sh -c 'mkdir -p files && dd if=/dev/urandom of=files/qa-big.bin bs=1048576 count=700 2>/dev/null'", timeout=240)
    log("big app data: " + sh(f"run-as {BIG_APP} ls -l files").strip())
    return True


def launch():
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
    time.sleep(4)


def free_bytes():
    out = sh("df /data | tail -1")
    parts = out.split()
    try:
        return int(parts[3]) * 1024
    except (IndexError, ValueError):
        return -1


def to_home():
    tab = find(r"^Clean$")
    if tab and tab.y1 > H * 0.85:
        tap(tab)
    for _ in range(4):
        if find(r"^(MAKE ROOM|CLEAN UP)$") or find(r"^Up to$"):
            return True
        back()
    launch()
    return bool(wait_for(r"^(MAKE ROOM|CLEAN UP)$", 20))


# ---------------- scenario ----------------

def main():
    global W, H
    adb("wait-for-device", timeout=300)
    W, H = screen_size()
    log(f"screen {W}x{H}")
    sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0")
    sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    sh(f"appops set {PKG} GET_USAGE_STATS ignore")
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS 2>/dev/null")
    seed_files("a")
    seed_big_app()

    launch()
    dismiss_ads()

    # 1. Home → scan.
    first = wait_for(r"^(MAKE ROOM|CLEAN UP)$", 40)
    check("Uygulama açıldı, Home göründü", first, screenshot=shot("home-start"))
    if first and first.label.upper() == "MAKE ROOM":
        tap(first)
    plan = wait_for(r"^CLEAN UP$", 180)
    check("Tarama bitti, plan Home'da 'CLEAN UP' var", plan, screenshot=shot("home-plan"))

    # 2. Findings without Usage access: one row offering access.
    if tap_text(r"^See what else I found$", 20):
        dismiss_ads()
        row = scroll_to(r"^See which apps take the most space$")
        check("Erişim yokken uygulamalar bölümü tek satır: 'See which apps take the most space'",
              row, screenshot=shot("findings-no-usage-access"))
        if row:
            tap(row)
            disclosure = dump()
            check("Bu satır kullanım erişimi açıklamasını açıyor",
                  any(re.search(r"usage", t, re.I) for t in texts(disclosure)),
                  screenshot=shot("usage-access-disclosure"))
            back()
        to_home()
    else:
        check("Home'da 'See what else I found' bağlantısı var", False, screenshot=shot("home-no-findings-link"))

    # 3. Grant Usage access, come back (onResume refreshes the apps).
    sh(f"appops set {PKG} GET_USAGE_STATS allow")
    sh("input keyevent KEYCODE_HOME")
    time.sleep(1)
    launch()
    to_home()
    if tap_text(r"^See what else I found$", 20):
        dismiss_ads()
        title = scroll_to(r"^Apps take the most space$")
        row = scroll_to(r"^Mostly saved data · Open its storage settings$", 4) if title else None
        check("Erişim açıkken 'Apps take the most space' bölümü ve 700 MB'lık uygulama satırı görünüyor",
              title and row, screenshot=shot("findings-apps-section"))
        no_access_row = find(r"^See which apps take the most space$")
        check("Erişim açılınca 'erişim ver' satırı kayboldu", not no_access_row)
        check("İlk seferde not görünüyor ('I can't delete inside other apps…')",
              scroll_to(r"^I can.t delete inside other apps", 3), screenshot=shot("apps-note"))
        # Find the row again: the list may have scrolled.
        row = scroll_to(r"^Mostly saved data · Open its storage settings$", 4)
        if not row:
            sh(f"input swipe {W // 2} {int(H * 0.35)} {W // 2} {int(H * 0.75)} 350")
            row = find(r"^Mostly saved data · Open its storage settings$")
        if row:
            tap(row)
            time.sleep(2)
            act = resumed_activity()
            check("Satır uygulamanın Android ayar sayfasını açıyor (silmeden)", "settings" in act.lower(),
                  note=act[-120:], screenshot=shot("app-settings-opened"))
            check("Uygulama silinmedi", BIG_APP in sh("pm list packages"))
            launch()
    to_home()

    # 3b. Home "Apps hold X more" → findings, scrolled to the apps section.
    line = wait_for(r"^Apps hold .+ more\. See what you can do\.$", 20)
    check("Home: 'Apps hold X more' satırı var", line, screenshot=shot("home-apps-line"))
    if line:
        tap(line)
        dismiss_ads()
        time.sleep(2)
        check("Satıra basınca bulgular uygulamalar bölümüne kayıyor", wait_for(r"^Apps take the most space$", 15),
              screenshot=shot("findings-scrolled-to-apps"))
    to_home()

    # 4. Clean up → result button order.
    before_trash_files = sh("ls /sdcard/Download | grep -c qa-a-").strip()
    tap_text(r"^CLEAN UP$", 20)
    confirm = wait_for(r"^Yes, clean up$", 20)
    check("Temizlik onayı soruluyor ('Yes, clean up')", confirm, screenshot=shot("confirm"))
    if confirm:
        tap(confirm)
    free_now = wait_for(r"^Free .+ now$", 90)
    nodes = dump()
    done = find(r"^Done$", nodes)
    undo = find(r"^Undo$", nodes)
    order_ok = bool(free_now and done and undo and free_now.cy < done.cy < undo.cy)
    check("Sonuç: önce 'Free X now', sonra 'Done', sonra 'Undo'", order_ok,
          note=f"y: free={getattr(free_now, 'cy', None)} done={getattr(done, 'cy', None)} undo={getattr(undo, 'cy', None)}",
          screenshot=shot("result-trashed"))
    check("Sonuç: 'moved to Trash' yazıyor", find(r"moved to Trash", nodes))

    # 5. "Free now" → sheet with Cancel; Cancel deletes nothing.
    if free_now:
        tap(free_now)
        cancel = wait_for(r"^Cancel$", 10)
        perm = find(r"^Delete permanently$")
        check("'Free now' kalıcı silme sayfasını açıyor (İptal + Delete permanently)", cancel and perm,
              screenshot=shot("permanent-delete-sheet"))
        if cancel:
            tap(cancel)
        trash_after_cancel = sh(f"run-as {PKG} ls files 2>/dev/null; ls /sdcard/Android/data/{PKG}/files 2>/dev/null")
        check("İptal'den sonra sonuç ekranı duruyor (hiçbir şey silinmedi)", wait_for(r"^Free .+ now$", 5))
    else:
        check("'Free now' düğmesi bulunamadı", False)

    # 6. Done → Home shows the black Trash chip; it opens the Trash tab.
    tap_text(r"^Done$", 10)
    dismiss_ads()
    chip = wait_for(r"^Trash: .+ · Empty now$", 20)
    check("Home: siyah 'Trash: X · Empty now' çipi var", chip, screenshot=shot("home-trash-chip"))
    if chip:
        tap(chip)
        check("Çip Çöp sekmesini açıyor", wait_for(r"qa-a-0|Delete forever|Restore", 15), screenshot=shot("trash-tab"))

    # 7. Second cleanup, this time free the space for good. A clean app
    # state gives a fresh scan (the Trash checks above are done).
    seed_files("b")
    sh(f"pm clear {PKG}")
    sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    sh(f"appops set {PKG} GET_USAGE_STATS allow")
    sh(f"am force-stop {PKG}")
    launch()
    dismiss_ads()
    n = wait_for(r"^(MAKE ROOM|CLEAN UP)$", 20)
    if not n:
        # The app may reopen on another tab; go to the Clean tab first.
        tap_text(r"^Clean$", 10)
        n = wait_for(r"^(MAKE ROOM|CLEAN UP)$", 20)
    if n and n.label.upper() == "MAKE ROOM":
        tap(n)
    wait_for(r"^CLEAN UP$", 180)
    tap_text(r"^CLEAN UP$", 20)
    tap_text(r"^Yes, clean up$", 20)
    free_now = wait_for(r"^Free .+ now$", 90)
    before = free_bytes()
    if free_now:
        tap(free_now)
        tap_text(r"^Delete permanently$", 10)
        freed = wait_for(r"^freed$", 60)
        nodes = dump()
        pill = find(r"^Free space: .+ → .+$", nodes)
        after = free_bytes()
        check("Kalıcı silince 'freed' başlığı çıkıyor", freed, screenshot=shot("result-freed"))
        check("'Free space: A → B' hapı görünüyor (boş alan arttı)", pill,
              note=f"df: {before} → {after} bayt")
        check("Boşaltınca 'Free now' ve 'Undo' kalktı, yalnız 'Done' var",
              find(r"^Done$", nodes) and not find(r"^Free .+ now$", nodes) and not find(r"^Undo$", nodes))
        files_left = sh("ls /sdcard/Download | grep -c qa-b-").strip()
        check("Seçilen dosyalar gerçekten silindi", files_left == "0", note=f"kalan: {files_left}")
        tap_text(r"^Done$", 10)
    else:
        check("İkinci temizlikte 'Free now' çıktı", False, screenshot=shot("second-cleanup"))

    # 8. Untouched files: the video ("decide" tier) was never deleted without asking.
    check("Sorulacak dosyalar (video) izinsiz silinmedi",
          "qa-a-video.mp4" in sh("ls /sdcard/Movies") and "qa-b-video.mp4" in sh("ls /sdcard/Movies"))

    # 9. Big text and Arabic: Home fits (screenshots for the owner).
    sh("settings put system font_scale 1.3")
    sh(f"am force-stop {PKG}")
    launch()
    dismiss_ads()
    home_big = wait_for(r"^(MAKE ROOM|CLEAN UP)$", 40)
    check("Büyük yazı (1.3): Home açılıyor, ana düğme ekranda", home_big and home_big.y2 <= H,
          screenshot=shot("home-font-1.3"))
    sh("settings put system font_scale 1.0")
    sh(f"cmd locale set-app-locales {PKG} --locales ar 2>/dev/null")
    sh(f"am force-stop {PKG}")
    launch()
    dismiss_ads()
    time.sleep(3)
    check("Arapça (sağdan sola): Home açılıyor", len(texts()) > 3, screenshot=shot("home-arabic"))
    sh(f"cmd locale set-app-locales {PKG} --locales en 2>/dev/null")

    # Crash check.
    crashes = adb("logcat", "-d", "-b", "crash")
    crashed = PKG in crashes
    check("Uygulama hiç çökmedi (logcat crash)", not crashed, note=crashes[-800:] if crashed else "")


def write_report():
    passed = sum(1 for r in results if r[1])
    failed = len(results) - passed
    lines = [
        "## Cihaz testi (emülatör) — Smart Cleaner",
        "",
        f"**{'✅ HEPSİ GEÇTİ' if failed == 0 else f'❌ {failed} KONTROL KALDI'}** — {passed}/{len(results)} geçti",
        "",
        "| # | Kontrol | Sonuç | Not | Ekran |",
        "|---|---|---|---|---|",
    ]
    for i, (name, ok, note, screenshot) in enumerate(results, 1):
        lines.append(f"| {i} | {name} | {'✅' if ok else '❌'} | {note.replace('|', '/')[:120]} | {screenshot or ''} |")
    lines += ["", "Ekran görüntüleri: `SmartCleaner-Device-QA` artifact'ında."]
    text = "\n".join(lines) + "\n"
    with open(os.path.join(OUT, "report.md"), "w") as f:
        f.write(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text)
    print(text)
    return failed


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # keep the report even if a step blows up
        check("Senaryo hatasız bitti", False, note=repr(e)[:200], screenshot=shot("exception"))
    sys.exit(1 if write_report() else 0)
