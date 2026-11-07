package com.neonplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

private val Ink = Color(0xFF090D12)
private val Panel = Color(0xFF121A22)
private val Panel2 = Color(0xFF18232D)
private val Mint = Color(0xFF69E6D2)
private val Violet = Color(0xFFB4A1FF)
private val Muted = Color(0xFF91A0AD)

data class VideoItem(
    val id: Long, val uri: Uri, val title: String, val displayName: String,
    val duration: Long, val size: Long, val folder: String, val dateAdded: Long
)

class MainActivity : ComponentActivity() {
    var pipPlayer: Player? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 13, 18)
        window.navigationBarColor = android.graphics.Color.rgb(9, 13, 18)
        setContent { NeonApp(onPlayer = { pipPlayer = it }) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pipPlayer?.isPlaying == true) {
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            enterPictureInPictureMode(params)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NeonApp(onPlayer: (Player?) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPrefs(context) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var selected by remember { mutableStateOf<VideoItem?>(null) }
    var activeQueue by remember { mutableStateOf<List<String>?>(null) }
    var tab by remember { mutableStateOf("الرئيسية") }
    var query by remember { mutableStateOf("") }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(prefs.favorites()) }
    var resumeMap by remember { mutableStateOf(prefs.progress()) }
    var playlists by remember { mutableStateOf(prefs.playlists()) }
    var settings by remember { mutableStateOf(prefs.settings()) }
    var permissionGranted by remember { mutableStateOf(hasVideoPermission(context)) }
    var scanning by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var networkDialog by remember { mutableStateOf(false) }
    var playlistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addToPlaylistVideo by remember { mutableStateOf<VideoItem?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }

    val token = remember { SessionToken(context, ComponentName(context, PlaybackService::class.java)) }
    val controllerFuture = remember { MediaController.Builder(context, token).buildAsync() }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener({ runCatching { controllerFuture.get() }.onSuccess { controller = it; onPlayer(it) } }, ContextCompat.getMainExecutor(context))
        onDispose { onPlayer(null); MediaController.releaseFuture(controllerFuture) }
    }

    LaunchedEffect(permissionGranted, refreshKey) {
        if (permissionGranted) {
            scanning = true
            videos = withContext(Dispatchers.IO) { queryVideos(context) }
            scanning = false
        }
    }
    LaunchedEffect(favorites) { prefs.saveFavorites(favorites) }
    LaunchedEffect(resumeMap) { prefs.saveProgress(resumeMap) }
    LaunchedEffect(playlists) { prefs.savePlaylists(playlists) }
    LaunchedEffect(settings) { prefs.saveSettings(settings) }

    BackHandler(selected != null) { selected = null }
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, secondary = Violet, background = Ink, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        val active = selected
        if (active != null) {
            if (controller == null) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Mint) }
            } else {
                PlayerScreen(
                    item = active, player = controller!!,
                    initialPosition = if (settings.rememberPosition) resumeMap[active.id] ?: 0L else 0L,
                    settings = settings,
                    onBack = { selected = null },
                    onProgress = { resumeMap = resumeMap + (active.id to it) },
                    onNext = {
                        val queue = activeQueue
                        if (queue != null) {
                            val nextId = queue.getOrNull(queue.indexOf(active.id) + 1)
                            val nextVideo = videos.firstOrNull { it.id == nextId }
                            if (nextVideo != null) selected = nextVideo else activeQueue = null
                        } else {
                            val index = videos.indexOfFirst { it.id == active.id }
                            if (index >= 0 && index < videos.lastIndex) selected = videos[index + 1]
                        }
                    }
                )
            }
        } else {
            Scaffold(containerColor = Ink, bottomBar = {
                NavigationBar(containerColor = Color(0xFF0D141B), tonalElevation = 0.dp) {
                    listOf("الرئيسية" to Icons.Default.Home, "الفيديوهات" to Icons.Default.VideoLibrary, "المجلدات" to Icons.Default.Folder, "المفضلة" to Icons.Default.Favorite, "قوائمي" to Icons.Default.QueueMusic, "الإعدادات" to Icons.Default.Settings).forEach { (label, icon) ->
                        NavigationBarItem(selected = tab == label, onClick = { tab = label; folderFilter = null }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 9.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = Mint.copy(.12f), unselectedIconColor = Muted))
                    }
                }
            }) { padding ->
                when (tab) {
                    "الإعدادات" -> SettingsScreen(settings, { settings = it }, Modifier.padding(padding))
                    "المجلدات" -> FolderScreen(videos, Modifier.padding(padding)) { folder -> folderFilter = folder; tab = "الفيديوهات" }
                    "قوائمي" -> PlaylistScreen(playlists, videos, onOpen = { video, ids -> activeQueue = ids; selected = video }, onCreate = { playlistDialog = true }, onDelete = { playlists = playlists.filterNot { p -> p.id == it } }, onPlayAll = { p -> activeQueue = p.videoIds; selected = videos.firstOrNull { it.id == p.videoIds.firstOrNull() } }, modifier = Modifier.padding(padding))
                    else -> {
                        val shown = when (tab) { "المفضلة" -> videos.filter { it.id in favorites }; else -> videos }
                        LibraryScreen(
                            videos = shown.filter { folderFilter == null || it.folder == folderFilter }, allCount = videos.size,
                            favoriteCount = favorites.size, folderCount = videos.map { it.folder }.distinct().size,
                            tab = tab, query = query, onQuery = { query = it }, loading = scanning,
                            granted = permissionGranted, onRequestPermission = { permissionLauncher.launch(permission) },
                            onRefresh = { refreshKey++ }, onOpen = { activeQueue = null; selected = it }, favorites = favorites,
                            onFavorite = { id -> favorites = if (id in favorites) favorites - id else favorites + id },
                            onAddPlaylist = { addToPlaylistVideo = it }, resumeMap = resumeMap,
                            onNetwork = { networkDialog = true }, modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
    }

    if (networkDialog) NetworkDialog(onDismiss = { networkDialog = false }) { url ->
        val uri = Uri.parse(url.trim())
        selected = VideoItem(-System.currentTimeMillis(), uri, uri.lastPathSegment ?: url, url, 0, 0, "الشبكة", 0)
        networkDialog = false
    }
    if (playlistDialog) AlertDialog(onDismissRequest = { playlistDialog = false }, title = { Text("قائمة تشغيل جديدة") }, text = { OutlinedTextField(newPlaylistName, { newPlaylistName = it }, label = { Text("اسم القائمة") }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (newPlaylistName.isNotBlank()) playlists = playlists + Playlist(UUID.randomUUID().toString(), newPlaylistName.trim(), emptyList()); newPlaylistName = ""; playlistDialog = false }) { Text("إنشاء") } }, dismissButton = { TextButton(onClick = { playlistDialog = false }) { Text("إلغاء") } })
    addToPlaylistVideo?.let { video ->
        AlertDialog(onDismissRequest = { addToPlaylistVideo = null }, title = { Text("إضافة إلى قائمة تشغيل") }, text = {
            if (playlists.isEmpty()) Text("أنشئ قائمة تشغيل أولاً من تبويب قوائمي.") else Column { playlists.forEach { p -> TextButton(onClick = { playlists = playlists.map { if (it.id == p.id && video.id !in it.videoIds) it.copy(videoIds = it.videoIds + video.id) else it }; addToPlaylistVideo = null }) { Text(p.name) } } }
        }, confirmButton = { TextButton(onClick = { addToPlaylistVideo = null }) { Text("إغلاق") } })
    }
}

private fun hasVideoPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun queryVideos(context: Context): List<VideoItem> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED)
    val result = mutableListOf<VideoItem>()
    val cursor: Cursor? = context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")
    cursor?.use {
        val id = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID); val title = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
        val name = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME); val duration = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val size = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE); val folder = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val date = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        while (it.moveToNext()) { val key = it.getLong(id); result += VideoItem(key, Uri.withAppendedPath(collection, key.toString()), it.getString(title) ?: "فيديو", it.getString(name) ?: "video", it.getLong(duration), it.getLong(size), it.getString(folder) ?: "غير معروف", it.getLong(date)) }
    }
    return result
}

