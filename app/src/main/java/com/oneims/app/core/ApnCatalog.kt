package com.oneims.app.core

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

/**
 * 离线资产内容变化时必须递增；SQLiteOpenHelper 会在升级事务中重建只读派生索引。
 */
internal const val APN_CATALOG_DATABASE_VERSION = 2

data class ApnCatalogEntry(
    val id: Long,
    val countryCode: String,
    val source: String,
    val carrier: String,
    val mcc: String,
    val mnc: String,
    val apn: String,
    val types: String,
    val protocol: String,
    val roamingProtocol: String,
    val user: String,
    val password: String,
    val authType: Int?,
    val mmsc: String,
    val mmsProxy: String,
    val mmsPort: String,
    val proxy: String,
    val port: String,
    val carrierId: Int?,
    val mvnoType: String,
    val mvnoMatchData: String,
    val carrierEnabled: Boolean,
    val userVisible: Boolean,
    val userEditable: Boolean,
    val networkTypeBitmask: String,
    val bearerBitmask: String,
) {
    val countryNameZh: String
        get() = ApnCountryIndex.displayName(countryCode)

    val countryNameEn: String
        get() = ApnCountryIndex.englishName(countryCode)

    val countryIso: String
        get() = countryCode

    val operatorName: String
        get() = carrier
    val normalizedTypes: List<String>
        get() = ApnCatalogPolicy.normalizeTypes(types)

    val supportsIms: Boolean
        get() = "ims" in normalizedTypes

    val isSafeImsTemplate: Boolean
        get() = carrierEnabled && normalizedTypes == listOf("ims")
}

data class ApnCatalogQuery(
    val mcc: String = "",
    val mnc: String = "",
    val carrierId: Int? = null,
    val search: String = "",
    val limit: Int = 120,
)

internal data class ApnSearchSelection(
    val clause: String?,
    val args: List<String>,
)

