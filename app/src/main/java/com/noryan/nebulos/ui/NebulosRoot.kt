package com.noryan.nebulos.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noryan.nebulos.BuildConfig
import com.noryan.nebulos.CoreBootstrap
import com.noryan.nebulos.MainViewModel
import com.noryan.nebulos.R
import com.noryan.nebulos.api.System
import com.noryan.nebulos.Setup
import com.noryan.nebulos.data.CoreRouter
import com.noryan.nebulos.data.CoreRows
import com.noryan.nebulos.data.FolderSearch
import com.noryan.nebulos.data.CoreSetup
import com.noryan.nebulos.data.GameEntity
import com.noryan.nebulos.ui.components.ExpandBox
import com.noryan.nebulos.ui.components.FocusLine
import com.noryan.nebulos.ui.components.LocalListIndent
import com.noryan.nebulos.ui.components.OptionButton
import com.noryan.nebulos.ui.components.PixelIconButton
import com.noryan.nebulos.ui.components.PlainTextButton
import com.noryan.nebulos.ui.components.PixelIcons
import com.noryan.nebulos.ui.components.SubMenu
import com.noryan.nebulos.ui.components.TabButton
import com.noryan.nebulos.data.CollectionRules
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

fun systemLabel(s: System): String = when (s) {
    System.NES -> "NES"; System.SNES -> "SNES"; System.N64 -> "N64"; System.GB -> "GameBoy"; System.GBC -> "GameBoy Color"
    System.GBA -> "GBA"; System.NDS -> "DS"; System.N3DS -> "3DS"; System.GAMECUBE -> "GameCube"; System.WII -> "Wii"
    System.WIIU -> "Wii U"; System.SWITCH -> "Switch"; System.PS1 -> "PS1"; System.PS2 -> "PS2"; System.PSP -> "PSP"
    System.GENESIS -> "Genesis"; System.MASTER_SYSTEM -> "Master System"; System.PCE -> "PC Engine"; System.ATARI -> "Atari"
    System.ARCADE -> "Arcade"; System.OTHER -> "Other"
}

internal fun openAllFilesAccess(ctx: android.content.Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

@Composable
private fun rememberAllFilesGranted(): State<Boolean> {
    val st = remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) st.value = Environment.isExternalStorageManager() }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    return st
}

private fun sysOf(g: GameEntity) = runCatching { System.valueOf(g.system) }.getOrNull()

@Composable
fun NebulosRoot(vm: MainViewModel) {
    val granted by vm.accessGranted.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val romPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::pickRom) }
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::pickBios) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::pickSave) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshAccess() }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            val s = setup
            when {
                !granted -> AccessScreen()
                s is Setup.Searching -> SearchingScreen(s.kind)
                s is Setup.Choose -> {
                    val roms = s.kind == FolderSearch.Kind.ROMS
                    ChooseScreen(s.kind, s.candidates, s.cancellable,
                        onChoose = { if (roms) vm.chooseRom(it) else vm.chooseBios(it) },
                        onSearchAgain = { if (roms) vm.startRomSearch() else vm.searchBios(first = !s.cancellable) },
                        onPick = { if (roms) romPicker.launch(null) else biosPicker.launch(null) },
                        onSkipOrCancel = { if (roms) vm.cancelSetup() else vm.skipBios() })
                }
                s is Setup.ChooseSave -> SaveChooseScreen(s.candidates, s.cancellable, onChoose = vm::chooseSave, onSearchAgain = vm::searchSaveAgain,
                    onPick = { savePicker.launch(null) }, onCancel = vm::cancelSetup)
                s is Setup.NotFound -> NotFoundScreen(s.cancellable, onPick = { romPicker.launch(null) }, onSearchAgain = { vm.startRomSearch() }, onCancel = vm::cancelSetup)
                else -> MainScreen(vm, pickRom = { romPicker.launch(null) }, pickBios = { biosPicker.launch(null) }, pickSave = { savePicker.launch(null) })
            }
            LaunchProgressScreen(vm)
        }
    }
}

