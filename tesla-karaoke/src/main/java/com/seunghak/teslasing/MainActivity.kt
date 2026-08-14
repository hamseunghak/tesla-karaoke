package com.seunghak.teslasing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import kotlin.math.roundToInt

private val AppBackground = Color(0xFF090B10)
private val Panel = Color(0xFF151820)
private val Muted = Color(0xFF9DA3B0)
private val TeslaRed = Color(0xFFE82127)

private enum class MediaSource { YouTube, Demo }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TeslaRed,
                    background = AppBackground,
                    surface = Panel,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) { TeslaSingApp() }
        }
    }
}

@Composable
private fun TeslaSingApp() {
    val context = LocalContext.current
    val synth = remember { SynthEngine() }
    val analyzer = remember { VocalAnalyzer() }
    val queue = remember { mutableStateListOf<Song>() }
    var selected by remember { mutableStateOf<Song?>(null) }
    var mediaSource by remember { mutableStateOf(MediaSource.YouTube) }
    var playing by remember { mutableStateOf(false) }
    var parked by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var keyShift by remember { mutableIntStateOf(0) }
    var tempo by remember { mutableFloatStateOf(1f) }
    var vocal by remember { mutableStateOf(VocalReading(0f, 0f, 0)) }
    var micAllowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micAllowed = it }

    fun stop() {
        playing = false
        synth.stop()
        analyzer.stop()
    }

    fun start(song: Song, resume: Boolean = false) {
        if (!parked) return
        selected = song
        val from = if (resume) positionMs else 0L
        if (!resume) positionMs = 0
        startedAt = SystemClock.elapsedRealtime() - (from / tempo).toLong()
        playing = true
        synth.play(song, keyShift, tempo, from)
        if (micAllowed) {
            analyzer.start(
                targetNote = {
                    val beat = ((positionMs / 1000.0) / (60.0 / song.bpm)).toInt()
                    song.melody[(beat / 2).coerceAtLeast(0) % song.melody.size] + keyShift
                },
                onReading = { vocal = it }
            )
        }
    }

    LaunchedEffect(playing, tempo, selected) {
        while (playing) {
            val song = selected ?: break
            positionMs = ((SystemClock.elapsedRealtime() - startedAt) * tempo).toLong()
            if (positionMs >= song.durationMs) {
                stop()
                positionMs = song.durationMs
                val next = queue.firstOrNull()
                if (next != null) {
                    queue.removeAt(0)
                    start(next)
                }
            }
            delay(50)
        }
    }

    DisposableEffect(Unit) { onDispose { stop() } }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        Column {
            TopBar(
                parked = parked,
                mediaSource = mediaSource,
                onMediaSourceChange = {
                    stop()
                    mediaSource = it
                },
                onParkedChange = {
                    parked = it
                    if (!it) stop()
                }
            )
            if (!parked) {
                DrivingLock()
            } else if (mediaSource == MediaSource.YouTube) {
                YouTubePanel()
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isPortrait = maxWidth < 700.dp
                    val library: @Composable (Modifier) -> Unit = { childModifier ->
                        LibraryPanel(
                            modifier = childModifier,
                            selected = selected,
                            queue = queue,
                            onSelect = { selected = it; positionMs = 0; stop() },
                            onQueue = { if (it !in queue) queue.add(it) }
                        )
                    }
                    val player: @Composable (Modifier) -> Unit = { childModifier ->
                        PlayerPanel(
                            modifier = childModifier,
                            song = selected,
                            playing = playing,
                            positionMs = positionMs,
                            keyShift = keyShift,
                            tempo = tempo,
                            vocal = vocal,
                            micAllowed = micAllowed,
                            onMicRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onToggle = {
                                selected?.let { song ->
                                    if (playing) stop() else start(song, resume = positionMs > 0 && positionMs < song.durationMs)
                                }
                            },
                            onSeek = { delta ->
                                selected?.let { song ->
                                    val wasPlaying = playing
                                    stop()
                                    positionMs = (positionMs + delta).coerceIn(0, song.durationMs)
                                    if (wasPlaying) start(song, resume = true)
                                }
                            },
                            onKey = { newKey ->
                                selected?.let { song ->
                                    val wasPlaying = playing
                                    stop(); keyShift = newKey
                                    if (wasPlaying) start(song, resume = true)
                                }
                            },
                            onTempo = { newTempo ->
                                selected?.let { song ->
                                    val wasPlaying = playing
                                    stop(); tempo = newTempo
                                    if (wasPlaying) start(song, resume = true)
                                }
                            }
                        )
                    }
                    if (isPortrait) {
                        Column(Modifier.fillMaxSize()) {
                            library(Modifier.weight(.43f))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                            player(Modifier.weight(.57f))
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            library(Modifier.weight(.36f))
                            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .08f)))
                            player(Modifier.weight(.64f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    parked: Boolean,
    mediaSource: MediaSource,
    onMediaSourceChange: (MediaSource) -> Unit,
    onParkedChange: (Boolean) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 700.dp
        Column(modifier = Modifier.fillMaxWidth().height(if (compact) 108.dp else 64.dp).padding(horizontal = if (compact) 14.dp else 22.dp)) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(if (compact) 30.dp else 34.dp).clip(CircleShape).background(TeslaRed),
            contentAlignment = Alignment.Center
        ) { Text("♪", fontSize = 20.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(11.dp))
        Text("TESLA SING", fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = if (compact) 16.sp else 18.sp)
        if (!compact) Text("  companion", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (!compact) {
            Text("오디오는 테슬라 Bluetooth로 연결", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(18.dp))
        }
        Row(
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (parked) Color(0xFF163D30) else Color(0xFF3A2526))
                .clickable { onParkedChange(!parked) }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (parked) Color(0xFF35DC94) else TeslaRed))
            Spacer(Modifier.width(8.dp))
            Text(if (parked) "P  주차됨" else "D  주행 중", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
            }
            if (compact) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("YouTube 노래방", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Tesla Bluetooth 출력", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(alpha = .12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else Muted,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontSize = 13.sp
    )
}

@Composable
private fun YouTubePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("tesla_sing_youtube", android.content.Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "").orEmpty()) }
    var draftApiKey by remember { mutableStateOf(apiKey) }
    var showSettings by remember { mutableStateOf(apiKey.isBlank()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var selected by remember { mutableStateOf<YouTubeVideo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("노래 제목과 가수 이름을 검색하세요.") }

    fun search() {
        if (loading) return
        prefs.edit().putString("api_key", apiKey.trim()).apply()
        loading = true
        message = "YouTube에서 검색 중…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { YouTubeClient.search(apiKey, query) }
            }.onSuccess {
                results = it
                message = if (it.isEmpty()) "검색 결과가 없습니다." else "${it.size}개의 영상을 찾았습니다."
            }.onFailure {
                message = it.message ?: "검색 중 오류가 발생했습니다."
            }
            loading = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val portrait = maxWidth < 700.dp
        val searchPane: @Composable (Modifier) -> Unit = { childModifier ->
            YouTubeSearchPane(
                modifier = childModifier,
                apiKey = apiKey,
                query = query,
                results = results,
                selected = selected,
                loading = loading,
                message = message,
                compact = portrait,
                onQueryChange = { query = it },
                onSearch = { search() },
                onSelect = { selected = it },
                onOpenSettings = {
                    draftApiKey = apiKey
                    showSettings = true
                }
            )
        }
        if (portrait) {
            Column(Modifier.fillMaxSize()) {
                searchPane(Modifier.weight(.52f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                YouTubePlayerPanel(
                    modifier = Modifier.weight(.48f),
                    video = selected,
                    compact = true,
                    onPlay = { selected?.let { openFullscreenYouTube(context, it) } }
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                searchPane(Modifier.weight(.38f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .08f)))
                YouTubePlayerPanel(
                    modifier = Modifier.weight(.62f),
                    video = selected,
                    compact = false,
                    onPlay = { selected?.let { openFullscreenYouTube(context, it) } }
                )
            }
        }
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YouTube 검색 API", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = draftApiKey,
                        onValueChange = { draftApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("YouTube Data API 키") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Text("API 키는 이 기기에만 저장됩니다.", color = Muted, fontSize = 11.sp)
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://console.cloud.google.com/apis/library/youtube.googleapis.com")
                                )
                            )
                        }
                    ) { Text("API 키 발급받기 ↗") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val savedKey = draftApiKey.trim()
                    apiKey = savedKey
                    prefs.edit().putString("api_key", savedKey).apply()
                    showSettings = false
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("취소") }
            }
        )
    }
}

