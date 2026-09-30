package org.foxgirls.audioranobe.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import org.foxgirls.audioranobe.ui.toast.toastError
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageActivity
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** A picked+cropped image, already downscaled and encoded as WebP (lib/image.ts + ImageCropper). */
class PickedImage(val bytes: ByteArray, val width: Int, val height: Int) {
    fun part(field: String = "file", filename: String = "image.webp"): MultipartBody.Part =
        MultipartBody.Part.createFormData(field, filename, bytes.toRequestBody("image/webp".toMediaType()))
}

class ImagePicker(private val launch: () -> Unit) {
    fun pick() = launch()
}

/**
 * Opens the gallery, then the crop UI (fixed aspect when given), then downsizes to the server's
 * pixel cap and re-encodes as WebP. [onResult] runs on the main thread with the encoded image.
 */
@Composable
fun rememberImageCropper(
    aspectX: Int? = null,
    aspectY: Int? = null,
    maxWidth: Int = 2048,
    maxHeight: Int = 2048,
    circle: Boolean = false,
    onResult: (PickedImage) -> Unit,
): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(CropContract) { res ->
        if (!res.isSuccessful) { res.error?.let { toastError(it.message ?: "Не удалось обрезать изображение") }; return@rememberLauncherForActivityResult }
        val uri = res.uriContent ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val img = withContext(Dispatchers.IO) { encodeWebp(context, uri, maxWidth, maxHeight) }
                onResult(img)
            } catch (e: Exception) { toastError(e) }
        }
    }
    return remember(aspectX, aspectY, maxWidth, maxHeight, circle) {
        ImagePicker {
            launcher.launch(
                    CropImageOptions(
                        imageSourceIncludeGallery = true,
                        imageSourceIncludeCamera = false,
                        fixAspectRatio = aspectX != null && aspectY != null,
                        aspectRatioX = aspectX ?: 1,
                        aspectRatioY = aspectY ?: 1,
                        cropShape = if (circle) CropImageView.CropShape.OVAL else CropImageView.CropShape.RECTANGLE,
                        guidelines = CropImageView.Guidelines.ON_TOUCH,
                        outputCompressFormat = Bitmap.CompressFormat.PNG,
                        outputCompressQuality = 100,
                        activityBackgroundColor = Color.parseColor("#161616"),
                        toolbarColor = Color.parseColor("#1A1A1D"),
                        toolbarTitleColor = Color.WHITE,
                        toolbarBackButtonColor = Color.WHITE,
                        activityMenuIconColor = Color.WHITE,
                        activityMenuTextColor = Color.WHITE,
                        cropMenuCropButtonTitle = "Готово",
                        activityTitle = "Обрезка изображения",
                    ),
            )
        }
    }
}

/** The cropper library's own CropImageContract, which it deprecates in favour of apps keeping a copy. */
private object CropContract : androidx.activity.result.contract.ActivityResultContract<CropImageOptions, CropImageView.CropResult>() {
    override fun createIntent(context: android.content.Context, input: CropImageOptions) =
        android.content.Intent(context, CropImageActivity::class.java).putExtra(
            CropImage.CROP_IMAGE_EXTRA_BUNDLE,
            android.os.Bundle(2).apply {
                putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, null)
                putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, input)
            },
        )

    override fun parseResult(resultCode: Int, intent: android.content.Intent?): CropImageView.CropResult {
        val result = intent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, CropImage.CROP_IMAGE_EXTRA_RESULT, CropImage.ActivityResult::class.java) }
        return if (resultCode == android.app.Activity.RESULT_CANCELED || result == null) CropImage.CancelledResult else result
    }
}

/** Plain gallery picker without cropping (illustrations, chapter art). */
@Composable
fun rememberImagePicker(maxWidth: Int = 4096, maxHeight: Int = 4096, onResult: (PickedImage) -> Unit): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try { onResult(withContext(Dispatchers.IO) { encodeWebp(context, uri, maxWidth, maxHeight) }) } catch (e: Exception) { toastError(e) }
        }
    }
    return remember { ImagePicker { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/** Generic file picker (audio uploads, backups). Returns the content Uri. */
@Composable
fun rememberFilePicker(mime: Array<String>, onResult: (Uri) -> Unit): ImagePicker {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onResult(uri) }
    return remember(mime) { ImagePicker { launcher.launch(mime) } }
}

@Composable
fun rememberMultiFilePicker(mime: Array<String>, onResult: (List<Uri>) -> Unit): ImagePicker {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) onResult(uris) }
    return remember(mime) { ImagePicker { launcher.launch(mime) } }
}

private fun encodeWebp(context: android.content.Context, uri: Uri, maxW: Int, maxH: Int): PickedImage {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxW * 2 || bounds.outHeight / sample > maxH * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IllegalStateException("Не удалось загрузить изображение")
    // Honour EXIF orientation.
    val orientation = try { cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: 1 } catch (_: Exception) { 1 }
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.preScale(1f, -1f)
    }
    if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    val scale = min(1f, min(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height))
    if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * scale).toInt()), maxOf(1, (bmp.height * scale).toInt()), true)
    val out = ByteArrayOutputStream()
    @Suppress("DEPRECATION")
    val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    bmp.compress(fmt, 85, out)
    return PickedImage(out.toByteArray(), bmp.width, bmp.height)
}