/** Full-screen loading screen while a heavy core (Switch) gets memory ready: progress bar, the current step, and the steps so far. B cancels. */
@Composable
private fun LaunchProgressScreen(vm: MainViewModel) {
    val p by vm.launchProgress.collectAsStateWithLifecycle()
    val cur = p ?: return
    BackHandler { vm.cancelLaunch() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LOADING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(cur.title.uppercase(), Modifier.padding(top = 6.dp, bottom = 24.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
            Box(Modifier.fillMaxWidth().height(22.dp).border(2.dp, androidx.compose.ui.graphics.Color(0xFF2A2A31)).padding(3.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(cur.fraction.coerceIn(0f, 1f)).background(androidx.compose.ui.graphics.Color(0xFFFF6B4A)))
            }
            Text("${(cur.fraction * 100).toInt().coerceIn(0, 100)}%", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(cur.message, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
            Column(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cur.steps.dropLast(1).forEach { Text("OK  $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            Text(coloredKeyHint("B CANCEL"), Modifier.padding(top = 26.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LogoMark(size: Int) {
    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size.dp))
}

@Composable
private fun MainScreen(vm: MainViewModel, pickRom: () -> Unit, pickBios: () -> Unit, pickSave: () -> Unit) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    val saved by vm.selectedTab.collectAsStateWithLifecycle()
    val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val n by vm.scanProgress.collectAsStateWithLifecycle()
    val cols = collections.orEmpty()
    val gameCount by vm.games.collectAsStateWithLifecycle()
    // A saved collection tab is only trusted once the DB has answered; a deleted collection falls back to GAMES (never written back here).
    val tab = if (collections == null) saved else CollectionRules.resolveTab(saved, cols)
    var tabOpts by remember { mutableStateOf<TabOptions?>(null) }
    // True while controller focus is somewhere in the top bar (a tab or the +), false when it is down on a game.
    var tabsFocused by remember { mutableStateOf(false) }
    val tabFocus = remember { HashMap<Long, FocusRequester>() }
    val focusSeen = remember { mutableStateOf(false) }
    fun focusOf(id: Long) = tabFocus.getOrPut(id) { FocusRequester() }
    // Start on the selected tab (the first focus lands on a tab). Focus can only be taken once the window has it.
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, collections != null) {
        if (windowFocused && collections != null && !focusSeen.value) {
            kotlinx.coroutines.delay(50)
            runCatching { focusOf(tab).requestFocus() }
        }
    }
    // Focus follows the screen that just closed: Settings -> the selected tab; the editor -> the tab it produced (or the + after a cancel).
    val plusFocus = remember { FocusRequester() }
    var closedSettings by remember { mutableStateOf(false) }
    var closedEditor by remember { mutableStateOf(false) }
    var sawSettings by remember { mutableStateOf(false) }
    var sawEditor by remember { mutableStateOf(false) }
    LaunchedEffect(settingsOpen) { if (settingsOpen) sawSettings = true else if (sawSettings) { sawSettings = false; closedSettings = true } }
    LaunchedEffect(editor != null) { if (editor != null) sawEditor = true else if (sawEditor) { sawEditor = false; closedEditor = true } }
    LaunchedEffect(closedSettings) { if (closedSettings) { closedSettings = false; runCatching { focusOf(tab).requestFocus() } } }
    LaunchedEffect(closedEditor, tab, cols) {
        if (closedEditor) {
            if (tab != CollectionRules.GAMES_TAB) { closedEditor = false; runCatching { focusOf(tab).requestFocus() } }
            else { kotlinx.coroutines.delay(250); closedEditor = false; runCatching { plusFocus.requestFocus() } }
        }
    }
    var lastOpts by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(tabOpts) {
        val o = tabOpts
        if (o != null) lastOpts = o.id
        else lastOpts?.let { id -> lastOpts = null; if (editor == null) runCatching { focusOf(id).requestFocus() } }
    }
    LaunchedEffect(cols, editor, settingsOpen) {
        val o = tabOpts
        if (o != null && (editor != null || cols.none { it.id == o.id })) tabOpts = null
    }
    // BACK (the B button), Start and X open Settings (which lists the device's apps first) from the home screen; Back closes Settings and the editor again.
    BackHandler {
        when {
            editor != null -> vm.cancelEditor()
            tabOpts != null -> tabOpts = null
            else -> vm.toggleSettings()
        }
    }
    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            focusSeen.value = true
            false
        }
    ) {
        val notice by vm.crashNotice.collectAsStateWithLifecycle()
        notice?.let { (title, hint) ->
            Column {
                FocusLine(title, onClick = vm::dismissCrashNotice, style = MaterialTheme.typography.titleSmall)
                Text(hint, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    editor != null -> CollectionEditorScreen(vm)
                    settingsOpen -> SettingsTab(vm, pickRom, pickBios, pickSave)
                    tab == CollectionRules.GAMES_TAB -> GameList(vm, null)
                    else -> GameList(vm, tab)
                }
            }
        }
        tabOpts?.let { o -> cols.firstOrNull { it.id == o.id }?.let { c -> CollectionOptions(vm, c, o.mode, { tabOpts = o.copy(mode = it) }, { tabOpts = null }) } }
        // Bottom bar: [GAMES] [+] and the collection tabs (they scroll when there are many; focus
        // scrolls them into view). Visual order matches the L1/R1 cycle. B (Back) opens Settings, X and Y do nothing on a tab; a long press on a collection
        // tab (touch) still opens that collection's own options.
        val newOpen = editor != null && editor?.collectionId == null
        val showSel = !settingsOpen && !newOpen
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).onFocusChanged { tabsFocused = it.hasFocus }, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cols.forEach { c ->
                        TabButton(CollectionRules.tabLabel(c.name), selected = showSel && tab == c.id,
                            modifier = Modifier.focusRequester(focusOf(c.id)).widthIn(min = 90.dp, max = 200.dp),
                            onLongPress = { tabOpts = if (tabOpts?.id == c.id) null else TabOptions(c.id, TabOptions.Mode.MENU) }) {
                            tabOpts = null; vm.selectTab(c.id)
                        }
                    }
                }
            }
            if (scanning) Text("SCANNING $n", Modifier.padding(start = 18.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
        }

    }
}