private fun openFullscreenYouTube(context: Context, video: YouTubeVideo) {
    context.startActivity(
        Intent(context, YouTubePlayerActivity::class.java).apply {
            putExtra(YouTubePlayerActivity.EXTRA_VIDEO_ID, video.videoId)
            putExtra(YouTubePlayerActivity.EXTRA_VIDEO_TITLE, video.title)
            putExtra(YouTubePlayerActivity.EXTRA_VIDEO_CHANNEL, video.channel)
        }
    )
}

@Composable
private fun YouTubeSearchPane(
    modifier: Modifier,
    apiKey: String,
    query: String,
    results: List<YouTubeVideo>,
    selected: YouTubeVideo?,
    loading: Boolean,
    message: String,
    compact: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (YouTubeVideo) -> Unit,
    onOpenSettings: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    fun submitSearch() {
        if (!loading && query.isNotBlank() && apiKey.isNotBlank()) {
            focusManager.clearFocus()
            onSearch()
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxHeight().padding(horizontal = if (compact) 12.dp else 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "youtube-heading") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YouTube 노래방", fontSize = if (compact) 16.sp else 24.sp, fontWeight = FontWeight.Bold)
                    Text("검색어에 ‘금영 노래방’을 자동 적용합니다.", color = Muted, fontSize = 11.sp)
                }
                TextButton(onClick = onOpenSettings) { Text("⚙ 설정", fontWeight = FontWeight.Bold) }
            }
        }
        item(key = "search-field") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("가수 또는 노래 제목") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitSearch() }),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submitSearch() }, enabled = !loading && query.isNotBlank() && apiKey.isNotBlank(), modifier = Modifier.height(56.dp)) {
                    Text(if (loading) "검색 중" else "검색")
                }
            }
        }
        item(key = "search-message") {
            Text(message, color = Muted, fontSize = 11.sp, maxLines = 2)
        }
        items(results, key = { "youtube-${it.videoId}" }) { video ->
            YouTubeResultRow(video, selected?.videoId == video.videoId) { onSelect(video) }
        }
    }
}

