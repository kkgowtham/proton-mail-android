/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonMail.
 *
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import ch.protonmail.android.data.local.model.Notification
import me.proton.core.util.kotlin.unsupported

@Database(entities = [Notification::class], version = 1)
abstract class NotificationDatabase : RoomDatabase() {

    abstract fun getDao(): NotificationDao

    companion object : DatabaseFactory<NotificationDatabase>(
        NotificationDatabase::class,
        "NotificationsDatabase.db"
    ) {

        @JvmOverloads
        @Synchronized
        @Deprecated(
            "Use with user Id",
            ReplaceWith("NotificationsDatabase.getInstance(context, userId)"),
            DeprecationLevel.ERROR
        )
        fun getInstance(context: Context, username: String? = null): NotificationDatabase =
            unsupported

        @Synchronized
        @Deprecated(
            "Use with user Id",
            ReplaceWith("AttachmentMetadataDatabase.NotificationsDatabase(context, userId)"),
            DeprecationLevel.ERROR
        )
        fun deleteDb(context: Context, username: String) {
            unsupported
        }
    }
}
