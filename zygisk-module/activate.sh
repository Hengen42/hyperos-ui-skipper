#!/system/bin/sh
# Enables the hook module in Vector or LSPosed and scopes it to SecurityCenter
# through the framework's CLI, so no manual approval and no reboot are needed.
PKG=dev.hengen.uiskipper
TARGET=com.miui.securitycenter

cli_path=""
if [ -f /data/adb/modules/zygisk_vector/cli ]; then
  cli_path="/data/adb/modules/zygisk_vector/cli"
elif [ -f /data/adb/modules/zygisk_lsposed/cli ]; then
  cli_path="/data/adb/modules/zygisk_lsposed/cli"
elif [ -f /data/adb/lspd/cli ]; then
  cli_path="/data/adb/lspd/cli"
fi

[ -n "$cli_path" ] || { echo "framework_cli_missing"; exit 1; }

# Borrow complete Android runtime env from zygote if missing
if [ -z "$BOOTCLASSPATH" ] || [ -z "$ANDROID_I18N_ROOT" ]; then
  ZPID=$(pidof zygote64 zygote | cut -d' ' -f1)
  if [ -n "$ZPID" ] && [ -r "/proc/$ZPID/environ" ]; then
    for kv in $(tr '\0' '\n' < "/proc/$ZPID/environ" | grep -E '^[A-Z_0-9]+=' | grep -vE '^(PATH|HOME|PWD|SHLVL|_)='); do
      export "$kv" 2>/dev/null
    done
  fi
fi

# Fallback if environ unreadable
if [ -z "$BOOTCLASSPATH" ]; then
  export ANDROID_ROOT="/system"
  export ANDROID_DATA="/data"
  export ANDROID_RUNTIME_ROOT="/apex/com.android.runtime"
  export ANDROID_TZDATA_ROOT="/apex/com.android.tzdata"
  export ANDROID_I18N_ROOT="/apex/com.android.i18n"
  export ANDROID_ART_ROOT="/apex/com.android.art"
  export BOOTCLASSPATH="/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/system/framework/framework.jar:/system/framework/framework-graphics.jar:/system/framework/ext.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/ims-common.jar"
fi

xcli() { timeout 20 sh "$cli_path" "$@" 2>&1; }

# Framework needs a moment to register a freshly installed package
i=0
while [ $i -lt 10 ]; do
  xcli modules ls | grep -qF "$PKG" && break
  sleep 1
  i=$((i + 1))
done

xcli modules enable "$PKG" | grep -q "Enabled: \[.*$PKG" || { echo "enable_failed"; exit 1; }
xcli scope set "$PKG" "$TARGET/0" | grep -qi "success" || { echo "scope_failed"; exit 1; }

am force-stop "$TARGET" 2>/dev/null
echo "ok"
exit 0