internal fun escapeApnLike(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

internal fun buildApnSearchSelection(searchInput: String): ApnSearchSelection {
    val search = searchInput.trim()
    if (search.isEmpty()) return ApnSearchSelection(null, emptyList())
    val tokens = ApnCountryIndex.resolveSearchTokens(search)
    val likeClauses = mutableListOf<String>()
    val likeArgs = mutableListOf<String>()
    val allTokens = (tokens + setOf(search)).distinct()
    allTokens.forEach { token ->
        val like = "%${escapeApnLike(token.lowercase(Locale.ROOT))}%"
        likeClauses += """
            (
                lower(carrier) LIKE ? ESCAPE '\' OR
                lower(apn) LIKE ? ESCAPE '\' OR
                lower(country) LIKE ? ESCAPE '\' OR
                (mcc || mnc) LIKE ? ESCAPE '\' OR
                mcc LIKE ? ESCAPE '\' OR
                mnc LIKE ? ESCAPE '\' OR
                lower(apn_types) LIKE ? ESCAPE '\'
            )
        """.trimIndent()
        repeat(7) { likeArgs += like }
    }
    return ApnSearchSelection(
        clause = "(${likeClauses.joinToString(" OR ")})",
        args = likeArgs,
    )
}

data class ApnCatalogSummary(
    val version: String,
    val records: Int,
    val imsRecords: Int,
    val plmns: Int,
    val countries: Int,
)

internal object ApnCatalogPolicy {
    fun normalizeTypes(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { value -> value.trim().lowercase(Locale.ROOT) }
            .filter { value -> value.isNotEmpty() }
            .distinct()

    fun matchesCurrentSim(
        entry: ApnCatalogEntry,
        mcc: String,
        mnc: String,
        carrierId: Int?,
    ): Boolean {
        val numericMatches =
            mcc.length == 3 && mnc.length in 2..3 &&
                entry.mcc == mcc && entry.mnc == mnc
        val carrierMatches =
            carrierId != null && carrierId > 0 && entry.carrierId == carrierId
        return numericMatches || carrierMatches
    }
}

/**
 * 只读离线 APN 仓库。首次调用时把可审计的 TSV 资产装入应用私有 SQLite，
 * 后续仅做本机参数化查询；没有网络请求，也不会直接修改系统 APN。
 */
class ApnCatalogRepository private constructor(context: Context) {
    private val database = ApnCatalogDatabase(context.applicationContext)

    fun summary(): ApnCatalogSummary {
        val db = database.readableDatabase
        val records = db.longForQuery("SELECT COUNT(*) FROM apn_profiles").toInt()
        val imsRecords = db.longForQuery(
            """
            SELECT COUNT(*) FROM apn_profiles
            WHERE instr(',' || lower(apn_types) || ',', ',ims,') > 0
            """.trimIndent(),
        ).toInt()
        val plmns = db.longForQuery(
            """
            SELECT COUNT(DISTINCT mcc || ':' || mnc)
            FROM apn_profiles WHERE mcc <> ''
            """.trimIndent(),
        ).toInt()
        val countries = db.longForQuery(
            "SELECT COUNT(DISTINCT country) FROM apn_profiles",
        ).toInt()
        return ApnCatalogSummary(
            version = CATALOG_VERSION,
            records = records,
            imsRecords = imsRecords,
            plmns = plmns,
            countries = countries,
        )
    }

    fun search(query: ApnCatalogQuery): List<ApnCatalogEntry> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        val normalizedMcc = query.mcc.trim()
        val normalizedMnc = query.mnc.trim()
        val validCarrierId = query.carrierId?.takeIf { value -> value > 0 }

        val searchSelection = buildApnSearchSelection(query.search)
        if (searchSelection.clause != null) {
            clauses += searchSelection.clause
            args += searchSelection.args
        }

        val orderArgs = mutableListOf<String>()
        val order = buildString {
            append("CASE ")
            if (normalizedMcc.length == 3 && normalizedMnc.length in 2..3) {
                append("WHEN mcc = ? AND mnc = ? THEN 0 ")
                orderArgs += normalizedMcc
                orderArgs += normalizedMnc
            }
            if (validCarrierId != null) {
                append("WHEN carrier_id = ? THEN 1 ")
                orderArgs += validCarrierId.toString()
            }
            append("ELSE 2 END, ")
            append("CASE WHEN instr(',' || lower(apn_types) || ',', ',ims,') > 0 ")
            append("THEN 0 ELSE 1 END, carrier COLLATE NOCASE, apn COLLATE NOCASE")
        }

        val selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val limit = query.limit.coerceIn(1, MAX_QUERY_ROWS).toString()
        return database.readableDatabase.query(
            TABLE,
            PROJECTION,
            selection,
            (args + orderArgs).toTypedArray(),
            null,
            null,
            order,
            limit,
        ).use(::readEntries)
    }

    companion object {
        const val CATALOG_VERSION = "2026.07.10"
        private const val MAX_QUERY_ROWS = 200
        private const val TABLE = "apn_profiles"
        private val PROJECTION = arrayOf(
            "_id",
            "country",
            "source",
            "carrier",
            "mcc",
            "mnc",
            "apn",
            "apn_types",
            "protocol",
            "roaming_protocol",
            "username",
            "password",
            "auth_type",
            "mmsc",
            "mms_proxy",
            "mms_port",
            "proxy",
            "port",
            "carrier_id",
            "mvno_type",
            "mvno_match_data",
            "carrier_enabled",
            "user_visible",
            "user_editable",
            "network_type_bitmask",
            "bearer_bitmask",
        )

        @Volatile
        private var instance: ApnCatalogRepository? = null

        fun get(context: Context): ApnCatalogRepository =
            instance ?: synchronized(this) {
                instance ?: ApnCatalogRepository(context).also { repository ->
                    instance = repository
                }
            }

        private fun readEntries(cursor: Cursor): List<ApnCatalogEntry> {
            val columns = PROJECTION.associateWith(cursor::getColumnIndexOrThrow)
            return buildList {
                while (cursor.moveToNext()) {
                    fun text(name: String) = cursor.getString(columns.getValue(name)).orEmpty()
                    fun nullableInt(name: String): Int? {
                        val index = columns.getValue(name)
                        return if (cursor.isNull(index)) null else cursor.getInt(index)
                    }
                    add(
                        ApnCatalogEntry(
                            id = cursor.getLong(columns.getValue("_id")),
                            countryCode = text("country"),
                            source = text("source"),
                            carrier = text("carrier"),
                            mcc = text("mcc"),
                            mnc = text("mnc"),
                            apn = text("apn"),
                            types = text("apn_types"),
                            protocol = text("protocol"),
                            roamingProtocol = text("roaming_protocol"),
                            user = text("username"),
                            password = text("password"),
                            authType = nullableInt("auth_type"),
                            mmsc = text("mmsc"),
                            mmsProxy = text("mms_proxy"),
                            mmsPort = text("mms_port"),
                            proxy = text("proxy"),
                            port = text("port"),
                            carrierId = nullableInt("carrier_id"),
                            mvnoType = text("mvno_type"),
                            mvnoMatchData = text("mvno_match_data"),
                            carrierEnabled = cursor.getInt(
                                columns.getValue("carrier_enabled"),
                            ) != 0,
                            userVisible = cursor.getInt(columns.getValue("user_visible")) != 0,
                            userEditable = cursor.getInt(columns.getValue("user_editable")) != 0,
                            networkTypeBitmask = text("network_type_bitmask"),
                            bearerBitmask = text("bearer_bitmask"),
                        )
                    )
                }
            }
        }

    }
}

private class ApnCatalogDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val appContext = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
        db.execSQL("CREATE INDEX apn_numeric_idx ON apn_profiles(mcc, mnc)")
        db.execSQL("CREATE INDEX apn_carrier_id_idx ON apn_profiles(carrier_id)")
        db.execSQL("CREATE INDEX apn_carrier_idx ON apn_profiles(carrier COLLATE NOCASE)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS apn_profiles")
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val statement = db.compileStatement(INSERT)
        try {
            appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).useLines { lines ->
                val iterator = lines.iterator()
                check(iterator.hasNext()) { "Offline APN catalog is empty" }
                check(iterator.next() == ApnCatalogTsv.header) {
                    "Offline APN catalog header is incompatible"
                }
                iterator.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val fields = ApnCatalogTsv.decodeLine(line)
                    statement.clearBindings()
                    fields.forEachIndexed { index, value ->
                        val bindIndex = index + 1
                        when (index) {
                            AUTH_TYPE_INDEX, CARRIER_ID_INDEX ->
                                value.toLongOrNull()?.let { statement.bindLong(bindIndex, it) }
                                    ?: statement.bindNull(bindIndex)
                            CARRIER_ENABLED_INDEX, USER_VISIBLE_INDEX, USER_EDITABLE_INDEX ->
                                statement.bindLong(bindIndex, value.defaultTrueInt())
                            else -> statement.bindString(bindIndex, value)
                        }
                    }
                    statement.executeInsert()
                }
            }
        } finally {
            statement.close()
        }
    }

    companion object {
        private const val DATABASE_NAME = "oneims_apn_catalog.db"
        private const val DATABASE_VERSION = APN_CATALOG_DATABASE_VERSION
        private const val ASSET_NAME = "apn_catalog.tsv"
        private const val AUTH_TYPE_INDEX = 11
        private const val CARRIER_ID_INDEX = 17
        private const val CARRIER_ENABLED_INDEX = 20
        private const val USER_VISIBLE_INDEX = 21
        private const val USER_EDITABLE_INDEX = 22

        private val CREATE_TABLE = """
            CREATE TABLE apn_profiles (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                country TEXT NOT NULL,
                source TEXT NOT NULL,
                carrier TEXT NOT NULL,
                mcc TEXT NOT NULL,
                mnc TEXT NOT NULL,
                apn TEXT NOT NULL,
                apn_types TEXT NOT NULL,
                protocol TEXT NOT NULL,
                roaming_protocol TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                auth_type INTEGER,
                mmsc TEXT NOT NULL,
                mms_proxy TEXT NOT NULL,
                mms_port TEXT NOT NULL,
                proxy TEXT NOT NULL,
                port TEXT NOT NULL,
                carrier_id INTEGER,
                mvno_type TEXT NOT NULL,
                mvno_match_data TEXT NOT NULL,
                carrier_enabled INTEGER NOT NULL,
                user_visible INTEGER NOT NULL,
                user_editable INTEGER NOT NULL,
                network_type_bitmask TEXT NOT NULL,
                bearer_bitmask TEXT NOT NULL
            )
        """.trimIndent()

        private val INSERT = """
            INSERT INTO apn_profiles (
                country, source, carrier, mcc, mnc, apn, apn_types, protocol,
                roaming_protocol, username, password, auth_type, mmsc, mms_proxy,
                mms_port, proxy, port, carrier_id, mvno_type, mvno_match_data,
                carrier_enabled, user_visible, user_editable, network_type_bitmask,
                bearer_bitmask
            ) VALUES (${List(25) { "?" }.joinToString()})
        """.trimIndent()

        private fun String.defaultTrueInt(): Long =
            if (equals("false", ignoreCase = true) || this == "0") 0L else 1L
    }
}