@Composable
private fun LibraryScreen(videos: List<VideoItem>, allCount: Int, favoriteCount: Int, folderCount: Int, tab: String, query: String, onQuery: (String) -> Unit, loading: Boolean, granted: Boolean, onRequestPermission: () -> Unit, onRefresh: () -> Unit, onOpen: (VideoItem) -> Unit, favorites: Set<Long>, onFavorite: (Long) -> Unit, onAddPlaylist: (VideoItem) -> Unit, resumeMap: Map<Long, Long>, onNetwork: () -> Unit, modifier: Modifier = Modifier) {
    val filtered = videos.filter { it.title.contains(query, true) || it.displayName.contains(query, true) || it.folder.contains(query, true) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("NEON PLAYER", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp); Text(if (tab == "المفضلة") "المفضلة" else "مكتبتك السينمائية", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold) }; IconButton(onClick = onNetwork) { Icon(Icons.Default.Language, "مصدر شبكة", tint = Mint) } } }
        item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = Muted); TextField(query, onQuery, modifier = Modifier.weight(1f), placeholder = { Text("ابحث عن فيديو أو مجلد", color = Muted) }, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)); if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, null, tint = Muted) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("الفيديوهات", allCount.toString(), Icons.Default.VideoLibrary, Modifier.weight(1f)); StatCard("المفضلة", favoriteCount.toString(), Icons.Default.Favorite, Modifier.weight(1f)); StatCard("المجلدات", folderCount.toString(), Icons.Default.Folder, Modifier.weight(1f)) } }
        if (!granted) item { EmptyPanel("اسمح بالوصول إلى الفيديوهات", "المكتبة المحلية على جهازك فقط.", "منح الإذن", onRequestPermission) }
        else if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mint) }
        else if (filtered.isEmpty()) item { EmptyPanel(if (query.isEmpty()) "لا توجد فيديوهات" else "لا توجد نتائج", "يمكنك تحديث المكتبة أو فتح مصدر شبكة.", "تحديث المكتبة", onRefresh) }
        else { item { Text("${filtered.size} فيديو", color = Color.White, fontWeight = FontWeight.Bold) }; items(filtered, key = { it.id }) { video -> VideoRow(video, video.id in favorites, resumeMap[video.id] ?: 0L, { onOpen(video) }, { onFavorite(video.id) }, { onAddPlaylist(video) }) } }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(17.dp)).background(Panel).padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Icon(icon, null, tint = Mint, modifier = Modifier.size(17.dp)); Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp); Text(label, color = Muted, fontSize = 9.sp, maxLines = 1) } }

