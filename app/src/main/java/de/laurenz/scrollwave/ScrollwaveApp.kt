package de.laurenz.scrollwave

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ScrollwaveApp(viewModel: MainViewModel, onLogin: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF161616),
            primary = Color.White,
            onPrimary = Color.Black,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            when (state.stage) {
                AppStage.LOGIN -> LoginScreen(state, onLogin)
                AppStage.LOADING_SOURCES -> LoadingScreen("Reddit wird geladen …")
                AppStage.SOURCES -> SourceScreen(state, viewModel::selectSource, viewModel::logout)
                AppStage.FEED -> FeedScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun LoginScreen(state: UiState, onLogin: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Scrollwave", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Deine Reddit-Medien als Fullscreen-Feed.", color = Color.LightGray)
            state.error?.let { Text(it, color = Color(0xFFFF8A80)) }
            Button(onClick = onLogin, enabled = !state.loading && !state.error.orEmpty().contains("CLIENT_ID")) {
                Text("Mit Reddit verbinden")
            }
        }
    }
}

@Composable
private fun LoadingScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Color.White)
            Text(label)
        }
    }
}

@Composable
private fun SourceScreen(state: UiState, onSelect: (FeedSource) -> Unit, onLogout: () -> Unit) {
    val customFeeds = state.sources.filter { it.kind == SourceKind.CUSTOM_FEED }
    val subreddits = state.sources.filter { it.kind == SourceKind.SUBREDDIT }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Feed auswählen", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onLogout, modifier = Modifier.height(48.dp)) { Text("Abmelden") }
        }
        state.error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(bottom = 12.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (customFeeds.isNotEmpty()) {
                item { SectionLabel("Eigene Custom Feeds") }
                items(customFeeds, key = FeedSource::id) { source -> SourceButton(source, onSelect) }
            }
            if (subreddits.isNotEmpty()) {
                item { SectionLabel("Abonnierte Subreddits") }
                items(subreddits, key = FeedSource::id) { source -> SourceButton(source, onSelect) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SourceButton(source: FeedSource, onSelect: (FeedSource) -> Unit) {
    Button(
        onClick = { onSelect(source) },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424), contentColor = Color.White),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(source.label, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FeedScreen(state: UiState, viewModel: MainViewModel) {
    var controlsVisible by remember { mutableStateOf(true) }
    var confirmRefresh by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> CircularProgressIndicator(color = Color.White)
                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = Color(0xFFFF8A80), modifier = Modifier.padding(24.dp))
                        Button(onClick = viewModel::refresh) { Text("Erneut suchen") }
                    }
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { state.posts.size })

            LaunchedEffect(pagerState, state.posts.size) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        controlsVisible = true
                        if (page >= state.posts.lastIndex - 3) viewModel.loadMore()
                    }
            }
            LaunchedEffect(controlsVisible, pagerState.currentPage) {
                if (controlsVisible) {
                    delay(3_000)
                    controlsVisible = false
                }
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { state.posts[it].id },
            ) { page ->
                val post = state.posts[page]
                MediaPage(
                    post = post,
                    active = pagerState.currentPage == page,
                    paused = post.id in state.pausedPosts,
                    quality = state.quality,
                    resumeMode = state.resumeMode,
                    positionFor = viewModel::position,
                    savePosition = viewModel::savePosition,
                    onTap = {
                        controlsVisible = true
                        viewModel.togglePaused(post.id)
                    },
                    onFailure = {
                        scope.launch {
                            delay(800)
                            if (pagerState.currentPage == page && page < state.posts.lastIndex) {
                                pagerState.animateScrollToPage(page + 1)
                            }
                        }
                    },
                )
            }

            if (controlsVisible) {
                FeedControls(
                    state = state,
                    onSources = viewModel::showSources,
                    onSort = viewModel::setSort,
                    onRange = viewModel::setTopRange,
                    onRefresh = { confirmRefresh = true },
                    onQuality = viewModel::setQuality,
                    onResume = viewModel::setResumeMode,
                    onLogout = viewModel::logout,
                )
            }

            if (state.loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 58.dp)
                        .size(24.dp),
                )
            }
        }
    }

    if (confirmRefresh) {
        AlertDialog(
            onDismissRequest = { confirmRefresh = false },
            title = { Text("Feed neu laden?") },
            text = { Text("Die aktuelle Position geht verloren.") },
            confirmButton = {
                TextButton(onClick = { confirmRefresh = false; viewModel.refresh() }) { Text("Neu laden") }
            },
            dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun FeedControls(
    state: UiState,
    onSources: () -> Unit,
    onSort: (FeedSort) -> Unit,
    onRange: (TopRange) -> Unit,
    onRefresh: () -> Unit,
    onQuality: (Quality) -> Unit,
    onResume: (ResumeMode) -> Unit,
    onLogout: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var rangeMenu by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ControlButton(
            label = state.selectedSource?.label.orEmpty(),
            modifier = Modifier.weight(1f),
            onClick = onSources,
        )
        Box {
            ControlButton(state.sort.label, onClick = { sortMenu = true })
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                FeedSort.entries.forEach { sort ->
                    DropdownMenuItem(text = { Text(sort.label) }, onClick = { sortMenu = false; onSort(sort) })
                }
            }
        }
        if (state.sort == FeedSort.TOP) {
            Box {
                ControlButton(state.topRange.label, onClick = { rangeMenu = true })
                DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                    TopRange.entries.forEach { range ->
                        DropdownMenuItem(text = { Text(range.label) }, onClick = { rangeMenu = false; onRange(range) })
                    }
                }
            }
        }
        ControlButton("↻", onClick = onRefresh)
        Box {
            ControlButton("⋮", onClick = { settingsMenu = true })
            DropdownMenu(expanded = settingsMenu, onDismissRequest = { settingsMenu = false }) {
                Quality.entries.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text("Qualität: ${quality.label}${if (state.quality == quality) " ✓" else ""}") },
                        onClick = { settingsMenu = false; onQuality(quality) },
                    )
                }
                ResumeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text("Zurück: ${mode.label}${if (state.resumeMode == mode) " ✓" else ""}") },
                        onClick = { settingsMenu = false; onResume(mode) },
                    )
                }
                DropdownMenuItem(text = { Text("Abmelden") }, onClick = { settingsMenu = false; onLogout() })
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
        ),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MediaPage(
    post: MediaPost,
    active: Boolean,
    paused: Boolean,
    quality: Quality,
    resumeMode: ResumeMode,
    positionFor: (String) -> Long,
    savePosition: (String, Long) -> Unit,
    onTap: () -> Unit,
    onFailure: () -> Unit,
) {
    val galleryState = rememberPagerState(pageCount = { post.media.size })
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black)
            .pointerInput(post.id) { detectTapGestures(onTap = { onTap() }) },
    ) {
        HorizontalPager(
            state = galleryState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = post.media.size > 1,
            key = { post.media[it].key },
        ) { index ->
            val media = post.media[index]
            when (media.kind) {
                MediaKind.IMAGE -> RemoteImage(media.url, onFailure)
                MediaKind.VIDEO -> Video(
                    media = media,
                    active = active && galleryState.currentPage == index,
                    paused = paused,
                    quality = quality,
                    resumeMode = resumeMode,
                    initialPosition = positionFor(media.key),
                    savePosition = { savePosition(media.key, it) },
                    onFailure = onFailure,
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 54.dp),
        ) {
            Text("u/${post.author}", fontWeight = FontWeight.Bold, color = Color.White)
            Text(relativeTime(post.createdUtc), color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
        }
        if (post.media.size > 1) {
            Text(
                "${galleryState.currentPage + 1}/${post.media.size}",
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 64.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White,
            )
        }
        if (paused && active) {
            Text("▶", fontSize = 52.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun RemoteImage(url: String, onFailure: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
        onError = { failed = true },
    )
    LaunchedEffect(failed) { if (failed) onFailure() }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun Video(
    media: RemoteMedia,
    active: Boolean,
    paused: Boolean,
    quality: Quality,
    resumeMode: ResumeMode,
    initialPosition: Long,
    savePosition: (Long) -> Unit,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val trackSelector = remember(media.url) { DefaultTrackSelector(context) }
    val player = remember(media.url) {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(media.url))
            if (resumeMode == ResumeMode.RESUME && initialPosition > 0) seekTo(initialPosition)
            prepare()
        }
    }
    var wasActive by remember(media.key) { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event != Lifecycle.Event.ON_STOP && event != Lifecycle.Event.ON_DESTROY
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onFailure()
        }
        player.addListener(listener)
        onDispose {
            savePosition(player.currentPosition.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(quality) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setForceHighestSupportedBitrate(quality == Quality.HIGH)
            .build()
    }
    LaunchedEffect(active, paused, foreground, resumeMode) {
        if (active && !wasActive && resumeMode == ResumeMode.RESTART) player.seekTo(0)
        if (active && !paused && foreground) player.play() else {
            savePosition(player.currentPosition.coerceAtLeast(0L))
            player.pause()
        }
        wasActive = active
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (!media.hasAudio) {
            Text(
                "Kein Ton",
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(14.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White,
            )
        }
        ProgressScrubber(player, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProgressScrubber(player: Player, modifier: Modifier = Modifier) {
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var dragging by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            delay(200)
        }
    }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp)
            .pointerInput(player, duration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var horizontal = false
                    var lastX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        total += change.position - change.previousPosition
                        if (!horizontal && abs(total.x) > viewConfiguration.touchSlop && abs(total.x) > abs(total.y)) {
                            horizontal = true
                            dragging = true
                        }
                        if (horizontal && duration > 0) {
                            change.consume()
                            lastX = change.position.x.coerceIn(0f, size.width.toFloat())
                            position = (duration * (lastX / size.width)).toLong()
                            player.seekTo(position)
                        }
                        if (!change.pressed) {
                            if (!horizontal && abs(total.x) < viewConfiguration.touchSlop && abs(total.y) < viewConfiguration.touchSlop && duration > 0) {
                                change.consume()
                                position = (duration * (down.position.x.coerceIn(0f, size.width.toFloat()) / size.width)).toLong()
                                player.seekTo(position)
                            }
                            dragging = false
                            break
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (dragging) 5.dp else 3.dp).align(Alignment.BottomCenter)) {
            val y = size.height - 1f
            drawLine(Color.White.copy(alpha = 0.32f), Offset(0f, y), Offset(size.width, y), strokeWidth = size.height)
            drawLine(Color.White, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = size.height, cap = StrokeCap.Butt)
            if (dragging) drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(size.width * fraction, y))
        }
    }
}

private fun relativeTime(createdUtc: Long): String {
    val seconds = ((System.currentTimeMillis() / 1_000) - createdUtc).coerceAtLeast(0)
    return when {
        seconds < 60 -> "gerade eben"
        seconds < 3_600 -> "vor ${seconds / 60} Min."
        seconds < 86_400 -> "vor ${seconds / 3_600} Std."
        seconds < 2_592_000 -> "vor ${seconds / 86_400} Tagen"
        seconds < 31_536_000 -> "vor ${seconds / 2_592_000} Monaten"
        else -> "vor ${seconds / 31_536_000} Jahren"
    }
}
