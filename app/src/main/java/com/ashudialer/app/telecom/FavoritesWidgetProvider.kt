package com.ashudialer.app.telecom

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.ashudialer.app.MainActivity
import com.ashudialer.app.R

/**
 * Favorites home-screen widget: a small always-visible list of starred
 * contacts with one tap to call any of them, no need to open the app,
 * search, or scroll a call log first. Every other quick-call surface in
 * this app (Recents, Contacts, the dialer's own suggestions) only exists
 * once the app is already open; this is the one that's visible the moment
 * the phone's screen turns on.
 *
 * The actual list content isn't built here - AppWidgetProvider callbacks
 * run on the main thread with no coroutine scope and no easy way to do the
 * ContentResolver query loadAllContacts() needs without risking an ANR on
 * a slow device. That part is delegated to FavoritesWidgetService's
 * RemoteViewsFactory, which the platform runs on a background thread by
 * design. This class only wires up the parts that don't need the contacts
 * data itself: telling each widget instance which adapter service backs
 * its list, and setting the header's tap target.
 */
class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_favorites)

        // Points this specific widget instance at the adapter service that
        // supplies its rows. The service itself is per-process, not
        // per-widget, but RemoteViewsService.onGetViewFactory reads the
        // widget id back out of the intent below to decide which
        // favorites list to build, so each instance still only ever shows
        // one shared favorites list (there's exactly one set of favorites
        // - the device's starred contacts - not one per widget instance).
        val adapterIntent = Intent(context, FavoritesWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Two widget instances otherwise share one Intent (by Intent
            // equality, extras don't count) and so one RemoteViewsFactory -
            // harmless here since both show the same data, but setData
            // keeps this provider correct if a future change ever makes
            // the list instance-specific, and costs nothing now.
            data = Uri.parse("content://widget/$widgetId")
        }
        views.setRemoteAdapter(android.R.id.list, adapterIntent)
        views.setEmptyView(android.R.id.list, android.R.id.empty)

        // Each row's own call button supplies a fill-in Intent (see
        // FavoritesWidgetService.getViewAt) carrying that row's phone
        // number; setPendingIntentTemplate below is what turns that
        // per-row fill-in into an actual tap action. A collection widget
        // (ListView/GridView backed by a RemoteViewsService) can't give
        // each row its own independent PendingIntent the way the header
        // button below gets one - every row shares this one template and
        // supplies only the differing extra, merged in via the
        // platform's own Intent.fillIn(), the same mechanism
        // CallActionReceiver's own PendingIntents rely on - hence the
        // matching FLAG_IMMUTABLE below rather than a divergent flag.
        val callTemplate = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_CALL_BACK
        }
        val callTemplatePendingIntent = PendingIntent.getBroadcast(
            context, 0, callTemplate,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(android.R.id.list, callTemplatePendingIntent)

        // Header "+" button: opens the app straight on the Contacts tab so
        // someone can star a contact immediately, rather than needing to
        // remember where that toggle lives. MainActivity already reads
        // this same extra name (see MainActivity's handling of
        // EXTRA_OPEN_TAB - kept as a plain string constant here rather
        // than importing DialerTab, since this module already depends on
        // MainActivity for its class reference and doesn't need a second,
        // enum-typed dependency just for one string).
        val openContactsIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_CONTACTS)
        }
        val openContactsPendingIntent = PendingIntent.getActivity(
            context, widgetId, openContactsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add_button, openContactsPendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    companion object {
        /**
         * Called from ContactsRepository.setContactFavorite right after a
         * star toggle succeeds, so every placed instance of this widget
         * reflects the change immediately - not on whatever cadence a
         * fixed refresh timer would otherwise impose (see the doc comment
         * on updatePeriodMillis="0" in favorites_widget_info.xml for why
         * this push model was chosen over one). Safe to call even when no
         * widget is currently placed: getAppWidgetIds then returns an
         * empty array and notifyAppWidgetViewDataChanged is simply never
         * called.
         */
        fun notifyFavoritesChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, FavoritesWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            // Invalidates the RemoteViewsFactory's cached rows (triggers
            // its onDataSetChanged) rather than notifyAppWidgetUpdate,
            // which would only reapply the same already-stale adapter
            // without ever re-querying the contacts provider.
            manager.notifyAppWidgetViewDataChanged(ids, android.R.id.list)
        }
    }
}
