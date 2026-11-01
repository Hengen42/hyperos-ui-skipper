#!/system/bin/sh
# Runs early at boot after the module is marked for removal, when package
# manager and settings are not available yet. Defer the cleanup to a one-shot
# late_start script that deletes itself. Failures must never block removal.
SD=/data/adb/service.d
CLEAN="$SD/hyperos_ui_skipper_cleanup.sh"
mkdir -p "$SD" 2>/dev/null

cat > "$CLEAN" <<'EOF'
#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 3
done
sleep 10
pm uninstall dev.hengen.uiskipper >/dev/null 2>&1
settings delete global hyperos_uiskipper_mode >/dev/null 2>&1
rm -f "$0"
EOF
chmod 0755 "$CLEAN" 2>/dev/null
exit 0
