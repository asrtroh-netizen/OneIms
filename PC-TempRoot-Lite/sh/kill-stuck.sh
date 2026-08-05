#!/system/bin/sh
# Align with TempRootShellCommands.KILL_STUCK_PRELOAD
pkill -9 -f preload-comet.so 2>/dev/null
pkill -9 -f 'LD_PRELOAD=/data/local/tmp/preload' 2>/dev/null
for p in $(pidof id 2>/dev/null); do
  grep -q preload-comet /proc/$p/maps 2>/dev/null && kill -9 $p
done
echo KILL_OK
