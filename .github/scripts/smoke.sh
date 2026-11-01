#!/usr/bin/env bash
# Emulator smoke test for the post-sign-in path. Debug builds only (needs run-as).
# A placeholder session is written into the app's own prefs with `run-as`, the way a signed-in
# user's phone looks after login; nothing in the app is changed or bypassed. Firestore calls then
# fail with 401/403, which exercises the empty / failed-load states a brand-new user sees.
set -u
APK="$1"; OUT="$2"
PKG=quest.srihari.medxelite
mkdir -p "$OUT"
adb logcat -c || true
# Emulator system apps (Pixel Launcher) often raise ANR dialogs on slow CI machines; they sit
# on top of the app and swallow taps. Hide them, and dismiss any that still appear.
adb shell settings put global hide_error_dialogs 1 || true
adb install -r -t "$APK"

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  local b
  b=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re,sys
label,f=sys.argv[1],sys.argv[2]
try: x=open(f,encoding='utf-8').read()
except Exception: sys.exit(0)
for m in re.finditer(r'<node [^>]*>', x):
    n=m.group(0)
    if f'text="{label}"' in n or f'content-desc="{label}"' in n:
        a=list(map(int,re.findall(r'\d+',re.search(r'bounds="([^"]+)"',n).group(1))))
        print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
)
  if [ -n "$b" ]; then adb shell input tap $b; echo "tapped $1 at $b"; else echo "no node for $1"; fi
}

alive() { adb shell pidof $PKG >/dev/null 2>&1; }

# Tap "Wait" on a system "isn't responding" dialog if one is showing (never on our app's own UI).
dismiss_anr() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  if grep -q "isn't responding" "$OUT/ui.xml" 2>/dev/null; then echo "system ANR dialog showing; dismissing"; tap_text "Wait"; sleep 2; fi
}

# 1) Cold launch signed out (sign-in screen).
adb shell am start -W -n $PKG/.MainActivity
sleep 6
adb exec-out screencap -p > "$OUT/0-signin.png"
adb shell am force-stop $PKG

# 2) Signed-in launch with a brand-new user's empty state.
FUTURE=$(( $(date +%s) * 1000 + 30*24*3600*1000 ))
JSON="{&quot;idToken&quot;:&quot;ci-token&quot;,&quot;refreshToken&quot;:&quot;ci-refresh&quot;,&quot;uid&quot;:&quot;ci-smoke-user&quot;,&quot;email&quot;:&quot;ci.smoke@example.com&quot;,&quot;displayName&quot;:null,&quot;photoUrl&quot;:null,&quot;provider&quot;:&quot;password&quot;,&quot;expiresAtMs&quot;:$FUTURE}"
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"session\">%s</string>\n</map>\n" "$JSON" > "$OUT/medx.auth.xml"
adb push "$OUT/medx.auth.xml" /data/local/tmp/medx.auth.xml
adb shell chmod 644 /data/local/tmp/medx.auth.xml
adb shell run-as $PKG mkdir -p shared_prefs
adb shell run-as $PKG cp /data/local/tmp/medx.auth.xml shared_prefs/medx.auth.xml
adb shell run-as $PKG ls -l shared_prefs

adb shell am start -W -n $PKG/.MainActivity
sleep 12
dismiss_anr
adb exec-out screencap -p > "$OUT/1-home.png"
alive && echo "ALIVE after home" || echo "DEAD after home"
i=2
for t in QBank Tests Classes Library Home; do
  dismiss_anr
  tap_text "$t"; sleep 6
  adb exec-out screencap -p > "$OUT/$i-$t.png"; i=$((i+1))
  alive && echo "ALIVE after $t" || echo "DEAD after $t"
done
# Settings via the avatar is not labelled; open it by route-free means: skip.

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/logcat-crash.txt" || true
grep -n -A60 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/fatal.txt" || true
if [ -s "$OUT/fatal.txt" ]; then echo "=== FATAL ==="; head -120 "$OUT/fatal.txt"; else echo "No FATAL EXCEPTION in logcat"; fi
exit 0
