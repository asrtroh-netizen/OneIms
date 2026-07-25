package com.onebattery.app.battery;

interface IBatteryShell {
    String ping();
    /**
     * Run `dumpsys batterystats` under the Shizuku shell UID and return truncated text.
     * Empty string on failure; detail may appear in [lastError].
     */
    String dumpBatteryStats(int maxChars);
    String lastError();
}
