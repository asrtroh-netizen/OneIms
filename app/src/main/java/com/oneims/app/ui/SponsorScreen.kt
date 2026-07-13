package com.oneims.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneims.app.R

private data class SponsorPreview(
    val title: String,
    val bitmap: ImageBitmap,
)

@Composable
fun SponsorScreen(
    onPublish: (String) -> Unit,
) {
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.sponsor_saved)
    val saveFailedMessage = stringResource(R.string.sponsor_save_failed)
    val longPressMessage = stringResource(R.string.sponsor_long_press_hint)
    var preview by remember { mutableStateOf<SponsorPreview?>(null) }

    fun saveQr(assetName: String, fileLabel: String) {
        val ok = saveSponsorQrToGallery(
            context = context,
            assetName = assetName,
            fileLabel = fileLabel,
        )
        onPublish(if (ok) savedMessage else saveFailedMessage)
    }

    OneImsPage(
        title = stringResource(R.string.sponsor_title),
        subtitle = stringResource(R.string.sponsor_subtitle),
    ) {
        item {
            SectionBlock(title = stringResource(R.string.sponsor_intro_title)) {
                Text(
                    text = stringResource(R.string.sponsor_intro),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SponsorQrSection(
                title = stringResource(R.string.sponsor_wechat_title),
                description = stringResource(R.string.sponsor_wechat_desc),
                assetName = "sponsor_wechat.jpg",
                onSave = {
                    saveQr("sponsor_wechat.jpg", "OneIMS-wechat-sponsor")
                },
                onPreview = { bitmap ->
                    preview = SponsorPreview(
                        title = context.getString(R.string.sponsor_wechat_title),
                        bitmap = bitmap,
                    )
                },
                onLongPress = { onPublish(longPressMessage) },
            )
        }

        item {
            SponsorQrSection(
                title = stringResource(R.string.sponsor_alipay_title),
                description = stringResource(R.string.sponsor_alipay_desc),
                assetName = "sponsor_alipay.png",
                onSave = {
                    saveQr("sponsor_alipay.png", "OneIMS-alipay-sponsor")
                },
                onPreview = { bitmap ->
                    preview = SponsorPreview(
                        title = context.getString(R.string.sponsor_alipay_title),
                        bitmap = bitmap,
                    )
                },
                onLongPress = { onPublish(longPressMessage) },
            )
        }

        item {
            InlineNotice(text = stringResource(R.string.sponsor_voluntary_notice))
        }
    }

    preview?.let { current ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(current.title) },
            text = {
                SponsorQrImage(
                    bitmap = current.bitmap,
                    title = current.title,
                    onClick = {},
                    onLongPress = { onPublish(longPressMessage) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { preview = null }) {
                    Text(stringResource(R.string.sponsor_close_preview))
                }
            },
        )
    }
}

@Composable
private fun SponsorQrSection(
    title: String,
    description: String,
    assetName: String,
    onSave: () -> Unit,
    onPreview: (ImageBitmap) -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(context.applicationContext, assetName) {
        runCatching {
            context.assets.open(assetName).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }

    SectionBlock(
        title = title,
        description = description,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (bitmap != null) {
                SponsorQrImage(
                    bitmap = bitmap,
                    title = title,
                    onClick = { onPreview(bitmap) },
                    onLongPress = onLongPress,
                )
                Text(
                    text = stringResource(R.string.sponsor_qr_interaction_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                InlineNotice(text = stringResource(R.string.sponsor_qr_missing))
            }
            OneImsPrimaryButton(
                text = stringResource(R.string.sponsor_save_local),
                onClick = onSave,
                enabled = bitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SponsorQrImage(
    bitmap: ImageBitmap,
    title: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .sizeIn(maxWidth = 420.dp, maxHeight = 420.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 将 assets 内赞赏码写入系统相册 Pictures/OneIMS。
 * minSdk 31，走 MediaStore，无需额外存储权限。
 */
private fun saveSponsorQrToGallery(
    context: Context,
    assetName: String,
    fileLabel: String,
): Boolean {
    val bitmap = runCatching {
        context.assets.open(assetName).use(BitmapFactory::decodeStream)
    }.getOrNull() ?: return false
    val isPng = assetName.endsWith(".png", ignoreCase = true)
    val mime = if (isPng) "image/png" else "image/jpeg"
    val displayName = "$fileLabel.${if (isPng) "png" else "jpg"}"
    val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/OneIMS",
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    return runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(compressFormat, 95, output))
        } ?: error("No output stream")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    }.getOrElse {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}