/** One line of the game list; [grouped] = it sits in a series tab, so the series part of its title is dropped. */
private data class ListRow(val g: com.noryan.nebulos.data.GameEntity, val grouped: Boolean)

/** A category tab across the top of the game list and the games under it. */
private class Category(val name: String, val rows: List<ListRow>) {
    /** Tab text: "The Legend of Zelda" -> "Zelda", "Super Mario" -> "Mario". */
    val label: String get() = name.removePrefix("The Legend of ").removePrefix("Super ")
}

/** Series that fold into one heading in the list: title prefix -> heading. A series with a single game stays a plain line. */
private val SERIES = listOf("metroid" to "Metroid", "the legend of zelda" to "The Legend of Zelda", "super mario" to "Super Mario",
    "sly " to "Sly Cooper", "jak " to "Jak and Daxter", "ratchet " to "Ratchet & Clank",
    "pokemon" to "Pokemon", "pokémon" to "Pokemon", "dark cloud" to "Dark Cloud", "god of war" to "God of War")

/** A game's line inside its series heading: the series part before " - " is dropped ("The Legend of Zelda - A Link to the Past" -> "A Link to the Past"). */
internal fun titleInSeries(title: String): String =
    if (title.startsWith("super mario land", ignoreCase = true) || title.startsWith("super mario world 2", ignoreCase = true)) title   // Land and Yoshi's Island keep their whole name
    else if (seriesOf(title) != null && " - " in title) title.substringAfter(" - ").trim().ifEmpty { title } else title

/** Every PlayStation 2 game folds into this one heading, whatever its series. */
private const val PS2_HEADING = "PS2"

private fun headingOf(g: com.noryan.nebulos.data.GameEntity): String? = if (g.system == "PS2") PS2_HEADING else seriesOf(g.title)

private fun seriesOf(title: String): String? = SERIES.firstOrNull { title.startsWith(it.first, ignoreCase = true) }?.second

/** Name of the tab that holds every game that is not part of a series. */
private const val OTHER_TAB = "Other"

/** Tabs: series with the most games first (ties by name; PS2 always counts as one), then OTHER with every game that is not part of one. */
private fun categoriesOf(games: List<com.noryan.nebulos.data.GameEntity>): List<Category> {
    val groups = games.groupBy { headingOf(it) }.filter { it.key != null && (it.value.size >= 2 || it.key == PS2_HEADING) }
    val out = groups.entries.sortedWith(compareBy({ -it.value.size }, { it.key!!.lowercase() })).map { (s, members) ->
        Category(s!!, members.sortedWith(compareBy({ if (s == PS2_HEADING) 0 else ReleaseOrder.rank(s, it.title) }, { it.title.lowercase() })).map { ListRow(it, true) })
    }
    val rest = games.filter { g -> headingOf(g)?.let { it in groups.keys } != true }
    return if (rest.isEmpty()) out else out + Category(OTHER_TAB, rest.map { ListRow(it, false) })
}

