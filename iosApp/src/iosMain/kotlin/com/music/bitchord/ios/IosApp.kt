package com.music.bitchord.ios

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.components.BottomFadeScrim
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FLOATING_BAR_MAX_WIDTH
import com.music.bitchord.ui.components.FloatingBarsTapGuard
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.GlassNavBar
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.ui.components.SongActionsSheet
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.TopBarAccountButton
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.components.topBarContentPadding
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryLink
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.MoodGenrePlaylistsScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.ui.utils.guardSheetFromContentTouches
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

// ---- Theme: the phone's (app/ui/theme/Theme.kt). Android bundles SF Pro; iOS's system font is it.

private val AccentRed = Color(0xFFFA2D48)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
)

private val BitChordTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
)

@Composable
fun BitChordIosTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = BitChordTypography,
        content = content,
    )
}

// ---- Root: MainActivity's BitChordApp, the parts iOS has -----------------------------------------

private const val TAB_HOME = 0
private const val TAB_EXPLORE = 1
private const val TAB_LIBRARY = 2
private const val TAB_SEARCH = 3

@Composable
fun IosApp() {
    BitChordIosTheme {
        val liquidGlassEnabled by AppSettings.liquidGlass.collectAsStateWithLifecycle()
        // As on the phone: the glass samples a recording of the page, laid over the window's
        // background so the recording is opaque like the screen.
        val windowBackground = MaterialTheme.colorScheme.background
        val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
            {
                drawRect(windowBackground)
                drawContent()
            }
        }
        val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
        CompositionLocalProvider(
            LocalLiquidGlassEnabled provides liquidGlassEnabled,
            LocalAppBackdrop provides appBackdrop,
        ) {
            AppScaffold(appBackdrop)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(appBackdrop: LayerBackdrop) {
    // `-debugTab N` (launch argument → NSUserDefaults' argument domain) opens on that tab: CI's
    // simulator run screenshots each one.
    var selectedTab by remember {
        mutableIntStateOf(NSUserDefaults.standardUserDefaults.integerForKey("debugTab").toInt().coerceIn(TAB_HOME, TAB_SEARCH))
    }
    val stacks = remember { List(4) { mutableStateListOf<Route>() } }
    var showPlayer by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var menuFromPlayer by remember { mutableStateOf(false) }
    var searchFocusRequested by remember { mutableStateOf(false) }
    val current by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val loading by IosPlayer.isLoading.collectAsStateWithLifecycle()
    val account by IosAccount.account.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val homeRefreshing by IosAppModel.homeRefreshing.collectAsStateWithLifecycle()
    val exploreRefreshing by IosAppModel.exploreRefreshing.collectAsStateWithLifecycle()
    val libraryRefreshing by IosAppModel.libraryRefreshing.collectAsStateWithLifecycle()
    val query by IosAppModel.query.collectAsStateWithLifecycle()

    val hazeState = remember { HazeState() }
    val glassActive = LocalLiquidGlassEnabled.current && isGlassSupported()
    val glassSamplesBackdrop = glassActive && !reduceDynamicBlur
    val navBarScroll = rememberFloatingTabBarScrollConnection()

    LaunchedEffect(Unit) {
        IosAppModel.loadHome()
        IosAppModel.loadExplore()
        IosAppModel.loadLibrary()
    }

    val nav = remember {
        object : Nav {
            override fun open(route: Route) { stacks[selectedTab].add(route) }
            override fun back() { stacks[selectedTab].removeLastOrNull() }
            override fun menu(song: Song, fromPlayer: Boolean) {
                menuSong = song
                menuFromPlayer = fromPlayer
            }
            override fun player() { showPlayer = true }
        }
    }

    // One list state per tab and per pushed page, hoisted so the top bar can read the scroll
    // (its small title fades in once the big in-list header has gone) and tabs keep their place.
    val tabListStates = remember { List(4) { LazyListState() } }
    val pageListStates = remember { mutableMapOf<Route, LazyListState>() }
    // A pushed page's "show all" shelf and filter box, as MainActivity's detailActiveShelf /
    // detailSearching: owned here because the top bar's title and back answer to them.
    val activeShelves = remember { mutableStateMapOf<Route, HomeShelf>() }
    val searchingPages = remember { mutableStateMapOf<Route, Boolean>() }
    val detailTitleDrop = with(LocalDensity.current) { 320.dp.toPx() }
    val route = stacks[selectedTab].lastOrNull()
    val currentListState = route?.let { pageListStates.getOrPut(it) { LazyListState() } } ?: tabListStates[selectedTab]
    val scrolled by remember(currentListState) {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 24
        }
    }
    // A release page's large header owns the title until it has scrolled away.
    val detailScrolled by remember(currentListState, detailTitleDrop) {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > detailTitleDrop
        }
    }
    val activeShelf = route?.let { activeShelves[it] }

    // Room for the frosted bar above and the tab bar (plus mini player) below — the phone's numbers.
    val listPadding = PaddingValues(
        top = topBarContentPadding(),
        bottom = if (current != null) 210.dp else 140.dp,
    )

    val homeLabel = stringResource(R.string.home)
    val exploreLabel = stringResource(R.string.explore)
    val libraryLabel = stringResource(R.string.library)
    val searchLabel = stringResource(R.string.search)
    val tabs = remember(homeLabel, exploreLabel, libraryLabel, searchLabel) {
        listOf(
            BottomTab(homeLabel, BitChordIcons.Home),
            BottomTab(exploreLabel, BitChordIcons.TabExplore),
            BottomTab(libraryLabel, BitChordIcons.TabLibrary),
            BottomTab(searchLabel, BitChordIcons.TabSearch),
        )
    }

    val onTabSelected: (Int) -> Unit = { index ->
        stacks[index].clear()
        selectedTab = index
        if (index == TAB_SEARCH) {
            IosAppModel.setQuery("")
            searchFocusRequested = true
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = selectedTab to route,
            transitionSpec = {
                if (initialState.second == null && targetState.second == null) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .then(
                    if (glassActive) {
                        Modifier
                            .then(if (glassSamplesBackdrop) Modifier.layerBackdrop(appBackdrop) else Modifier)
                            .nestedScroll(navBarScroll)
                    } else {
                        Modifier
                    },
                ),
            label = "content",
        ) { (tab, shown) ->
            val listState = shown?.let { pageListStates.getOrPut(it) { LazyListState() } } ?: tabListStates[tab]
            when (shown) {
                null -> TabRoot(tab, listPadding, listState, nav)
                is Route.Detail -> ReleasePage(
                    shown.browseId, shown.title, shown.subtitle, shown.thumbnailUrl, BrowseType.OTHER,
                    listPadding, listState, nav,
                    activeShelf = activeShelves[shown],
                    onActiveShelfChange = { shelf -> if (shelf == null) { activeShelves.remove(shown) } else { activeShelves[shown] = shelf } },
                    searching = searchingPages[shown] == true,
                    onSearchingChange = { searchingPages[shown] = it },
                )
                is Route.Artist -> ReleasePage(
                    shown.browseId, shown.name, "", null, BrowseType.ARTIST,
                    listPadding, listState, nav,
                    activeShelf = activeShelves[shown],
                    onActiveShelfChange = { shelf -> if (shelf == null) { activeShelves.remove(shown) } else { activeShelves[shown] = shelf } },
                    searching = false,
                    onSearchingChange = {},
                )
                is Route.Shelves -> ShelvesPage(shown, listPadding, listState, nav)
                Route.Settings -> SettingsPage(listPadding, listState)
            }
        }

        BottomFadeScrim(
            pageColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter).rotate(180f),
        )

        val searchFieldFocus = remember { FocusRequester() }
        val pages by IosAppModel.pages.collectAsStateWithLifecycle()
        val routePage = when (route) {
            is Route.Detail -> pages[route.browseId]
            is Route.Artist -> pages[route.browseId]
            else -> null
        }
        FrostedTopBar(
            title = activeShelf?.title ?: when (route) {
                is Route.Detail -> routePage?.title ?: route.title
                is Route.Artist -> routePage?.title ?: route.name
                is Route.Shelves -> route.title
                Route.Settings -> stringResource(R.string.settings)
                null -> tabs[selectedTab].label
            },
            transparentBackdrop = true,
            artworkPageChrome = true,
            backButtonHazeState = hazeState,
            trailingTitle = if (activeShelf != null) routePage?.title else null,
            scrolled = when (route) {
                Route.Settings, is Route.Shelves -> true
                null -> scrolled || selectedTab == TAB_SEARCH
                else -> activeShelf != null || detailScrolled
            },
            refreshing = route == null && when (selectedTab) {
                TAB_HOME -> homeRefreshing
                TAB_EXPLORE -> exploreRefreshing
                TAB_LIBRARY -> libraryRefreshing
                else -> false
            },
            onBack = when {
                route != null && activeShelf != null -> ({ activeShelves.remove(route); Unit })
                route != null -> ({ nav.back() })
                else -> null
            },
            modifier = Modifier.align(Alignment.TopCenter),
            accessory = if (selectedTab == TAB_SEARCH && route == null) {
                {
                    val keyboard = LocalSoftwareKeyboardController.current
                    LaunchedEffect(searchFocusRequested) {
                        if (searchFocusRequested) {
                            runCatching { searchFieldFocus.requestFocus() }
                            keyboard?.show()
                            searchFocusRequested = false
                        }
                    }
                    SearchField(
                        query = query,
                        onQueryChange = IosAppModel::setQuery,
                        onSubmit = { IosAppModel.submit() },
                        focusRequester = searchFieldFocus,
                    )
                }
            } else {
                null
            },
            actions = {
                val releaseSongs = (routePage?.songs as? UiState.Success)?.data.orEmpty()
                if (route is Route.Detail && activeShelf == null && routePage?.type != BrowseType.ARTIST &&
                    releaseSongs.isNotEmpty()
                ) {
                    val searching = searchingPages[route] == true
                    IconButton(onClick = { searchingPages[route] = !searching }) {
                        Icon(
                            if (searching) Icons.Rounded.Close else BitChordIcons.Search,
                            contentDescription = stringResource(if (searching) R.string.close_search else R.string.search_this_list),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                val artistId = when (route) {
                    is Route.Artist -> route.browseId
                    is Route.Detail -> route.browseId.takeIf { it.startsWith("UC") }
                    else -> null
                }
                if (artistId != null && activeShelf == null) {
                    IconButton(onClick = { IosPlayer.bridge.share("https://music.youtube.com/channel/$artistId") }) {
                        Icon(
                            Icons.Rounded.IosShare,
                            contentDescription = stringResource(R.string.share),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (route == null) {
                    TopBarAccountButton(
                        account = account,
                        onClick = { nav.open(Route.Settings) },
                    )
                }
            },
        )

        BottomFadeScrim(
            withMiniPlayer = current != null,
            pageColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val floatingBarsHeight = remember { mutableIntStateOf(0) }
        FloatingBarsTapGuard(
            barsHeight = { floatingBarsHeight.intValue },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (glassActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                    .fillMaxWidth()
                    .onSizeChanged { floatingBarsHeight.intValue = it.height },
            ) {
                GlassNavBar(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onTabSelected = onTabSelected,
                    scrollConnection = navBarScroll,
                    song = current,
                    isPlaying = playing,
                    isLoading = loading,
                    onPlayPause = IosPlayer::togglePlay,
                    onNext = IosPlayer::next,
                    onPrevious = IosPlayer::previous,
                    onExpand = { showPlayer = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                    .fillMaxWidth()
                    .onSizeChanged { floatingBarsHeight.intValue = it.height },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                current?.let { song ->
                    MiniPlayer(
                        song = song,
                        isPlaying = playing,
                        isLoading = loading,
                        hazeState = hazeState,
                        onPlayPause = IosPlayer::togglePlay,
                        onNext = IosPlayer::next,
                        onPrevious = IosPlayer::previous,
                        onExpand = { showPlayer = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                FloatingBottomBar(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    hazeState = hazeState,
                    onTabSelected = onTabSelected,
                )
            }
        }
    }

    if (showPlayer && current != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlayer = false },
            sheetState = sheet,
            shape = PlayerSheetShape,
            containerColor = Color.Transparent,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            sheetMaxWidth = Dp.Unspecified,
        ) {
            Box(Modifier.fillMaxSize().guardSheetFromContentTouches(sheet)) {
                NowPlaying(nav, onClose = { showPlayer = false })
            }
        }
    }

    menuSong?.let { song ->
        SongMenu(song, menuFromPlayer, nav, onDismiss = { menuSong = null }, onClosePlayer = { showPlayer = false })
    }
}

/**
 * The player sheet's clip, as on the phone: as tall again above its top edge, so the artwork
 * travelling ahead of the sheet isn't cut off mid-air.
 */
private object PlayerSheetShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, -size.height, size.width, size.height))
}

interface Nav {
    fun open(route: Route)
    fun back()
    fun menu(song: Song, fromPlayer: Boolean = false)
    fun player()
}

/** Where a card or result leads: a track plays, anything else opens its page. */
fun Nav.openItem(item: ShelfItem) {
    val videoId = item.videoId
    if (videoId != null) {
        IosPlayer.play(listOf(Song(videoId, item.title, item.subtitle.substringBefore(" • "), item.thumbnailUrl)), radio = true)
        return
    }
    val browseId = item.browseId ?: return
    openBrowse(browseId, item.title, item.subtitle, item.thumbnailUrl)
}

fun Nav.openBrowse(browseId: String, title: String, subtitle: String, thumbnailUrl: String?) = when {
    browseId.startsWith("UC") -> open(Route.Artist(browseId, title))
    browseId.startsWith("FEmusic_moods_and_genres") -> open(Route.Shelves(title, browseId, null))
    else -> open(Route.Detail(browseId, title, subtitle, thumbnailUrl))
}

// ---- The four tabs, each the phone's own page from :sharedUi -------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabRoot(tab: Int, padding: PaddingValues, listState: LazyListState, nav: Nav) {
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    when (tab) {
        TAB_HOME -> {
            val state by IosAppModel.home.collectAsStateWithLifecycle()
            val refreshing by IosAppModel.homeRefreshing.collectAsStateWithLifecycle()
            val loadingMore by IosAppModel.homeLoadingMore.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                listState = listState,
                currentSong = currentSong,
                isPlaying = playing,
                title = stringResource(R.string.listen_now),
                signedIn = signedIn,
                onSignIn = { IosPlayer.bridge.openSignIn() },
                onItemClick = { item, _ -> nav.openItem(item) },
                onItemLongPress = { item ->
                    item.videoId?.let { nav.menu(Song(it, item.title, item.subtitle, item.thumbnailUrl)) }
                },
                onRetry = { IosAppModel.loadHome() },
                refreshing = refreshing,
                onRefresh = { IosAppModel.loadHome(refresh = true) },
                pullState = rememberPullToRefreshState(),
                contentPadding = padding,
                onLoadMore = if (IosAppModel.canLoadMoreHome) ({ IosAppModel.moreHome() }) else null,
                loadingMore = loadingMore,
            )
        }
        TAB_EXPLORE -> {
            val state by IosAppModel.explore.collectAsStateWithLifecycle()
            val refreshing by IosAppModel.exploreRefreshing.collectAsStateWithLifecycle()
            ExploreScreen(
                state = state,
                listState = listState,
                onCategoryClick = { nav.open(Route.Shelves(it.title, it.browseId, it.params)) },
                onRetry = { IosAppModel.loadExplore() },
                refreshing = refreshing,
                onRefresh = { IosAppModel.loadExplore(refresh = true) },
                pullState = rememberPullToRefreshState(),
                contentPadding = padding,
            )
        }
        TAB_LIBRARY -> {
            val state by IosAppModel.library.collectAsStateWithLifecycle()
            val refreshing by IosAppModel.libraryRefreshing.collectAsStateWithLifecycle()
            LibraryScreen(
                signedIn = signedIn,
                state = state,
                listState = listState,
                onShelfItemClick = { nav.openItem(it) },
                onShelfItemLongPress = { item -> item.videoId?.let { nav.menu(Song(it, item.title, item.subtitle, item.thumbnailUrl)) } },
                onNewPlaylist = { IosPlayer.bridge.toast("Create playlists on music.youtube.com for now") },
                onShowAll = { shelf -> shelf.moreBrowseId?.let { nav.open(Route.Shelves(shelf.title, it, shelf.moreParams)) } },
                replay = {},
                onSignIn = { IosPlayer.bridge.openSignIn() },
                onRetry = { IosAppModel.loadLibrary() },
                refreshing = refreshing,
                onRefresh = { IosAppModel.loadLibrary(refresh = true) },
                pullState = rememberPullToRefreshState(),
                contentPadding = padding,
                links = if (signedIn) {
                    listOf(
                        LibraryLink(ShelfItem("Liked Music", "Auto playlist", null, null, "VLLM"), BitChordIcons.Heart),
                        LibraryLink(ShelfItem("Equalizer — Selaras", "Per-device EQ", null, null, "selaras:"), Icons.Filled.Equalizer),
                    )
                } else {
                    listOf(LibraryLink(ShelfItem("Equalizer — Selaras", "Per-device EQ", null, null, "selaras:"), Icons.Filled.Equalizer))
                },
                deviceItems = emptyList(),
            )
        }
        else -> SearchTab(padding, listState, nav)
    }
}

@Composable
private fun SearchTab(padding: PaddingValues, listState: LazyListState, nav: Nav) {
    val query by IosAppModel.query.collectAsStateWithLifecycle()
    val filter by IosAppModel.filter.collectAsStateWithLifecycle()
    val results by IosAppModel.results.collectAsStateWithLifecycle()
    val loadingMore by IosAppModel.searchLoadingMore.collectAsStateWithLifecycle()
    val history by IosAppModel.history.collectAsStateWithLifecycle()
    val suggestions by IosAppModel.suggestions.collectAsStateWithLifecycle()
    val typeahead by IosAppModel.typeahead.collectAsStateWithLifecycle()
    val reset by IosAppModel.scrollReset.collectAsStateWithLifecycle()
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        onQueryChange = IosAppModel::setQuery,
        filter = filter,
        currentSong = currentSong,
        isPlaying = playing,
        onFilterChange = IosAppModel::setFilter,
        results = results,
        loadingMore = loadingMore,
        onLoadMore = IosAppModel::moreResults,
        listState = listState,
        scrollResetTrigger = reset,
        focusRequested = false,
        onFocusHandled = {},
        onSongClick = { songs, i ->
            songs.getOrNull(i)?.let(IosAppModel::rememberSong)
            IosPlayer.play(listOf(songs[i]), radio = true)
        },
        onSongLongPress = { nav.menu(it) },
        onSongSwipe = IosPlayer::playNext,
        onTopResultPlay = { IosAppModel.rememberSong(it); IosPlayer.play(listOf(it), radio = true) },
        onTopResultPlaylist = { IosPlayer.play(listOf(it), radio = true) },
        onBrowseClick = { item: BrowseItem ->
            IosAppModel.rememberBrowse(item)
            when (item.type) {
                BrowseType.ARTIST -> nav.open(Route.Artist(item.browseId, item.title))
                else -> nav.open(Route.Detail(item.browseId, item.title, item.subtitle, item.thumbnailUrl))
            }
        },
        history = history,
        suggestions = suggestions,
        typeaheadResults = typeahead,
        onSubmit = { IosAppModel.submit() },
        onSuggestionClick = { IosAppModel.submit(it) },
        onHistoryClick = { entry: SearchHistoryEntity ->
            when (entry.entityType) {
                EntityType.TRACK -> IosPlayer.play(listOf(Song(entry.id, entry.title, entry.subtitle.substringAfter(" • "), entry.artworkUrl)), radio = true)
                EntityType.ARTIST -> nav.open(Route.Artist(entry.id, entry.title))
                else -> nav.open(Route.Detail(entry.id, entry.title, entry.subtitle, entry.artworkUrl))
            }
        },
        onHistoryRemove = IosAppModel::forget,
        onHistoryClear = IosAppModel::clearHistory,
        onTypeaheadLongPress = { nav.menu(it) },
        contentPadding = padding,
        topPadding = topBarContentPadding(),
        // The field is in the top bar, as on the phone; see the bar's accessory.
        showField = false,
    )
}

// ---- Album / playlist / artist: the phone's DetailScreen -----------------------------------------

@Composable
private fun ReleasePage(
    browseId: String,
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    type: BrowseType,
    padding: PaddingValues,
    listState: LazyListState,
    nav: Nav,
    activeShelf: HomeShelf?,
    onActiveShelfChange: (HomeShelf?) -> Unit,
    searching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
) {
    if (browseId == "selaras:") {
        LaunchedEffect(Unit) { nav.back(); IosPlayer.bridge.openSelaras() }
        return
    }
    LaunchedEffect(browseId) { IosAppModel.openPage(browseId, title, subtitle, thumbnailUrl, type) }
    val pages by IosAppModel.pages.collectAsStateWithLifecycle()
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    val page = pages[browseId]
        ?: DetailPage(browseId, title, subtitle, thumbnailUrl, UiState.Loading, IosAppModel.browseTypeOf(browseId, type))
    val withAlbum: (Song) -> Song = { song ->
        if (page.type == BrowseType.ALBUM) song.copy(albumName = song.albumName ?: page.title) else song
    }
    DetailScreen(
        page = page,
        currentSong = currentSong,
        isPlaying = playing,
        listState = listState,
        activeShelf = activeShelf,
        onActiveShelfChange = onActiveShelfChange,
        onSongClick = { songs, index -> IosPlayer.play(songs.map(withAlbum), index) },
        onSongLongPress = { nav.menu(withAlbum(it)) },
        onSongSwipe = { IosPlayer.playNext(withAlbum(it)) },
        onShuffle = { songs ->
            if (songs.isNotEmpty()) {
                val shuffled = songs.map(withAlbum).shuffled()
                IosPlayer.play(shuffled, 0)
            }
        },
        onSectionItemClick = { item ->
            val id = item.browseId
            if (id != null) {
                nav.open(Route.Detail(id, item.title, item.subtitle, item.thumbnailUrl))
            } else {
                nav.openItem(item)
            }
        },
        searching = searching,
        onSearchingChange = onSearchingChange,
        onArtistClick = { id, name -> nav.open(Route.Artist(id, name)) },
        onAddSuggested = { song -> IosAppModel.addSuggestedSong(browseId, song) },
        onToggleLibrary = if (signedIn) ({ IosAppModel.toggleLibrary(browseId) }) else null,
        onToggleSubscription = if (signedIn) ({ IosAppModel.toggleSubscription(browseId) }) else null,
        contentPadding = padding,
    )
}

// ---- Mood / genre / "show all" -------------------------------------------------------------------

@Composable
private fun ShelvesPage(route: Route.Shelves, padding: PaddingValues, listState: LazyListState, nav: Nav) {
    val key = route.browseId + (route.params ?: "")
    LaunchedEffect(key) { IosAppModel.openShelves(route) }
    val all by IosAppModel.shelves.collectAsStateWithLifecycle()
    MoodGenrePlaylistsScreen(
        title = route.title,
        state = all[key] ?: UiState.Loading,
        listState = listState,
        onItemClick = { item -> nav.openItem(item) },
        onRetry = { IosAppModel.openShelves(route) },
        contentPadding = padding,
    )
}

@Composable
private fun ShelfCarousel(shelf: HomeShelf, nav: Nav) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionTitle(shelf.title)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(shelf.items, key = { (it.browseId ?: it.videoId ?: it.title) + it.subtitle }) { item ->
                Column(Modifier.width(150.dp).clickable { nav.openItem(item) }) {
                    AsyncImage(
                        model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(150.dp).clip(
                            if (item.browseId?.startsWith("UC") == true) CircleShape else RoundedCornerShape(10.dp),
                        ).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Text(item.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- Settings ----------------------------------------------------------------------------------

@Composable
private fun SettingsPage(padding: PaddingValues, listState: LazyListState) {
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    val account by IosAccount.account.collectAsStateWithLifecycle()
    val autoplay by IosSettings.autoplay.collectAsStateWithLifecycle()
    val synced by IosSettings.syncedLyrics.collectAsStateWithLifecycle()
    val blur by IosSettings.lyricsBlur.collectAsStateWithLifecycle()
    val reduce by IosSettings.reduceAnimation.collectAsStateWithLifecycle()
    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
        item { SectionTitle("Account") }
        item {
            if (signedIn) {
                ListItem(
                    headlineContent = { Text(account?.name ?: "Signed in") },
                    supportingContent = { Text(account?.email ?: "YouTube Music") },
                    leadingContent = {
                        AsyncImage(account?.thumbnailUrl, null, Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                    },
                    trailingContent = { TextButton(onClick = { IosPlayer.bridge.signOut() }) { Text("Sign out") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            } else {
                ListItem(
                    headlineContent = { Text("Sign in to YouTube Music") },
                    supportingContent = { Text("Your library, likes and personal recommendations") },
                    leadingContent = { Icon(Icons.Filled.Login, null) },
                    modifier = Modifier.clickable { IosPlayer.bridge.openSignIn() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item { SectionTitle("Sound") }
        item {
            ListItem(
                headlineContent = { Text("Selaras equalizer") },
                supportingContent = { Text("Parametric EQ, AutoEq headphone profiles, per output") },
                leadingContent = { Icon(Icons.Filled.Equalizer, null) },
                modifier = Modifier.clickable { IosPlayer.bridge.openSelaras() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        item { Toggle("AutoPlay", "Keep playing similar music when the queue ends", autoplay) { IosSettings.set(IosSettings.autoplay, "autoplay", it) } }
        item { SectionTitle("Lyrics") }
        item { Toggle("Synced lyrics", "Follow the song line by line", synced) { IosSettings.set(IosSettings.syncedLyrics, "syncedLyrics", it) } }
        item { Toggle("Blur other lines", "Focus on the line being sung", blur) { IosSettings.set(IosSettings.lyricsBlur, "lyricsBlur", it) } }
        item { SectionTitle("Appearance") }
        item { Toggle("Liquid glass", "The floating tab bar and buttons as refracting glass", liquidGlass) { AppSettings.setLiquidGlass(it) } }
        item { Toggle("Reduce dynamic blur", null, reduceBlur) { AppSettings.setReduceDynamicBlur(it) } }
        item { Toggle("Reduce animation", null, reduce) { IosSettings.set(IosSettings.reduceAnimation, "reduceAnimation", it) } }
        item {
            Text(
                "BitChord for iOS · BitChordxSelaras",
                Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = value, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ---- Now Playing: the phone's player, whole --------------------------------------------------------

@Composable
private fun NowPlaying(nav: Nav, onClose: () -> Unit) {
    val song by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val loading by IosPlayer.isLoading.collectAsStateWithLifecycle()
    val duration by IosPlayer.durationMs.collectAsStateWithLifecycle()
    val queue by IosPlayer.queue.collectAsStateWithLifecycle()
    val index by IosPlayer.index.collectAsStateWithLifecycle()
    val repeat by IosPlayer.repeatMode.collectAsStateWithLifecycle()
    val shuffle by IosPlayer.shuffle.collectAsStateWithLifecycle()
    val autoplay by IosSettings.autoplay.collectAsStateWithLifecycle()
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    val account by IosAccount.account.collectAsStateWithLifecycle()
    val like by IosPlayer.likeStatus.collectAsStateWithLifecycle()
    val lyrics by IosPlayer.lyrics.collectAsStateWithLifecycle()
    val lyricsSource by IosPlayer.lyricsSource.collectAsStateWithLifecycle()
    val lyricsChecked by IosPlayer.lyricsChecked.collectAsStateWithLifecycle()
    val providers by IosPlayer.lyricsProviderStates.collectAsStateWithLifecycle()
    var offsetOpen by remember { mutableStateOf(false) }
    val current = song ?: return

    // Album and artist links, which a card-started track often lacks.
    var links by remember(current.videoId) { mutableStateOf<Song?>(null) }
    LaunchedEffect(current.videoId) {
        if (current.albumId == null || current.artistId == null) links = YtMusicRepository.trackLinks(current.videoId).getOrNull()
    }
    val shown = links?.let { current.copy(artistId = current.artistId ?: it.artistId, albumId = current.albumId ?: it.albumId, albumName = current.albumName ?: it.albumName) } ?: current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        NowPlayingScreen(
            song = shown,
            isPlaying = playing,
            isLoading = loading,
            position = IosPlayer.position,
            durationMs = duration,
            audioVersionSwitching = false,
            qualityUpgraded = false,
            queue = queue,
            queueIndex = index,
            hasPrevious = index > 0 || IosPlayer.position.positionMs > 3_000,
            hasNext = index < queue.lastIndex || autoplay,
            repeatMode = repeat,
            shuffleEnabled = shuffle,
            autoplayEnabled = autoplay,
            signedIn = signedIn,
            accountName = account?.name,
            likeStatus = like,
            onToggleLike = IosPlayer::toggleLike,
            onPlayPause = IosPlayer::togglePlay,
            onNext = IosPlayer::next,
            onPrevious = IosPlayer::previous,
            onBlockedControl = {},
            onSeek = IosPlayer::seek,
            onSeekFraction = IosPlayer::seekFraction,
            onToggleShuffle = IosPlayer::toggleShuffle,
            onCycleRepeat = IosPlayer::cycleRepeat,
            onToggleAutoplay = IosPlayer::toggleAutoplay,
            onJumpTo = IosPlayer::jumpTo,
            onRemoveFromQueue = IosPlayer::remove,
            onMoveInQueue = IosPlayer::move,
            onClearQueue = IosPlayer::clearQueue,
            onOpenMenu = { nav.menu(shown, fromPlayer = true) },
            onOpenAlbum = { albumId -> onClose(); nav.open(Route.Detail(albumId, shown.albumName ?: "", shown.artist, shown.thumbnailUrl)) },
            onOpenArtist = { browseId, name ->
                onClose()
                if (browseId != null) nav.open(Route.Artist(browseId, name))
            },
            onOpenPlaybackSource = {},
            onListenTogether = { IosPlayer.bridge.toast("Listen Together isn't on iOS yet") },
            lyrics = lyrics,
            lyricsSource = lyricsSource,
            lyricsProviderStates = providers,
            onSelectLyricsProvider = IosPlayer::selectLyricsProvider,
            lyricsUnavailable = lyricsChecked && lyrics == null,
            lyricsOffsetOpen = offsetOpen,
            onDismissLyricsOffset = { offsetOpen = false },
            windowWidth = maxWidth,
            windowHeight = maxHeight,
        )
    }
}

// ---- Song menu: the phone's SongActionsSheet ---------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(song: Song, fromPlayer: Boolean, nav: Nav, onDismiss: () -> Unit, onClosePlayer: () -> Unit) {
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    val likes by IosPlayer.likeStatuses.collectAsStateWithLifecycle()
    LaunchedEffect(song.videoId) { IosPlayer.loadSongMenu(song.videoId) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The sheet paints itself in the track's own colours, corners and drag handle included.
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        SongActionsSheet(
            song = song,
            signedIn = signedIn,
            likeStatus = likes[song.videoId] ?: LikeStatus.INDIFFERENT,
            onPlayNext = { IosPlayer.playNext(song); onDismiss() },
            onAddToQueue = { IosPlayer.addToQueue(song); onDismiss() },
            onStartRadio = { IosPlayer.play(listOf(song), radio = true); onDismiss() },
            onDownload = { IosPlayer.bridge.toast("Downloads aren't available on iOS yet") },
            onToggleLike = { IosPlayer.toggleLike(song.videoId) },
            onToggleDislike = { IosPlayer.toggleDislike(song.videoId) },
            onAddToPlaylist = {
                onDismiss()
                IosPlayer.bridge.toast("Adding to playlists isn't available on iOS yet")
            },
            onOpenAlbum = { id ->
                onDismiss()
                if (fromPlayer) onClosePlayer()
                nav.open(Route.Detail(id, song.albumName ?: song.title, song.artist, song.thumbnailUrl))
            },
            onOpenArtist = { id ->
                onDismiss()
                if (fromPlayer) onClosePlayer()
                nav.open(Route.Artist(id, song.artist))
            },
            showSleepTimer = fromPlayer,
            onShare = {
                IosPlayer.bridge.share("https://music.youtube.com/watch?v=${song.videoId}")
                onDismiss()
            },
        )
    }
}

// ---- Small parts -------------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ErrorText(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = retry, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
    }
}
