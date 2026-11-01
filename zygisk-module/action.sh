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
  echo "HyperOS UI Skipper: Ayar kaydedilemedi."
fi

am force-stop com.miui.securitycenter 2>/dev/null
echo "com.miui.securitycenter yeniden başlatıldı."

if [ "$NEW" = "2" ]; then
  echo "Seçenek: Tam Otomatik Onayla (Anında)"
else
  echo "Seçenek: Sadece Sayacı Atla (Önerilen)"
fi

STATUS=$(sh "$MODDIR/status.sh" --prop 2>/dev/null)
case "$STATUS" in
  *state=not_enabled*|*state=no_scope*)
    echo "Çerçevede otomatik etkinleştiriliyor..."
    sh "$MODDIR/activate.sh" >/dev/null 2>&1
    STATUS=$(sh "$MODDIR/status.sh" --prop 2>/dev/null)
    ;;
esac
case "$STATUS" in
  *state=ok*) echo "✅ Kanca başarıyla devrede." ;;
  *state=no_vector*) echo "❌ Vector veya LSPosed modülü aktif değil." ;;
  *state=no_daemon*) echo "❌ Çerçeve servisi çalışmıyor; cihazı yeniden başlatın." ;;
  *state=no_apk*) echo "❌ Kanca APK'sı kurulu değil; WebUI'den kurun." ;;
  *state=not_enabled*) echo "❌ Modül onay bekliyor; etkinleştirip cihazı yeniden başlatın." ;;
  *state=no_scope*) echo "❌ com.miui.securitycenter kapsamı eksik." ;;
esac
exit 0
