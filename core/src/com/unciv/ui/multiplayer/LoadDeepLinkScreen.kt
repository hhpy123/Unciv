package com.unciv.ui.multiplayer

import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.center
import com.unciv.ui.utils.toLabel

class LoadDeepLinkScreen : BaseScreen() {
    init {
        val loadingLabel = "Loading...".toLabel()
        stage.addActor(loadingLabel)
        loadingLabel.center(stage)
    }
}