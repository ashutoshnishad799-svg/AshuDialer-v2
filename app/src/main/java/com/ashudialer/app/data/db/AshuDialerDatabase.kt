/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CallLogEntity::class, BlockedNumberEntity::class, CallNoteEntity::class, SimRoutingEntity::class, VibrationRuleEntity::class, QuietHoursEntity::class, PrivateSpaceEntity::class, LockedNumberEntity::class, CallbackReminderEntity::class, ReportedSpamEntity::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AshuDialerDatabase : RoomDatabase() {

    abstract fun callLogDao(): CallLogDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun callNoteDao(): CallNoteDao
    abstract fun simRoutingDao(): SimRoutingDao
    abstract fun vibrationRuleDao(): VibrationRuleDao
    abstract fun quietHoursDao(): QuietHoursDao
    abstract fun privateSpaceDao(): PrivateSpaceDao
    abstract fun lockedNumberDao(): LockedNumberDao
    abstract fun callbackReminderDao(): CallbackReminderDao
    abstract fun reportedSpamDao(): ReportedSpamDao

    companion object {
        @Volatile
        private var INSTANCE: AshuDialerDatabase? = null

        fun getInstance(context: Context): AshuDialerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AshuDialerDatabase::class.java,
                    "pixel_dialer.db"
                )
                    // fallbackToDestructiveMigration() (an upgrade-path
                    // fallback, i.e. old version -> new version with no
                    // Migration registered) used to be enabled here. That
                    // silently DROPS AND RECREATES EVERY TABLE - including
                    // private_space_config and locked_numbers - any time
                    // this @Database's version number increases without a
                    // matching Migration, which is exactly why an already
                    // set-up Private Space came back asking to be set up
                    // again after a fresh app update: bumping `version`
                    // for whatever new entity/column prompted it wiped the
                    // Private Space password (and every other local table)
                    // on that update, with no warning to the person.
                    // fallbackToDestructiveMigrationOnDowngrade() only
                    // permits that destructive reset for the (much rarer,
                    // and lower-stakes) case of the installed app's DB
                    // version being *higher* than what this code defines -
                    // an actual upgrade with a missing Migration will now
                    // throw instead of silently deleting the person's data,
                    // which surfaces the mistake immediately during testing
                    // rather than as a silent data-loss bug in production.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    // Real upgrades (old installed schema version -> a newer
                    // version bump in this file) with no registered
                    // Migration used to throw IllegalStateException instead
                    // of silently wiping data - safer against silent data
                    // loss, but it meant ANY future version bump without a
                    // matching Migration crashed the app for every existing
                    // install the moment any DAO was first touched (which
                    // for InCallActivity/PixelInCallService is the instant a
                    // call screen appears - the DB is opened lazily, not at
                    // process start, so the crash surfaces exactly there).
                    // WARNING - this fallback is only truly harmless for
                    // call_log (it re-syncs from the system call log). It is
                    // NOT harmless for vibration_rules, blocked_numbers,
                    // call_notes, sim_routing_rules, quiet_hours_schedule,
                    // locked_numbers, private_space_config, reported_spam or
                    // callback_reminders: those are things the person typed
                    // in by hand and have no other source to re-sync from, so
                    // any version bump without a real Migration silently
                    // wipes them on app update (this is why a saved
                    // vibration pattern could "stop working" after an update).
                    // Schemas are now exported (exportSchema = true, see
                    // app/schemas) so the NEXT version bump should ship with
                    // a real addMigrations(...) entry rather than relying on
                    // this line. Do not bump `version` without one.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
