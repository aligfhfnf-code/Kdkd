@file:OptIn(ExperimentalFoundationApi::class)

package com.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

// Возможные package-имена Headunit Revived / Headunit. Если не найдёт — выбери вручную в настройках.
private val HU_CANDIDATES = listOf("com.andrerinas.headunitrevived", "info.anodsplace.headunit.contrib", "ca.yyx.hu")
private val CARD = Color(0xCC1B1B1F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = Prefs(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Launcher(prefs) } }
    }
}

fun Context.launch(pkg: String): Boolean {
    val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
}

fun huPackage(ctx: Context, p: Prefs): String? {
    val saved = p.get("hu", "")
    if (saved.isNotEmpty() && ctx.packageManager.getLaunchIntentForPackage(saved) != null) return saved
    return HU_CANDIDATES.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
}

@Composable
fun Launcher(p: Prefs) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf(false) }
    val dock = p.get("dock", "").split(",").filter { it.isNotBlank() }

    Box(Modifier.fillMaxSize()) {
        Background(p.get("wp", "gradient"), p.get("bgv", "0"))
        Column(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                if (p.on("clock")) Clock()
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (p.on("speed")) SpeedCard(p.get("unit", "kmh") == "mph", Modifier.weight(1f).fillMaxHeight())
                if (p.on("music")) MusicCard(Modifier.weight(2f).fillMaxHeight())
            }
            Spacer(Modifier.height(12.dp))
            // Нижняя панель
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CARD).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoundBtn("⚙") { settings = true }
                Button(onClick = {
                    val pkg = huPackage(ctx, p)
                    if (pkg == null || !ctx.launch(pkg)) {
                        Toast.makeText(ctx, "Headunit Revived не найден. Выбери его в настройках.", Toast.LENGTH_LONG).show()
                        settings = true
                    }
                }) { Text("🚗  Android Auto", fontSize = 18.sp) }
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(dock) { pkg -> AppIcon(pkg, 56.dp, onClick = { ctx.launch(pkg) }) }
                }
                RoundBtn("▦") { allApps = true }
            }
        }
    }

    if (allApps) AppsDialog(
        title = "Все приложения (долгое нажатие — в док / из дока)",
        onClick = { allApps = false; ctx.launch(it) },
        onLong = { pkg ->
            val n = if (pkg in dock) dock - pkg else dock + pkg
            p.set("dock", n.joinToString(","))
        },
        onDismiss = { allApps = false })
    if (settings) SettingsDialog(p) { settings = false }
}

@Composable
fun RoundBtn(label: String, onClick: () -> Unit) = Box(
    Modifier.size(56.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClick),
    contentAlignment = Alignment.Center
) { Text(label, fontSize = 24.sp) }

@Composable
fun Background(mode: String, v: String) {
    val ctx = LocalContext.current
    when (mode) {
        "system" -> {} // видно системные обои (windowShowWallpaper)
        "black" -> Box(Modifier.fillMaxSize().background(Color.Black))
        "image" -> {
            val bmp = remember(v) {
                BitmapFactory.decodeFile(File(ctx.filesDir, "bg.jpg").path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
            }
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Color.Black))
        }
        else -> Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF4A0B0), Color(0xFF3A1F5C), Color(0xFF14101F)))))
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(5000) } }
    Column {
        Text(SimpleDateFormat("HH:mm").format(now), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(SimpleDateFormat("d MMMM").format(now), fontSize = 18.sp, color = Color.White)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun SpeedCard(mph: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var mps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { if (!granted) req.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val lm = ctx.getSystemService(LocationManager::class.java)
        val l = LocationListener { loc -> mps = if (loc.hasSpeed()) loc.speed else 0f }
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l) } catch (_: Exception) { }
        onDispose { lm.removeUpdates(l) }
    }
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(CARD)
            .clickable { if (!granted) req.launch(Manifest.permission.ACCESS_FINE_LOCATION) }.padding(16.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!granted) Text("Нажми — разрешить GPS", color = Color.White)
        else {
            val v = if (mph) mps * 2.23694f else mps * 3.6f
            Text("${v.toInt()}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (mph) "mph" else "км/ч", fontSize = 18.sp, color = Color.LightGray)
        }
    }
}

@Composable
fun MusicCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var ctl by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var art by remember { mutableStateOf<Bitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var noAccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val list = ctx.getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(ctx, MediaListener::class.java))
                val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
                ctl = c; noAccess = false
                val md = c?.metadata
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
            } catch (_: SecurityException) { noAccess = true }
            delay(1000)
        }
    }

    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(CARD).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val b = art
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("♪", fontSize = 40.sp, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            if (noAccess) {
                Text("Нужен доступ к музыке", color = Color.White, fontWeight = FontWeight.Bold)
                TextButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Открыть настройки") }
            } else {
                Text(title.ifEmpty { "Ничего не играет" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 10.dp)) {
                    val t = ctl?.transportControls
                    Text("⏮", fontSize = 32.sp, modifier = Modifier.clickable { t?.skipToPrevious() })
                    Text(if (playing) "⏸" else "▶", fontSize = 32.sp, modifier = Modifier.clickable { if (playing) t?.pause() else t?.play() })
                    Text("⏭", fontSize = 32.sp, modifier = Modifier.clickable { t?.skipToNext() })
                }
            }
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp, onClick: () -> Unit, onLong: () -> Unit = {}) {
    val pm = LocalContext.current.packageManager
    val bmp = remember(pkg) { try { pm.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() } catch (_: Exception) { null } }
    if (bmp != null) Image(bmp, null, Modifier.size(size).clip(CircleShape).combinedClickable(onClick = onClick, onLongClick = onLong))
}

@Composable
fun AppsDialog(title: String, onClick: (String) -> Unit, onLong: (String) -> Unit = {}, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.ResolveInfoFlags.of(0))
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xEE1B1B1F)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                LazyVerticalGrid(GridCells.Adaptive(90.dp), Modifier.heightIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps) { (pkg, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(pkg, 56.dp, { onClick(pkg) }, { onLong(pkg) })
                            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(p: Prefs, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var pickHu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ctx.contentResolver.openInputStream(uri)?.use { i -> File(ctx.filesDir, "bg.jpg").outputStream().use { o -> i.copyTo(o) } }
            p.set("bgv", System.currentTimeMillis().toString()); p.set("wp", "image")
        }
    }
    @Composable fun sw(label: String, key: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(p.on(key), { p.toggle(key) })
    }
    @Composable fun chips(key: String, def: String, items: List<Pair<String, String>>) =
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (v, l) -> FilterChip(p.get(key, def) == v, { p.set(key, v) }, { Text(l) }) }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Готово") } },
        title = { Text("Настройки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sw("Часы", "clock"); sw("Скорость (GPS)", "speed"); sw("Музыка", "music")
                Text("Единицы скорости"); chips("unit", "kmh", listOf("kmh" to "км/ч", "mph" to "mph"))
                Text("Фон"); chips("wp", "gradient", listOf("gradient" to "Градиент", "system" to "Обои системы", "black" to "Выкл"))
                OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Своё фото как обои") }
                OutlinedButton({ pickHu = true }) { Text("Приложение для Android Auto (Headunit)") }
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Доступ к музыке") }
                OutlinedButton({
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) ctx.startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }) { Text("Сделать лаунчером по умолчанию") }
            }
        })
    if (pickHu) AppsDialog("Выбери Headunit Revived", { p.set("hu", it); pickHu = false }, onDismiss = { pickHu = false })
}