/** The game list: the whole library ([collectionId] == null) or one collection's games. Same rows; A / tap opens the launch panel. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GameList(vm: MainViewModel, collectionId: Long?) {
    val catStep by vm.catStep.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val all by vm.games.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val tick by vm.prefsTick.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val launching by vm.launching.collectAsStateWithLifecycle()
    var expanded by remember(collectionId) { mutableStateOf<Long?>(null) }
    val optFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(expanded) { if (expanded != null) runCatching { optFocus.requestFocus() } }
    var addOpen by remember(collectionId) { mutableStateOf<Long?>(null) }
    var coreOpen by remember(collectionId) { mutableStateOf<Long?>(null) }
    // A / tap on a game opens its launch panel (RESUME GAME when it has a save state, START GAME, GAME OPTIONS) instead of launching at once.
    var launchFor by remember(collectionId) { mutableStateOf<Long?>(null) }
    var launchResume by remember(collectionId) { mutableStateOf(false) }
    val launchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(launchFor) { if (launchFor != null) runCatching { launchFocus.requestFocus() } }
    BackHandler(enabled = launchFor != null || expanded != null) { launchFor = null; expanded = null; addOpen = null; coreOpen = null }
    val games = if (collectionId == null) all else CollectionRules.gamesOf(all, members[collectionId].orEmpty())
    val cols = collections.orEmpty()
    // Series (see [seriesOf]) are tabs across the top; the selected tab's games are listed under them. Fewer than two tabs = a plain list.
    val cats = remember(games) { categoriesOf(games) }
    var catSel by remember(collectionId) { mutableStateOf<String?>(null) }
    val cat = cats.firstOrNull { it.name == catSel } ?: cats.firstOrNull()
    val rows = cat?.rows.orEmpty()
    // Shoulder buttons step the selected tab (stops at the ends). A press that happened before this list appeared is ignored.
    val stepSeen = remember { intArrayOf(catStep.second) }
    LaunchedEffect(catStep) {
        if (catStep.second == stepSeen[0]) return@LaunchedEffect
        stepSeen[0] = catStep.second
        val i = cats.indexOfFirst { it === cat }
        if (i < 0) return@LaunchedEffect
        val to = (i + catStep.first).coerceIn(0, cats.lastIndex)
        if (to != i) { launchFor = null; expanded = null; addOpen = null; coreOpen = null; catSel = cats[to].name }
    }
    if (collectionId == null && games.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (scanning) "SCANNING..." else "NO GAMES FOUND\nPress X to open SETTINGS and check the ROM folder.", textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
    if (cats.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.forEach { c ->
            TabButton(c.label, selected = c === cat, modifier = Modifier.widthIn(min = 70.dp, max = 220.dp)) {
                launchFor = null; expanded = null; addOpen = null; coreOpen = null; catSel = c.name
            }
        }
    }
    // Pull the list down (touch) to rescan the ROM folder; the spinner shows while the scan runs.
    PullToRefreshBox(isRefreshing = scanning, onRefresh = { vm.rescan() }, modifier = Modifier.weight(1f).fillMaxWidth()) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (launching) item { Text("PREPARING ROM...", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        if (collectionId != null && games.isEmpty()) item(key = "empty") {
            FocusLine("NO GAMES - PRESS A TO EDIT", dim = true, onClick = { vm.editCollection(collectionId) })
        }
        items(rows, key = { it.g.id }) { row ->
            val g = row.g
            val sys = sysOf(g)
            val core = remember(g, tick, all.size, g.coreOverride) { vm.coreFor(g) }
            // Opens the launch panel under the game (a game with no core just launches, which explains why it can't).
            val open = {
                addOpen = null; coreOpen = null
                if (launchFor == g.id) launchFor = null
                else if (core == null) { launchFor = null; expanded = null; vm.launch(ctx, g) }
                else { expanded = null; launchResume = vm.hasSaveState(ctx, g); launchFor = g.id }
            }
            Column {
                FocusLine(
                    (if (g.favorite) "* " else "") + (if (row.grouped && g.system != "PS2") titleInSeries(g.title) else g.title) + if (core == null) " (NO CORE)" else "",
                    Modifier.onPreviewKeyEvent { e ->
                        // A acts on key up, like a tap; the press itself is consumed so the line does not also see it as a click.
                        if (e.key == Key.ButtonA) { if (e.type == KeyEventType.KeyUp) open(); true } else false
                    },
                    dim = core == null,
                    onClick = open
                )
                if (launchFor == g.id) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                        Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 10.dp).widthIn(max = 296.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // The first button is the one that continues: RESUME GAME with a save state, else START GAME.
                            if (launchResume) OptionButton("RESUME GAME", Modifier.focusRequester(launchFocus)) { launchFor = null; vm.launch(ctx, g) }
                            OptionButton("START GAME", if (launchResume) Modifier else Modifier.focusRequester(launchFocus)) { launchFor = null; vm.launch(ctx, g, fresh = launchResume) }
                            OptionButton("GAME OPTIONS") { launchFor = null; expanded = g.id }
                        }
                    }
                }
                if (expanded == g.id) {
                    val cands = sys?.let { CoreRouter.candidatesFor(it, g.gameId, title = g.title) }.orEmpty()
                    val auto = sys?.let { CoreRouter.autoChoice(it, g.title, gameId = g.gameId) }
                    val chosen = CoreRouter.resolveOverride(g.coreOverride, cands)
                    val autoLabel = "AUTO" + (auto?.displayName?.let { " ($it)" } ?: "")
                    // A narrow, centered stack of in-game-menu-style buttons under the game's name.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                        Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 10.dp).widthIn(max = 296.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (collectionId != null)
                                OptionButton("REMOVE FROM COLLECTION", Modifier.focusRequester(optFocus)) { vm.setMember(collectionId, g.id, false); expanded = null }
                            if (cols.isNotEmpty()) {
                                OptionButton("ADD TO COLLECTION", if (collectionId == null) Modifier.focusRequester(optFocus) else Modifier, selected = addOpen == g.id) { addOpen = if (addOpen == g.id) null else g.id }
                                if (addOpen == g.id) SubMenu {
                                    cols.forEach { c ->
                                        val inIt = g.id in members[c.id].orEmpty()
                                        OptionButton((if (inIt) "[X] " else "[ ] ") + CollectionRules.tabLabel(c.name), selected = inIt, small = true, flat = true) { vm.setMember(c.id, g.id, !inIt) }
                                    }
                                }
                            }
                            // One CORE field. Closed: a single button. Open: the same box grows, with the current choice as its header
                            // and every other core that can run this game listed under it.
                            val current = "CORE: " + (chosen?.displayName ?: autoLabel)
                            if (coreOpen != g.id) {
                                OptionButton(current, if (collectionId == null && cols.isEmpty()) Modifier.focusRequester(optFocus) else Modifier) { coreOpen = g.id }
                            } else ExpandBox(
                                header = { OptionButton(current, selected = true, flat = true) { coreOpen = null } },
                            ) {
                                // Everything except what the header already shows (AUTO resolves to [auto], so it is not listed again).
                                val others = cands.filter { it.id != chosen?.id && (chosen != null || it.id != auto?.id) }
                                if (chosen != null) OptionButton(autoLabel, small = true, flat = true) { vm.setGameCore(g, null); coreOpen = null }
                                others.forEach { c -> OptionButton(c.displayName, small = true, flat = true) { vm.setGameCore(g, c.id); coreOpen = null } }
                                if (chosen == null && others.isEmpty()) Text("NO OTHER CORES FOR THIS SYSTEM", Modifier.padding(8.dp).fillMaxWidth(),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
}

/** PlayStation 2 face-button colours for the key hints: A = red, B = blue, X = green, Y = pink (swapped from the PS2 colours). */
private val HINT_COLORS = mapOf(
    'A' to androidx.compose.ui.graphics.Color(0xFFE5566D), 'B' to androidx.compose.ui.graphics.Color(0xFF6C8FE0),
    'X' to androidx.compose.ui.graphics.Color(0xFF3FC9A0), 'Y' to androidx.compose.ui.graphics.Color(0xFFE08FC2),
)