@Composable
private fun YouTubeResultRow(video: YouTubeVideo, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (selected) TeslaRed.copy(alpha = .2f) else Panel)
            .clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(width = 64.dp, height = 42.dp).clip(RoundedCornerShape(9.dp)).background(TeslaRed),
            contentAlignment = Alignment.Center
        ) { Text("▶", fontSize = 18.sp, color = Color.White) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Text(video.channel, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun YouTubePlayerPanel(
    modifier: Modifier,
    video: YouTubeVideo?,
    compact: Boolean,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    if (video == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▶", color = TeslaRed, fontSize = 54.sp)
                Spacer(Modifier.height(12.dp))
                Text("재생할 영상을 선택하세요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("노래방·반주 영상을 선택하면 YouTube 앱에서 재생됩니다.", color = Muted, fontSize = 13.sp)
            }
        }
        return
    }

    Column(modifier.padding(if (compact) 12.dp else 20.dp)) {
        Text(video.title, fontSize = if (compact) 15.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.channel, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(if (compact) 12.dp else 18.dp))
                .background(Color.Black)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = video.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg" },
                contentDescription = "${video.title} YouTube 썸네일",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier.size(68.dp).clip(CircleShape).background(TeslaRed.copy(alpha = .94f)),
                contentAlignment = Alignment.Center
            ) { Text("▶", color = Color.White, fontSize = 28.sp, modifier = Modifier.padding(start = 4.dp)) }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!compact) {
                Text("YouTube 앱에서 안정적으로 재생됩니다.", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.videoId}"))
                    )
                }
            ) { Text("YouTube에서 열기 ↗", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun DrivingLock() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF252A36), AppBackground), radius = 900f)
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("안전 운전 중", fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("노래방 조작은 주차 상태에서만 사용할 수 있어요.", color = Muted, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text("상단의 주행 상태를 눌러 주차 상태로 전환하세요.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LibraryPanel(
    modifier: Modifier,
    selected: Song?,
    queue: List<Song>,
    onSelect: (Song) -> Unit,
    onQueue: (Song) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = demoSongs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    Column(modifier.padding(20.dp)) {
        Text("노래 찾기", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("제목 또는 가수 검색") },
            singleLine = true,
            leadingIcon = { Text("⌕", fontSize = 24.sp) },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오리지널 데모", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${filtered.size}곡", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { song ->
                SongRow(song, selected?.id == song.id, song in queue, onSelect, onQueue)
            }
        }
        if (queue.isNotEmpty()) {
            Text("다음 곡  ${queue.joinToString(" · ") { it.title }}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SongRow(song: Song, selected: Boolean, queued: Boolean, onSelect: (Song) -> Unit, onQueue: (Song) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(song.color).copy(alpha = .22f) else Panel)
            .clickable { onSelect(song) }.padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(11.dp)).background(Color(song.color)),
            contentAlignment = Alignment.Center
        ) { Text("♪", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${song.artist} · ${formatTime(song.durationMs)}", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        TextButton(onClick = { onQueue(song) }, enabled = !queued) {
            Text(if (queued) "대기 중" else "+ 예약", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlayerPanel(
    modifier: Modifier,
    song: Song?,
    playing: Boolean,
    positionMs: Long,
    keyShift: Int,
    tempo: Float,
    vocal: VocalReading,
    micAllowed: Boolean,
    onMicRequest: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onKey: (Int) -> Unit,
    onTempo: (Float) -> Unit
) {
    if (song == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", color = TeslaRed, fontSize = 58.sp)
                Text("부를 노래를 골라주세요", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("왼쪽 목록에서 노래를 선택하면 가사가 표시됩니다.", color = Muted)
            }
        }
        return
    }
    val lineIndex = song.lyrics.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val current = song.lyrics[lineIndex]
    val next = song.lyrics.getOrNull(lineIndex + 1)
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 700.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 26.dp, vertical = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(song.title, fontSize = if (compact) 15.sp else 20.sp, fontWeight = FontWeight.Bold)
                Text(song.artist, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            if (!micAllowed) {
                OutlinedButton(onClick = onMicRequest) { Text("마이크 허용") }
            } else {
                Text(if (playing && vocal.score > 0) "LIVE  ${vocal.score}점" else "MIC READY", color = Color(0xFF35DC94), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(
                Brush.verticalGradient(listOf(Color(song.color).copy(alpha = .20f), Panel))
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                AnimatedContent(targetState = current.text, label = "lyrics") { lyric ->
                    Text(lyric, fontSize = if (compact) 24.sp else 34.sp, lineHeight = if (compact) 32.sp else 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
                Text(next?.text.orEmpty(), color = Color.White.copy(alpha = .38f), fontSize = if (compact) 16.sp else 22.sp, textAlign = TextAlign.Center)
                AnimatedVisibility(micAllowed && playing) {
                    Row(modifier = Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("음성", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(progress = { vocal.level }, modifier = Modifier.width(130.dp).height(5.dp), color = Color(0xFF35DC94))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (positionMs.toFloat() / song.durationMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = Color(song.color),
            trackColor = Color.White.copy(alpha = .12f)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(positionMs), color = Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatTime(song.durationMs), color = Muted, fontSize = 11.sp)
        }
        if (compact) {
            PlaybackButtons(song, playing, onToggle, onSeek)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepControl("키", if (keyShift == 0) "원키" else "%+d".format(keyShift), { onKey((keyShift - 1).coerceAtLeast(-5)) }, { onKey((keyShift + 1).coerceAtMost(5)) }, compact = true)
                StepControl("템포", "${(tempo * 100).roundToInt()}%", { onTempo((tempo - .05f).coerceAtLeast(.75f)) }, { onTempo((tempo + .05f).coerceAtMost(1.25f)) }, compact = true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaybackButtons(song, playing, onToggle, onSeek)
                Spacer(Modifier.width(12.dp))
                StepControl("키", if (keyShift == 0) "원키" else "%+d".format(keyShift), { onKey((keyShift - 1).coerceAtLeast(-5)) }, { onKey((keyShift + 1).coerceAtMost(5)) })
                StepControl("템포", "${(tempo * 100).roundToInt()}%", { onTempo((tempo - .05f).coerceAtLeast(.75f)) }, { onTempo((tempo + .05f).coerceAtMost(1.25f)) })
            }
        }
    }
    }
}

@Composable
private fun PlaybackButtons(song: Song, playing: Boolean, onToggle: () -> Unit, onSeek: (Long) -> Unit) {
    SmallControl("−10", onClick = { onSeek(-10_000) })
    Spacer(Modifier.width(8.dp))
    Button(
        onClick = onToggle,
        modifier = Modifier.width(110.dp).height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(song.color)),
        shape = RoundedCornerShape(24.dp)
    ) { Text(if (playing) "Ⅱ  일시정지" else "▶  시작", fontWeight = FontWeight.Bold) }
    Spacer(Modifier.width(8.dp))
    SmallControl("+10", onClick = { onSeek(10_000) })
}

@Composable
private fun SmallControl(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) { Text(label) }
}

@Composable
private fun StepControl(label: String, value: String, minus: () -> Unit, plus: () -> Unit, compact: Boolean = false) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = minus, modifier = if (compact) Modifier.width(44.dp) else Modifier) { Text("−", fontSize = 18.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (compact) 38.dp else 42.dp)) {
                Text(label, color = Muted, fontSize = 9.sp)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            TextButton(onClick = plus, modifier = if (compact) Modifier.width(44.dp) else Modifier) { Text("+", fontSize = 18.sp) }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
