#!/bin/bash
# Home screen / app drawer screenshots of the installed QA build (icon + label), EN and TR.
set -x
OUT=icon-shots; mkdir -p $OUT
PKG=com.mrzekai.depoakilli.qa
adb install -r -g app/build/outputs/apk/qa/app-qa.apk
sleep 20
adb shell settings put global hide_error_dialogs 1
shot() { adb exec-out screencap -p > $OUT/$1.png; }
drawer() {
  adb shell input keyevent KEYCODE_HOME; sleep 3
  shot "$1-home"
  # open the all-apps drawer
  adb shell input swipe 540 2000 540 400 300; sleep 3
  shot "$1-drawer"
  # search the drawer so the icon is big and alone
  adb shell input text "Smart"; sleep 3
  shot "$1-search"
  adb shell input keyevent KEYCODE_HOME; sleep 2
}
drawer en
adb root; sleep 5
adb shell setprop persist.sys.locale tr-TR
adb shell stop; sleep 2; adb shell start
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 40
adb shell settings put global hide_error_dialogs 1
drawer tr
adb shell dumpsys package $PKG | grep -E "versionName|versionCode" | head -2 > $OUT/version.txt
cat $OUT/version.txt
