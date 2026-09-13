#!/system/bin/sh
##########################################################################
# Ashu Phone - Root Dialer Magisk/KernelSU module — post-fs-data hook
#
# WHY THIS SCRIPT EXISTS (it used to be intentionally empty):
#
# This module relies on the root solution's own automatic module-mounting
# (Magic Mount / OverlayFS) to place system/priv-app/AshuDialer/AshuDialer.apk
# onto the live /system/priv-app/ path. That is genuinely how Magisk,
# official KernelSU, and most working setups behave.
#
# On some KernelSU-Next builds, though, that automatic mount does not
# reliably happen for every module even though the module's own files are
# staged correctly under /data/adb/modules/<id>/system/ - confirmed as a
# live, reproducible issue on this exact setup: manually verified that
# /system/priv-app/AshuDialer/ simply did not exist after a successful,
# error-free module install and reboot, while the module's own staged
# copy under /data/adb/modules/.../system/priv-app/AshuDialer/ was
# present and byte-correct. Not a corrupted APK, not a signing problem,
# not a PackageManager rejection - the file was never placed on the live
# path for PackageManagerService to scan in the first place.
#
# CORRECTION from an earlier version of this script: that version reached
# for `mkdir -p "$TARGET_DIR"` followed by a bind mount, reasoning that
# post-fs-data.sh runs early enough to modify things before Zygote/
# PackageManagerService starts. The *timing* reasoning was correct, but
# it missed a more basic constraint: /system is a genuinely read-only
# partition at this stage on modern Android (verified: even `mount -o
# rw,remount /system` fails outright on this class of device) - plain
# mkdir cannot create a new directory there no matter how early it runs,
# since that's a real write to a read-only block device, not something
# early-boot script timing alone can bypass. A bind mount can't substitute
# for the missing mkdir either: `mount --bind` requires its target
# directory to already exist - it does not create one, unlike Magic
# Mount's own overlay engine, which uses a different (kernel overlay/
# tmpfs-based) mechanism specifically so it CAN place a brand new
# directory under /system.
#
# The fix below needs only mkdir/mount/cp - no dependency on any root
# solution's own overlay engine, no image files or loop devices - by
# laying a tmpfs (a real, always-available writable RAM filesystem, not a
# write to the underlying partition) directly over /system/priv-app
# itself. A tmpfs mount point does not need to pre-exist as writable
# storage the way a bind-mount target does, since it isn't attaching to
# existing storage at all - it hands back a fresh empty writable
# filesystem for that mount point outright.
#
# Existing priv-apps must be preserved, not just this module's own app -
# otherwise every other priv-app on the device would vanish the moment
# the tmpfs covers the directory. That copy has to happen in two
# distinct steps, in this exact order:
#   1. Copy the existing priv-app folders OUT to a temp holding area
#      BEFORE the tmpfs mount - while /system/priv-app/ still refers to
#      the real, original contents.
#   2. Mount the tmpfs, then copy those saved folders (plus this
#      module's own AshuDialer folder) back IN.
# Doing the backup after the tmpfs mount cannot work: once the tmpfs
# covers /system/priv-app, that same path no longer reaches the original
# files at all - there would be nothing left to copy from.
##########################################################################

MODDIR="${0%/*}"
PRIVAPP_DIR="/system/priv-app"
TARGET_APK="$PRIVAPP_DIR/AshuDialer/AshuDialer.apk"
TARGET_PERMS="/system/etc/permissions/privapp-permissions-ashu-dialer.xml"
SOURCE_PERMS="$MODDIR/system/etc/permissions/privapp-permissions-ashu-dialer.xml"
BACKUP_DIR="/dev/ashudialer_privapp_backup_$$"

