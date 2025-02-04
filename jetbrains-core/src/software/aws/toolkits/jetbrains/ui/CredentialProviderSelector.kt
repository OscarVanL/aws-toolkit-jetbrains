// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION") // TODO: Migrate to SimpleListCellRenderer when we drop < 192 FIX_WHEN_MIN_IS_192

package software.aws.toolkits.jetbrains.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.util.containers.OrderedSet
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.jetbrains.utils.ui.selected
import javax.swing.JList

/**
 * Combo box used to select a credential provider
 */
class CredentialProviderSelector : ComboBox<Any>() {
    private val comboBoxModel = Model()

    init {
        model = comboBoxModel
        setRenderer(Renderer())
    }

    fun setCredentialsProviders(providers: List<ToolkitCredentialsProvider>) {
        comboBoxModel.items = providers
    }

    /**
     * Returns the ID of the selected provider, even if it was invalid
     */
    fun getSelectedCredentialsProvider(): String? {
        selected().let {
            return when (it) {
                is ToolkitCredentialsProvider -> it.id
                is String -> it
                else -> null
            }
        }
    }

    fun setSelectedCredentialsProvider(provider: ToolkitCredentialsProvider) {
        selectedItem = provider
    }

    /**
     * Secondary method to set the selected credential provider using ID.
     * This should be used if the provider ID does not exist and we want to expose that
     * to the user. For example, run configurations that persist the ID used to invoke it
     * that had the ID deleted between uses should show the raw ID and that it is not found
     */
    fun setSelectedInvalidCredentialsProvider(providerId: String?) {
        comboBoxModel.add(providerId)
        selectedItem = providerId
    }

    private inner class Model : CollectionComboBoxModel<Any>(OrderedSet<Any>()) {
        fun setItems(newItems: List<Any>) {
            internalList.apply {
                clear()
                addAll(newItems)
            }
        }
    }

    private inner class Renderer : ListCellRendererWrapper<Any>() {
        override fun customize(
            list: JList<*>,
            value: Any?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            when (value) {
                is String -> setText("$value (Not valid)")
                is ToolkitCredentialsProvider -> setText(value.displayName)
            }
        }
    }
}
