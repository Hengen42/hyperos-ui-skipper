#!/system/bin/sh
# HyperOS UI Skipper - Installer Script
# Packages the WebUI/config layer and the Xposed hook APK, installing and
# configuring everything automatically during flash.

ui_print "****************************************"
ui_print "        HyperOS UI Skipper v1.0.3       "
ui_print "               by Hengen                "
ui_print "****************************************"

set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in action.sh uninstall.sh service.sh status.sh activate.sh; do
  [ -f "$MODPATH/$f" ] && set_perm "$MODPATH/$f" 0 0 0755
done

has_framework=0
if [ -d /data/adb/modules/zygisk_vector ]; then
  ui_print "- Found Vector framework (zygisk_vector)"
  has_framework=1
elif [ -d /data/adb/modules/zygisk_lsposed ] || [ -d /data/adb/lspd ]; then
  ui_print "- Found LSPosed framework"
  has_framework=1
else
  ui_print "! Warning: Vector or LSPosed was not detected."
  ui_print "  This module requires an Xposed framework to operate."
fi

APK="$MODPATH/HyperOS-UI-Skipper.apk"
TMP_APK=/data/local/tmp/HyperOS-UI-Skipper.apk
if [ -f "$APK" ]; then
  ui_print "- Installing hook APK (dev.hengen.uiskipper)..."
  cp "$APK" "$TMP_APK" && chmod 0644 "$TMP_APK"
  if OUT=$(pm install -r "$TMP_APK" 2>&1) && echo "$OUT" | grep -q Success; then
    ui_print "- Hook APK installed successfully."
  else
    ui_print "! APK installation failed: $OUT"
    ui_print "  You can reinstall it later using the WebUI button."
  fi
  rm -f "$TMP_APK"
else
  ui_print "! Error: HyperOS-UI-Skipper.apk missing inside zip."
fi

ui_print "- Target package: com.miui.securitycenter"
ui_print "- Default behavior: Skip Countdown Only"

if [ $has_framework -eq 1 ] && pm path dev.hengen.uiskipper >/dev/null 2>&1; then
  ui_print "- Activating in framework (scope: com.miui.securitycenter)..."
  if [ "$(sh "$MODPATH/activate.sh" | tail -n1)" = "ok" ]; then
    ui_print "- Activated successfully; no reboot required."
  else
    ui_print "! Auto-activation failed. Please enable dev.hengen.uiskipper"
    ui_print "  and select com.miui.securitycenter in your framework manager."
  fi
fi

# Make the module active immediately
LIVE=/data/adb/modules/hyperos_ui_skipper
mkdir -p "$LIVE" && cp -af "$MODPATH"/. "$LIVE"/ && {
  sh "$LIVE/status.sh" --prop >/dev/null 2>&1
  ui_print "- Module is ready to use immediately."
}
ui_print "- You can customize behavior anytime via the Module WebUI."
ui_print "****************************************"
