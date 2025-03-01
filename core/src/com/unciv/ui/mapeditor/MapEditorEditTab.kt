package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.mapgenerator.MapGenerationRandomness
import com.unciv.logic.map.mapgenerator.RiverGenerator
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.mapeditor.MapEditorOptionsTab.TileMatchFuzziness
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.*

class MapEditorEditTab(
    private val editorScreen: MapEditorScreen,
    headerHeight: Float
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val subTabs: TabbedPager
    private val brushTable = Table(skin)
    private val brushSlider: UncivSlider
    private val brushLabel = "Brush ([1]):".toLabel()
    private val brushCell: Cell<Actor>

    private var ruleset = editorScreen.ruleset
    internal val randomness = MapGenerationRandomness()  // for auto river

    enum class BrushHandlerType { None, Direct, Tile, Road, River, RiverFromTo }
    private var brushHandlerType = BrushHandlerType.None

    /** This applies the current brush to one tile **without** validation or transient updates */
    private var brushAction: (TileInfo)->Unit = {}
    /** Brush size: 1..5 means hexagon of radius x, -1 means floodfill */
    internal var brushSize = 1
        set(value) {
            field = value
            brushSlider.value = if (value < 0) 6f else value.toFloat()
        }
    /** Copy of same field in [MapEditorOptionsTab] */
    private var tileMatchFuzziness = TileMatchFuzziness.CompleteMatch

    /** Tile to run a river _from_ (both ends are set with the same tool, so we need the memory) */
    private var riverStartTile: TileInfo? = null
    /** Tile to run a river _to_ */
    private var riverEndTile: TileInfo? = null

    private enum class AllEditSubTabs(
        val caption: String,
        val key: Char,
        val icon: String,
        val instantiate: (MapEditorEditTab, Ruleset)->Table
    ) {
        Terrain("Terrain", 't', "OtherIcons/Terrains", { parent, ruleset -> MapEditorEditTerrainTab(parent, ruleset) }),
        TerrainFeatures("Features", 'f', "OtherIcons/Star", { parent, ruleset -> MapEditorEditFeaturesTab(parent, ruleset) }),
        NaturalWonders("Wonders", 'w', "OtherIcons/Star", { parent, ruleset -> MapEditorEditWondersTab(parent, ruleset) }),
        Resources("Resources", 'r', "OtherIcons/Resources", { parent, ruleset -> MapEditorEditResourcesTab(parent, ruleset) }),
        Improvements("Improvements", 'i', "OtherIcons/Improvements", { parent, ruleset -> MapEditorEditImprovementsTab(parent, ruleset) }),
        Rivers("Rivers", 'v', "OtherIcons/Star", { parent, ruleset -> MapEditorEditRiversTab(parent, ruleset) }),
        StartingLocations("Starting locations", 's', "OtherIcons/Nations", { parent, ruleset -> MapEditorEditStartsTab(parent, ruleset) }),
        // Units("Units", 'u', "OtherIcons/Shield", { parent, ruleset -> MapEditorEditUnitsTab(parent, ruleset) }),
    }

    init {
        top()

        brushTable.apply {
            pad(5f)
            defaults().pad(10f).left()
            add(brushLabel)
            brushCell = add().padLeft(0f)
            brushSlider = UncivSlider(1f,6f,1f, getTipText = { getBrushTip(it).tr() }) {
                brushSize = if (it > 5f) -1 else it.toInt()
                brushLabel.setText("Brush ([${getBrushTip(it).take(1)}]):".tr())
            }
            add(brushSlider).padLeft(0f)
        }

        // TabbedPager parameters specify content page area. Assume subTabs will have the same headerHeight
        // as the master tabs, the 2f is for the separator, and the 10f for reduced header padding:
        val subTabsHeight = editorScreen.stage.height - 2 * headerHeight - brushTable.prefHeight - 2f + 10f
        val subTabsWidth = editorScreen.getToolsWidth()
        subTabs = TabbedPager(
            minimumHeight = subTabsHeight,
            maximumHeight = subTabsHeight,
            minimumWidth = subTabsWidth,
            maximumWidth = subTabsWidth,
            headerPadding = 5f,
            capacity = AllEditSubTabs.values().size
        )

        for (page in AllEditSubTabs.values()) {
            // Empty tabs with placeholders, filled when activated()
            subTabs.addPage(page.caption, Group(), ImageGetter.getImage(page.icon), 20f,
                shortcutKey = KeyCharAndCode(page.key), disabled = true)
        }
        subTabs.selectPage(0)

        add(brushTable).fillX().row()
        addSeparator(Color.GRAY)
        add(subTabs).left().fillX().row()
    }

    private fun selectPage(index: Int) = subTabs.selectPage(index)

    fun setBrush(name: String, icon: String, isRemove: Boolean = false, applyAction: (TileInfo)->Unit) {
        brushHandlerType = BrushHandlerType.Tile
        brushCell.setActor(FormattedLine(name, icon = icon, iconCrossed = isRemove).render(0f))
        brushAction = applyAction
    }
    private fun setBrush(name: String, icon: Actor, applyAction: (TileInfo)->Unit) {
        brushHandlerType = BrushHandlerType.Tile
        val line = Table().apply {
            add(icon).padRight(10f)
            add(name.toLabel())
        }
        brushCell.setActor(line)
        brushAction = applyAction
    }
    fun setBrush(handlerType: BrushHandlerType, name: String, icon: String,
                 isRemove: Boolean = false, applyAction: (TileInfo)->Unit) {
        setBrush(name, icon, isRemove, applyAction)
        brushHandlerType = handlerType
    }
    fun setBrush(handlerType: BrushHandlerType, name: String, icon: Actor, applyAction: (TileInfo)->Unit) {
        setBrush(name, icon, applyAction)
        brushHandlerType = handlerType
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (editorScreen.editTabsNeedRefresh) {
            // ruleset has changed
            ruleset = editorScreen.ruleset
            ImageGetter.setNewRuleset(ruleset)
            for (page in AllEditSubTabs.values()) {
                val tab = page.instantiate(this, ruleset)
                subTabs.replacePage(page.caption, tab)
                subTabs.setPageDisabled(page.caption, (tab as IMapEditorEditSubTabs).isDisabled())
            }
            brushHandlerType = BrushHandlerType.None
            editorScreen.editTabsNeedRefresh = false
        }

        editorScreen.tileClickHandler = this::tileClickHandler
        pager.setScrollDisabled(true)
        tileMatchFuzziness = editorScreen.tileMatchFuzziness

        val keyPressDispatcher = editorScreen.keyPressDispatcher
        keyPressDispatcher['t'] = { selectPage(0) }
        keyPressDispatcher['f'] = { selectPage(1) }
        keyPressDispatcher['w'] = { selectPage(2) }
        keyPressDispatcher['r'] = { selectPage(3) }
        keyPressDispatcher['i'] = { selectPage(4) }
        keyPressDispatcher['v'] = { selectPage(5) }
        keyPressDispatcher['s'] = { selectPage(6) }
        keyPressDispatcher['u'] = { selectPage(7) }
        keyPressDispatcher['1'] = { brushSize = 1 }
        keyPressDispatcher['2'] = { brushSize = 2 }
        keyPressDispatcher['3'] = { brushSize = 3 }
        keyPressDispatcher['4'] = { brushSize = 4 }
        keyPressDispatcher['5'] = { brushSize = 5 }
        keyPressDispatcher[KeyCharAndCode.ctrl('f')] = { brushSize = -1 }
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        editorScreen.tileClickHandler = null
        editorScreen.keyPressDispatcher.revertToCheckPoint()
    }

    fun tileClickHandler(tile: TileInfo) {
        if (brushSize < -1 || brushSize > 5 || brushHandlerType == BrushHandlerType.None) return
        editorScreen.hideSelection()

        when (brushHandlerType) {
            BrushHandlerType.None -> Unit
            BrushHandlerType.RiverFromTo ->
                selectRiverFromOrTo(tile)
            else ->
                paintTilesWithBrush(tile)
        }
    }

    private fun selectRiverFromOrTo(tile: TileInfo) {
        val tilesToHighlight = mutableSetOf(tile)
        if (tile.isLand) {
            // Land means river from. Start the river if we have a 'to', choose a 'to' if not.
            riverStartTile = tile
            if (riverEndTile != null) return paintRiverFromTo()
            val riverGenerator = RiverGenerator(editorScreen.tileMap, randomness, ruleset)
            riverEndTile = riverGenerator.getClosestWaterTile(tile)
            if (riverEndTile != null) tilesToHighlight += riverEndTile!!
        } else {
            // Water means river to. Start the river if we have a 'from'
            riverEndTile = tile
            if (riverStartTile != null) return paintRiverFromTo()
        }
        tilesToHighlight.forEach { editorScreen.highlightTile(it, Color.BLUE) }
    }
    private fun paintRiverFromTo() {
        val resultingTiles = mutableSetOf<TileInfo>()
        randomness.seedRNG(editorScreen.newMapParameters.seed)
        try {
            val riverGenerator = RiverGenerator(editorScreen.tileMap, randomness, ruleset)
            riverGenerator.spawnRiver(riverStartTile!!, riverEndTile!!, resultingTiles)
        } catch (ex: Exception) {
            println(ex.message)
            ToastPopup("River generation failed!", editorScreen)
        }
        riverStartTile = null
        riverEndTile = null
        editorScreen.isDirty = true
        resultingTiles.forEach { editorScreen.updateAndHighlight(it, Color.SKY) }
    }

    internal fun paintTilesWithBrush(tile: TileInfo) {
        val tiles =
            if (brushSize == -1) {
                val bfs = BFS(tile) { it.isSimilarEnough(tile) }
                bfs.stepToEnd()
                bfs.getReachedTiles().asSequence()
            } else {
                tile.getTilesInDistance(brushSize - 1)
            }
        tiles.forEach {
            when (brushHandlerType) {
                BrushHandlerType.Direct -> directPaintTile(it)
                BrushHandlerType.Tile -> paintTile(it)
                BrushHandlerType.Road -> roadPaintTile(it)
                BrushHandlerType.River -> riverPaintTile(it)
                else -> {} // other cases can't reach here
            }
        }
    }

    /** Used for starting locations - no temp tile as brushAction needs to access tile.tileMap */ 
    private fun directPaintTile(tile: TileInfo) {
        brushAction(tile)
        editorScreen.isDirty = true
        editorScreen.updateAndHighlight(tile)
    }

    /** Used for rivers - same as directPaintTile but may need to update 10,12 and 2 o'clock neighbor tiles too */
    private fun riverPaintTile(tile: TileInfo) {
        directPaintTile(tile)
        tile.neighbors.forEach {
            if (it.position.x > tile.position.x || it.position.y > tile.position.y)
                editorScreen.updateTile(it)
        }
    }

    // Used for roads - same as paintTile but all neighbors need TileGroup.update too
    private fun roadPaintTile(tile: TileInfo) {
        if (!paintTile(tile)) return
        tile.neighbors.forEach { editorScreen.updateTile(it) }
    }

    /** apply brush to a single tile */
    private fun paintTile(tile: TileInfo): Boolean {
        // Approach is "Try - matches - leave or revert" because an off-map simulation would fail some tile filters
        val savedTile = tile.clone()
        val paintedTile = tile.clone()
        brushAction(paintedTile)
        paintedTile.ruleset = ruleset
        try {
            paintedTile.setTerrainTransients()
        } catch (ex: Exception) {
            val message = ex.message ?: throw ex
            if (!message.endsWith("not exist in this ruleset!")) throw ex
            ToastPopup(message, editorScreen)
        }

        brushAction(tile)
        tile.setTerrainTransients()
        tile.normalizeToRuleset(ruleset)    // todo: this does not do what we need
        if (!paintedTile.isSimilarEnough(tile)) {
            // revert tile to original state
            tile.applyFrom(savedTile)
            return false
        }

        if (tile.naturalWonder != savedTile.naturalWonder)
            editorScreen.naturalWondersNeedRefresh = true
        editorScreen.isDirty = true
        editorScreen.updateAndHighlight(tile)
        return true
    }

    private fun TileInfo.isSimilarEnough(other: TileInfo) = when {
        tileMatchFuzziness <= TileMatchFuzziness.CompleteMatch &&
                improvement != other.improvement ||
                roadStatus != other.roadStatus -> false
        tileMatchFuzziness <= TileMatchFuzziness.NoImprovement &&
                resource != other.resource -> false
        tileMatchFuzziness <= TileMatchFuzziness.BaseAndFeatures &&
                terrainFeatures.toSet() != other.terrainFeatures.toSet() -> false
        tileMatchFuzziness <= TileMatchFuzziness.BaseTerrain &&
                baseTerrain != other.baseTerrain -> false
        tileMatchFuzziness <= TileMatchFuzziness.LandOrWater &&
                isLand != other.isLand -> false
        else -> naturalWonder == other.naturalWonder
    }

    private fun TileInfo.applyFrom(other: TileInfo) {
        // 90% copy w/o position, improvement times or transients. Add units once Unit paint is in.
        baseTerrain = other.baseTerrain
        setTerrainFeatures(other.terrainFeatures)
        resource = other.resource
        improvement = other.improvement
        naturalWonder = other.naturalWonder
        roadStatus = other.roadStatus
        hasBottomLeftRiver = other.hasBottomLeftRiver
        hasBottomRightRiver = other.hasBottomRightRiver
        hasBottomRiver = other.hasBottomRiver
        setTerrainTransients()
    }

    companion object {
        private fun getBrushTip(value: Float) = if (value > 5f) "Floodfill" else value.toInt().toString()
    }
}
