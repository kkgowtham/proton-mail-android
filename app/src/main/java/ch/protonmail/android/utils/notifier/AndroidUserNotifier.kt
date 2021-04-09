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

package ch.protonmail.android.utils.notifier

import android.content.Context
import android.os.Handler
import android.os.Looper
import ch.protonmail.android.R
import ch.protonmail.android.core.UserManager
import ch.protonmail.android.data.local.model.Message
import ch.protonmail.android.servers.notification.NotificationServer
import ch.protonmail.android.utils.extensions.showToast
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

class AndroidUserNotifier @Inject constructor(
    private val notificationServer: NotificationServer,
    private val userManager: UserManager,
    private val context: Context,
    private val dispatchers: DispatcherProvider
) : UserNotifier {

    override fun showPersistentError(errorMessage: String, messageSubject: String?) {
        val user = userManager.requireCurrentUserBlocking()
        notificationServer.notifySaveDraftError(user.id, errorMessage, messageSubject, user.name)
    }

    override fun showError(errorMessage: String) {
        Handler(Looper.getMainLooper()).post {
            context.showToast(errorMessage)
        }
    }

    override fun showSendMessageError(errorMessage: String, messageSubject: String?) {
        val error = "\"$messageSubject\" - $errorMessage"
        val user = userManager.requireCurrentUserBlocking()
        notificationServer.notifySingleErrorSendingMessage(user.id, user.name, error)
    }

    override suspend fun showMessageSent() {
        withContext(dispatchers.Main) {
            context.showToast(R.string.message_sent)
        }
    }

    override fun showHumanVerificationNeeded(message: Message) {
        val user = userManager.requireCurrentUserBlocking()
        notificationServer.notifyVerificationNeeded(
            user.id,
            user.name,
            checkNotNull(message.subject) { "'subject' cannot be null" },
            checkNotNull(message.messageId) { "'messageId' cannot be null" },
            message.isInline,
            checkNotNull(message.addressID) { "'addressID' cannot be null" }
        )
    }

}