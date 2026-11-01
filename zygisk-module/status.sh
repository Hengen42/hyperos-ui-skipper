#!/system/bin/sh
# Prints key=value health lines for the module, the Xposed framework (Vector/LSPosed)
# and the hook APK. Used by the WebUI, action.sh and service.sh.
MODDIR=/data/adb/modules/hyperos_ui_skipper
PKG=dev.hengen.uiskipper
TARGET=com.miui.securitycenter

framework_name="None"
cli_path=""
if [ -d /data/adb/modules/zygisk_vector ] && [ ! -f /data/adb/modules/zygisk_vector/disable ] && [ ! -f /data/adb/modules/zygisk_vector/remove ]; then
  framework_name="Vector"
  cli_path="/data/adb/modules/zygisk_vector/cli"
elif [ -d /data/adb/modules/zygisk_lsposed ] && [ ! -f /data/adb/modules/zygisk_lsposed/disable ] && [ ! -f /data/adb/modules/zygisk_lsposed/remove ]; then
  framework_name="LSPosed"
  [ -f /data/adb/modules/zygisk_lsposed/cli ] && cli_path="/data/adb/modules/zygisk_lsposed/cli"
  [ -z "$cli_path" ] && [ -f /data/adb/lspd/cli ] && cli_path="/data/adb/lspd/cli"
elif [ -f /data/adb/lspd/cli ]; then
  framework_name="LSPosed"
  cli_path="/data/adb/lspd/cli"
fi

framework_module=0
[ "$framework_name" != "None" ] && framework_module=1

framework_daemon=0
if pidof vectord >/dev/null 2>&1; then
  framework_daemon=1
  [ "$framework_name" = "None" ] && framework_name="Vector"
elif pidof lspd >/dev/null 2>&1; then
  framework_daemon=1
  [ "$framework_name" = "None" ] && framework_name="LSPosed"
fi

apk=0
pm path "$PKG" 2>/dev/null | grep -q '^package:' && apk=1

# Framework CLI is a Java process. Skipped with --fast for instant UI response.
xcli() {
  if [ -z "$cli_path" ] || [ ! -f "$cli_path" ]; then
    return 1
  fi
  # Borrow complete Android runtime env from zygote if missing
  if [ -z "$BOOTCLASSPATH" ] || [ -z "$ANDROID_I18N_ROOT" ]; then
    ZPID=$(pidof zygote64 zygote | cut -d' ' -f1)
    if [ -n "$ZPID" ] && [ -r "/proc/$ZPID/environ" ]; then
      for kv in $(tr '\0' '\n' < "/proc/$ZPID/environ" | grep -E '^[A-Z_0-9]+=' | grep -vE '^(PATH|HOME|PWD|SHLVL|_)='); do
        export "$kv" 2>/dev/null
      done
    fi
  fi
  # Safe fallback if environ was blocked by SELinux
  if [ -z "$BOOTCLASSPATH" ]; then
    export ANDROID_ROOT="/system"
    export ANDROID_DATA="/data"
    export ANDROID_RUNTIME_ROOT="/apex/com.android.runtime"
    export ANDROID_TZDATA_ROOT="/apex/com.android.tzdata"
    export ANDROID_I18N_ROOT="/apex/com.android.i18n"
    export ANDROID_ART_ROOT="/apex/com.android.art"
    export BOOTCLASSPATH="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/system/framework/framework.jar:/system/framework/framework-graphics.jar:/system/framework/ext.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/ims-common.jar"
  fi
  timeout 15 sh "$cli_path" "$@" 2>/dev/null
}

enabled=unknown
scope=unknown
hook=unknown
if [ "$1" != "--fast" ] && [ "$framework_daemon" = 1 ] && [ "$apk" = 1 ]; then
  enabled=0
  scope=0
  if [ -n "$cli_path" ]; then
    xcli modules ls | grep -F "$PKG" | grep -q ' enabled' && enabled=1
    if [ "$enabled" = 1 ]; then
      xcli scope ls "$PKG" | grep -q "$TARGET" && scope=1
    fi
  fi
fi

# Check framework logs for successful hook attachment
if [ "$enabled" = 1 ]; then
  hook=0
  LOG=$(ls -t /data/adb/lspd/log/modules_*.log /data/adb/vector/log/modules_*.log 2>/dev/null | head -n1)
  if [ -n "$LOG" ] && grep -q "intercept timer hooked successfully" "$LOG" 2>/dev/null; then
    hook=1
  fi
fi

mode=$(settings get global hyperos_uiskipper_mode 2>/dev/null)
case "$mode" in 1|2) ;; *) mode=1 ;; esac

if [ "$mode" = "2" ]; then
  mode_desc="Tam Otomatik"
  mode_desc_en="Full Auto"
else
  mode_desc="Sadece Sayacı Atla"
  mode_desc_en="Skip Countdown Only"
fi

if [ "$framework_module" != 1 ]; then
  state=no_vector
  msg="❌ Xposed çerçevesi (Vector / LSPosed) bulunamadı."
elif [ "$framework_daemon" != 1 ]; then
  state=no_daemon
  msg="❌ $framework_name servisi çalışmıyor — cihazı yeniden başlatın."
elif [ "$apk" != 1 ]; then
  state=no_apk
  msg="❌ Kanca APK'sı kurulu değil — WebUI üzerinden kurun."
elif [ "$enabled" = unknown ]; then
  state=checking
  msg="⏳ Durum denetleniyor..."
elif [ "$enabled" != 1 ]; then
  state=not_enabled
  msg="❌ Modül $framework_name yöneticisinde onay bekliyor."
elif [ "$scope" != 1 ]; then
  state=no_scope
  msg="❌ Hedef kapsam eksik — $framework_name içinde $TARGET seçilmeli."
else
  state=ok
  msg="✅ Aktif ($framework_name) — $mode_desc"
fi

echo "state=$state"
echo "framework_name=$framework_name"
echo "framework_module=$framework_module"
echo "framework_daemon=$framework_daemon"
echo "vector_module=$framework_module"
echo "vector_daemon=$framework_daemon"
echo "apk=$apk"
echo "enabled=$enabled"
echo "scope=$scope"
echo "hook=$hook"
echo "mode=$mode"
echo "mode_desc=$mode_desc"
echo "mode_desc_en=$mode_desc_en"

if [ "$1" = "--prop" ] && [ "$state" != checking ]; then
  PROP="$MODDIR/module.prop"
  if [ -f "$PROP" ]; then
    TMP="$PROP.tmp"
    if grep -v '^description=' "$PROP" > "$TMP"; then
      echo "description=$msg" >> "$TMP"
      mv -f "$TMP" "$PROP"
    fi
  fi
fi
exit 0
