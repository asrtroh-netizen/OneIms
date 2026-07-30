package rikka.shizuku.server;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    public static final String PERMISSION = "af.shizuku.plus.permission.API_V23";
    public static final String PERMISSION_LEGACY = "af.shizuku.manager.permission.API_V23";
    public static final String PERMISSION_ORIGINAL = "moe.shizuku.manager.permission.API_V23";

    // CARE_MIN（宿主内嵌）：Manager 固定为 OneIMS 宿主包。Not final：保留邻仓运行时纠偏形状。
    public static String MANAGER_APPLICATION_ID = "com.oneims.app";
    public static final String PLUS_APPLICATION_ID = "af.shizuku.plus.api";
    public static final String DROPIN_APPLICATION_ID = "moe.shizuku.privileged.api";
    /** 邻仓试验田；宿主融合后不再作为主 Manager。 */
    public static final String ONEKUKU_MINI_APPLICATION_ID = "com.onekuku.care";
    /** OneIMS 宿主（CARE_MIN 真源）。 */
    public static final String HOST_APPLICATION_ID = "com.oneims.app";

    // Computed on demand (rather than a constant) because it derives from MANAGER_APPLICATION_ID,
    // which can be corrected after this class is first loaded - a constant would freeze in the
    // default flavor's action string.
    public static String getRequestPermissionAction() {
        return MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";
    }

    public static final int BINDER_TRANSACTION_getApplications = 10001;
    public static final int BINDER_TRANSACTION_isCustomApiEnabled = 10002;
    public static final int BINDER_TRANSACTION_getDhizukuBinder = 10003;
    // Direct getter for the running server's patch version. The patch version is otherwise only
    // delivered via the oneway bindApplication callback, which the manager's own client doesn't
    // reliably receive; this lets it (and any client) read it directly, like getVersion()/getUid().
    public static final int BINDER_TRANSACTION_getServerPatchVersion = 10004;
}
