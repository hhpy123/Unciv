package com.unciv

object Constants {
    const val worker = "Worker"
    const val settler = "Settler"
    const val eraSpecificUnit = "Era Starting Unit"
    const val spreadReligionAbilityCount = "Spread Religion"
    const val removeHeresyAbilityCount = "Remove Foreign religions from your own cities"

    const val impassable = "Impassable"
    const val ocean = "Ocean"

    /** The "Coast" _terrain_ */
    const val coast = "Coast"
    /** The "Coastal" terrain _filter_ */
    const val coastal = "Coastal"

    const val mountain = "Mountain"
    const val hill = "Hill"
    const val plains = "Plains"
    const val lakes = "Lakes"
    const val desert = "Desert"
    const val grassland = "Grassland"
    const val tundra = "Tundra"
    const val snow = "Snow"

    const val forest = "Forest"
    const val jungle = "Jungle"
    const val ice = "Ice"
    val vegetation = arrayOf(forest, jungle)

    // Note the difference in case. **Not** interchangeable!
    // TODO this is very opaque behaviour to modders
    /** The "Fresh water" terrain _unique_ */
    const val freshWater = "Fresh water"
    /** The "Fresh Water" terrain _filter_ */
    const val freshWaterFilter = "Fresh Water"

    const val barbarianEncampment = "Barbarian encampment"

    const val peaceTreaty = "Peace Treaty"
    const val researchAgreement = "Research Agreement"
    const val openBorders = "Open Borders"
    const val random = "Random"
    const val unknownNationName = "???"
    const val unknownCityName = "???"

    const val fort = "Fort"

    const val futureTech = "Future Tech"
    // Easter egg name. Is to avoid conflicts when players name their own religions.
    // This religion name should never be displayed.
    const val noReligionName = "The religion of TheLegend27"
    
    const val neutralVictoryType = "Neutral"

    const val cancelImprovementOrder = "Cancel improvement order"
    const val tutorialPopupNamePrefix = "Tutorial: "

    const val OK = "OK"
    const val close = "Close"
    const val yes = "Yes"
    const val no = "No"

    const val barbarians = "Barbarians"
    const val spectator = "Spectator"

    const val rising = "Rising"
    const val lowering = "Lowering"
    const val remove = "Remove "

    const val uniqueOrDelimiter = "\" OR \""
    
    const val dropboxMultiplayerServer = "Dropbox"

    /**
     * Use this to determine whether a [MapUnit][com.unciv.logic.map.MapUnit]'s movement is exhausted
     * (currentMovement <= this) if and only if a fuzzy comparison is needed to account for Float rounding errors.
     * _Most_ checks do compare to 0!
     */
    const val minimumMovementEpsilon = 0.05f  // 0.1f was used previously, too - here for global searches
    const val defaultFontSize = 18
    const val headingFontSize = 24
}
