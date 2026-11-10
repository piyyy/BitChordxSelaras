package com.music.bitchord.ios

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.player.RepeatModes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The queue, and everything Now Playing shows about it. Sound itself is the Swift side's
 * (see [PlaybackBridge]); this is the phone's PlaybackService/MainViewModel player half, for iOS.
 */
object IosPlayer : PlaybackListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var bridge: PlaybackBridge
        private set

    val queue = MutableStateFlow<List<Song>>(emptyList())
    val index = MutableStateFlow(-1)
    val current = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val durationMs = MutableStateFlow(0L)
    val position = PlaybackPosition()
    val shuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(RepeatModes.OFF)
    val likeStatus = MutableStateFlow(LikeStatus.INDIFFERENT)
    val outputName = MutableStateFlow("iPhone")

    val lyrics = MutableStateFlow<List<LyricLine>?>(null)
    val lyricsSource = MutableStateFlow<LyricsSource?>(null)
    val lyricsChecked = MutableStateFlow(false)
    val lyricsProviderStates = MutableStateFlow(LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED })
    private val lyricsResults = HashMap<LyricsSource, LyricsRepository.Result>()

    private var unshuffled: List<Song>? = null
    private var radioSeed: String? = null
    private var extending: Job? = null
    private var lyricsJob: Job? = null
    private var menuJob: Job? = null

    fun install(bridge: PlaybackBridge) {
        this.bridge = bridge
        bridge.setListener(this)
        outputName.value = bridge.currentOutputName()
    }

    // ---- Starting things ----------------------------------------------------------------------

    /** Plays [songs] from [startIndex]; with [radio], the queue keeps going with similar tracks. */
    fun play(songs: List<Song>, startIndex: Int = 0, radio: Boolean = false) {
        if (songs.isEmpty()) return
        unshuffled = null
        shuffle.value = false
        queue.value = songs
        radioSeed = if (radio || songs.size == 1) songs[startIndex.coerceIn(songs.indices)].videoId else null
        start(startIndex.coerceIn(songs.indices))
        if (radioSeed != null && songs.size <= 1) extendWithRadio()
    }

    fun playNext(song: Song) {
        if (current.value == null) return play(listOf(song), radio = true)
        queue.update { it.toMutableList().apply { add((index.value + 1).coerceAtMost(size), song) } }
        bridge.toast("Playing next")
        updateButtons()
    }

    fun addToQueue(song: Song) {
        if (current.value == null) return play(listOf(song), radio = true)
        queue.update { it + song }
        bridge.toast("Added to queue")
        updateButtons()
    }

    private fun start(at: Int, startMs: Long = 0L, playWhenReady: Boolean = true) {
        val song = queue.value.getOrNull(at) ?: return
        index.value = at
        current.value = song
        isLoading.value = true
        durationMs.value = song.durationMillis()
        position.report(startMs)
        likeStatus.value = LikeStatus.INDIFFERENT
        bridge.load(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            album = song.albumName,
            artworkUrl = song.artworkAt(HEADER_ART_PX),
            startMs = startMs,
            playWhenReady = playWhenReady,
        )
        queue.value.getOrNull(at + 1)?.let { bridge.prefetch(it.videoId) }
        updateButtons()
        loadLyrics(song)
        loadMenu(song)
        if (queue.value.size - at <= 3 && IosSettings.autoplay.value) extendWithRadio()
    }

    // ---- Controls -----------------------------------------------------------------------------

    fun togglePlay() = if (isPlaying.value) bridge.pause() else bridge.play()

    val hasNext: Boolean get() = index.value < queue.value.lastIndex || repeatMode.value == RepeatModes.ALL
    val hasPrevious: Boolean get() = index.value > 0 || position.positionMs > 3_000

    fun next() {
        val q = queue.value
        when {
            index.value < q.lastIndex -> start(index.value + 1)
            repeatMode.value == RepeatModes.ALL && q.isNotEmpty() -> start(0)
            IosSettings.autoplay.value -> extendWithRadio(thenAdvance = true)
        }
    }

    fun previous() {
        if (position.positionMs > 3_000 || index.value <= 0) seek(0) else start(index.value - 1)
    }

    fun seek(ms: Long) {
        position.report(ms)
        position.seeks++
        bridge.seekTo(ms)
    }

    fun seekFraction(fraction: Float) = seek((durationMs.value * fraction).toLong())

    fun jumpTo(i: Int) = start(i)

    fun remove(i: Int) {
        if (i == index.value) return
        queue.update { it.toMutableList().apply { removeAt(i) } }
        if (i < index.value) index.value--
        updateButtons()
    }

    fun move(from: Int, to: Int) {
        val list = queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val song = list.removeAt(from)
        list.add(to, song)
        val cur = index.value
        index.value = when (cur) {
            from -> to
            in (from + 1)..to -> cur - 1
            in to until from -> cur + 1
            else -> cur
        }
        queue.value = list
        updateButtons()
    }

    fun clearQueue() {
        val song = current.value ?: return
        queue.value = listOf(song)
        index.value = 0
        updateButtons()
    }

    fun toggleShuffle() {
        val q = queue.value
        val cur = current.value ?: return
        if (!shuffle.value) {
            unshuffled = q
            val rest = q.filterIndexed { i, _ -> i != index.value }.shuffled()
            queue.value = listOf(cur) + rest
            index.value = 0
            shuffle.value = true
        } else {
            val original = unshuffled ?: q
            queue.value = original
            index.value = original.indexOfFirst { it.videoId == cur.videoId }.coerceAtLeast(0)
            shuffle.value = false
            unshuffled = null
        }
        updateButtons()
    }

    fun cycleRepeat() {
        repeatMode.value = when (repeatMode.value) {
            RepeatModes.OFF -> RepeatModes.ALL
            RepeatModes.ALL -> RepeatModes.ONE
            else -> RepeatModes.OFF
        }
        updateButtons()
    }

    fun toggleAutoplay() = IosSettings.set(IosSettings.autoplay, "autoplay", !IosSettings.autoplay.value)

    fun toggleLike() {
        val song = current.value ?: return
        val next = if (likeStatus.value == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE
        likeStatus.value = next
        scope.launch {
            YtMusicRepository.rate(song.videoId, next).onFailure {
                likeStatus.value = if (next == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE
                bridge.toast(it.message ?: "Couldn't update the like")
            }
        }
    }

    // ---- The song menu: like / dislike any track (MainViewModel.loadSongMenu / toggleLike) -------

    /** What the song menu shows for each track it has been opened on. */
    val likeStatuses = MutableStateFlow<Map<String, LikeStatus>>(emptyMap())

    fun loadSongMenu(videoId: String) {
        scope.launch {
            YtMusicRepository.songMenu(videoId).getOrNull()?.likeStatus?.let { status ->
                likeStatuses.update { it + (videoId to status) }
                if (current.value?.videoId == videoId) likeStatus.value = status
            }
        }
    }

    private fun rate(videoId: String, target: LikeStatus) {
        val before = likeStatuses.value[videoId]
            ?: if (current.value?.videoId == videoId) likeStatus.value else LikeStatus.INDIFFERENT
        likeStatuses.update { it + (videoId to target) }
        if (current.value?.videoId == videoId) likeStatus.value = target
        scope.launch {
            YtMusicRepository.rate(videoId, target).onFailure {
                likeStatuses.update { map -> map + (videoId to before) }
                if (current.value?.videoId == videoId) likeStatus.value = before
                bridge.toast(it.message ?: "Couldn't update the like")
            }
        }
    }

    fun toggleLike(videoId: String) {
        val now = likeStatuses.value[videoId] ?: LikeStatus.INDIFFERENT
        rate(videoId, if (now == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE)
    }

    fun toggleDislike(videoId: String) {
        val now = likeStatuses.value[videoId] ?: LikeStatus.INDIFFERENT
        val target = if (now == LikeStatus.DISLIKE) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE
        rate(videoId, target)
        // As on the phone: disliking what is playing moves on from it.
        if (target == LikeStatus.DISLIKE && current.value?.videoId == videoId) next()
    }

    private fun updateButtons() = bridge.setQueueButtons(hasPrevious, hasNext)

    // ---- Radio --------------------------------------------------------------------------------

    private fun extendWithRadio(thenAdvance: Boolean = false) {
        if (extending?.isActive == true) return
        val seed = current.value?.videoId ?: radioSeed ?: return
        extending = scope.launch {
            val more = YtMusicRepository.radio(seed).getOrNull().orEmpty()
            val known = queue.value.map { it.videoId }.toSet()
            val fresh = more.filter { it.videoId !in known }
            if (fresh.isNotEmpty()) {
                queue.update { it + fresh }
                updateButtons()
                if (thenAdvance) start(index.value + 1)
            }
        }
    }

    // ---- Lyrics & the track's menu -------------------------------------------------------------

    private fun loadLyrics(song: Song, only: LyricsSource? = null) {
        lyricsJob?.cancel()
        if (only == null) {
            lyrics.value = null
            lyricsSource.value = null
            lyricsChecked.value = false
            lyricsResults.clear()
            lyricsProviderStates.value = LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED }
        }
        lyricsJob = scope.launch {
            val duration = durationMs.value.takeIf { it > 0 } ?: song.durationMillis()
            val found = LyricsRepository.lyrics(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                durationMs = duration,
                album = song.albumName,
                sources = only?.let { setOf(it) } ?: LyricsSource.entries.toSet(),
                order = IosSettings.lyricsSourceOrder.value,
                onSourceStarted = { source -> setProvider(source, LyricsProviderState.FETCHING) },
                onSourceResult = { source, result ->
                    if (result != null) lyricsResults[source] = result
                    setProvider(source, if (result != null) LyricsProviderState.FOUND else LyricsProviderState.NOT_FOUND)
                },
                onSourceCancelled = { source -> setProvider(source, LyricsProviderState.NOT_FETCHED) },
            )
            if (current.value?.videoId != song.videoId) return@launch
            val chosen = only?.let { lyricsResults[it] } ?: found
            if (chosen != null) {
                lyrics.value = chosen.lines
                lyricsSource.value = chosen.source
            }
            lyricsChecked.value = true
        }
    }

    private fun setProvider(source: LyricsSource, state: LyricsProviderState) {
        lyricsProviderStates.update { it + (source to state) }
    }

    fun selectLyricsProvider(source: LyricsSource) {
        lyricsResults[source]?.let {
            lyrics.value = it.lines
            lyricsSource.value = it.source
            return
        }
        if (lyricsProviderStates.value[source] == LyricsProviderState.NOT_FETCHED) {
            current.value?.let { loadLyrics(it, only = source) }
        }
    }

    private fun loadMenu(song: Song) {
        menuJob?.cancel()
        menuJob = scope.launch {
            YtMusicRepository.songMenu(song.videoId).getOrNull()?.likeStatus?.let {
                if (current.value?.videoId == song.videoId) likeStatus.value = it
            }
        }
    }

    // ---- From Swift ---------------------------------------------------------------------------

    override fun onState(isPlaying: Boolean, isLoading: Boolean, positionMs: Long, durationMs: Long) {
        this.isPlaying.value = isPlaying
        this.isLoading.value = isLoading
        position.report(positionMs)
        position.advancing = isPlaying
        // The sleep timer (set from the player's song menu), as PlaybackService enforces it.
        if (isPlaying && SleepTimer.remainingMs() == 0L) {
            SleepTimer.cancel()
            bridge.pause()
        }
        if (durationMs > 0 && durationMs != this.durationMs.value) {
            val first = this.durationMs.value <= 0L
            this.durationMs.value = durationMs
            if (first && lyrics.value == null) current.value?.let { loadLyrics(it) }
        }
    }

    override fun onEnded() {
        if (SleepTimer.afterTrack.value) {
            SleepTimer.cancel()
            return
        }
        if (repeatMode.value == RepeatModes.ONE) seek(0).also { bridge.play() } else next()
    }

    override fun onError(message: String) {
        isLoading.value = false
        bridge.toast(message)
    }

    override fun onRemoteNext() = next()
    override fun onRemotePrevious() = previous()
    override fun onCookie(cookie: String?) = IosAccount.onCookie(cookie)
    override fun onOutputChanged(name: String) { outputName.value = name }
}
