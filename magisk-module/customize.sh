ui_print "- Installing Ashu Dialer System Permissions"
ui_print "- Setting up Priv-App Environment"

# Give permissions to the priv-app XML so the OS reads it correctly
set_perm_recursive $MODPATH/system/etc/permissions 0 0 0755 0644
