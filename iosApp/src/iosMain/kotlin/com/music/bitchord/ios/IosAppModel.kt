package com.music.bitchord.ios

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
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
