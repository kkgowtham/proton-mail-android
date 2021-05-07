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

package ch.protonmail.android.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.protonmail.android.feature.account.AccountStateManager
import ch.protonmail.android.utils.startMailboxActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var accountStateManager: AccountStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountStateManager.register(this)

        // Start Login or MailboxActivity.
        accountStateManager.state
            .onEach {
                when (it) {
                    is AccountStateManager.State.Processing -> Unit
                    is AccountStateManager.State.AccountNeeded -> accountStateManager.login()
                    is AccountStateManager.State.PrimaryExist -> {
                        startMailboxActivity()
                        finish()
                    }
                }
            }.launchIn(lifecycleScope)

        // Finish if Login closed.
        accountStateManager.onLoginClosed { finish() }
    }
}