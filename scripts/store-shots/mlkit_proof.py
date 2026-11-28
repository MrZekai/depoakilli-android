#!/usr/bin/env python3
"""
Fable review, item 1: with INTERNET removed, does Google's usage-log uploader
(datatransport) crash the app? On a Play Store emulator image with network:
run the photo scan (MediaPipe + ML Kit face/labels from Play services), wait
for ML Kit's models, then force-run every datatransport job of the app and
look for a crash. Writes proof.txt. Usage: mlkit_proof.py <set dir> <out dir> <label>
"""
import os, re, sys, time
SET, OUT, LABEL = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], SET, SET, OUT]
sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "device-qa"))
import run as qa  # noqa: E402
import capture_similar as cs  # noqa: E402

PKG = cs.PKG
lines = []


def note(m):
    print(f"[proof] {m}", flush=True)
    lines.append(m)


def logcat(*tags):
    spec = [f"{t}:V" for t in tags] + ["*:S"]
    return qa.adb("logcat", "-d", "-v", "time", *spec, timeout=120)


def fatal():
    out = qa.adb("logcat", "-d", "-v", "time", "AndroidRuntime:E", "*:S", timeout=60)
    return [l for l in out.splitlines() if PKG in l or "FATAL" in l or "Exception" in l][:40]


def scan_once(tag):
    qa.adb("logcat", "-c")
    qa.sh(f"am force-stop {PKG}")
    qa.sh(f"am start -n {PKG}/com.mrzekai.depoakilli.MainActivity --es demo_pro on --es watch_target similar")
    time.sleep(3)
    cs.clear_system_dialogs()
    out = cs.wait_log(r"similar: looked=", 900)
    note(f"{tag}: scan " + (re.findall(r"similar: (.+)", out)[-1] if out else "did not finish"))
    s = cs.strings("values")
    card = qa.wait_for(cs.rx(s["types_entry"]), 30, dismiss=False) or qa.scroll_to(cs.rx(s["types_entry"]), 14)
    note(f"{tag}: photo types card shown: {bool(card)}")
    ml = logcat("MlKitContext", "DynamiteModule", "ModuleInstall", "MLKit", "FaceDetector", "ImageLabeler",
                "OptionalModuleUtils", "TransportRuntime", "Uploader", "SmartCleanerPhotos")
    for l in ml.splitlines()[-40:]:
        note(f"{tag} log: {l[:220]}")
    return bool(card)


def main():
    qa.adb("wait-for-device", timeout=300)
    qa.W, qa.H = qa.screen_size()
    time.sleep(15)
    note(f"build: {LABEL}")
    note("image: " + qa.sh("getprop ro.build.fingerprint").strip())
    note("Play Store: " + ("yes" if "com.android.vending" in qa.sh("pm list packages com.android.vending") else "no"))
    note("Play services: " + qa.sh("dumpsys package com.google.android.gms | grep -m1 versionName").strip())
    note("INTERNET granted: " + str("android.permission.INTERNET: granted=true" in qa.sh(f"dumpsys package {PKG}")))
    qa.sh("settings put global hide_error_dialogs 1")
    qa.sh(f"appops set {PKG} MANAGE_EXTERNAL_STORAGE allow")
    qa.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    qa.sh(f"mkdir -p {cs.CAM}")
    qa.adb("push", SET + "/.", cs.CAM + "/", timeout=600)
    mf = [l.split() for l in open(os.path.join(SET, "manifest.txt")).read().split("\n") if l.strip()]
    qa.sh("; ".join(f"touch -m -d @{e} {cs.CAM}/{n}" for n, e in mf), timeout=120)
    qa.sh(f"rm -f {cs.CAM}/manifest.txt")
    cs.scan()
    card = scan_once("run 1")
    # Play services may fetch the models after the first call: wait and run again.
    for i in range(4):
        if card:
            break
        time.sleep(150)
        card = scan_once(f"run {i + 2}")
    # Force the uploader: every datatransport job of the app, now.
    jobs = qa.sh(f"dumpsys jobscheduler {PKG}")
    ids = sorted(set(re.findall(r"JOB #u\d+a\d+/(\d+): \S+ " + re.escape(PKG) + r"/com\.google\.android\.datatransport", jobs)))
    note(f"datatransport jobs: {ids or 'none'}")
    qa.adb("logcat", "-c")
    for j in ids:
        note(f"run job {j}: " + qa.sh(f"cmd jobscheduler run -f {PKG} {j}").strip()[:120])
    time.sleep(45)
    dt = logcat("TransportRuntime", "Uploader", "JobInfoScheduler", "AlarmManagerScheduler", "CctTransportBackend", "TRuntime.Uploader", "TRuntime.CctTransportBackend")
    for l in dt.splitlines()[-30:]:
        note(f"after jobs: {l[:220]}")
    crash = fatal()
    note(f"crash lines after forcing the uploader: {len(crash)}")
    for l in crash:
        note(f"CRASH: {l[:240]}")
    note("RESULT: " + ("CRASH" if crash else "no crash") + f"; types card: {card}")
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, f"proof-{LABEL}.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
        f.write("\n--- full datatransport log ---\n" + dt)
    return 0


if __name__ == "__main__":
    sys.exit(main())
