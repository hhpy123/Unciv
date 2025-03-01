package com.unciv.ui.pickerscreens

import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick

open class PickerScreen(disableScroll: Boolean = false) : BaseScreen() {

    val pickerPane = PickerPane(disableScroll = disableScroll)

    /** @see PickerPane.closeButton */
    val closeButton by pickerPane::closeButton
    /** @see PickerPane.descriptionLabel */
    val descriptionLabel by pickerPane::descriptionLabel
    /** @see PickerPane.rightSideGroup */
    val rightSideGroup by pickerPane::rightSideGroup
    /** @see PickerPane.rightSideButton */
    val rightSideButton by pickerPane::rightSideButton

    /** @see PickerPane.topTable */
    val topTable by pickerPane::topTable
    /** @see PickerPane.scrollPane */
    val scrollPane by pickerPane::scrollPane
    /** @see PickerPane.splitPane */
    val splitPane by pickerPane::splitPane

    init {
        pickerPane.setFillParent(true)
        stage.addActor(pickerPane)
        ensureLayout() 
    }

    /** Make sure that anyone relying on sizes of the tables within this class during construction gets correct size readings.
     * (see [com.unciv.ui.pickerscreens.PolicyPickerScreen]) */
    private fun ensureLayout() {
        pickerPane.validate()
    }

    /**
     * Initializes the [Close button][closeButton]'s action (and the Back/ESC handler)
     * to return to the [previousScreen] if specified, or else to the world screen.
     */
    fun setDefaultCloseAction(previousScreen: BaseScreen?=null) {
        val closeAction = {
            if (previousScreen != null) game.setScreen(previousScreen)
            else game.resetToWorldScreen()
            dispose()
        }
        pickerPane.closeButton.onClick(closeAction)
        onBackButtonClicked(closeAction)
    }

    /** Enables the [rightSideButton]. See [pick] for a way to set the text. */
    fun setRightSideButtonEnabled(enabled: Boolean) {
        pickerPane.setRightSideButtonEnabled(enabled)
    }

    /** Sets the text of the [rightSideButton] and enables it if it's the player's turn */
    fun pick(rightButtonText: String) {
        pickerPane.pick(rightButtonText)
    }
}
