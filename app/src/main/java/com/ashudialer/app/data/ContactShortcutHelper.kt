package com.ashudialer.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ashudialer.app.R
import com.ashudialer.app.telecom.OutgoingCallActivity

/**
 * Pins a "call this person" shortcut to the home screen - tapping it places
 * the call directly, without opening the app first. Uses the contact's own
 * saved photo as the shortcut icon (cropped to a circle to match the
 * launcher's usual icon shape) when there is one; falls back to the app's
 * call icon otherwise.
 */
object ContactShortcutHelper {

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(context: Context, contact: Contact) {
        val label = contact.displayName.ifBlank { contact.phoneNumber }

        val callIntent = Intent(context, OutgoingCallActivity::class.java).apply {
            action = Intent.ACTION_CALL
            data = Uri.parse("tel:${contact.phoneNumber}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val icon = loadContactIcon(context, contact.photoUri)
            ?: IconCompat.createWithResource(context, R.drawable.ic_call_notification)

        val shortcut = ShortcutInfoCompat.Builder(context, "call_${contact.contactId}")
            .setShortLabel(label)
            .setLongLabel("Call $label")
            .setIcon(icon)
            .setIntent(callIntent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun loadContactIcon(context: Context, photoUri: String?): IconCompat? {
        if (photoUri.isNullOrBlank()) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                IconCompat.createWithBitmap(cropToCircle(original))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cropToCircle(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(RectF(rect), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        canvas.drawBitmap(bitmap, Rect(left, top, left + size, top + size), rect, paint)
        return output
    }
}
