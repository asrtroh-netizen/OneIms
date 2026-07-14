#!/system/bin/sh
# OneBridge starter — launched by OneIMS embedded ADB / wireless debugging.
# Does NOT clone Shizuku; only boots BridgeService under shell uid.

PACKAGE=com.oneims.bridge
CLASS=com.oneims.bridge.server.BridgeService
NICE=onebridge_server

APK=$(pm path "$PACKAGE" 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\r')
if [ -z "$APK" ]; then
  echo "OneBridge: package $PACKAGE not installed" >&2
  exit 1
fi

# Kill previous server process if present
pkill -f "$NICE" 2>/dev/null || true

export CLASSPATH="$APK"
exec /system/bin/app_process /system/bin --nice-name="$NICE" "$CLASS" "$@"