@Composable
private fun VideoRow(item: VideoItem, favorite: Boolean, progress: Long, onClick: () -> Unit, onFavorite: () -> Unit, onPlaylist: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Panel).clickable(onClick = onClick).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(112.dp, 76.dp).clip(RoundedCornerShape(13.dp)).background(Panel2)) { AsyncImage(item.uri, item.title, Modifier.fillMaxSize()); Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp)); Text(formatTime(item.duration), Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(.7f), RoundedCornerShape(4.dp)).padding(3.dp), color = Color.White, fontSize = 9.sp); if (progress > 0 && item.duration > 0) LinearProgressIndicator(progress = { (progress.toFloat() / item.duration).coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp), color = Mint) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(item.folder, color = Muted, fontSize = 10.sp); Text("${formatSize(item.size)} • ${formatTime(item.duration)}", color = Muted, fontSize = 9.sp) }
        Column { IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Color(0xFFFF718D) else Muted) }; IconButton(onClick = onPlaylist) { Icon(Icons.Default.PlaylistAdd, null, tint = Mint) } }
    }
}

@Composable
private fun FolderScreen(videos: List<VideoItem>, modifier: Modifier = Modifier, onFolder: (String) -> Unit) {
    val folders = videos.groupBy { it.folder }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("المجلدات", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold) }; items(folders.entries.toList(), key = { it.key }) { entry -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).clickable { onFolder(entry.key) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Mint, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(entry.key, color = Color.White, fontWeight = FontWeight.SemiBold); Text("${entry.value.size} فيديو", color = Muted, fontSize = 11.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted) } } }
}

@Composable
private fun PlaylistScreen(playlists: List<Playlist>, videos: List<VideoItem>, onOpen: (VideoItem, List<Long>) -> Unit, onCreate: () -> Unit, onDelete: (String) -> Unit, onPlayAll: (Playlist) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("قوائم التشغيل", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Icon(Icons.Default.Add, null); Text("جديدة") } } }
        if (playlists.isEmpty()) item { Text("أنشئ قائمة واجمع فيها فيديوهاتك.", color = Muted) }
        items(playlists, key = { it.id }) { p -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.QueueMusic, null, tint = Mint); Spacer(Modifier.width(10.dp)); Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${p.videoIds.size}", color = Muted); IconButton(onClick = { onPlayAll(p) }) { Icon(Icons.Default.PlayArrow, "تشغيل القائمة", tint = Mint) }; IconButton(onClick = { onDelete(p.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) } }; p.videoIds.mapNotNull { id -> videos.find { it.id == id } }.forEach { v -> TextButton(onClick = { onOpen(v, p.videoIds) }) { Text(v.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }
    }
}

