package com.unciv.ui.options

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.newgamescreen.TranslatedSelectBox
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*


private const val MOD_CHECK_WITHOUT_BASE = "-none-"

class ModCheckTab(
    val screen: BaseScreen
) : Table(), TabbedPager.IPageExtensions {
    private val fixedContent = Table()

    // marker for automatic first run on selecting the tab
    private var modCheckFirstRun = true

    private var modCheckBaseSelect: TranslatedSelectBox? = null
    private val modCheckResultTable = Table()

    init {
        defaults().pad(10f).align(Align.top)

        fixedContent.defaults().pad(10f).align(Align.top)
        val reloadModsButton = "Reload mods".toTextButton().onClick(::runAction)
        fixedContent.add(reloadModsButton).row()

        val labeledBaseSelect = Table().apply {
            add("Check extension mods based on:".toLabel()).padRight(10f)
            val baseMods = listOf(MOD_CHECK_WITHOUT_BASE) + RulesetCache.getSortedBaseRulesets()
            modCheckBaseSelect = TranslatedSelectBox(baseMods, MOD_CHECK_WITHOUT_BASE, BaseScreen.skin).apply {
                selectedIndex = 0
                onChange { runAction() }
            }
            add(modCheckBaseSelect)
        }
        fixedContent.add(labeledBaseSelect).row()

        add(modCheckResultTable)
    }

    private fun runAction() {
        if (modCheckFirstRun) runModChecker()
        else runModChecker(modCheckBaseSelect!!.selected.value)
    }

    override fun getFixedContent() = fixedContent

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        runAction()
    }

    fun runModChecker(base: String = MOD_CHECK_WITHOUT_BASE) {

        modCheckFirstRun = false
        if (modCheckBaseSelect == null) return

        modCheckResultTable.clear()

        val rulesetErrors = RulesetCache.loadRulesets()
        if (rulesetErrors.isNotEmpty()) {
            val errorTable = Table().apply { defaults().pad(2f) }
            for (rulesetError in rulesetErrors)
                errorTable.add(rulesetError.toLabel()).width(stage.width / 2).row()
            modCheckResultTable.add(errorTable)
        }

        modCheckResultTable.add("Checking mods for errors...".toLabel()).row()
        modCheckBaseSelect!!.isDisabled = true

        launchCrashHandling("ModChecker") {
            for (mod in RulesetCache.values.sortedBy { it.name }) {
                if (base != MOD_CHECK_WITHOUT_BASE && mod.modOptions.isBaseRuleset) continue

                val modLinks =
                    if (base == MOD_CHECK_WITHOUT_BASE) mod.checkModLinks(forOptionsPopup = true)
                    else RulesetCache.checkCombinedModLinks(linkedSetOf(mod.name), base, forOptionsPopup = true)
                modLinks.sortByDescending { it.errorSeverityToReport }
                val noProblem = !modLinks.isNotOK()
                if (modLinks.isNotEmpty()) modLinks += Ruleset.RulesetError("", Ruleset.RulesetErrorSeverity.OK)
                if (noProblem) modLinks += Ruleset.RulesetError("No problems found.".tr(), Ruleset.RulesetErrorSeverity.OK)

                postCrashHandlingRunnable {
                    // When the options popup is already closed before this postRunnable is run,
                    // Don't add the labels, as otherwise the game will crash
                    if (stage == null) return@postCrashHandlingRunnable
                    // Don't just render text, since that will make all the conditionals in the mod replacement messages move to the end, which makes it unreadable
                    // Don't use .toLabel() either, since that activates translations as well, which is what we're trying to avoid,
                    // Instead, some manual work needs to be put in.

                    val iconColor = modLinks.getFinalSeverity().color
                    val iconName = when (iconColor) {
                        Color.RED -> "OtherIcons/Stop"
                        Color.YELLOW -> "OtherIcons/ExclamationMark"
                        else -> "OtherIcons/Checkmark"
                    }
                    val icon = ImageGetter.getImage(iconName)
                        .apply { color = Color.BLACK }
                        .surroundWithCircle(30f, color = iconColor)

                    val expanderTab = ExpanderTab(mod.name, icon = icon, startsOutOpened = false) {
                        it.defaults().align(Align.left)
                        if (!noProblem && mod.folderLocation != null) {
                            val replaceableUniques = getDeprecatedReplaceableUniques(mod)
                            if (replaceableUniques.isNotEmpty())
                                it.add("Autoupdate mod uniques".toTextButton()
                                    .onClick { autoUpdateUniques(screen, mod, replaceableUniques) }).pad(10f).row()
                        }
                        for (line in modLinks) {
                            val label = Label(line.text, BaseScreen.skin)
                                .apply { color = line.errorSeverityToReport.color }
                            label.wrap = true
                            it.add(label).width(stage.width / 2).row()
                        }
                        if (!noProblem)
                            it.add("Copy to clipboard".toTextButton().onClick {
                                Gdx.app.clipboard.contents = modLinks
                                    .joinToString("\n") { line -> line.text }
                            }).row()
                    }

                    val loadingLabel = modCheckResultTable.children.last()
                    modCheckResultTable.removeActor(loadingLabel)
                    modCheckResultTable.add(expanderTab).row()
                    modCheckResultTable.add(loadingLabel).row()
                }
            }

            // done with all mods!
            postCrashHandlingRunnable {
                modCheckResultTable.removeActor(modCheckResultTable.children.last())
                modCheckBaseSelect!!.isDisabled = false
            }
        }
    }


    private fun getDeprecatedReplaceableUniques(mod: Ruleset): HashMap<String, String> {

        val objectsToCheck = sequenceOf(
            mod.beliefs,
            mod.buildings,
            mod.nations,
            mod.policies,
            mod.technologies,
            mod.terrains,
            mod.tileImprovements,
            mod.unitPromotions,
            mod.unitTypes,
            mod.units,
        )
        val allDeprecatedUniques = HashSet<String>()
        val deprecatedUniquesToReplacementText = HashMap<String, String>()

        val deprecatedUniques = objectsToCheck
            .flatMap { it.values }
            .flatMap { it.uniqueObjects }
            .filter { it.getDeprecationAnnotation() != null }


        for (deprecatedUnique in deprecatedUniques) {
            if (allDeprecatedUniques.contains(deprecatedUnique.text)) continue
            allDeprecatedUniques.add(deprecatedUnique.text)

            // note that this replacement does not contain conditionals attached to the original!


            var uniqueReplacementText = deprecatedUnique.getReplacementText(mod)
            while (Unique(uniqueReplacementText).getDeprecationAnnotation() != null)
                uniqueReplacementText = Unique(uniqueReplacementText).getReplacementText(mod)

            for (conditional in deprecatedUnique.conditionals)
                uniqueReplacementText += " <${conditional.text}>"
            val replacementUnique = Unique(uniqueReplacementText)

            val modInvariantErrors = mod.checkUnique(
                replacementUnique,
                false,
                "",
                UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant,
                deprecatedUnique.sourceObjectType!!
            )
            for (error in modInvariantErrors)
                println(error.text + " - " + error.errorSeverityToReport)
            if (modInvariantErrors.isNotEmpty()) continue // errors means no autoreplace

            if (mod.modOptions.isBaseRuleset) {
                val modSpecificErrors = mod.checkUnique(
                    replacementUnique,
                    false,
                    "",
                    UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant,
                    deprecatedUnique.sourceObjectType
                )
                for (error in modSpecificErrors)
                    println(error.text + " - " + error.errorSeverityToReport)
                if (modSpecificErrors.isNotEmpty()) continue
            }

            deprecatedUniquesToReplacementText[deprecatedUnique.text] = uniqueReplacementText
            println("Replace \"${deprecatedUnique.text}\" with \"$uniqueReplacementText\"")
        }
        return deprecatedUniquesToReplacementText
    }

    private fun autoUpdateUniques(screen: BaseScreen, mod: Ruleset, replaceableUniques: HashMap<String, String>) {

        val filesToReplace = listOf(
            "Beliefs.json",
            "Buildings.json",
            "Nations.json",
            "Policies.json",
            "Techs.json",
            "Terrains.json",
            "TileImprovements.json",
            "UnitPromotions.json",
            "UnitTypes.json",
            "Units.json",
        )

        val jsonFolder = mod.folderLocation!!.child("jsons")
        for (fileName in filesToReplace) {
            val file = jsonFolder.child(fileName)
            if (!file.exists() || file.isDirectory) continue
            var newFileText = file.readString()
            for ((original, replacement) in replaceableUniques) {
                newFileText = newFileText.replace("\"$original\"", "\"$replacement\"")
            }
            file.writeString(newFileText, false)
        }
        val toastText = "Uniques updated!"
        ToastPopup(toastText, screen).open(true)
        runModChecker()
    }
}
