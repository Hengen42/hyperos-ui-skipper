#!/system/bin/sh
# Refresh the description shown in the module manager once the system is up.
MODDIR=${0%/*}

module_active() {
  [ -d "$MODDIR" ] && [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ]
}

module_active || exit 0
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  module_active || exit 0
  sleep 3
done

# Give the Xposed framework daemon (Vector or LSPosed) time to start before probing it.
i=0
while [ $i -lt 30 ] && ! pidof vectord lspd >/dev/null 2>&1; do
  module_active || exit 0
  sleep 2
  i=$((i + 1))
done

STATUS=$(sh "$MODDIR/status.sh" --retry-prop 2>/dev/null)
case "$STATUS" in
  *state=waiting_unlock*) ;;
  *) exit 0 ;;
esac

# Native Binder query only: no Java CLI or health polling while first unlock waits.
# Unknown/stopped user states must never trigger a speculative health refresh.
while module_active; do
  case "$(cmd activity get-started-user-state 0 2>/dev/null)" in
    RUNNING_UNLOCKED)
      module_active || exit 0
      sh "$MODDIR/status.sh" --retry-prop >/dev/null 2>&1
      break
      ;;
    RUNNING_LOCKED|RUNNING_UNLOCKING) sleep 5 ;;
    *) break ;;
  esac
done
exit 0
