#!/system/bin/sh
# Action button: toggle Mode 1 <-> Mode 2, restart SecurityCenter so the new
# mode applies, and refresh the status shown in the module manager.
MODDIR=/data/adb/modules/hyperos_ui_skipper

MODE=$(settings get global hyperos_uiskipper_mode 2>/dev/null)
case "$MODE" in
  2) NEW=1 ;;
  *) NEW=2 ;;
esac

if ! settings put global hyperos_uiskipper_mode "$NEW"; then
  echo "HyperOS UI Skipper: Failed to save setting."
fi

am force-stop com.miui.securitycenter 2>/dev/null
echo "com.miui.securitycenter restarted."

if [ "$NEW" = "2" ]; then
  echo "Mode: Full Automatic (Instant)"
else
  echo "Mode: Skip Countdown Only (Recommended)"
fi

STATUS=$(sh "$MODDIR/status.sh" --prop 2>/dev/null)
case "$STATUS" in
  *state=not_enabled*|*state=no_scope*)
    echo "Activating automatically in framework..."
    sh "$MODDIR/activate.sh" >/dev/null 2>&1
    STATUS=$(sh "$MODDIR/status.sh" --prop 2>/dev/null)
    ;;
esac
case "$STATUS" in
  *state=ok*) echo "✅ Hook successfully active." ;;
  *state=no_vector*) echo "❌ Vector or LSPosed module is not active." ;;
  *state=no_daemon*) echo "❌ Framework service is not running; reboot device." ;;
  *state=no_apk*) echo "❌ Hook APK is not installed; install via WebUI." ;;
  *state=not_enabled*) echo "❌ Module is pending approval; enable and reboot device." ;;
  *state=no_scope*) echo "❌ com.miui.securitycenter scope is missing." ;;
esac
exit 0
