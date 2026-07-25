package com.onetools.app.caller

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.PhoneLookup
import com.onetools.app.BuildConfig
import com.onetools.app.R
import kotlinx.coroutines.runBlocking

/**
 * Contacts Directory for Pixel Phone — aligned with public Directory / PhoneLookup
 * contract used by Pixel Telo (clean-room; not a source copy).
 *
 * Goal: system dialer native line only (no incoming-call overlay).
 */
class OneCallerDirectoryProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private var authority: String = ""

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = ctx.getString(R.string.caller_directory_authority)
        matcher.addURI(authority, "directories", CODE_DIRECTORIES)
        matcher.addURI(authority, "phone_lookup/*", CODE_PHONE_LOOKUP)
        matcher.addURI(authority, "data/phones/filter/*", CODE_PHONE_LOOKUP)
        matcher.addURI(authority, "contacts/filter/*", CODE_CONTACTS_FILTER)
        matcher.addURI(authority, "contacts/lookup/*", CODE_CONTACT_LOOKUP)
        matcher.addURI(authority, "contacts/lookup/*/#", CODE_CONTACT_LOOKUP)
        CnMobileGeo.warm(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        return when (matcher.match(uri)) {
            CODE_DIRECTORIES -> directoriesCursor(projection)
            CODE_PHONE_LOOKUP -> {
                val number = uri.lastPathSegment.orEmpty()
                phoneLookupCursor(number, projection)
            }
            CODE_CONTACT_LOOKUP -> {
                // Dialer may re-query with LOOKUP_KEY we returned (prefix onecaller:).
                val key = uri.pathSegments.getOrNull(2).orEmpty()
                val number = key.removePrefix(LOOKUP_PREFIX)
                phoneLookupCursor(number, projection)
            }
            CODE_CONTACTS_FILTER -> {
                val filter = uri.lastPathSegment.orEmpty()
                phoneLookupCursor(filter, projection)
            }
            else -> null
        }
    }

    private fun directoriesCursor(projection: Array<out String>?): Cursor {
        val columns = projection ?: DEFAULT_DIRECTORY_COLUMNS
        val display = context?.getString(R.string.caller_directory_display) ?: "OneCaller"
        val values = mapOf<String, Any?>(
            Directory.ACCOUNT_NAME to ACCOUNT_NAME,
            Directory.ACCOUNT_TYPE to ACCOUNT_TYPE,
            Directory.DISPLAY_NAME to display,
            Directory.PACKAGE_NAME to BuildConfig.APPLICATION_ID,
            Directory.TYPE_RESOURCE_ID to R.string.caller_directory_display,
            Directory.EXPORT_SUPPORT to Directory.EXPORT_SUPPORT_ANY_ACCOUNT,
            Directory.SHORTCUT_SUPPORT to Directory.SHORTCUT_SUPPORT_NONE,
            Directory.PHOTO_SUPPORT to Directory.PHOTO_SUPPORT_NONE,
            Directory.DIRECTORY_AUTHORITY to authority,
        )
        return MatrixCursor(columns).also { it.addProjectionAwareRow(columns, values) }
    }

    private fun phoneLookupCursor(rawNumber: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: DEFAULT_PHONE_COLUMNS
        val empty = MatrixCursor(columns)
        val hit = resolveLabel(rawNumber) ?: return empty
        val digits = NumberMatcher.digits(rawNumber).ifBlank { rawNumber }
        // Pixel Phone paints CUSTOM rows as "LABEL + NUMBER". Number is already the
        // DISPLAY_NAME / in-call primary line — leave NUMBER empty so the subtitle is
        // geo-only (e.g. "陕西西安 · 联通"), never "… · 联通 185 0926 8666".
        val values = mapOf<String, Any?>(
            Data._ID to hit.idHash,
            Data.MIMETYPE to Phone.CONTENT_ITEM_TYPE,
            Data.CONTACT_ID to hit.idHash,
            Contacts._ID to hit.idHash,
            Contacts.LOOKUP_KEY to "$LOOKUP_PREFIX$digits",
            Contacts.DISPLAY_NAME to hit.displayName,
            PhoneLookup._ID to hit.idHash,
            PhoneLookup.DISPLAY_NAME to hit.displayName,
            PhoneLookup.NUMBER to "",
            PhoneLookup.TYPE to Phone.TYPE_CUSTOM,
            PhoneLookup.LABEL to hit.label,
            Phone.NUMBER to "",
            Phone.TYPE to Phone.TYPE_CUSTOM,
            Phone.LABEL to hit.label,
        )
        return MatrixCursor(columns).also { it.addProjectionAwareRow(columns, values) }
    }

    private fun resolveLabel(rawNumber: String): LabelHit? {
        val ctx = context ?: return null
        val digits = NumberMatcher.digits(rawNumber)
        if (digits.isEmpty()) return null
        val geo = CnMobileGeo.lookup(ctx, digits)
        val rules = runBlocking { CallRuleStore(ctx).snapshot() }
        val user = NumberMatcher.lookup(rules, digits)
        // Lightweight: only user ALLOW/LABEL rules + offline geo. Never paint geo as name;
        // never pull spam/network tags into DISPLAY_NAME.
        val allowRule = user.matchedRules.firstOrNull { it.kind == CallRuleKind.ALLOW }
        val labelRule = user.matchedRules.firstOrNull { it.kind == CallRuleKind.LABEL }
        val kind = when {
            allowRule != null -> CallRuleKind.ALLOW
            labelRule != null -> CallRuleKind.LABEL
            else -> null
        }
        val tag = when (kind) {
            CallRuleKind.ALLOW -> allowRule?.tag?.ifBlank { allowRule.pattern }
            CallRuleKind.LABEL -> labelRule?.tag?.ifBlank { labelRule.pattern }
            else -> null
        }
        val numberDisplay = formatPhoneDisplay(digits)
        val composed = DialerLabelComposer.compose(
            numberDisplay = numberDisplay,
            geo = geo,
            ruleKind = kind,
            ruleTag = tag,
            fallbackAllow = ctx.getString(R.string.caller_label_allow),
            fallbackLabel = ctx.getString(R.string.caller_label_mark),
            fallbackBlock = ctx.getString(R.string.caller_label_block),
            spamFmt = { t -> ctx.getString(R.string.caller_label_spam_fmt, t) },
        ) ?: return null
        return LabelHit(
            idHash = (digits.hashCode().toLong() and 0x7fff_ffffL) + 1L,
            displayName = composed.displayName,
            label = composed.label,
        )
    }

    private fun MatrixCursor.addProjectionAwareRow(
        columns: Array<out String>,
        values: Map<String, Any?>,
    ) {
        addRow(columns.map { values[it] }.toTypedArray())
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun formatPhoneDisplay(digits: String): String {
        val d = digits.removePrefix("86")
        return if (d.length == 11) {
            "${d.substring(0, 3)} ${d.substring(3, 7)} ${d.substring(7)}"
        } else {
            d
        }
    }

    private data class LabelHit(val idHash: Long, val displayName: String, val label: String)

    companion object {
        private const val CODE_DIRECTORIES = 1
        private const val CODE_PHONE_LOOKUP = 2
        private const val CODE_CONTACTS_FILTER = 3
        private const val CODE_CONTACT_LOOKUP = 4
        private const val LOOKUP_PREFIX = "onecaller:"

        const val ACCOUNT_NAME = "OneCallerLocal"
        val ACCOUNT_TYPE: String get() = BuildConfig.APPLICATION_ID

        private val DEFAULT_DIRECTORY_COLUMNS = arrayOf(
            Directory.ACCOUNT_NAME,
            Directory.ACCOUNT_TYPE,
            Directory.DISPLAY_NAME,
            Directory.PACKAGE_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.EXPORT_SUPPORT,
            Directory.SHORTCUT_SUPPORT,
            Directory.PHOTO_SUPPORT,
            Directory.DIRECTORY_AUTHORITY,
        )

        private val DEFAULT_PHONE_COLUMNS = arrayOf(
            Data._ID,
            Data.MIMETYPE,
            Data.CONTACT_ID,
            Contacts.LOOKUP_KEY,
            Contacts.DISPLAY_NAME,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.NUMBER,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
        )

        fun notifyChanged(resolver: android.content.ContentResolver) {
            Directory.notifyDirectoryChange(resolver)
        }
    }
}
