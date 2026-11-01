#!/system/bin/sh
# Refresh the description shown in the module manager once the system is up.
MODDIR=${0%/*}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 3
done

# Give the Xposed framework daemon (Vector or LSPosed) time to start before probing it.
i=0
while [ $i -lt 30 ] && ! pidof vectord lspd >/dev/null 2>&1; do
  sleep 2
  i=$((i + 1))
done
sleep 5

sh "$MODDIR/status.sh" --prop >/dev/null 2>&1
exit 0
