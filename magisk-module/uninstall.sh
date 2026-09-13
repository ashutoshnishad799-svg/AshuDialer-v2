#!/system/bin/sh
##########################################################################
# Ashu Phone - Root Dialer Magisk Module — uninstall hook
#
# Magisk automatically un-mounts everything this module placed under
# system/ once the module is removed - this script does not need to (and
# must not) manually rm anything under /system itself, since that's real
# partition content on non-systemless setups and Magisk's own systemless
# unmount already handles the systemless case cleanly. This file exists
# only so Magisk has an explicit, intentional uninstall hook on record
# instead of relying purely on implicit default behavior; it is
# deliberately a no-op beyond the status message below, for exactly the
# same "cannot brick the device" reasoning as post-fs-data.sh.
##########################################################################

ui_print "- Removing Ashu Phone Root Dialer module"
ui_print "- The priv-app copy will be gone after this reboot."
ui_print "- If you also want to use Ashu Phone without root, reinstall the"
ui_print "  normal APK from the Play Store or GitHub release page."
