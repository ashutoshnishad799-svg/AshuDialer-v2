#!/system/bin/sh
##########################################################################
# Ashu Phone - Root Dialer Magisk/KernelSU module — service.sh
#
# Unlike post-fs-data.sh (which runs very early in boot, before most
# system services including Telecom itself are up - too early to touch
# telecom at all), service.sh runs in Magisk's/KernelSU's "late_start"
# stage, after boot has essentially finished and normal system services
# are already running. That's specifically why this script exists
# separately rather than folding this into post-fs-data.sh: the
# `cmd telecom` shell command this relies on genuinely does not exist
# as a working target until Telecom's service is up, which is not the
# case yet when post-fs-data.sh runs.
#
# WHAT THIS ACTUALLY DOES: silently sets Ashu Phone as the default
# dialer via `cmd telecom set-default-dialer`, with no RoleManager
# dialog, no user tap, nothing visible at all - this is the shell-level
# equivalent of what RoleManager's dialog does after the person taps
# "Allow", made possible only because root/shell already carries the
# system trust an ordinary app process does not. This is NOT something
# an app itself (even a priv-app) can do on its own via public API -
# Android deliberately requires either a signed user consent dialog
# (RoleManager) or shell-level trust (this) for changing the default
# dialer, specifically so a malicious app can never silently reroute
# someone's calls. Root access is exactly that shell-level trust, which
# is why this is safe to do unattended here but is not something the
# app's own Kotlin code is allowed to do.
#
# Deliberately conditional: this only runs the command if Ashu Phone
# isn't ALREADY the default dialer, so a person who has intentionally
# picked a different default dialer app since installing this module
# does not get silently overridden back to Ashu Phone on every single
# reboot - that would be a genuinely hostile thing for a "flash and
# forget" module to do. It also only proceeds if the priv-app APK
# actually placed correctly (mirrors the same check action.sh reports),
# since attempting to set a not-actually-installed package as default
# dialer would just silently fail anyway.
##########################################################################

# Telecom (and Package Manager) aren't guaranteed to be immediately
# ready the instant service.sh starts, even at late_start - waiting
# briefly and retrying is more reliable than a single immediate attempt.
MAX_ATTEMPTS=15
ATTEMPT=0

while [ "$ATTEMPT" -lt "$MAX_ATTEMPTS" ]; do
  # Chained fallback, same reasoning as the module's WebUI: `cmd telecom
  # get-default-dialer` isn't consistently documented across Android/
  # Telecom versions, so this also tries dumpsys telecom's own "Default
  # Dialer" line and a Settings.Secure read - whichever actually returns
  # something on this device's Android version is used.
  CURRENT_DIALER=$(cmd telecom get-default-dialer 2>/dev/null)
  if [ -z "$CURRENT_DIALER" ]; then
    CURRENT_DIALER=$(dumpsys telecom 2>/dev/null | grep -m1 -i "Default Dialer" | sed 's/.*: *//')
  fi
  if [ -z "$CURRENT_DIALER" ]; then
    CURRENT_DIALER=$(settings get secure dialer_default_application 2>/dev/null)
  fi
  if [ -n "$CURRENT_DIALER" ]; then
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  sleep 2
done

if [ "$CURRENT_DIALER" = "com.ashudialer.app" ]; then
  log -t AshuPhoneModule "Ashu Phone is already the default dialer - nothing to do."
  exit 0
fi

INSTALLED_PATH=$(pm path com.ashudialer.app 2>/dev/null)
if [ -z "$INSTALLED_PATH" ]; then
  log -t AshuPhoneModule "Ashu Phone is not installed yet - skipping auto-default-dialer."
  exit 0
fi

case "$INSTALLED_PATH" in
  *"/priv-app/"*) ;;
  *)
    log -t AshuPhoneModule "Ashu Phone is installed but not as a priv-app yet - skipping auto-default-dialer this boot."
    exit 0
    ;;
esac

set_default_dialer() {
  cmd telecom set-default-dialer com.ashudialer.app >/dev/null 2>&1 && return 0
  cmd role add-role-holder --user 0 android.app.role.DIALER com.ashudialer.app 0 >/dev/null 2>&1 && return 0
  cmd role add-role-holder android.app.role.DIALER com.ashudialer.app 0 >/dev/null 2>&1 && return 0
  return 1
}

if set_default_dialer; then
  log -t AshuPhoneModule "Ashu Phone set as default dialer automatically."
else
  log -t AshuPhoneModule "Automatic default-dialer assignment failed; the in-app default-app flow will be used."
fi

##########################################################################
# NOTE on VOICE_CALL/VOICE_UPLINK/VOICE_DOWNLINK access:
#
# An earlier version of this script set several `appops set` ops
# (PROJECT_MEDIA, MIUI_RECORD_AUDIO, etc.) and two `setprop` properties
# (persist.vendor.audio.calldebug.enable, persist.sys.miui.call_
# recording_allowed) here, on the theory that MIUI needed extra shell-
# level overrides beyond the standard priv-app permission grant.
#
# That theory did not hold up against real working call recorders'
# actual source. Both BCR and SKVALEX (two independently maintained,
# widely-used rooted call recorders, including on MIUI/HyperOS devices)
# were checked directly - neither module's post-fs-data.sh or service.sh
# does anything beyond installing the priv-app and its permissions XML.
# SKVALEX's own post-fs-data.sh/service.sh/system.prop files are the
# unmodified empty Magisk module template. Their READMEs confirm
# CAPTURE_AUDIO_OUTPUT (the standard AOSP priv-app permission, granted
# via privapp-permissions-ashu-dialer.xml, same as this module already
# does) is what gates VOICE_CALL/VOICE_UPLINK/VOICE_DOWNLINK - nothing
# else. The op names and property names in the earlier version of this
# script were not confirmed against any real source; they were a guess,
# and have been removed rather than left in as dead weight that does
# nothing but look like a fix.
#
# If VOICE_CALL and friends still aren't available after this module is
# installed and the device has rebooted, the real fix is making sure
# CAPTURE_AUDIO_OUTPUT is actually present in the app's own
# AndroidManifest.xml <uses-permission> list (an allowlist entry here
# grants nothing to an app that never requested the permission) and that
# CAPTURE_AUDIO_OUTPUT is the real gate - not a shell-level workaround.
##########################################################################