/** Colours the button letters of a hint like "A SELECT    X OPTIONS" (a single letter standing alone as a word). */
private fun coloredKeyHint(hint: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    append(hint)
    Regex("(?<![A-Z])[ABXY](?![A-Z])").findAll(hint).forEach { m ->
        HINT_COLORS[m.value[0]]?.let { addStyle(androidx.compose.ui.text.SpanStyle(color = it), m.range.first, m.range.last + 1) }
    }
}

/** True while Nebulos is the device's chosen home app; re-read on every resume (the choice is made in a system screen). */
@Composable
private fun rememberIsHomeApp(): androidx.compose.runtime.State<Boolean> {
    val ctx = LocalContext.current
    val st = remember { mutableStateOf(isHomeApp(ctx)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) st.value = isHomeApp(ctx) }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    return st
}

private fun isHomeApp(ctx: android.content.Context): Boolean {
    val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
    return ctx.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
}

private fun openHomeAppSettings(ctx: android.content.Context) {
    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
        .onFailure { runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } }
}

@Composable
private fun SettingsTab(vm: MainViewModel, pickRom: () -> Unit, pickBios: () -> Unit, pickSave: () -> Unit) {
    val ctx = LocalContext.current
    val romDir by vm.romDir.collectAsStateWithLifecycle()
    val biosDir by vm.biosDir.collectAsStateWithLifecycle()
    val biosLines by vm.biosLines.collectAsStateWithLifecycle()
    val biosNote by vm.biosNote.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val saveInfo by vm.saveInfo.collectAsStateWithLifecycle()
    val saveStatus by vm.saveStatus.collectAsStateWithLifecycle()
    val saveWarning by vm.saveWarning.collectAsStateWithLifecycle()
    val saveOthers by vm.saveOthers.collectAsStateWithLifecycle()
    val tick by vm.prefsTick.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    fun toggle(k: String) { open = if (open == k) null else k }
    val allFiles by rememberAllFilesGranted()
    val apps = remember { launchableApps(ctx) }   // re-read each time Settings opens
    // A plain list: each setting's name on the left, its value on the right. A row that is open folds its options down under it, indented.
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { AppIconRow(apps) { launchApp(ctx, it) } }
        }
        item {
            SettingRow("ROM FOLDER", romDir?.let(::friendlyPath) ?: "NONE CHOSEN", open == "folder", { toggle("folder") }) {
                FocusLine("SEARCH AGAIN", right = "AUTO-FIND") { vm.startRomSearch() }
                FocusLine("PICK FOLDER...") { pickRom() }
                FocusLine("RESCAN NOW", right = status.ifEmpty { null }) { vm.rescan() }
                Note("Nebulos opens games by real path (All files access is ${if (allFiles) "on" else "OFF - press A on ALL FILES ACCESS below"}). Folders starting with . or _ and emulator data folders are not scanned.")
                if (!allFiles) FocusLine("ALL FILES ACCESS", right = "OFF (TAP)") { openAllFilesAccess(ctx) }
            }
        }
        item {
            SettingRow("BIOS FOLDER", biosDir?.let(::friendlyPath) ?: "NONE (OPTIONAL)", open == "bios", { toggle("bios") }) {
                if (biosDir == null) Note("No BIOS folder chosen. Games that need one will say so.")
                FocusLine("SEARCH AGAIN", right = "AUTO-FIND") { vm.searchBios(first = false) }
                FocusLine("PICK FOLDER...") { pickBios() }
                if (biosDir != null) FocusLine("IMPORT NOW") { vm.runBiosImport() }
                if (biosNote.isNotEmpty()) Note(biosNote)
                biosLines.forEach { l -> Note(l) }
                Note("Copies PS2 BIOS, Switch prod.keys + firmware and PS1 and other console BIOS into the cores. Your files are only read; newer copies are never overwritten.")
            }
        }
        item {
            val saveName = if (saveWarning != null) "APP STORAGE (WARNING)" else saveInfo?.path?.let(::friendlyPath) ?: "NOT SET UP YET"
            SettingRow("SAVE DATA FOLDER", saveName, open == "save", { toggle("save") }) {
                if (saveWarning != null) Note("WARNING: $saveWarning")
                saveInfo?.let { i -> Note("${i.dataFiles}${if (i.capped) "+" else ""} save files, ${com.noryan.nebulos.api.save.SaveRoot.sizeLabel(i.bytes)}, last changed ${saveDate(i.lastModified)}") }
                FocusLine("SEARCH AGAIN", right = "AUTO-FIND") { vm.searchSaveAgain() }
                FocusLine("PICK FOLDER...") { pickSave() }
                if (saveStatus.isNotEmpty()) Note("STATUS: $saveStatus")
                saveOthers.forEach { o -> FocusLine("USE ${o.path}", right = com.noryan.nebulos.api.save.SaveRoot.sizeLabel(o.bytes)) { vm.chooseSave(o.path) } }
                Note("All game saves and save states of every emulator live in this one folder (named ${com.noryan.nebulos.api.save.SaveRoot.FOLDER_NAME}). Copy it to another device's ROMs folder and Nebulos finds it by itself. Shader caches, configs, keys and BIOS are not stored there.")
            }
        }
        if (saveWarning != null && open != "save") item { Note("WARNING: $saveWarning") }
        item {
            val isHome by rememberIsHomeApp()
            FocusLine("NEBULOS LAUNCHER", right = if (isHome) "YES" else "NO", onClick = { openHomeAppSettings(ctx) })
        }
        item { Section("ABOUT") }
        item { Note("Nebulos ${BuildConfig.VERSION_NAME} - GPLv3. Embedded emulator cores keep their own licenses.") }
    }
    }
}