# WHY_CHECK_FIRST: a plain `-f` file-exists check, not a mount-table grep.
# What actually matters for PackageManagerService to see this app is
# whether the APK bytes are readable at this exact path when the system
# later scans /system/priv-app/ - not which specific mechanism (the
# platform's own automount, or this script's own tmpfs merge below) put
# them there. If the platform's own automount already worked (Magisk,
# official KernelSU, APatch, or a healthy KernelSU-Next), this check
# finds the file already in place and the entire block below is skipped
# - so this script can never conflict with or duplicate a working
# platform automount, and only ever activates as a fallback.
if [ ! -f "$TARGET_APK" ]; then
  # Step 1: back up whatever priv-apps already exist on THIS device,
  # while /system/priv-app/ still points at the real original contents.
  # /dev is used as the backup's temporary home because it's always a
  # writable tmpfs this early in boot, before /data is necessarily ready
  # for large temp files - and this backup only needs to survive the
  # next few lines of this same script, not across reboots.
  mkdir -p "$BACKUP_DIR"
  cp -a "$PRIVAPP_DIR"/. "$BACKUP_DIR"/ 2>/dev/null

  # Step 2: cover /system/priv-app with a fresh writable tmpfs, then
  # restore the backed-up original priv-apps into it, then add this
  # module's own app. From here on, /system/priv-app/ contains the same
  # priv-apps the device shipped with, plus AshuDialer.
  mount -t tmpfs -o mode=0755 tmpfs "$PRIVAPP_DIR"
  cp -a "$BACKUP_DIR"/. "$PRIVAPP_DIR"/ 2>/dev/null
  mkdir -p "$PRIVAPP_DIR/AshuDialer"
  cp -a "$MODDIR/system/priv-app/AshuDialer/AshuDialer.apk" "$PRIVAPP_DIR/AshuDialer/AshuDialer.apk"

  # A fresh tmpfs mount labels everything under it tmpfs_t by default,
  # not the system_file label real /system content has - SELinux (which
  # is enforcing on essentially every production device) would otherwise
  # block zygote/PackageManagerService from treating these as genuine
  # system files even though the bytes are now sitting at the right
  # path, silently reproducing the exact "file exists but the system
  # acts like it doesn't" symptom this whole script exists to fix.
  # restorecon reads the platform's own path-based file_contexts rules
  # (the same ones that give real /system/... content its system_file
  # label) and explicitly documents tmpfs among the pseudo-filesystems it
  # supports relabeling, so this brings every file under this tmpfs back
  # in line with what a real /system/priv-app/ entry would carry.
  restorecon -R "$PRIVAPP_DIR" 2>/dev/null

  rm -rf "$BACKUP_DIR"
fi

# Same underlying constraint as /system/priv-app/ above: this app's
# permissions XML doesn't exist yet under /system/etc/permissions/, and
# `touch` to create a brand new file there fails for the identical reason
# `mkdir` failed for AshuDialer's own directory - it's still a write to
# the same read-only partition, regardless of whether the write creates a
# file or a directory. An earlier version of this script used `touch`
# assuming the destination directory's mere existence was enough for a
# new file to be created inside it; that assumption doesn't hold on a
# read-only filesystem, since the *directory being writable* and the
# *directory merely existing* are different properties, and only the
# latter is true here. The fix follows the exact same two-step
# backup-then-tmpfs-then-restore shape used for /system/priv-app/ above:
# every existing file already under /system/etc/permissions/ is preserved
# by the same copy-out/copy-back sequence, and this app's XML is added
# alongside them.
if [ ! -f "$TARGET_PERMS" ]; then
  PERMS_DIR="/system/etc/permissions"
  PERMS_BACKUP_DIR="/dev/ashudialer_perms_backup_$$"

  mkdir -p "$PERMS_BACKUP_DIR"
  cp -a "$PERMS_DIR"/. "$PERMS_BACKUP_DIR"/ 2>/dev/null

  mount -t tmpfs -o mode=0755 tmpfs "$PERMS_DIR"
  cp -a "$PERMS_BACKUP_DIR"/. "$PERMS_DIR"/ 2>/dev/null
  cp -a "$SOURCE_PERMS" "$TARGET_PERMS"
  # Same SELinux relabeling reason as the priv-app tmpfs above.
  restorecon -R "$PERMS_DIR" 2>/dev/null

  rm -rf "$PERMS_BACKUP_DIR"
fi
