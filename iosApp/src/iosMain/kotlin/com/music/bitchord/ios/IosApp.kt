package com.music.bitchord.ios

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
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
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryLink
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.ui.utils.guardSheetFromContentTouches
import kotlinx.coroutines.launch

// ---- Theme: the phone's (app/ui/theme/Theme.kt), on the system's SF Pro --------------------------

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

private val AccentRed = Color(0xFFFA2D48)

@Composable
fun BitChordIosTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

// ---- Root --------------------------------------------------------------------------------------

private enum class Tab(val label: String) { HOME("Home"), EXPLORE("Explore"), LIBRARY("Library"), SEARCH("Search") }

private val TabBarHeight = 64.dp
private val MiniPlayerHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosApp() {
    BitChordIosTheme {
        var tab by remember { mutableStateOf(Tab.HOME) }
        val stacks = remember { Tab.entries.associateWith { mutableStateListOf<Route>() } }
        var showPlayer by remember { mutableStateOf(false) }
        var menuSong by remember { mutableStateOf<Song?>(null) }
        val current by IosPlayer.current.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            IosAppModel.loadHome()
            IosAppModel.loadExplore()
            IosAppModel.loadLibrary()
        }

        val nav = remember {
            object : Nav {
                override fun open(route: Route) { stacks.getValue(tab).add(route) }
                override fun back() { stacks.getValue(tab).removeLastOrNull() }
                override fun menu(song: Song) { menuSong = song }
                override fun player() { showPlayer = true }
            }
        }

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val chrome = TabBarHeight + bottomInset + (if (current != null) MiniPlayerHeight + 8.dp else 0.dp)
        val padding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = chrome + 16.dp,
        )

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val stack = stacks.getValue(tab)
            AnimatedContent(
                targetState = tab to stack.lastOrNull(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page",
            ) { (shownTab, route) ->
                when (route) {
                    null -> TabRoot(shownTab, padding, nav)
                    is Route.Detail -> DetailPage(route, padding, nav)
                    is Route.Artist -> ArtistPageView(route, padding, nav)
                    is Route.Shelves -> ShelvesPage(route, padding, nav)
                    Route.Settings -> SettingsPage(padding, nav)
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                current?.let { song -> MiniPlayer(song, onOpen = { showPlayer = true }) }
                TabBar(tab, bottomInset) { picked ->
                    if (picked == tab) stacks.getValue(picked).clear() else tab = picked
                }
            }
        }

        if (showPlayer && current != null) {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showPlayer = false },
                sheetState = sheet,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = Color.Black,
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            ) {
                Box(Modifier.fillMaxSize().guardSheetFromContentTouches(sheet)) {
                    NowPlaying(nav, onClose = { showPlayer = false })
                }
            }
        }

        menuSong?.let { song -> SongMenu(song, nav, onDismiss = { menuSong = null }) }
    }
}