/**
 * One Settings row. Closed: a menu-style button (name, with [value] under it). Open: the same button becomes the header of a single
 * bordered box that holds [children] (options drawn as flat small buttons, notes as centered text).
 */
@Composable
private fun SettingRow(label: String, value: String?, expanded: Boolean, onToggle: () -> Unit, dim: Boolean = false, children: @Composable () -> Unit) {
    FocusLine(label, right = value, marked = expanded, dim = dim, onAlt = onToggle, onClick = onToggle)
    if (expanded) CompositionLocalProvider(LocalListIndent provides 28.dp) { Column { children() } }
}

private class LaunchableApp(val label: String, val component: android.content.ComponentName, val icon: android.graphics.drawable.Drawable)

/** The device's apps as a row of icons (scrolls sideways); controller focus shows as an orange border. */
@Composable
private fun AppIconRow(apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit) {
    if (apps.isEmpty()) return
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(apps, key = { it.component.flattenToString() }) { a ->
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val bitmap = remember(a) { a.icon.toBitmap(128, 128).asImageBitmap() }
            Image(bitmap, a.label, Modifier.size(56.dp).clip(shape)
                .border(androidx.compose.foundation.BorderStroke(if (focused) 3.dp else 2.dp, if (focused) androidx.compose.ui.graphics.Color(0xFFFF6B4A) else androidx.compose.ui.graphics.Color(0xFF2A2A31)), shape)
                .clickable(interactionSource = src, indication = null) { onLaunch(a) }.padding(4.dp))
        }
    }
}