@Composable
private fun SettingsScreen(settings: PlayerSettings, onChange: (PlayerSettings) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("الإعدادات", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("خيارات التشغيل والإيماءات تُحفظ تلقائياً", color = Muted, fontSize = 12.sp) }
        item { SettingSwitch("حفظ موضع المشاهدة", "استئناف الفيديو من آخر موضع", settings.rememberPosition) { onChange(settings.copy(rememberPosition = it)) }; SettingSwitch("التشغيل التلقائي", "الانتقال إلى الفيديو التالي", settings.autoplay) { onChange(settings.copy(autoplay = it)) }; SettingSwitch("إيماءات السطوع والصوت", "السحب العمودي يميناً ويساراً", settings.verticalGestures) { onChange(settings.copy(verticalGestures = it)) }; SettingSwitch("السحب للبحث", "السحب الأفقي لتقديم الفيديو وتأخيره", settings.swipeSeek) { onChange(settings.copy(swipeSeek = it)) } }
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(15.dp)) { Text("القفز بالنقر المزدوج: ${settings.doubleTapSeekSeconds} ثانية", color = Color.White); Slider(settings.doubleTapSeekSeconds.toFloat(), { onChange(settings.copy(doubleTapSeekSeconds = it.toInt())) }, valueRange = 5f..30f, steps = 4, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint)) } }
        item { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(15.dp)) { Text("سرعة الضغط المطوّل: ${settings.longPressSpeed}×", color = Color.White); Slider(settings.longPressSpeed, { onChange(settings.copy(longPressSpeed = it)) }, valueRange = 1.25f..3f, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint)) } }
        item { Text("Neon Player • إصدار التطوير 1.1", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 10.sp) }; Switch(checked, onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Mint)) } }

@Composable
private fun EmptyPanel(title: String, subtitle: String, action: String, onAction: () -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.VideoLibrary, null, tint = Mint, modifier = Modifier.size(36.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp); Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text(action) } } }

