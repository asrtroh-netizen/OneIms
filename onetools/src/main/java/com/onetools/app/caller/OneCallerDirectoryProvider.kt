package com.onetools.app.caller

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.PhoneLookup
import com.onetools.app.R
import kotlinx.coroutines.runBlocking

/**
 * Clean-room Contacts Directory for dialer / caller-ID labels.
 * Public ContactsContract.Directory contract only — not derived from Pixel Telo.
 */
class OneCallerDirectoryProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private var authority: String = ""

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = ctx.getString(R.string.caller_directory_authority)
        matcher.addURI(authority, "directories", CODE_DIRECTORIES)
        matcher.addURI(authority, "phone_lookup/*", CODE_PHONE_LOOKUP)
        matcher.addURI(authority, "contacts/filter/*", CODE_CONTACTS_FILTER)
        matcher.addURI(authority, "data/phones/filter/*", CODE_PHONE_FILTER)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val proj = projection ?: return MatrixCursor(emptyArray())
        return when (matcher.match(uri)) {
            CODE_DIRECTORIES -> directoriesCursor(proj)
            CODE_PHONE_LOOKUP,
            CODE_PHONE_FILTER,
            -> lookupCursor(proj, uri.lastPathSegment.orEmpty())
            CODE_CONTACTS_FILTER -> contactsFilterCursor(proj, uri.lastPathSegment.orEmpty())
            else -> null
        }
    }

    private fun directoriesCursor(projection: Array<out String>): Cursor {
        val label = context?.getString(R.string.caller_directory_display) ?: "OneCaller"
        val cursor = MatrixCursor(projection)
        cursor.addRow(
            projection.map { column ->
                when (column) {
                    Directory.ACCOUNT_NAME -> ACCOUNT_NAME
                    Directory.ACCOUNT_TYPE -> ACCOUNT_TYPE
                    Directory.DISPLAY_NAME -> label
                    Directory.TYPE_RESOURCE_ID -> R.string.caller_directory_display
                    Directory.EXPORT_SUPPORT -> Directory.EXPORT_SUPPORT_NONE
                    Directory.SHORTCUT_SUPPORT -> Directory.SHORTCUT_SUPPORT_NONE
                    Directory.PHOTO_SUPPORT -> Directory.PHOTO_SUPPORT_NONE
                    else -> null
                }
            }.toTypedArray(),
        )
        return cursor
    }

    private fun lookupCursor(projection: Array<out String>, rawNumber: String): Cursor {
        val cursor = MatrixCursor(projection)
        val hit = resolveLabel(rawNumber) ?: return cursor
        cursor.addRow(
            projection.map { column ->
                when (column) {
                    PhoneLookup._ID,
                    ContactsContract.Contacts._ID,
                    -> hit.idHash
                    PhoneLookup.DISPLAY_NAME,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    -> hit.displayName
                    PhoneLookup.LABEL -> hit.label
                    PhoneLookup.NUMBER -> NumberMatcher.digits(rawNumber)
                    PhoneLookup.TYPE -> ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM
                    else -> null
                }
            }.toTypedArray(),
        )
        return cursor
    }

    private fun contactsFilterCursor(projection: Array<out String>, filter: String): Cursor {
        val cursor = MatrixCursor(projection)
        val digits = NumberMatcher.digits(filter)
        if (digits.isEmpty()) return cursor
        val hit = resolveLabel(digits) ?: return cursor
        cursor.addRow(
            projection.map { column ->
                when (column) {
                    ContactsContract.Contacts._ID -> hit.idHash
                    ContactsContract.Contacts.DISPLAY_NAME -> hit.displayName
                    else -> null
                }
            }.toTypedArray(),
        )
        return cursor
    }

    private fun resolveLabel(rawNumber: String): LabelHit? {
        val ctx = context ?: return null
        val rules = runBlocking { CallRuleStore(ctx).snapshot() }
        val digits = NumberMatcher.digits(rawNumber)
        if (digits.isEmpty()) return null
        val hits = rules.filter { NumberMatcher.matches(it, digits) }
        val chosen = hits.firstOrNull { it.kind == CallRuleKind.ALLOW }
            ?: hits.firstOrNull { it.kind == CallRuleKind.BLOCK }
            ?: return null
        val tag = chosen.tag.ifBlank {
            when (chosen.kind) {
                CallRuleKind.ALLOW -> ctx.getString(R.string.caller_label_allow)
                CallRuleKind.BLOCK -> ctx.getString(R.string.caller_label_block)
            }
        }
        val name = when (chosen.kind) {
            CallRuleKind.ALLOW -> tag
            CallRuleKind.BLOCK -> ctx.getString(R.string.caller_label_spam_fmt, tag)
        }
        return LabelHit(
            idHash = digits.hashCode().toLong().and(0x7fff_ffffL),
            displayName = name,
            label = tag,
        )
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    private data class LabelHit(val idHash: Long, val displayName: String, val label: String)

    companion object {
        private const val CODE_DIRECTORIES = 1
        private const val CODE_PHONE_LOOKUP = 2
        private const val CODE_CONTACTS_FILTER = 3
        private const val CODE_PHONE_FILTER = 4
        const val ACCOUNT_NAME = "OneCaller"
        const val ACCOUNT_TYPE = "com.onetools.app.caller"

        fun notifyChanged(resolver: android.content.ContentResolver) {
            Directory.notifyDirectoryChange(resolver)
        }
    }
}
