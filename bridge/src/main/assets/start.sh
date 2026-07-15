#!/system/bin/sh
# OneBridge starter — launched by OneIMS embedded ADB / wireless debugging.
# Does NOT clone Shizuku; only boots BridgeService under shell uid.
# Background + marker so ADB shell can return (do not exec / block the stream).

PACKAGE=com.oneims.bridge
CLASS=com.oneims.bridge.server.BridgeService
NICE=onebridge_server

APK=$(pm path "$PACKAGE" 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\r')
if [ -z "$APK" ]; then
  echo "OneBridge_missing"
  exit 1
fi

pkill -f "$NICE" 2>/dev/null || true

export CLASSPATH="$APK"
/system/bin/app_process /system/bin --nice-name="$NICE" "$CLASS" "$@" >/dev/null 2>&1 &
echo "OneBridge_started"
