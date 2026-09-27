package com.ashudialer.app.telecom

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.ashudialer.app.R
import com.ashudialer.app.data.Contact
import com.ashudialer.app.data.ContactsRepository
import kotlinx.coroutines.runBlocking

/**
 * Supplies the actual rows for the Favorites widget's list. Split out from
 * FavoritesWidgetProvider because a collection widget (a ListView/GridView
 * placed via RemoteViews, as opposed to a handful of individually-set
 * views) is required by the platform to get its rows from a bound
 * RemoteViewsService, not from anything the AppWidgetProvider builds
 * directly - see RemoteViews.setRemoteAdapter's own documentation. The
 * platform runs every method below on a dedicated background thread it
 * manages itself (never the widget host's main thread), which is what
 * makes the runBlocking calls in this file safe: unlike a call from
 * MainActivity.onCreate (see the warning in ThemePreference.kt), there is
 * no UI frame here that a blocking read could stall.
 */
class FavoritesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        FavoritesRemoteViewsFactory(applicationContext)
}

private class FavoritesRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private val repository = ContactsRepository(context)
    private var favorites: List<Contact> = emptyList()

    // Loaded once up front here rather than per-row in getViewAt - decoding
    // the same handful of photos on every single getViewAt call (the
    // platform can call it more than once per row across scroll/redraw
    // events) would repeat the same disk + decode work for no benefit,
    // since the underlying favorites list changes at most once between one
    // onDataSetChanged and the next.
    private val photoCache = HashMap<String, Bitmap?>()

    override fun onCreate() {}

    /**
     * Called by the platform once up front, and again every time
     * FavoritesWidgetProvider.notifyFavoritesChanged() triggers a refresh
     * (see that function's own doc comment for why a push model was
     * chosen over a polling updatePeriodMillis). Deliberately reloads and
     * re-sorts from scratch each time rather than trying to diff against
     * the previous list - the whole favorites list comfortably fits in
     * memory and changes rarely, so there's nothing this codebase's
     * existing incremental-update patterns (e.g. CallLogRepository's
     * grouping) would meaningfully save here.
     */
    override fun onDataSetChanged() {
        favorites = runBlocking {
            repository.loadAllContacts()
        }
            .filter { it.isFavorite }
            .distinctBy { it.contactId }
            .sortedBy { it.displayName.lowercase() }

        photoCache.clear()
        for (contact in favorites) {
            val uri = contact.photoUri
            if (!uri.isNullOrBlank()) {
                photoCache[contact.id] = decodePhoto(uri)
            }
        }
    }

    private fun decodePhoto(uriString: String): Bitmap? = try {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Exception) {
        // A stale or since-revoked content:// photo URI must never take the
        // whole row down with it - falls back to the initials circle below,
        // same as Avatar.kt's own !photoUri.isNullOrBlank() branch falls
        // back to initials when there's no photo at all.
        null
    }

    override fun onDestroy() {
        photoCache.clear()
        favorites = emptyList()
    }

    override fun getCount(): Int = favorites.size

    override fun getViewAt(position: Int): RemoteViews {
        val contact = favorites.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_favorite_item)
        val views = RemoteViews(context.packageName, R.layout.widget_favorite_item)

        views.setTextViewText(R.id.favorite_name, contact.displayName)

        val photo = photoCache[contact.id]
        if (photo != null) {
            views.setImageViewBitmap(R.id.favorite_photo, photo)
            views.setViewVisibility(R.id.favorite_photo, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.favorite_initial_bg, android.view.View.GONE)
            views.setViewVisibility(R.id.favorite_initial_text, android.view.View.GONE)
        } else {
            // Matches Avatar.kt's own initials rule: first letter of up to
            // the first two words of the name, both upper-cased - e.g.
            // "Ashu Nishad" -> "AN", a single-word name -> just its first
            // letter. Kept in sync by hand since RemoteViews rows can't
            // share a Composable with the in-app Avatar.
            val initials = contact.displayName.trim().split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
            views.setTextViewText(R.id.favorite_initial_text, initials)
            views.setViewVisibility(R.id.favorite_photo, android.view.View.GONE)
            views.setViewVisibility(R.id.favorite_initial_bg, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.favorite_initial_text, android.view.View.VISIBLE)
        }

        // Fill-in Intent for this one row: merged onto
        // FavoritesWidgetProvider's shared setPendingIntentTemplate at tap
        // time (Android's own mechanism for giving each row of one
        // collection widget its own tap target off of a single shared
        // PendingIntent template - a collection widget's rows cannot each
        // carry a fully independent PendingIntent the way the header
        // button does). Only the number actually varies per row, so only
        // it needs to be set here.
        val fillInIntent = Intent().apply {
            putExtra(CallActionReceiver.EXTRA_CALL_BACK_NUMBER, contact.phoneNumber)
        }
        views.setOnClickFillInIntent(R.id.favorite_row_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = favorites.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
