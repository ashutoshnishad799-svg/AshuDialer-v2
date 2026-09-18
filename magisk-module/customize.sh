##########################################################################
# Ashu Phone - Root Dialer Magisk Module
#
# This script deliberately does almost nothing beyond printing status
# messages and one safety check. Magisk's own installer already handles
# safely staging this module's system/ folder via systemless Magic Mount
# before this script runs - nothing here writes to the real /system
# partition, deletes anything, or runs any command that could brick a
# device. If Magisk itself is healthy enough to run this script at all,
# this module cannot make the device unbootable: the worst case is Ashu
# Phone not appearing as a system app, which is fixed by simply
# disabling/removing the module from Magisk's app.
##########################################################################

ui_print "- Ashu Phone Root Dialer module"
ui_print "- Installing as a privileged system app (systemless, via Magisk)"

# Basic sanity check: bail out cleanly (not destructively) on very old
# Magisk where privapp-permissions handling may behave differently, rather
# than proceeding into an unverified state.
if [ "$MAGISK_VER_CODE" -lt 20400 ]; then
  ui_print "! Magisk is older than recommended (v20.4+) for this module."
  ui_print "! Installation will continue, but please update Magisk if you hit issues."
fi

# Android will not run two installed copies of the same applicationId at
# once. If a normal (data/app) copy of Ashu Phone - from the Play Store, a
# direct APK download, or a previous non-root install - is still present,
# it and this module's system/priv-app copy silently fight over the same
# package name at boot. Depending on install order this usually just means
# "the priv-app copy never actually takes effect and CAPTURE_AUDIO_OUTPUT
# stays unavailable", not a crash - but it's the single most common reason
# someone flashes this, reboots, and Root setup -> Check still says
# "Not set up". Warning about it here, before the reboot, is much more
# useful than a silent no-op.
EXISTING_PATH=$(pm path com.ashudialer.app 2>/dev/null | head -n1)
if [ -n "$EXISTING_PATH" ]; then
  case "$EXISTING_PATH" in
    *"/data/app/"*)
      ui_print "! A regular (non-system) copy of Ashu Phone is already installed."
      ui_print "! Uninstall it BEFORE rebooting, or this module's system copy"
      ui_print "! may not take effect. Use: adb uninstall com.ashudialer.app"
      ui_print "! (or Settings -> Apps -> Ashu Phone -> Uninstall), then reboot."
      ;;
    *)
      ui_print "- Existing install detected at: $EXISTING_PATH"
      ;;
  esac
else
  ui_print "- No existing Ashu Phone install detected. Good - clean install path."
fi

ui_print "- Files staged. A reboot is required to finish activation."
ui_print "- After rebooting: open Ashu Phone -> Settings -> Recording setup -> Root -> Check"
