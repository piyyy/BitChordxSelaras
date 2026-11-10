package com.music.bitchord.ios

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.music.bitchord.playback.contextEntryKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** Signed-in state: the cookie lives in the Keychain on the Swift side. */
object IosAccount {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val signedIn = MutableStateFlow(false)
    val account = MutableStateFlow<Account?>(null)

    fun restore(cookie: String?) = onCookie(cookie)

    fun onCookie(cookie: String?) {
        Innertube.cookie = cookie?.takeIf { it.isNotBlank() }
        signedIn.value = Innertube.cookie != null
        account.value = null
        if (signedIn.value) {
            scope.launch { account.value = YtMusicRepository.account().getOrNull() }
        }
        IosAppModel.refreshAll()
    }
}

/** What a page in the stack shows. */
sealed interface Route {
    data class Detail(val browseId: String, val title: String, val subtitle: String, val thumbnailUrl: String?) : Route
    data class Artist(val browseId: String, val name: String) : Route
    data class Shelves(val title: String, val browseId: String, val params: String?) : Route
    data object Settings : Route
}

data class DetailState(
    val header: Route.Detail,
    val songs: UiState<List<Song>> = UiState.Loading,
    val continuation: String? = null,
    val description: String? = null,
)

/**
 * The pages' data, as the phone's MainViewModel fetches it, for the iOS host: one object, flows the
 * Compose UI collects, every fetch through [YtMusicRepository].
 */
object IosAppModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Home ---------------------------------------------------------------------------------

    val home = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val homeRefreshing = MutableStateFlow(false)
    val homeLoadingMore = MutableStateFlow(false)
    private var homeContinuation: String? = null

    fun loadHome(refresh: Boolean = false) {
        if (refresh) homeRefreshing.value = true else if (home.value !is UiState.Success) home.value = UiState.Loading
        scope.launch {
            YtMusicRepository.home()
                .onSuccess { feed ->
                    homeContinuation = feed.continuation
                    home.value = UiState.Success(feed.shelves)
                }
                .onFailure { if (home.value !is UiState.Success) home.value = UiState.Error(it.message ?: "Couldn't load Home") }
            homeRefreshing.value = false
        }
    }

    fun moreHome() {
        val token = homeContinuation ?: return
        if (homeLoadingMore.value) return
        homeLoadingMore.value = true
        scope.launch {
            YtMusicRepository.moreHome(token).onSuccess { feed ->
                homeContinuation = feed.continuation
                home.update { state -> (state as? UiState.Success)?.let { UiState.Success(it.data + feed.shelves) } ?: state }
            }.onFailure { homeContinuation = null }
            homeLoadingMore.value = false
        }
    }

    val canLoadMoreHome: Boolean get() = homeContinuation != null

    // ---- Explore ------------------------------------------------------------------------------

    val explore = MutableStateFlow<UiState<List<MoodGenreSection>>>(UiState.Loading)
    val exploreRefreshing = MutableStateFlow(false)

    fun loadExplore(refresh: Boolean = false) {
        if (refresh) exploreRefreshing.value = true
        scope.launch {
            YtMusicRepository.moodAndGenres()
                .onSuccess { explore.value = UiState.Success(it) }
                .onFailure { if (explore.value !is UiState.Success) explore.value = UiState.Error(it.message ?: "Couldn't load Explore") }
            exploreRefreshing.value = false
        }
    }

    // ---- Library ------------------------------------------------------------------------------

    val library = MutableStateFlow<UiState<LibraryPage>>(UiState.Loading)
    val libraryRefreshing = MutableStateFlow(false)

    fun loadLibrary(refresh: Boolean = false) {
        if (!IosAccount.signedIn.value) {
            library.value = UiState.Success(LibraryPage(emptyList(), emptyList(), emptyList()))
            return
        }
        if (refresh) libraryRefreshing.value = true
        scope.launch {
            YtMusicRepository.library()
                .onSuccess { library.value = UiState.Success(it) }
                .onFailure { if (library.value !is UiState.Success) library.value = UiState.Error(it.message ?: "Couldn't load your library") }
            libraryRefreshing.value = false
        }
    }

    // ---- Search -------------------------------------------------------------------------------

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(SearchFilter.ALL)
    val results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val typeahead = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchLoadingMore = MutableStateFlow(false)
    val history = MutableStateFlow(readHistory())
    val scrollReset = MutableStateFlow(0)
    private var searchContinuation: String? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun setQuery(text: String) {
        query.value = text
        if (text.isBlank()) {
            results.value = null
            suggestions.value = emptyList()
            typeahead.value = emptyList()
            return
        }
        suggestJob?.cancel()
        suggestJob = scope.launch {
            delay(180)
            suggestions.value = YtMusicRepository.searchSuggestions(text).getOrNull().orEmpty()
            typeahead.value = YtMusicRepository.searchTypeahead(text).getOrNull()?.rows.orEmpty().take(4)
        }
    }

    fun submit(text: String = query.value) {
        if (text.isBlank()) return
        query.value = text
        suggestJob?.cancel()
        suggestions.value = emptyList()
        typeahead.value = emptyList()
        runSearch()
    }

    fun setFilter(value: SearchFilter) {
        filter.value = value
        if (results.value != null) runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        results.value = UiState.Loading
        scrollReset.value++
        searchJob = scope.launch {
            YtMusicRepository.searchPage(query.value, filter.value)
                .onSuccess {
                    searchContinuation = it.continuation
                    results.value = UiState.Success(it.rows)
                }
                .onFailure { results.value = UiState.Error(it.message ?: "Search failed") }
        }
    }

    fun moreResults() {
        val token = searchContinuation ?: return
        if (searchLoadingMore.value) return
        searchLoadingMore.value = true
        scope.launch {
            YtMusicRepository.searchContinuation(token, filter.value).onSuccess { page ->
                searchContinuation = page.continuation
                results.update { s -> (s as? UiState.Success)?.let { UiState.Success(it.data + page.rows) } ?: s }
            }.onFailure { searchContinuation = null }
            searchLoadingMore.value = false
        }
    }

    fun remember(entry: SearchHistoryEntity) {
        history.update { list -> (listOf(entry) + list.filterNot { it.id == entry.id }).take(30) }
        writeHistory()
    }

    fun rememberSong(song: Song) = remember(
        SearchHistoryEntity(song.videoId, song.title, "Song • ${song.artist}", song.thumbnailUrl, EntityType.TRACK),
    )

    fun rememberBrowse(item: BrowseItem) = remember(
        SearchHistoryEntity(
            item.browseId, item.title, item.subtitle, item.thumbnailUrl,
            when (item.type) {
                BrowseType.ARTIST -> EntityType.ARTIST
                BrowseType.PLAYLIST -> EntityType.PLAYLIST
                else -> EntityType.ALBUM
            },
        ),
    )

    fun forget(id: String) {
        history.update { list -> list.filterNot { it.id == id } }
        writeHistory()
    }

    fun clearHistory() {
        history.value = emptyList()
        writeHistory()
    }

    private fun readHistory(): List<SearchHistoryEntity> = runCatching {
        NSUserDefaults.standardUserDefaults.stringForKey("searchHistoryV2")
            ?.let { json.decodeFromString(ListSerializer(SearchHistoryEntity.serializer()), it) }
    }.getOrNull().orEmpty()

    private fun writeHistory() {
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(ListSerializer(SearchHistoryEntity.serializer()), history.value),
            "searchHistoryV2",
        )
    }

    // ---- Album / playlist / artist / mood pages ------------------------------------------------

    val details = MutableStateFlow<Map<String, DetailState>>(emptyMap())
    val artists = MutableStateFlow<Map<String, UiState<ArtistPage>>>(emptyMap())
    val shelves = MutableStateFlow<Map<String, UiState<List<HomeShelf>>>>(emptyMap())

    fun openDetail(route: Route.Detail) {
        if (details.value[route.browseId]?.songs is UiState.Success) return
        details.update { it + (route.browseId to DetailState(route)) }
        scope.launch {
            YtMusicRepository.browseSongs(route.browseId)
                .onSuccess { page ->
                    val header = page.header?.let {
                        route.copy(
                            title = route.title.ifBlank { it.title },
                            subtitle = route.subtitle.ifBlank { it.subtitle },
                            thumbnailUrl = route.thumbnailUrl ?: it.thumbnailUrl,
                        )
                    } ?: route
                    details.update { it + (route.browseId to DetailState(header, UiState.Success(page.songs), page.continuation, page.description)) }
                }
                .onFailure { e ->
                    details.update { it + (route.browseId to DetailState(route, UiState.Error(e.message ?: "Couldn't load"))) }
                }
        }
    }

    fun moreDetail(browseId: String) {
        val state = details.value[browseId] ?: return
        val token = state.continuation ?: return
        details.update { it + (browseId to state.copy(continuation = null)) }
        scope.launch {
            YtMusicRepository.moreSongs(token).onSuccess { page ->
                details.update { map ->
                    val s = map[browseId] ?: return@update map
                    val songs = (s.songs as? UiState.Success)?.data.orEmpty() + page.songs
                    map + (browseId to s.copy(songs = UiState.Success(songs), continuation = page.continuation))
                }
            }
        }
    }

    fun openArtist(browseId: String) {
        if (artists.value[browseId] is UiState.Success) return
        artists.update { it + (browseId to UiState.Loading) }
        scope.launch {
            val state = YtMusicRepository.artistPage(browseId).fold(
                { UiState.Success(it) },
                { UiState.Error(it.message ?: "Couldn't load the artist") },
            )
            artists.update { it + (browseId to state) }
        }
    }

    // ---- Album / playlist / artist pages: MainViewModel.openDetail, as the phone loads them ----

    val pages = MutableStateFlow<Map<String, DetailPage>>(emptyMap())
    private val fillInJobs = mutableMapOf<String, Job>()

    /** YouTube's browse ids are prefixed by kind (MainViewModel.browseTypeOf). */
    fun browseTypeOf(browseId: String, fallback: BrowseType = BrowseType.OTHER): BrowseType = when {
        browseId.startsWith("UC") -> BrowseType.ARTIST
        browseId.startsWith("MPREb") || browseId.startsWith("VLOLAK") || browseId.startsWith("OLAK") -> BrowseType.ALBUM
        browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
        else -> fallback
    }

    private fun List<Song>.withArtwork(fallback: String?): List<Song> {
        if (fallback == null) return this
        return map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = fallback) else it }
    }

    private fun updatePage(browseId: String, change: (DetailPage) -> DetailPage) =
        pages.update { map -> map[browseId]?.let { map + (browseId to change(it)) } ?: map }

    fun openPage(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        val existing = pages.value[browseId]
        if (existing != null && existing.songs !is UiState.Error) return
        val resolved = browseTypeOf(browseId, type)
        pages.update {
            it + (browseId to DetailPage(browseId, title, subtitle, thumbnailUrl, UiState.Loading, resolved))
        }
        scope.launch {
            var sections = emptyList<HomeShelf>()
            var artwork: String? = null
            var name: String? = null
            var credit: String? = null
            var more: String? = null
            var suggested: List<Song> = emptyList()
            var library: LibraryState? = null
            var description: String? = null
            var subscriberCountText: String? = null
            var monthlyListenerCount: String? = null
            var subscription: com.music.bitchord.data.model.SubscriptionState? = null
            val state: UiState<List<Song>> = if (resolved == BrowseType.ARTIST) {
                YtMusicRepository.artistPage(browseId).fold(
                    onSuccess = { page ->
                        sections = page.sections
                        artwork = page.thumbnailUrl
                        name = page.name
                        description = page.description
                        subscriberCountText = page.subscriberCountText
                        monthlyListenerCount = page.monthlyListenerCount
                        subscription = page.subscription
                        if (page.songs.isEmpty()) {
                            UiState.Error(androidx.compose.ui.res.AndroidStrings.string(com.music.bitchord.R.string.no_tracks_here))
                        } else {
                            UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                        }
                    },
                    onFailure = { UiState.Error(it.message ?: "Couldn't load") },
                )
            } else {
                YtMusicRepository.browseSongs(browseId).fold(
                    onSuccess = { page ->
                        page.header?.let { header ->
                            if (title.isBlank()) name = header.title
                            if (resolved == BrowseType.ALBUM && header.subtitle.isNotBlank()) {
                                credit = header.subtitle
                            } else if (subtitle.isBlank()) {
                                credit = header.subtitle
                            }
                            if (thumbnailUrl == null) artwork = header.thumbnailUrl
                        }
                        description = page.description
                        if (page.songs.isEmpty()) {
                            UiState.Error(androidx.compose.ui.res.AndroidStrings.string(com.music.bitchord.R.string.no_tracks_here))
                        } else {
                            more = page.continuation
                            suggested = page.suggested.withArtwork(thumbnailUrl ?: artwork)
                            library = page.library
                            UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                        }
                    },
                    onFailure = { UiState.Error(it.message ?: "Couldn't load") },
                )
            }
            updatePage(browseId) {
                it.copy(
                    songs = state,
                    sections = sections,
                    thumbnailUrl = artwork ?: it.thumbnailUrl,
                    title = name ?: it.title,
                    subtitle = credit ?: it.subtitle,
                    suggestedSongs = suggested,
                    continuation = more,
                    library = library,
                    description = description,
                    subscriberCountText = subscriberCountText,
                    monthlyListenerCount = monthlyListenerCount,
                    subscription = subscription,
                )
            }
            more?.let { fillIn(browseId, it, thumbnailUrl ?: artwork) }
        }
    }

    /** Walks a long playlist's continuation until every track is in (MainViewModel.fillIn). */
    private fun fillIn(browseId: String, token: String, artworkFallback: String?) {
        fillInJobs[browseId]?.cancel()
        fillInJobs[browseId] = scope.launch {
            delay(150)
            var next: String? = token
            while (next != null) {
                val fetched = YtMusicRepository.moreSongs(next).getOrNull() ?: return@launch
                val current = pages.value[browseId] ?: return@launch
                val existing = (current.songs as? UiState.Success)?.data ?: return@launch
                val known = existing.mapTo(HashSet()) { it.contextEntryKey() }
                val added = fetched.songs.filter { known.add(it.contextEntryKey()) }.withArtwork(artworkFallback)
                val knownVideoIds = (existing + added).mapTo(HashSet()) { it.videoId }
                val knownSuggested = current.suggestedSongs.mapTo(HashSet()) { it.videoId }
                val addedSuggested = fetched.suggested
                    .filter { it.videoId !in knownVideoIds && knownSuggested.add(it.videoId) }
                    .withArtwork(artworkFallback)
                if (added.isEmpty() && addedSuggested.isEmpty()) return@launch
                updatePage(browseId) {
                    it.copy(
                        songs = UiState.Success(existing + added),
                        suggestedSongs = it.suggestedSongs + addedSuggested,
                        continuation = fetched.continuation,
                    )
                }
                next = fetched.continuation
            }
        }
    }

    fun toggleLibrary(browseId: String) {
        val current = pages.value[browseId]?.library ?: return
        val target = !current.saved
        updatePage(browseId) { it.copy(library = current.copy(saved = target)) }
        scope.launch {
            if (YtMusicRepository.setSaved(current.playlistId, target).isFailure) {
                updatePage(browseId) { it.copy(library = current) }
            } else {
                loadLibrary(refresh = true)
            }
        }
    }

    fun toggleSubscription(browseId: String) {
        val current = pages.value[browseId]?.subscription ?: return
        val target = !current.subscribed
        updatePage(browseId) { it.copy(subscription = current.copy(subscribed = target)) }
        scope.launch {
            if (YtMusicRepository.setSubscribed(current.channelId, target).isFailure) {
                updatePage(browseId) { it.copy(subscription = current) }
            }
        }
    }

    fun addSuggestedSong(browseId: String, song: Song) {
        val playlistId = browseId.removePrefix("VL")
        scope.launch {
            YtMusicRepository.addToPlaylist(playlistId, listOf(song.videoId)).onSuccess {
                updatePage(browseId) { page ->
                    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
                    page.copy(
                        suggestedSongs = page.suggestedSongs.filterNot { it.videoId == song.videoId },
                        songs = UiState.Success(songs + song),
                    )
                }
            }
        }
    }

    // ---- Playlists: the "Add to playlist" sheet (MainViewModel.loadPlaylists / addToPlaylists) ----

    val playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlistsLoading = MutableStateFlow(false)

    fun loadPlaylists() {
        if (!IosAccount.signedIn.value || playlistsLoading.value) return
        playlistsLoading.value = true
        scope.launch {
            YtMusicRepository.userPlaylists().onSuccess { playlists.value = it }
            playlistsLoading.value = false
        }
    }

    /** Adds [song] to each of [targets]; [onResult] gets (added, alreadyThere, failed). */
    fun addToPlaylists(targets: List<UserPlaylist>, song: Song, onResult: (Int, Int, Int) -> Unit) {
        if (targets.isEmpty()) return
        scope.launch {
            var added = 0
            var already = 0
            var failed = 0
            for (playlist in targets) {
                val known = (pages.value[playlist.browseId]?.songs as? UiState.Success)?.data
                    ?: YtMusicRepository.allSongs(playlist.browseId).getOrNull()
                if (known?.any { it.videoId == song.videoId } == true) {
                    already++
                    continue
                }
                YtMusicRepository.addToPlaylist(playlist.playlistId, listOf(song.videoId)).fold(
                    onSuccess = {
                        added++
                        updatePage(playlist.browseId) { page ->
                            val songs = (page.songs as? UiState.Success)?.data ?: return@updatePage page
                            page.copy(songs = UiState.Success(songs + song))
                        }
                    },
                    onFailure = { failed++ },
                )
            }
            if (added > 0) loadLibrary(refresh = true)
            onResult(added, already, failed)
        }
    }

    fun createPlaylist(title: String, privacy: PlaylistPrivacy, song: Song?) {
        val name = title.trim().ifBlank {
            androidx.compose.ui.res.AndroidStrings.string(com.music.bitchord.R.string.new_playlist)
        }
        scope.launch {
            YtMusicRepository.createPlaylist(name, privacy, listOfNotNull(song?.videoId)).onSuccess { playlistId ->
                playlists.update {
                    listOf(UserPlaylist(playlistId = playlistId, title = name, subtitle = if (song != null) "1 song" else "", thumbnailUrl = song?.thumbnailUrl)) + it
                }
                loadLibrary(refresh = true)
            }.onFailure { IosPlayer.bridge.toast(it.message ?: "Couldn't create the playlist") }
        }
    }

    fun openShelves(route: Route.Shelves) {
        val key = route.browseId + (route.params ?: "")
        if (shelves.value[key] is UiState.Success) return
        shelves.update { it + (key to UiState.Loading) }
        scope.launch {
            val state = YtMusicRepository.moodGenreShelves(route.browseId, route.params).fold(
                { UiState.Success(it) },
                { UiState.Error(it.message ?: "Couldn't load") },
            )
            shelves.update { it + (key to state) }
        }
    }

    /** Plays a whole album/playlist page, fetching the rest of a long one first. */
    fun playAll(browseId: String, shuffle: Boolean = false) {
        val songs = (details.value[browseId]?.songs as? UiState.Success)?.data ?: return
        val list = if (shuffle) songs.shuffled() else songs
        IosPlayer.play(list, 0)
    }

    fun refreshAll() {
        loadHome()
        loadLibrary()
    }
}