@Composable
private fun NetworkDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("فتح مصدر شبكة") }, text = { Column { Text("يدعم روابط HTTP/HTTPS وHLS وDASH وRTSP بحسب توافق المصدر.", color = Muted, fontSize = 12.sp); OutlinedTextField(url, { url = it }, label = { Text("رابط الفيديو") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true) } }, confirmButton = { TextButton(onClick = { if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://")) onOpen(url) }) { Text("تشغيل") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

private data class TrackChoice(val group: Tracks.Group, val index: Int, val label: String)

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(item: VideoItem, player: Player, initialPosition: Long, settings: PlayerSettings, onBack: () -> Unit, onProgress: (Long) -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(player.isPlaying) }
    var controls by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(if (player.duration > 0) player.duration else item.duration) }
    var position by remember { mutableLongStateOf(initialPosition) }
    var speed by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var trackDialog by remember { mutableStateOf(0) } // 1 audio, 2 subtitles
    var tracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentAutoplay by rememberUpdatedState(settings.autoplay)
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = context.contentResolver.getType(uri) ?: "application/x-subrip"
            val config = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            val newItem = MediaItem.Builder().setUri(item.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build()).setSubtitleConfigurations(listOf(config)).build()
            val at = player.currentPosition; player.setMediaItem(newItem, at); player.prepare(); player.play()
        }
    }
    LaunchedEffect(item.uri) { player.setMediaItem(MediaItem.fromUri(item.uri)); player.prepare(); if (initialPosition > 0) player.seekTo(initialPosition); player.play() }
    DisposableEffect(player) { val listener = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }; override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(1L); if (state == Player.STATE_ENDED && currentAutoplay) currentOnNext() } }; player.addListener(listener); onDispose { currentOnProgress(player.currentPosition); player.removeListener(listener) } }
    LaunchedEffect(player) { while (true) { if (!dragging) { position = player.currentPosition; if (position > 0) currentOnProgress(position) }; delay(700) } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); setKeepContentOnPlayerReset(true) } }, modifier = Modifier.fillMaxSize()
            .pointerInput(settings.doubleTapSeekSeconds) { detectTapGestures(onTap = { controls = !controls }, onDoubleTap = { point -> val forward = point.x > size.width / 2; player.seekTo((player.currentPosition + (if (forward) 1 else -1) * settings.doubleTapSeekSeconds * 1000L).coerceIn(0L, max(0L, player.duration))); controls = true }, onLongPress = { player.setPlaybackSpeed(settings.longPressSpeed); speed = settings.longPressSpeed }, onPress = { tryAwaitRelease(); player.setPlaybackSpeed(1f); speed = 1f }) }
            .pointerInput(settings) {
                var startX = 0f
                detectDragGestures(
                    onDragStart = { startX = it.x },
                    onDragEnd = { player.setPlaybackSpeed(1f); speed = 1f },
                    onDrag = { change, drag ->
                        if (abs(drag.x) > abs(drag.y) && settings.swipeSeek) {
                            player.seekTo((player.currentPosition + (drag.x * 500).toLong()).coerceIn(0L, max(0L, player.duration)))
                        } else if (settings.verticalGestures) {
                            val activity = context as? ComponentActivity
                            if (startX < size.width / 2) {
                                val lp = activity?.window?.attributes
                                if (lp != null) {
                                    lp.screenBrightness = (lp.screenBrightness - drag.y / 900f).coerceIn(.05f, 1f)
                                    activity.window.attributes = lp
                                }
                            } else {
                                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVolume = (current - (drag.y / 45f).toInt()).coerceIn(0, maxVolume)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            }
                        }
                        change.consume()
                    }
                )
            }
        )
        if (controls) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.68f), Color.Transparent, Color.Black.copy(.78f)))) )
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Column(Modifier.weight(1f)) { Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1); Text(item.displayName, color = Muted, fontSize = 10.sp, maxLines = 1) }; IconButton(onClick = { trackDialog = 1; tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> TrackChoice(g, i, g.getTrackFormat(i).label ?: g.getTrackFormat(i).language ?: "Audio ${i + 1}") } } }) { Icon(Icons.Default.Audiotrack, "المسار الصوتي", tint = Mint) }; IconButton(onClick = { trackDialog = 2; tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> TrackChoice(g, i, g.getTrackFormat(i).label ?: g.getTrackFormat(i).language ?: "Subtitle ${i + 1}") } } }) { Icon(Icons.Default.Subtitles, "الترجمة", tint = Mint) }; IconButton(onClick = { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }) { Icon(Icons.Default.Add, "إضافة ملف ترجمة", tint = Color.White) } }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) { IconButton(onClick = { player.seekBack() }) { Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(38.dp)) }; IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(70.dp).clip(CircleShape).background(Mint)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(40.dp)) }; IconButton(onClick = { player.seekForward() }) { Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(38.dp)) } }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(formatTime(position), color = Color.White, fontSize = 10.sp); Slider(value = position.coerceAtMost(duration).toFloat(), onValueChange = { dragging = true; position = it.toLong() }, onValueChangeFinished = { player.seekTo(position); dragging = false }, valueRange = 0f..max(1f, duration.toFloat()), modifier = Modifier.weight(1f).padding(horizontal = 8.dp), colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint)); Text(formatTime(duration), color = Color.White, fontSize = 10.sp) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> .75f; else -> 1f }; player.setPlaybackSpeed(speed) }) { Icon(Icons.Default.Speed, null, tint = Mint); Text("${speed}×", color = Color.White) }; TextButton(onClick = onNext) { Text("التالي", color = Color.White); Icon(Icons.Default.SkipNext, null, tint = Mint) }; TextButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) (context as? MainActivity)?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }) { Text("PiP", color = Mint, fontWeight = FontWeight.Bold) } } }
        } else Box(Modifier.fillMaxSize().clickable { controls = true })
    }

    if (trackDialog != 0) AlertDialog(onDismissRequest = { trackDialog = 0 }, title = { Text(if (trackDialog == 1) "المسار الصوتي" else "مسار الترجمة") }, text = { Column { if (trackDialog == 2) TextButton(onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT).build(); trackDialog = 0 }) { Text("إيقاف الترجمة") }; if (tracks.isEmpty()) Text("لا توجد مسارات مدمجة متاحة. يمكنك إضافة ملف ترجمة خارجي من زر +.", color = Muted); tracks.forEach { choice -> TextButton(onClick = { val type = if (trackDialog == 1) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT; player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false).clearOverridesOfType(type).addOverride(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.index))).build(); trackDialog = 0 }) { Text(choice.label) } } } }, confirmButton = { TextButton(onClick = { trackDialog = 0 }) { Text("إغلاق") } })
}

private fun formatTime(ms: Long): String { val total = (ms / 1000).coerceAtLeast(0); val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60; return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s) }
private fun formatSize(bytes: Long): String = when { bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024)); bytes >= 1024L * 1024 -> String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024)); else -> "${bytes / 1024} KB" }
