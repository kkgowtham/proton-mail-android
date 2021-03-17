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

package ch.protonmail.android.contacts.groups.edit

import androidx.work.WorkManager
import ch.protonmail.android.api.ProtonMailApiManager
import ch.protonmail.android.api.models.contacts.receive.ContactLabelFactory
import ch.protonmail.android.api.models.messages.receive.ServerLabel
import ch.protonmail.android.data.local.ContactDao
import ch.protonmail.android.data.local.model.ContactEmailContactLabelJoin
import ch.protonmail.android.data.local.model.ContactLabel
import ch.protonmail.android.worker.CreateContactGroupWorker
import com.birbit.android.jobqueue.JobManager
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Completable
import io.reactivex.Single
import kotlinx.coroutines.flow.flowOf
import java.io.IOException
import kotlin.test.Test

class ContactGroupEditCreateRepositoryTest {

    private val jobManager: JobManager = mockk(relaxed = true)

    private val workManager: WorkManager = mockk(relaxed = true)

    private val apiManager: ProtonMailApiManager = mockk(relaxed = true)

    private val contactDao: ContactDao = mockk(relaxed = true)

    private val contactLabelFactory: ContactLabelFactory = mockk(relaxed = true)

    private val createContactGroupWorker: CreateContactGroupWorker.Enqueuer = mockk(relaxed = true)

    private val repository = ContactGroupEditCreateRepository(
        jobManager, workManager, apiManager, contactDao, contactLabelFactory, createContactGroupWorker
    )

    @Test
    fun `when editContactGroup is called labelConverterFactory maps DB object to Server Object`() {
        val contactLabel = ContactLabel("Id", "name", "color")

        repository.editContactGroup(contactLabel)

        verify { contactLabelFactory.createServerObjectFromDBObject(contactLabel) }
    }

    @Test
    fun `when editContactGroup is called updateLabelCompletable API gets called with the request object`() {
        val contactGroupId = "contact-group-id"
        val contactLabel = ContactLabel(contactGroupId, "name", "color")
        val updateLabelRequest = ServerLabel(contactGroupId, "name", "color", 0, 0, 0, 0)
        every { contactLabelFactory.createServerObjectFromDBObject(contactLabel) } returns updateLabelRequest

        repository.editContactGroup(contactLabel)

        verify { apiManager.updateLabelCompletable(contactGroupId, updateLabelRequest.labelBody) }
    }

    @Test
    fun `when editContactGroup succeeds then save contact group and contact email to the DB`() {
        val contactGroupId = "contact-group-id"
        val contactLabel = ContactLabel(contactGroupId, "name", "color")
        val emailLabelJoinedList = listOf(ContactEmailContactLabelJoin("emailId", "labelId"))
        every { apiManager.updateLabelCompletable(any(), any()) } returns Completable.complete()
        every { contactDao.fetchJoins(contactGroupId) } returns flowOf(emailLabelJoinedList)
        every { contactDao.fetchJoinsBlocking(contactGroupId) } returns emailLabelJoinedList

        val testObserver = repository.editContactGroup(contactLabel).test()

        testObserver.awaitTerminalEvent()
        coVerifyOrder {
            contactDao.fetchJoinsBlocking(contactGroupId)
            contactDao.saveContactGroupLabel(contactLabel)
            contactDao.saveContactEmailContactLabelBlocking(emailLabelJoinedList)
        }
    }

    @Test
    fun `when editContactGroup API call fails then createContactGroupWorker is called`() {
        val contactGroupId = "contact-group-id"
        val contactLabel = ContactLabel(contactGroupId, "name", "color")
        val apiError = IOException("Test-exception")
        every { apiManager.updateLabelCompletable(any(), any()) } returns Completable.error(apiError)

        val testObserver = repository.editContactGroup(contactLabel).test()

        testObserver.awaitTerminalEvent()
        testObserver.assertError(apiError)
        verify { createContactGroupWorker.enqueue("name", "color", 0, 0, true, contactGroupId) }
    }

    @Test
    fun `when createContactGroup is called labelConverterFactory maps DB object to Server Object`() {
        val contactLabel = ContactLabel("Id", "name", "color")

        repository.createContactGroup(contactLabel)

        verify { contactLabelFactory.createServerObjectFromDBObject(contactLabel) }
    }

    @Test
    fun `when createContactGroup is called createLabelCompletable API gets called with the request object`() {
        val contactGroupId = "contact-group-id"
        val contactLabel = ContactLabel(contactGroupId, "name", "color")
        val updateLabelRequest = ServerLabel(contactGroupId, "name", "color", 0, 0, 0, 0)
        every { contactLabelFactory.createServerObjectFromDBObject(contactLabel) } returns updateLabelRequest

        repository.createContactGroup(contactLabel)

        verify { apiManager.createLabelCompletable(updateLabelRequest.labelBody) }
    }

    @Test
    fun `when createContactGroup succeeds then save contact group to the DB`() {
        val contactLabel = ContactLabel("", "name", "color", 1, 0, false, 2)
        every { apiManager.createLabelCompletable(any()) } returns Single.just(contactLabel)

        val testObserver = repository.createContactGroup(contactLabel).test()

        testObserver.awaitTerminalEvent()
        verify { contactDao.saveContactGroupLabel(contactLabel) }
    }

    @Test
    fun `when createContactGroup API call fails then createContactGroupWorker is called`() {
        val contactLabel = ContactLabel("", "name", "color", 1, 0, false, 2)
        val apiError = IOException("test-exception")
        every { apiManager.createLabelCompletable(any()) } returns Single.error(apiError)

        val testObserver = repository.createContactGroup(contactLabel).test()

        testObserver.awaitTerminalEvent()
        testObserver.assertError(apiError)
        verify { createContactGroupWorker.enqueue("name", "color", 1, 0, false, "") }
    }
}
