#!/usr/bin/env python3
"""
Faz 3B-2, 3C and Sales screens on a real emulator (debug build, demo plans):
blurry/dark one by one, photo types, personal plan (goal + plan), ready-made
filters, what I learned, paywall (S2) and the one-time upgrade offer (S4),
in every app language. Also checks the offer shows once only.
Usage: capture_assist.py <set dir> <out dir>
"""
import os, re, sys
import time
SET, OUT = sys.argv[1], sys.argv[2]
sys.argv = [sys.argv[0], SET, SET, OUT]  # capture_similar reads three arguments on import
sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "device-qa"))
import run as qa  # noqa: E402
import capture_similar as cs  # noqa: E402

PKG = cs.PKG
CAM = cs.CAM
results = []


def log(m):
    print(f"[assist] {m}", flush=True)


def check(name, ok, note=""):
    results.append((name, bool(ok)))
    log(("PASS " if ok else "FAIL ") + name + (f" — {note}" if note else ""))


def launch(tag, target, demo="on"):
    qa.sh(f"am force-stop {PKG}")
    qa.sh(f"cmd locale set-app-locales {PKG} --locales {tag}")
    qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity --es demo_pro {demo} --es watch_target {target}")
    time.sleep(4)


def find_any(s, keys, timeout=60, scroll=6):
    pats = "|".join(cs.rx(s[k]) for k in keys if k in s)
    n = qa.wait_for(pats, timeout, dismiss=False)
    return n or qa.scroll_to(pats, scroll)


def main():
    qa.adb("wait-for-device", timeout=300)
    qa.W, qa.H = qa.screen_size()
    time.sleep(10)
    qa.sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    qa.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    qa.sh(f"mkdir -p {CAM}")
    qa.adb("push", SET + "/.", CAM + "/", timeout=600)
    lines = open(os.path.join(SET, "manifest.txt")).read().split("\n")
    qa.sh("; ".join(f"touch -m -d @{e} {CAM}/{n}" for n, e in (l.split() for l in lines if l.strip())), timeout=120)
    qa.sh(f"rm -f {CAM}/manifest.txt")
    cs.scan()
    qa.adb("logcat", "-c")
    launch("en", "similar")
    out = cs.wait_log(r"similar: looked=", 900)
    check("tarama bitti", out is not None)
    if out:
        log("run: " + re.findall(r"similar: (.+)", out)[-1])
        log("all similar log: " + " | ".join(re.findall(r"similar[^\n]*", out)[-6:]))
    for listing, tag, vdir in cs.LOCALES:
        s = cs.strings(vdir)
        d = os.path.join(OUT, listing)
        # 3B-2: blurry/dark one by one
        launch(tag, "similar")
        bad = find_any(s, ["review_bad_title"], 120, 8)
        if bad:
            cs.shot(d, "01-review-entries")
            qa.tap(bad)
            time.sleep(3)
            cs.shot(d, "02-bad-one-by-one")
            if listing == "en-US":
                check("kötü kareler: tek tek ekranı açıldı", qa.find(cs.rx(s["review_keep"]) + "|" + cs.rx(s["review_trash"])))
        else:
            log(f"{listing}: no bad-shots card: " + " | ".join(qa.texts()[:15]))
            if listing == "en-US":
                check("kötü kareler: giriş kartı var", False)
        # 3B-2: photo types
        launch(tag, "similar")
        ty = find_any(s, ["types_entry"], 120, 8)
        if ty:
            qa.tap(ty)
            time.sleep(3)
            cs.shot(d, "03-photo-types")
            show = qa.scroll_to(cs.rx(s["types_show"]), 4)
            if show:
                qa.tap(show)
                time.sleep(3)
                cs.shot(d, "04-photo-types-one-by-one")
        else:
            log(f"{listing}: no types card: " + " | ".join(qa.texts()[:15]))
            if listing == "en-US":
                check("fotoğraf türleri: giriş kartı var", False)
        # 3C: plan goal + plan
        launch(tag, "debug_plan")
        goal = find_any(s, ["plan_question", "plan_build"], 90, 2)
        cs.shot(d, "05-plan-goal")
        build = qa.scroll_to(cs.rx(s["plan_build"]), 4)
        if build:
            qa.tap(build)
            time.sleep(4)
            cs.shot(d, "06-plan")
        if listing == "en-US":
            check("plan: hedef ekranı açıldı", goal)
            check("plan: plan kuruldu", build)
        # 3C: filters, learned
        launch(tag, "debug_filters")
        fl = find_any(s, ["filters_pick", "filters_label"], 60, 1)
        time.sleep(3)
        cs.shot(d, "07-filters")
        launch(tag, "debug_learned")
        le = find_any(s, ["learned_title", "learned_body"], 60, 1)
        cs.shot(d, "08-learned")
        if listing == "en-US":
            check("filtreler ekranı açıldı", fl)
            check("öğrendiklerim ekranı açıldı", le)
        # Sales: paywall (S2) and the one-time offer (S4), fresh free-tier flags
        qa.sh(f"am force-stop {PKG}")
        qa.sh(f"run-as {PKG} rm -f shared_prefs/pro_free_tier.xml")
        launch(tag, "debug_paywall", demo="plans")
        pw = find_any(s, ["paywall_plan_yearly", "paywall_title"], 60, 1)
        time.sleep(2)
        cs.shot(d, "09-paywall")
        other = qa.scroll_to(cs.rx(s["paywall_other_option"]), 4)
        if other:
            cs.shot(d, "10-paywall-bottom")
        close = qa.find("^" + re.escape(s["paywall_close"]) + "$")
        if close:
            qa.tap(close)
        else:
            qa.back()
        time.sleep(3)
        offer = qa.wait_for(cs.rx(s["offer_title"]), 15, dismiss=False)
        cs.shot(d, "11-leave-offer")
        if listing == "en-US":
            check("paywall: yıllık ve hafta planı görünüyor", pw)
            check("paywall: 'diğer seçenek' bağlantısı var", other)
            check("S4: ilk 'hayır'dan sonra teklif çıktı", offer)
            if offer:
                dec = qa.find("^" + re.escape(s["offer_decline"]) + "$")
                qa.tap(dec) if dec else qa.back()
                time.sleep(2)
                launch(tag, "debug_paywall", demo="plans")
                find_any(s, ["paywall_plan_yearly"], 30, 1)
                c2 = qa.find("^" + re.escape(s["paywall_close"]) + "$")
                qa.tap(c2) if c2 else qa.back()
                time.sleep(3)
                again = qa.wait_for(cs.rx(s["offer_title"]), 8, dismiss=False)
                check("S4: teklif ikinci kez çıkmadı", not again)
    log(f"{sum(1 for _, r in results if r)}/{len(results)} PASS")
    return 0 if all(r for _, r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
