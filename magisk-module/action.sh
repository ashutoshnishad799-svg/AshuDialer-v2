#!/system/bin/sh
##########################################################################
# Ashu Phone - Root Dialer Magisk/KernelSU module — Action Button
#
# Runs when the person taps "Action" next to this module in Magisk
# Manager or KernelSU Manager (both support this file the same way -
# it's not Magisk-specific despite the module using Magisk's directory
# layout). This does NOT run automatically at boot; it only runs when
# manually triggered from the manager app, on demand.
#
# WHAT THIS IS FOR: post-fs-data.sh already places the APK + permissions
# XML automatically at boot (with its own tmpfs-fallback logic - see the
# big comment block in that file for why). This script exists for the
# case where that already happened at boot, but something afterward
# (a reboot into a state where /system/priv-app briefly wasn't ready
# yet, a manual `pm uninstall`, clearing a broken tmpfs, etc.) means the
# live state doesn't match anymore RIGHT NOW, without requiring a full
# reboot to find out or to re-apply it.
#
# Deliberately read/report-first, re-apply second: this always prints
# the current live status before touching anything, so tapping Action
# is safe to run repeatedly just to check status - it does not
# unconditionally re-copy/re-mount every single time regardless of
# whether anything is actually missing.
##########################################################################

MODDIR="${0%/*}"
PRIVAPP_DIR="/system/priv-app"
TARGET_APK="$PRIVAPP_DIR/AshuDialer/AshuDialer.apk"
TARGET_PERMS="/system/etc/permissions/privapp-permissions-ashu-dialer.xml"

echo "===================================================="
echo " Ashu Phone - Root Dialer module: current status"
echo "===================================================="
echo ""

if [ -f "$TARGET_APK" ]; then
  echo "[OK] APK present at: $TARGET_APK"
else
  echo "[MISSING] APK not found at: $TARGET_APK"
fi

if [ -f "$TARGET_PERMS" ]; then
  echo "[OK] Permissions XML present at: $TARGET_PERMS"
  if grep -q "CAPTURE_AUDIO_OUTPUT" "$TARGET_PERMS" 2>/dev/null; then
    echo "[OK] CAPTURE_AUDIO_OUTPUT is listed in the permissions XML"
  else
    echo "[WARN] CAPTURE_AUDIO_OUTPUT not found in the permissions XML content"
  fi
else
  echo "[MISSING] Permissions XML not found at: $TARGET_PERMS"
fi

INSTALLED_PATH=$(pm path com.ashudialer.app 2>/dev/null | head -n1 | sed 's/^package://')
if [ -n "$INSTALLED_PATH" ]; then
  echo "[INFO] PackageManager reports Ashu Phone installed at: $INSTALLED_PATH"
  case "$INSTALLED_PATH" in
    *"/priv-app/"*) echo "[OK] That path is a priv-app path - correctly running as a system app." ;;
    *"/data/app/"*) echo "[WARN] That path is a regular /data/app install, NOT the priv-app copy. A normal install is shadowing the system one - uninstall it with: pm uninstall com.ashudialer.app" ;;
    *) echo "[INFO] Unrecognized path shape - check manually if unsure." ;;
  esac
else
  echo "[MISSING] PackageManager does not report Ashu Phone installed at all."
fi

# Same chained fallback as service.sh/the WebUI - cmd telecom
# get-default-dialer isn't consistently documented across Android
# versions, so this also tries dumpsys telecom's own line and a
# Settings.Secure read.
CURRENT_DIALER=$(cmd telecom get-default-dialer 2>/dev/null)
if [ -z "$CURRENT_DIALER" ]; then
  CURRENT_DIALER=$(dumpsys telecom 2>/dev/null | grep -m1 -i "Default Dialer" | sed 's/.*: *//')
fi
if [ -z "$CURRENT_DIALER" ]; then
  CURRENT_DIALER=$(settings get secure dialer_default_application 2>/dev/null)
fi
if [ "$CURRENT_DIALER" = "com.ashudialer.app" ]; then
  echo "[OK] Ashu Phone is the default dialer."
elif [ -n "$CURRENT_DIALER" ]; then
  echo "[INFO] Current default dialer is: $CURRENT_DIALER (not Ashu Phone)"
else
  echo "[INFO] Could not determine the current default dialer on this Android version."
fi

echo ""
echo "----------------------------------------------------"
echo " Re-applying module files now (safe to run anytime)"
echo "----------------------------------------------------"
sh "$MODDIR/post-fs-data.sh"
echo ""

# After re-applying files, immediately try to claim the Dialer role so the
# person does not have to wait for a reboot or get stuck on an absent role dialog.
if pm path com.ashudialer.app >/dev/null 2>&1; then
  if cmd telecom set-default-dialer com.ashudialer.app >/dev/null 2>&1 || \
     cmd role add-role-holder --user 0 android.app.role.DIALER com.ashudialer.app 0 >/dev/null 2>&1 || \
     cmd role add-role-holder android.app.role.DIALER com.ashudialer.app 0 >/dev/null 2>&1; then
    echo "[OK] Ashu Phone default-dialer role requested."
  else
    echo "[WARN] Could not claim Dialer role automatically; use Android Default apps once."
  fi
fi

echo "Done. If anything above was [MISSING] or [WARN] and is now fixed,"
echo "force-stop Ashu Phone (or reboot) and re-run the in-app 'Test now'"
echo "check under Settings -> Recording setup -> Root."
