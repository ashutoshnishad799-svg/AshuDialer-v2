package com.ashudialer.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen crop step between picking a photo and saving it as a contact
 * photo: drag to pan, pinch to zoom, fixed circular frame (matching how
 * every avatar in this app renders), output a square JPEG cropped to what's
 * inside the frame. Built from scratch rather than pulling in a cropping
 * library, since this environment can't reliably resolve a new Gradle
 * dependency at build time.
 */
@Composable
fun PhotoCropDialog(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropped: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDialerPalette.current

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(imageUri) { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(imageUri)
                val decoded = input?.use { BitmapFactory.decodeStream(it) }
                // Respect EXIF rotation so a photo taken in portrait doesn't
                // end up cropped sideways.
                decoded?.let { correctOrientation(context, imageUri, it) }
            } catch (_: Exception) {
                null
            }
        }
        if (sourceBitmap == null) loadFailed = true
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frameDiameterPx by remember { mutableFloatStateOf(0f) }
    var canvasSizePx by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val bitmap = sourceBitmap
                when {
                    loadFailed -> {
                        Text(
                            "Couldn't open that photo",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    bitmap == null -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bitmap) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        offset += pan
                                    }
                                }
                        ) {
                            canvasSizePx = Offset(size.width, size.height)
                            frameDiameterPx = min(size.width, size.height) * 0.8f

                            // Draw the image scaled to cover the canvas, then panned/
                            // zoomed by the person's gestures, clamped so the crop
                            // frame can never show empty space beyond the image edge.
                            val baseScale = max(
                                size.width / imageBitmap.width.toFloat(),
                                size.height / imageBitmap.height.toFloat()
                            )
                            val effectiveScale = baseScale * scale
                            val drawWidth = imageBitmap.width * effectiveScale
                            val drawHeight = imageBitmap.height * effectiveScale

                            val maxOffsetX = max(0f, (drawWidth - size.width) / 2f)
                            val maxOffsetY = max(0f, (drawHeight - size.height) / 2f)
                            offset = Offset(
                                offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )

                            val left = (size.width - drawWidth) / 2f + offset.x
                            val top = (size.height - drawHeight) / 2f + offset.y

                            drawImage(
                                image = imageBitmap,
                                dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt())
                            )

                            // Dim everything outside the circular frame so the crop
                            // area is unambiguous.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val framePath = Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(center = center, radius = frameDiameterPx / 2f))
                            }
                            clipPath(path = framePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                                drawRect(color = Color.Black.copy(alpha = 0.55f))
                            }
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = frameDiameterPx / 2f,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            Text(
                "Drag to move, pinch to zoom",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel, enabled = !isSaving) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        val bitmap = sourceBitmap ?: return@Button
                        if (isSaving) return@Button
                        isSaving = true
                        val baseScale = max(
                            canvasSizePx.x / bitmap.width.toFloat(),
                            canvasSizePx.y / bitmap.height.toFloat()
                        )
                        val effectiveScale = baseScale * scale
                        val cropped = cropToFrame(
                            source = bitmap,
                            canvasSize = canvasSizePx,
                            frameDiameterPx = frameDiameterPx,
                            drawScale = effectiveScale,
                            panOffset = offset
                        )
                        val bytes = ByteArrayOutputStream().apply {
                            cropped.compress(Bitmap.CompressFormat.JPEG, 92, this)
                        }.toByteArray()
                        onCropped(bytes)
                    },
                    enabled = sourceBitmap != null && !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Use photo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Maps the visible crop-frame circle back onto the original bitmap's pixel
 * coordinates and produces the final square output bitmap.
 */
private fun cropToFrame(
    source: Bitmap,
    canvasSize: Offset,
    frameDiameterPx: Float,
    drawScale: Float,
    panOffset: Offset
): Bitmap {
    if (canvasSize.x <= 0f || canvasSize.y <= 0f || drawScale <= 0f) return source

    val drawWidth = source.width * drawScale
    val drawHeight = source.height * drawScale
    val drawLeft = (canvasSize.x - drawWidth) / 2f + panOffset.x
    val drawTop = (canvasSize.y - drawHeight) / 2f + panOffset.y

    val frameLeft = (canvasSize.x - frameDiameterPx) / 2f
    val frameTop = (canvasSize.y - frameDiameterPx) / 2f

    // Frame's top-left/size in source-bitmap pixel space.
    val srcLeft = ((frameLeft - drawLeft) / drawScale).coerceIn(0f, source.width.toFloat())
    val srcTop = ((frameTop - drawTop) / drawScale).coerceIn(0f, source.height.toFloat())
    val srcSize = (frameDiameterPx / drawScale)
        .coerceAtMost(source.width - srcLeft)
        .coerceAtMost(source.height - srcTop)
        .coerceAtLeast(1f)

    val cropped = Bitmap.createBitmap(
        source,
        srcLeft.toInt(),
        srcTop.toInt(),
        srcSize.toInt(),
        srcSize.toInt()
    )

    // Downscale very large photos so the saved contact photo stays a
    // reasonable size rather than storing a multi-megapixel crop.
    val targetSize = 512
    return if (cropped.width > targetSize) {
        Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    } else {
        cropped
    }
}

private fun correctOrientation(context: android.content.Context, uri: Uri, bitmap: Bitmap): Bitmap {
    return try {
        // The platform's own android.media.ExifInterface, not the AndroidX
        // exifinterface artifact - this ships in the SDK itself (API 5+) so
        // it needs no extra Gradle dependency, which matters in an
        // environment where a newly added dependency can't be verified to
        // resolve at build time.
        val input = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = input.use { android.media.ExifInterface(it) }
        val orientation = exif.getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (_: Exception) {
        bitmap
    }
}
