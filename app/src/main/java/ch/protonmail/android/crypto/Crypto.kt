/*
 * Copyright (c) 2022 Proton AG
 *
 * This file is part of Proton Mail.
 *
 * Proton Mail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Mail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Mail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.crypto

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PROTECTED
import ch.protonmail.android.core.UserManager
import ch.protonmail.android.domain.entity.PgpField
import ch.protonmail.android.domain.entity.user.AddressKey
import ch.protonmail.android.domain.entity.user.AddressKeys
import ch.protonmail.android.domain.entity.user.UserKey
import ch.protonmail.android.domain.entity.user.UserKeys
import ch.protonmail.android.utils.crypto.OpenPGP
import ch.protonmail.android.utils.crypto.TextDecryptionResult
import com.proton.gopenpgp.armor.Armor
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.util.kotlin.EMPTY_STRING
import timber.log.Timber
import com.proton.gopenpgp.crypto.Crypto as GoOpenPgpCrypto

/**
 * @param <KC> Type of the Container.
 * @see UserKeys
 * @see AddressKeys
 *
 * @param <K> Type of the Key.
 * @see UserKey
 * @see AddressKey
 */
abstract class Crypto<K>(
    private val userManager: UserManager,
    protected val openPgp: OpenPGP,
    private val userId: UserId
) {

    protected val user by lazy {
        userManager.getUserBlocking(userId)
    }

    protected val userKeys
        get() = user.keys

    protected abstract val currentKeys: Collection<K>

    protected abstract val primaryKey: K?

    protected val mailboxPassword get() = userManager.getUserPassphraseBlocking(userId)

    /**
     * Return passphrase for decryption
     */
    protected abstract val passphrase: ByteArray?

    /**
     * @return Non null [K]
     * @throws IllegalStateException if [primaryKey] is actually `null`
     */
    protected fun requirePrimaryKey(): K =
        checkNotNull(primaryKey) { "No primary key found" }

    @VisibleForTesting(otherwise = PROTECTED)
    abstract fun passphraseFor(key: K): ByteArray?

    protected abstract val K.privateKey: PgpField.PrivateKey

    fun sign(data: ByteArray): String = openPgp.signBinDetached(
        data,
        requirePrimaryKey().privateKey.string,
        passphrase
    )

    fun sign(data: String): String = openPgp.signTextDetached(
        data,
        requirePrimaryKey().privateKey.string,
        passphrase
    )

    /**
     * Encrypt for Message or Contact
     */
    fun encrypt(text: String, sign: Boolean): CipherText {
        val publicKey = buildArmoredPublicKey(requirePrimaryKey().privateKey)
        val privateKey = requirePrimaryKey().takeIf { sign }?.privateKey?.string
        val keyPassphrase = passphraseFor(requirePrimaryKey())
        val armored = openPgp.encryptMessage(
            text,
            publicKey,
            privateKey,
            keyPassphrase,
            true
        )
        return CipherText(armored)
    }

    /**
     * Decrypt Message or Contact Data
     */
    abstract fun decrypt(message: CipherText): TextDecryptionResult

    /**
     * Decrypt Message or Contact Data
     */
    fun decrypt(message: CipherText, publicKeys: List<ByteArray>, time: Long): TextDecryptionResult {
        return withCurrentKeys("Error decrypting message") { key ->
            val unarmored = Armor.unarmor(key.privateKey.string)
            openPgp.decryptMessageVerifyBinKeyPrivbinkeys(
                message.armored,
                publicKeys,
                listOf(unarmored),
                passphraseFor(key),
                time
            )
        }
    }

    fun getVerificationKeys(): List<ByteArray> = currentKeys
        .map { GoOpenPgpCrypto.newKeyFromArmored(it.privateKey.string).publicKey }

    fun buildArmoredPublicKeyOrNull(key: PgpField.PrivateKey): String? =
        runCatching { buildArmoredPublicKey(key) }
            .onFailure { Timber.e(it) }
            .getOrNull()

    fun buildArmoredPublicKey(key: PgpField.PrivateKey): String {
        val newKey = GoOpenPgpCrypto.newKeyFromArmored(key.string)
        return newKey.armoredPublicKey
            .also { newKey.clearPrivateParams() }
    }

    fun getUnarmoredKeys(): List<ByteArray> =
        currentKeys.map { Armor.unarmor(it.privateKey.string) }

    /**
     * Try to run [block] for every [K] in [currentKeys]
     * @return result of the first succeed [block]
     * @throws IllegalStateException if [block] fails for each key
     */
    protected inline fun <V> withCurrentKeys(errorMessage: String? = null, block: (K) -> V): V {
        for (key in currentKeys) {
            runCatching {
                return block(key)
            }.onFailure(Timber::d)
        }
        val messagePrefix = errorMessage?.let { "$it. " } ?: EMPTY_STRING
        throw IllegalStateException(
            "${messagePrefix}There is no valid decryption key, currentKeys size: ${currentKeys.size}"
        )
    }

    companion object {

        @JvmStatic
        @Deprecated(
            "Please use injected UserCrypto instead",
            ReplaceWith(
                "userCrypto",
                "ch.protonmail.android.crypto.UserCrypto"
            )
        )
        fun forUser(userManager: UserManager, userId: UserId): UserCrypto =
            UserCrypto(userManager, userManager.openPgp, userId)

        @JvmStatic
        fun forAddress(userManager: UserManager, userId: UserId, addressId: AddressId): AddressCrypto =
            AddressCrypto(userManager, userManager.openPgp, userId, addressId)
    }
}
