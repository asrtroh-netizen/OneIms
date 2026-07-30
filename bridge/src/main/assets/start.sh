#!/system/bin/sh
# OneBridge starter — Phase4: host APK embeds BridgeService (not separate com.oneims.bridge).
# Align OneKukuCoreComponent.bridgeBootShellCommand: no unconditional pkill.
# Background + marker so ADB shell can return (do not exec / block the stream).

# Prefer host package; fall back to legacy standalone bridge APK if present.
PACKAGE="${ONEBRIDGE_PACKAGE:-com.oneims.app}"
CLASS=com.oneims.bridge.server.BridgeService
NICE=onebridge_server

APK=$(pm path "$PACKAGE" 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\r')
if [ -z "$APK" ]; then
  PACKAGE=com.oneims.bridge
  APK=$(pm path "$PACKAGE" 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\r')
fi
if [ -z "$APK" ]; then
  printf '%s\n' "__OB_BOOT_MISS__"
  exit 1
fi

if pidof "$NICE" >/dev/null 2>&1; then
  printf '%s\n' "__OB_BOOT_OK__"
  exit 0
fi

export CLASSPATH="$APK"
# Detach from adb shell session so SIGHUP on stream close does not kill the server.
(setsid /system/bin/app_process /system/bin --nice-name="$NICE" "$CLASS" "$@" >/dev/null 2>&1 </dev/null &) || \
(nohup /system/bin/app_process /system/bin --nice-name="$NICE" "$CLASS" "$@" >/dev/null 2>&1 </dev/null &)
printf '%s\n' "__OB_BOOT_OK__"