/** Launcher-visible apps other than Nebulos, A-Z. Needs the LAUNCHER <queries> entry in the manifest (Android 11+ package visibility). */
private fun launchableApps(ctx: android.content.Context): List<LaunchableApp> {
    val pm = ctx.packageManager
    val main = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(main, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { LaunchableApp(it.loadLabel(pm).toString(), android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadIcon(pm)) }
        .sortedBy { it.label.lowercase() }
}

private fun launchApp(ctx: android.content.Context, a: LaunchableApp) {
    runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            .setComponent(a.component).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }
}

@Composable
internal fun Section(t: String) {
    Text(t, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun Note(t: String) {
    Text(t, Modifier.padding(start = 16.dp + LocalListIndent.current, end = 16.dp, top = 2.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A folder path for people: `/storage/emulated/0/_ROMS` -> `Internal Storage/_ROMS`, `/storage/6ADF-AE4E/_ROMS/x` -> `SD Card/_ROMS/x`.
 * Every folder of the real path is kept; only the volume part is renamed. Anything else keeps its own path, split the same way.
 */
internal fun friendlyPath(path: String): String {
    val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
    if (parts.firstOrNull() == "storage" && parts.size >= 2) {
        return if (parts[1] == "emulated") (listOf("Internal Storage") + parts.drop(3)).joinToString("/")
        else (listOf("SD Card") + parts.drop(2)).joinToString("/")
    }
    if (parts.firstOrNull() == "sdcard") return (listOf("Internal Storage") + parts.drop(1)).joinToString("/")
    return parts.joinToString("/").ifEmpty { "/" }
}
