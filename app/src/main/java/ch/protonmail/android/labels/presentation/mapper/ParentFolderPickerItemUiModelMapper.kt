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

package ch.protonmail.android.labels.presentation.mapper

import android.content.Context
import androidx.core.graphics.toColorInt
import ch.protonmail.android.R
import ch.protonmail.android.labels.domain.model.LabelId
import ch.protonmail.android.labels.domain.model.LabelOrFolderWithChildren.Folder
import ch.protonmail.android.labels.presentation.model.ParentFolderPickerItemUiModel
import ch.protonmail.android.labels.presentation.model.ParentFolderPickerItemUiModel.Folder.Icon
import me.proton.core.domain.arch.Mapper
import timber.log.Timber
import javax.inject.Inject

/**
 * A Mapper of [ParentFolderPickerItemUiModel]
 *
 * @property useFolderColor whether the user enabled the settings for use Colors for Folders.
 *  TODO to be implemented in MAILAND-1818, ideally inject its use case. Currently defaults to `true`
 */
class ParentFolderPickerItemUiModelMapper @Inject constructor(
    context: Context
) : Mapper<Collection<Folder>, List<ParentFolderPickerItemUiModel>> {

    private val useFolderColor: Boolean = true

    private val defaultIconColor = context.getColor(R.color.icon_norm)

    /**
     * @param includeNoneUiModel whether [ParentFolderPickerItemUiModel.None] should be in the list ( at the first
     *  position )
     */
    fun toUiModels(
        folders: Collection<Folder>,
        currentSelectedFolder: LabelId?,
        includeNoneUiModel: Boolean
    ): List<ParentFolderPickerItemUiModel> {
        val noneUiModel = if (includeNoneUiModel) {
            listOf(ParentFolderPickerItemUiModel.None(isSelected = currentSelectedFolder == null))
        } else {
            emptyList()
        }

        return noneUiModel + folders.flatMap { label ->
            labelToUiModels(
                folder = label,
                currentSelectedFolder = currentSelectedFolder,
                folderLevel = 0,
                parentColor = null
            )
        }
    }

    private fun labelToUiModels(
        folder: Folder,
        currentSelectedFolder: LabelId?,
        folderLevel: Int,
        parentColor: Int?
    ): List<ParentFolderPickerItemUiModel.Folder> {

        val parent = ParentFolderPickerItemUiModel.Folder(
            id = folder.id,
            name = folder.name,
            icon = buildIcon(folder, parentColor),
            folderLevel = folderLevel,
            isSelected = folder.id == currentSelectedFolder,
        )
        val children = folder.children.flatMap {
            labelToUiModels(
                folder = it,
                currentSelectedFolder = currentSelectedFolder,
                folderLevel = folderLevel + 1,
                parentColor = parent.icon.colorInt
            )
        }
        return listOf(parent) + children
    }

    private fun buildIcon(
        folder: Folder,
        parentColor: Int?
    ): Icon {
        val folderColorInt = folder.color.toColorIntOrNull() ?: parentColor ?: defaultIconColor
        return if (folder.children.isNotEmpty()) {
            if (useFolderColor) {
                Icon(
                    drawableRes = Icon.WITH_CHILDREN_COLORED_ICON_RES,
                    colorInt = folderColorInt,
                    contentDescriptionRes = Icon.WITH_CHILDREN_CONTENT_DESCRIPTION_RES
                )
            } else {
                Icon(
                    drawableRes = Icon.WITH_CHILDREN_BW_ICON_RES,
                    colorInt = defaultIconColor,
                    contentDescriptionRes = Icon.WITH_CHILDREN_CONTENT_DESCRIPTION_RES
                )
            }
        } else {
            if (useFolderColor) {
                Icon(
                    drawableRes = Icon.WITHOUT_CHILDREN_COLORED_ICON_RES,
                    colorInt = folderColorInt,
                    contentDescriptionRes = Icon.WITHOUT_CHILDREN_CONTENT_DESCRIPTION_RES
                )
            } else {
                Icon(
                    drawableRes = Icon.WITHOUT_CHILDREN_BW_ICON_RES,
                    colorInt = defaultIconColor,
                    contentDescriptionRes = Icon.WITHOUT_CHILDREN_CONTENT_DESCRIPTION_RES
                )
            }
        }
    }

    private fun String.toColorIntOrNull(): Int? = try {
        toColorInt()
    } catch (exc: IllegalArgumentException) {
        Timber.e(exc, "Unknown label color: $this")
        null
    }
}