internal object ApnCatalogTsv {
    const val header =
        "country\tsource\tcarrier\tmcc\tmnc\tapn\ttype\tprotocol\troaming_protocol\t" +
            "user\tpassword\tauthtype\tmmsc\tmmsproxy\tmmsport\tproxy\tport\tcarrier_id\t" +
            "mvno_type\tmvno_match_data\tcarrier_enabled\tuser_visible\tuser_editable\t" +
            "network_type_bitmask\tbearer_bitmask"
    private const val FIELD_COUNT = 25

    fun decodeLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        line.forEach { character ->
            when {
                escaped -> {
                    current.append(
                        when (character) {
                            't' -> '\t'
                            'n' -> '\n'
                            'r' -> '\r'
                            '\\' -> '\\'
                            else -> error("Unsupported APN catalog escape: \\$character")
                        }
                    )
                    escaped = false
                }
                character == '\\' -> escaped = true
                character == '\t' -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        check(!escaped) { "APN catalog row ends with an escape character" }
        fields += current.toString()
        check(fields.size == FIELD_COUNT) {
            "APN catalog row has ${fields.size} fields; expected $FIELD_COUNT"
        }
        return fields
    }
}

private fun SQLiteDatabase.longForQuery(sql: String): Long =
    rawQuery(sql, null).use { cursor ->
        check(cursor.moveToFirst()) { "APN catalog aggregate returned no row" }
        cursor.getLong(0)
    }