interface Nav {
    fun open(route: Route)
    fun back()
    fun menu(song: Song)
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
private fun TabRoot(tab: Tab, padding: PaddingValues, nav: Nav) {
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    when (tab) {
        Tab.HOME -> {
            val state by IosAppModel.home.collectAsStateWithLifecycle()
            val refreshing by IosAppModel.homeRefreshing.collectAsStateWithLifecycle()
            val loadingMore by IosAppModel.homeLoadingMore.collectAsStateWithLifecycle()
            Box {
                HomeScreen(
                    state = state,
                    listState = rememberLazyListState(),
                    onItemClick = { item, _ -> nav.openItem(item) },
                    onRetry = { IosAppModel.loadHome() },
                    refreshing = refreshing,
                    onRefresh = { IosAppModel.loadHome(refresh = true) },
                    pullState = rememberPullToRefreshState(),
                    contentPadding = padding,
                    title = "Home",
                    signedIn = signedIn,
                    onSignIn = { IosPlayer.bridge.openSignIn() },
                    onItemLongPress = { item ->
                        item.videoId?.let { nav.menu(Song(it, item.title, item.subtitle, item.thumbnailUrl)) }
                    },
                    onLoadMore = if (IosAppModel.canLoadMoreHome) ({ IosAppModel.moreHome() }) else null,
                    loadingMore = loadingMore,
                    currentSong = currentSong,
                    isPlaying = playing,
                )
                TopIcon(Icons.Filled.Settings, Modifier.align(Alignment.TopEnd).padding(top = padding.calculateTopPadding())) {
                    nav.open(Route.Settings)
                }
            }
        }
        Tab.EXPLORE -> {
            val state by IosAppModel.explore.collectAsStateWithLifecycle()
            val refreshing by IosAppModel.exploreRefreshing.collectAsStateWithLifecycle()
            ExploreScreen(
                state = state,
                listState = rememberLazyListState(),
                onCategoryClick = { nav.open(Route.Shelves(it.title, it.browseId, it.params)) },
                onRetry = { IosAppModel.loadExplore() },
                refreshing = refreshing,
                onRefresh = { IosAppModel.loadExplore(refresh = true) },
                pullState = rememberPullToRefreshState(),
                contentPadding = padding,
            )
        }
        Tab.LIBRARY -> {
            val state by IosAppModel.library.collectAsStateWithLifecycle()
            val refreshing by IosAppModel.libraryRefreshing.collectAsStateWithLifecycle()
            LibraryScreen(
                signedIn = signedIn,
                state = state,
                listState = rememberLazyListState(),
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
        Tab.SEARCH -> SearchTab(padding, nav)
    }
}

@Composable
private fun SearchTab(padding: PaddingValues, nav: Nav) {
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
    var focus by remember { mutableStateOf(false) }
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
        listState = rememberLazyListState(),
        scrollResetTrigger = reset,
        focusRequested = focus,
        onFocusHandled = { focus = false },
        onSongClick = { songs, i ->
            songs.getOrNull(i)?.let(IosAppModel::rememberSong)
            IosPlayer.play(listOf(songs[i]), radio = true)
        },
        onSongLongPress = nav::menu,
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
        onTypeaheadLongPress = nav::menu,
        contentPadding = padding,
        topPadding = padding.calculateTopPadding(),
    )
}

// ---- Album / playlist ---------------------------------------------------------------------------

@Composable
private fun DetailPage(route: Route.Detail, padding: PaddingValues, nav: Nav) {
    if (route.browseId == "selaras:") {
        LaunchedEffect(Unit) { nav.back(); IosPlayer.bridge.openSelaras() }
        return
    }
    LaunchedEffect(route.browseId) { IosAppModel.openDetail(route) }
    val details by IosAppModel.details.collectAsStateWithLifecycle()
    val state = details[route.browseId] ?: DetailState(route)
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val header = state.header
    val isAlbum = route.browseId.startsWith("MPRE")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
        item {
            Box {
                Column(
                    Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding() + 48.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = header.thumbnailUrl.artworkAt(HEADER_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(header.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (header.subtitle.isNotBlank()) {
                        Text(header.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { IosAppModel.playAll(route.browseId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = AccentRed),
                        ) { Icon(Icons.Filled.PlayArrow, null); Text(" Play") }
                        Button(
                            onClick = { IosAppModel.playAll(route.browseId, shuffle = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = AccentRed),
                        ) { Icon(Icons.Filled.Shuffle, null); Text(" Shuffle") }
                    }
                    state.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                BackButton(Modifier.padding(top = padding.calculateTopPadding()), nav)
            }
        }
        when (val songs = state.songs) {
            UiState.Loading -> item { Loading() }
            is UiState.Error -> item { ErrorText(songs.message) { IosAppModel.openDetail(route) } }
            is UiState.Success -> {
                itemsIndexed(songs.data, key = { i, s -> "$i:${s.videoId}" }) { i, song ->
                    SongRow(
                        song = song,
                        onClick = { IosPlayer.play(songs.data, i) },
                        onLongPress = { nav.menu(song) },
                        onSwipeToQueue = { IosPlayer.playNext(song) },
                        trackNumber = if (isAlbum) i + 1 else null,
                        isCurrent = currentSong?.videoId == song.videoId,
                        isPlaying = playing,
                    )
                    if (i == songs.data.lastIndex && state.continuation != null) {
                        LaunchedEffect(state.continuation) { IosAppModel.moreDetail(route.browseId) }
                    }
                }
            }
        }
    }
}

// ---- Artist ------------------------------------------------------------------------------------

@Composable
private fun ArtistPageView(route: Route.Artist, padding: PaddingValues, nav: Nav) {
    LaunchedEffect(route.browseId) { IosAppModel.openArtist(route.browseId) }
    val artists by IosAppModel.artists.collectAsStateWithLifecycle()
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val state = artists[route.browseId] ?: UiState.Loading

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
        val page = (state as? UiState.Success)?.data
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsyncImage(
                    model = page?.thumbnailUrl.artworkAt(HEADER_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.background))))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(page?.name ?: route.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    (page?.monthlyListenerCount ?: page?.subscriberCountText)?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                BackButton(Modifier.padding(top = padding.calculateTopPadding()), nav)
            }
        }
        when (state) {
            UiState.Loading -> item { Loading() }
            is UiState.Error -> item { ErrorText(state.message) { IosAppModel.openArtist(route.browseId) } }
            is UiState.Success -> {
                val data = state.data
                if (data.songs.isNotEmpty()) {
                    item { SectionTitle("Top songs") }
                    itemsIndexed(data.songs.take(10), key = { i, s -> "top$i${s.videoId}" }) { i, song ->
                        SongRow(
                            song = song,
                            onClick = { IosPlayer.play(data.songs, i) },
                            onLongPress = { nav.menu(song) },
                            onSwipeToQueue = { IosPlayer.playNext(song) },
                            isCurrent = currentSong?.videoId == song.videoId,
                            isPlaying = playing,
                        )
                    }
                }
                items(data.sections, key = { "sec" + it.title }) { shelf -> ShelfCarousel(shelf, nav) }
                data.description?.takeIf { it.isNotBlank() }?.let {
                    item {
                        SectionTitle("About")
                        Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ---- Mood / genre / "show all" -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelvesPage(route: Route.Shelves, padding: PaddingValues, nav: Nav) {
    val key = route.browseId + (route.params ?: "")
    LaunchedEffect(key) { IosAppModel.openShelves(route) }
    val all by IosAppModel.shelves.collectAsStateWithLifecycle()
    val currentSong by IosPlayer.current.collectAsStateWithLifecycle()
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    Box {
        HomeScreen(
            state = all[key] ?: UiState.Loading,
            listState = rememberLazyListState(),
            onItemClick = { item, _ -> nav.openItem(item) },
            onRetry = { IosAppModel.openShelves(route) },
            refreshing = false,
            onRefresh = {},
            pullState = rememberPullToRefreshState(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 44.dp, bottom = padding.calculateBottomPadding()),
            title = route.title,
            leadHero = false,
            currentSong = currentSong,
            isPlaying = playing,
        )
        BackButton(Modifier.padding(top = padding.calculateTopPadding()), nav)
    }
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
private fun SettingsPage(padding: PaddingValues, nav: Nav) {
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    val account by IosAccount.account.collectAsStateWithLifecycle()
    val autoplay by IosSettings.autoplay.collectAsStateWithLifecycle()
    val synced by IosSettings.syncedLyrics.collectAsStateWithLifecycle()
    val blur by IosSettings.lyricsBlur.collectAsStateWithLifecycle()
    val reduce by IosSettings.reduceAnimation.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(Modifier, nav)
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
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
            onOpenMenu = { nav.menu(shown) },
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

// ---- Mini player and tab bar -------------------------------------------------------------------

@Composable
private fun MiniPlayer(song: Song, onOpen: () -> Unit) {
    val playing by IosPlayer.isPlaying.collectAsStateWithLifecycle()
    val loading by IosPlayer.isLoading.collectAsStateWithLifecycle()
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = IosPlayer::togglePlay) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", Modifier.size(30.dp))
            }
        }
        IconButton(onClick = IosPlayer::next) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", Modifier.size(28.dp)) }
    }
}

@Composable
private fun TabBar(selected: Tab, bottomInset: androidx.compose.ui.unit.Dp, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .padding(bottom = bottomInset)
            .height(TabBarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val icon = when (tab) {
                Tab.HOME -> BitChordIcons.Home
                Tab.EXPLORE -> BitChordIcons.TabExplore
                Tab.LIBRARY -> BitChordIcons.TabLibrary
                Tab.SEARCH -> BitChordIcons.TabSearch
            }
            val tint = if (tab == selected) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(26.dp))
                Text(tab.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---- Song menu ---------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(song: Song, nav: Nav, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.artworkAt(ROW_ART_PX), null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider()
        MenuRow(Icons.Filled.PlayArrow, "Play") { IosPlayer.play(listOf(song), radio = true); onDismiss() }
        MenuRow(Icons.AutoMirrored.Filled.QueueMusic, "Play next") { IosPlayer.playNext(song); onDismiss() }
        MenuRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue") { IosPlayer.addToQueue(song); onDismiss() }
        MenuRow(Icons.Filled.Favorite, "Like") {
            scope.launch {
                YtMusicRepository.rate(song.videoId, LikeStatus.LIKE)
                    .onSuccess { IosPlayer.bridge.toast("Added to Liked Music") }
                    .onFailure { IosPlayer.bridge.toast(it.message ?: "Sign in to like songs") }
            }
            onDismiss()
        }
        song.albumId?.let { albumId ->
            MenuRow(Icons.Filled.Album, "Go to album") { onDismiss(); nav.open(Route.Detail(albumId, song.albumName ?: "", song.artist, song.thumbnailUrl)) }
        }
        song.artistId?.let { artistId ->
            MenuRow(Icons.Filled.Person, "Go to artist") { onDismiss(); nav.open(Route.Artist(artistId, song.artist)) }
        }
        MenuRow(Icons.Filled.Share, "Share") { IosPlayer.bridge.share("https://music.youtube.com/watch?v=${song.videoId}"); onDismiss() }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// ---- Small parts -------------------------------------------------------------------------------

@Composable
private fun BackButton(modifier: Modifier, nav: Nav) {
    IconButton(onClick = nav::back, modifier = modifier.padding(4.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.padding(8.dp)) { Icon(icon, contentDescription = null) }
}

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
