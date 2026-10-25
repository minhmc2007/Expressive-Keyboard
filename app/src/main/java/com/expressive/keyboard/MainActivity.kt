@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class
)

package com.expressive.keyboard

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface as AndroidTypeface
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// 11. ЭКРАН НАСТРОЕК В СТИЛЕ MATERIAL 3 EXPRESSIVE (ANDROID 16)
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyboardPrefs.ensureInit(this)
        WordPredictorEngine.ensureInitialized(this)
        IconStore.ensureLoaded(this)
        PluginRepository.ensureLoaded(this)
        EmojiCatalog.warmUp(this)
        enableEdgeToEdge()
        setContent {
            val settings by KeyboardPrefs.observe()
            ExpressiveTheme(settings, appUi = true) { SettingsScreen(settings) }
        }
    }
}

enum class SettingsPage(val title: String, val subtitle: String, val icon: ImageVector, val shape: ExpressiveShapePreset) {
    HOME("Expressive Keyboard", "", Icons.Rounded.Keyboard, ExpressiveShapePreset.COOKIE_12),
    LOOK("Theme & font", "Light/dark theme, accent shape, font, and animations", Icons.Rounded.Palette, ExpressiveShapePreset.COOKIE_12),
    COLORS("Colors & background", "Custom colors, key colors, transparency, and background image", Icons.Rounded.ColorLens, ExpressiveShapePreset.FLOWER_6),
    KEYS("Size & buttons", "Key height, number row, spacebar keys, and one-handed mode", Icons.Rounded.Keyboard, ExpressiveShapePreset.CLOVER_4),
    LAYOUT("Custom key layout", "Rearrange letters, numbers, and symbols, or add your own", Icons.Rounded.SwapHoriz, ExpressiveShapePreset.SUNNY_8),
    ICONS("Emoji & icons", "Emoji style (Twemoji, Noto, Blob…), custom button icons", Icons.Rounded.EmojiEmotions, ExpressiveShapePreset.SUNNY_8),
    TOOLBAR("Toolbar", "Choose which buttons appear and their order", Icons.Rounded.DragIndicator, ExpressiveShapePreset.FLOWER_6),
    LANGUAGES("Languages", "Layouts, Chinese/Japanese input, and suggestion dictionaries", Icons.Rounded.Language, ExpressiveShapePreset.COOKIE_12),
    TYPING("Suggestions & corrections", "Autocorrect, capitalization, and punctuation", Icons.Rounded.Spellcheck, ExpressiveShapePreset.SUNNY_8),
    GESTURES("Gestures & speed", "Glide typing, spacebar/backspace swipes, long press, paired brackets", Icons.Rounded.Gesture, ExpressiveShapePreset.CLOVER_4),
    CLIPBOARD("Clipboard & GIF", "Clipboard history and GIPHY key", Icons.Rounded.ContentPaste, ExpressiveShapePreset.FLOWER_6),
    FEEDBACK("Vibration & sound", "Feedback strength, duration, and feel", Icons.Rounded.Vibration, ExpressiveShapePreset.COOKIE_12),
    PLUGINS("Plugins & mods", "Scripts, custom panels/buttons, keyboard overlays, and themes", Icons.Rounded.Extension, ExpressiveShapePreset.FLOWER_6),
    BACKUP("Backup", "Export/import settings, reset, and learned words", Icons.Rounded.Backup, ExpressiveShapePreset.CLOVER_4),
    ABOUT("About", "Version, features, and licenses", Icons.Rounded.Info, ExpressiveShapePreset.CIRCLE)
}

private const val FEATURE_AVAILABLE = com.google.mlkit.genai.common.FeatureStatus.AVAILABLE
private const val FEATURE_DOWNLOADABLE = com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE
private const val FEATURE_DOWNLOADING = com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING

internal fun isImeEnabled(context: Context): Boolean = runCatching {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.enabledInputMethodList.any { it.packageName == context.packageName }
}.getOrDefault(false)

internal fun isImeSelected(context: Context): Boolean = runCatching {
    val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
    id.substringBefore('/') == context.packageName
}.getOrDefault(false)

internal fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

internal fun importCustomFont(context: Context, uri: Uri): String? = runCatching {
    var name = "CustomFont.ttf"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
    }
    val temp = File(context.cacheDir, "font_check.ttf")
    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return null
    if (AndroidTypeface.Builder(temp).build() == null) {
        temp.delete()
        return null
    }
    temp.copyTo(File(context.filesDir, KeyboardPrefs.CUSTOM_FONT_FILENAME), overwrite = true)
    temp.delete()
    name
}.getOrNull()

/** Картинка уменьшается до ~1600 px и пересжимается — клавиатура не держит в памяти 50-мегапиксельное фото. */
internal fun importBackground(context: Context, uri: Uri): Boolean = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    val maxSide = max(bounds.outWidth, bounds.outHeight)
    if (maxSide <= 0) return false
    var sample = 1
    while (maxSide / (sample * 2) >= 1600) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return false
    File(context.filesDir, KeyboardPrefs.BG_IMAGE_FILENAME).outputStream().use {
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it)
    }
    bmp.recycle()
    true
}.getOrDefault(false)

@Composable
fun SettingsScreen(settings: KeyboardSettings) {
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.HOME) { page = SettingsPage.HOME }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != SettingsPage.HOME
            val enterSpring = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 380f)
            if (forward) {
                (slideInHorizontally(enterSpring) { it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(enterSpring) { -it / 6 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(enterSpring) { -it / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(enterSpring) { it / 3 } + fadeOut(tween(160)))
            }
        },
        label = "settingsPage"
    ) { p ->
        SettingsPageScaffold(p, settings, onNavigate = { page = it })
    }
}

@Composable
internal fun SettingsPageScaffold(page: SettingsPage, settings: KeyboardSettings, onNavigate: (SettingsPage) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val cs = MaterialTheme.colorScheme
    var testText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val showTest = page != SettingsPage.ABOUT && page != SettingsPage.BACKUP
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = cs.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(tr(page.title), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (page != SettingsPage.HOME) {
                        FilledTonalIconButton(onClick = { onNavigate(SettingsPage.HOME) }, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = cs.surfaceContainer, scrolledContainerColor = cs.surfaceContainer),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showTest) {
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(tr("Try the keyboard here…")) },
                    leadingIcon = { Icon(Icons.Rounded.Keyboard, null) },
                    trailingIcon = {
                        if (testText.isNotEmpty()) IconButton(onClick = { testText = "" }) { Icon(Icons.Rounded.Close, null) }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                when (page) {
                    SettingsPage.HOME -> HomePage(settings, onNavigate)
                    SettingsPage.LOOK -> LookPage(settings)
                    SettingsPage.COLORS -> ColorsPage(settings)
                    SettingsPage.KEYS -> KeysPage(settings)
                    SettingsPage.LAYOUT -> LayoutEditorPage(settings)
                    SettingsPage.ICONS -> IconsPage(settings)
                    SettingsPage.TOOLBAR -> ToolbarPage(settings)
                    SettingsPage.LANGUAGES -> LanguagesPage(settings)
                    SettingsPage.TYPING -> TypingPage(settings, SettingsPage.TYPING)
                    SettingsPage.GESTURES -> TypingPage(settings, SettingsPage.GESTURES)
                    SettingsPage.CLIPBOARD -> TypingPage(settings, SettingsPage.CLIPBOARD)
                    SettingsPage.FEEDBACK -> FeedbackPage(settings)
                    SettingsPage.PLUGINS -> PluginsPage()
                    SettingsPage.BACKUP -> BackupPage()
                    SettingsPage.ABOUT -> AboutPage()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
internal fun rememberSpec(settings: KeyboardSettings) =
    remember(settings.shapePreset, settings.waveCount, settings.waveAmplitudePercent) { settings.accentSpec() }

@Composable
internal fun PreviewSection(settings: KeyboardSettings) {
    SettingsSection("Preview") { GroupItem { StylePreview(settings, rememberSpec(settings)) } }
}

// ----------------------------------------------------------------------- главная

@Composable
internal fun HomePage(settings: KeyboardSettings, onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var imeSelected by remember { mutableStateOf(isImeSelected(context)) }
    var micGranted by remember { mutableStateOf(hasMicPermission(context)) }
    // окно получает фокус после возврата из системных настроек и после закрытия выбора клавиатуры
    LaunchedEffect(focused) {
        if (focused) {
            imeEnabled = isImeEnabled(context)
            imeSelected = isImeSelected(context)
            micGranted = hasMicPermission(context)
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    SettingsSection(null) {
        ChoiceItem(
            Icons.Rounded.Translate, "App language", null,
            listOf("SYSTEM" to "System default", "ru" to "Russian", "en" to "English"), settings.appLanguage
        ) { v -> KeyboardPrefs.edit(context) { putString("app_language", v) } }
    }
    SettingsSection("Design") {
        GroupItem { DesignPicker(settings) }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(tr("Find a setting: vibration, font, swipe…")) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
        singleLine = true
    )
    if (query.isNotBlank()) {
        val found = SettingsSearch.find(query)
        SettingsSection(tr("Found: {0}", found.size)) {
            if (found.isEmpty()) GroupItem { Text(tr("Nothing found"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            found.forEach { (title, p) ->
                ActionItem(p.icon, title, tr(p.title), onClick = { onNavigate(p) })
            }
        }
        return
    }
    HeroCard(
        spec = rememberSpec(settings),
        imeEnabled = imeEnabled,
        imeSelected = imeSelected,
        micGranted = micGranted,
        onEnable = {
            runCatching { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        onSelect = { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() },
        onMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    )
    PreviewSection(settings)
    val groups = listOf(
        "Appearance" to listOf(SettingsPage.LOOK, SettingsPage.COLORS, SettingsPage.KEYS, SettingsPage.LAYOUT, SettingsPage.ICONS, SettingsPage.TOOLBAR),
        "Typing" to listOf(SettingsPage.LANGUAGES, SettingsPage.TYPING, SettingsPage.GESTURES, SettingsPage.CLIPBOARD),
        "Feedback & extensions" to listOf(SettingsPage.FEEDBACK, SettingsPage.PLUGINS),
        "Other" to listOf(SettingsPage.BACKUP, SettingsPage.ABOUT)
    )
    var i = 0
    for ((title, pages) in groups) {
        SettingsSection(title) {
            for (p in pages) {
                val idx = i++
                CategoryTile(p, idx) { onNavigate(p) }
            }
        }
    }
}

// ----------------------------------------------------------------------- внешний вид

@Composable
internal fun LookPage(settings: KeyboardSettings) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { importCustomFont(context, uri) }
            if (name != null) {
                KeyboardPrefs.saveCustomFont(context, name)
                Toast.makeText(context, tr("Font “{0}” applied", name), Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, tr("A valid .ttf or .otf file is required"), Toast.LENGTH_LONG).show()
        }
    }
    PreviewSection(settings)
    SettingsSection("Theme") {
        ChoiceItem(
            Icons.Rounded.DarkMode, "Mode", "AMOLED — pure black for OLED screens",
            listOf("SYSTEM" to "Auto", "LIGHT" to "Light", "DARK" to "Dark", "AMOLED" to "AMOLED"),
            settings.themeMode
        ) { mode -> KeyboardPrefs.edit(context) { putString("theme_mode", mode) } }
    }
    SettingsSection("Accent shape") {
        GroupItem { Column(Modifier.padding(vertical = 16.dp)) { ShapePicker(settings) } }
        SliderItem(
            icon = Icons.Rounded.AutoAwesome, title = "Number of waves",
            value = settings.waveCount.toFloat(), range = 4f..18f, steps = 13,
            format = { "${it.roundToInt()}" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putInt("wave_count", v.roundToInt()) } }
        )
        SliderItem(
            icon = Icons.Rounded.Tune, title = "Wave depth",
            value = settings.waveAmplitudePercent, range = 0f..14f,
            format = { "${it.roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("wave_amplitude_percent", v) } }
        )
    }
    SettingsSection("Font") {
        ChoiceItem(
            Icons.Rounded.TextFields, "Typeface",
            if (settings.fontStyle == "CUSTOM") tr("Custom font: {0}", settings.customFontName) else "Rounded — as designed",
            listOf("ROUNDED" to "Rounded", "SYSTEM" to "System", "SERIF" to "Serif", "MONOSPACE" to "Mono"),
            settings.fontStyle
        ) { f -> KeyboardPrefs.edit(context) { putString("font_style", f) } }
        SliderItem(
            icon = Icons.Rounded.FormatBold, title = "Font weight",
            value = settings.fontWeight.toFloat(), range = 300f..900f, steps = 5,
            format = {
                when (it.roundToInt() / 100 * 100) {
                    300 -> "Thin"; 400 -> "Regular"; 500 -> "Medium"; 600 -> "Semibold"; 700 -> "Bold"; 800 -> "Extra bold"
                    else -> "Black"
                }
            },
            onCommit = { v -> KeyboardPrefs.edit(context) { putInt("font_weight", (v / 100f).roundToInt() * 100) } }
        )
        SliderItem(
            icon = Icons.Rounded.FormatSize, title = "Letter size",
            value = settings.keyLabelScale, range = 0.7f..1.4f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_label_scale", v) } }
        )
        ActionItem(
            icon = Icons.Rounded.FontDownload,
            title = "Load custom font",
            subtitle = ".ttf or .otf file",
            onClick = { fontLauncher.launch("*/*") },
            trailing = if (settings.customFontName.isNotEmpty()) {
                {
                    IconButton(onClick = { KeyboardPrefs.resetCustomFont(context) }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete font", tint = cs.error)
                    }
                }
            } else null
        )
    }
    SettingsSection("Animations") {
        SliderItem(
            icon = Icons.Rounded.Animation, title = "Key press response",
            value = settings.pressAnimation, range = 0f..2f,
            format = { if (it < 0.05f) tr("Off") else "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("press_animation", if (v < 0.05f) 0f else v) } }
        )
        ToggleItem(Icons.Rounded.AutoAwesome, "Panel animations", "Smooth transitions between keyboard, emoji, and panels", settings.panelAnimations) {
            KeyboardPrefs.edit(context) { putBoolean("panel_animations", it) }
        }
    }
}

// ----------------------------------------------------------------------- цвета и фон

private data class ColorTarget(val key: String, val title: String)

@Composable
internal fun ColorsPage(settings: KeyboardSettings) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<ColorTarget?>(null) }
    val auto = remember(cs) { buildKeyPalette(cs, KeyboardSettings()) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { importBackground(context, uri) }
            if (ok) KeyboardPrefs.setBackgroundImage(context)
            else Toast.makeText(context, tr("Could not open image"), Toast.LENGTH_LONG).show()
        }
    }
    val monet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    PreviewSection(settings)
    SettingsSection("Color source") {
        ChoiceItem(
            Icons.Rounded.Palette, "Material You palette",
            if (settings.colorSource == "SEED") "The whole theme uses the selected color"
            else if (monet) "Colors are taken from wallpaper (Monet)" else "Monet requires Android 12; using the base palette",
            listOf("DYNAMIC" to "From wallpaper", "SEED" to "Custom color"),
            settings.colorSource
        ) { v -> KeyboardPrefs.edit(context) { putString("color_source", v) } }
        AnimatedVisibility(
            visible = settings.colorSource == "SEED",
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            GroupItem {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCHES.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { c ->
                                ColorDot(Color(c), settings.seedColor == c) { KeyboardPrefs.edit(context) { putInt("seed_color", c) } }
                            }
                        }
                    }
                    FilledTonalButton(onClick = { picker = ColorTarget("seed_color", "Primary color") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Colorize, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Choose any color"))
                    }
                }
            }
        }
    }
    SettingsSection("Keyboard colors") {
        ColorRow("Keyboard background", settings.colorBackground, auto.background) { picker = ColorTarget("color_background", "Keyboard background") }
        ColorRow("Letter keys", settings.colorKey, auto.letter) { picker = ColorTarget("color_key", "Letter keys") }
        ColorRow("Letter text", settings.colorKeyText, auto.onLetter) { picker = ColorTarget("color_key_text", "Letter text") }
        ColorRow("Function keys", settings.colorFunc, auto.func) { picker = ColorTarget("color_func", "Function keys") }
        ColorRow("Function key text", settings.colorFuncText, auto.onFunc) { picker = ColorTarget("color_func_text", "Function key text") }
        ColorRow("Accent (Enter, pressed)", settings.colorAccent, auto.accent) { picker = ColorTarget("color_accent", "Accent") }
        ColorRow("Accent text", settings.colorAccentText, auto.onAccent) { picker = ColorTarget("color_accent_text", "Accent text") }
        SliderItem(
            icon = Icons.Rounded.Opacity, title = "Key opacity",
            value = settings.keyOpacity, range = 0.2f..1f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_opacity", v) } }
        )
        ActionItem(Icons.Rounded.RestartAlt, "Restore theme colors", "Reset custom colors and opacity", onClick = { KeyboardPrefs.resetColors(context) })
    }
    SettingsSection("Blur") {
        ChoiceItem(
            Icons.Rounded.BlurOn, "Blur behind keyboard",
            when {
                Build.VERSION.SDK_INT < 31 -> "Requires Android 12 or later"
                settings.windowBlur == "ON" -> "Apps behind the keyboard show through the frosted background"
                settings.windowBlur == "OFF" -> "Regular opaque background"
                else -> "Enabled for Liquid Glass; disabled for other themes"
            },
            listOf("THEME" to "Use theme setting", "ON" to "On", "OFF" to "Off"), settings.windowBlur
        ) { v -> KeyboardPrefs.edit(context) { putString("window_blur", v) } }
        if (settings.windowBlur != "OFF") {
            SliderItem(
                Icons.Rounded.Opacity, "Blur strength", settings.windowBlurDp, 4f..100f, { "${it.roundToInt()} dp" },
                onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("window_blur_dp", v) } }
            )
        }
    }
    SettingsSection("Background image") {
        ActionItem(
            icon = Icons.Rounded.Wallpaper,
            title = if (settings.hasBackgroundImage) "Change image" else "Choose image",
            subtitle = "Gallery image behind the keys",
            onClick = { bgLauncher.launch("image/*") },
            trailing = if (settings.hasBackgroundImage) {
                {
                    IconButton(onClick = { KeyboardPrefs.clearBackgroundImage(context) }) {
                        Icon(Icons.Rounded.DeleteOutline, "Remove image", tint = cs.error)
                    }
                }
            } else null
        )
        AnimatedVisibility(settings.hasBackgroundImage) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SliderItem(
                    icon = Icons.Rounded.Brightness6, title = "Dim amount",
                    value = settings.bgDim, range = 0f..0.9f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bg_dim", v) } }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SliderItem(
                        icon = Icons.Rounded.BlurOn, title = "Blur",
                        value = settings.bgBlurDp, range = 0f..24f,
                        format = { if (it < 0.5f) tr("None") else "${it.roundToInt()} dp" },
                        onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bg_blur_dp", v) } }
                    )
                }
            }
        }
    }

    picker?.let { t ->
        val current = when (t.key) {
            "seed_color" -> settings.seedColor
            "color_background" -> settings.colorBackground
            "color_key" -> settings.colorKey
            "color_key_text" -> settings.colorKeyText
            "color_func" -> settings.colorFunc
            "color_func_text" -> settings.colorFuncText
            "color_accent" -> settings.colorAccent
            else -> settings.colorAccentText
        }
        ColorPickerDialog(t.title, current, allowAuto = t.key != "seed_color", onDismiss = { picker = null }) { c ->
            KeyboardPrefs.edit(context) {
                putInt(t.key, c)
                if (t.key == "seed_color") putString("color_source", "SEED")
            }
            picker = null
        }
    }
}

// ----------------------------------------------------------------------- клавиши

@Composable
internal fun KeysPage(settings: KeyboardSettings) {
    val context = LocalContext.current
    PreviewSection(settings)
    SettingsSection("Dimensions") {
        SliderItem(
            icon = Icons.Rounded.Height, title = "Key height",
            value = settings.keyHeightDp, range = 44f..68f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_height_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.RoundedCorner, title = "Key corner radius",
            value = settings.cornerRadiusDp, range = 6f..30f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("corner_radius_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.SpaceBar, title = "Space between keys",
            value = settings.keySpacingDp, range = 2f..8f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("key_spacing_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Menu, title = "Space between rows",
            value = settings.rowSpacingDp, range = 4f..12f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("row_spacing_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.Straighten, title = "Side margins",
            value = settings.sidePaddingDp, range = 0f..32f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("side_padding_dp", v) } }
        )
        SliderItem(
            icon = Icons.Rounded.VerticalAlignBottom, title = "Bottom margin",
            value = settings.bottomOffsetDp, range = 0f..64f,
            format = { "${it.roundToInt()} dp" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("bottom_offset_dp", v) } }
        )
    }
    SettingsSection("One-handed mode") {
        ChoiceItem(
            Icons.Rounded.SwapHoriz, "Position", "Also toggled with ⇄ on the toolbar",
            listOf("OFF" to "Off", "LEFT" to "Left", "RIGHT" to "Right"), settings.oneHanded
        ) { v -> KeyboardPrefs.edit(context) { putString("one_handed", v) } }
        SliderItem(
            icon = Icons.Rounded.Straighten, title = "Keyboard width",
            value = settings.oneHandedWidth, range = 0.65f..0.95f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("one_handed_width", v) } }
        )
    }
    SettingsSection("Keys & buttons") {
        ToggleItem(Icons.Rounded.Dialpad, "Number row", null, settings.showNumberRow) {
            KeyboardPrefs.edit(context) { putBoolean("show_number_row", it) }
        }
        ToggleItem(Icons.AutoMirrored.Rounded.Label, "Key hints", "Small symbol available on long press", settings.showHints) {
            KeyboardPrefs.edit(context) { putBoolean("show_hints", it) }
        }
        ToggleItem(Icons.Rounded.TouchApp, "Key pop-up on press", null, settings.keyPreview) {
            KeyboardPrefs.edit(context) { putBoolean("key_preview", it) }
        }
        ToggleItem(Icons.Rounded.BorderOuter, "Key borders", null, settings.showKeyBorders) {
            KeyboardPrefs.edit(context) { putBoolean("show_key_borders", it) }
        }
        ToggleItem(Icons.Rounded.SelectAll, "Editing toolbar", "Above keyboard: cursor, select, copy, and paste", settings.editBar) {
            KeyboardPrefs.edit(context) { putBoolean("edit_bar", it) }
        }
        ToggleItem(Icons.Rounded.Language, "Language switch key", "Without it, switch languages by swiping on the spacebar", settings.showGlobeKey) {
            KeyboardPrefs.edit(context) { putBoolean("show_globe_key", it) }
        }
        if (settings.showGlobeKey) {
            val globeActions = listOf(
                "LANGUAGES" to "Language list", "SYSTEM" to "Choose keyboard", "EMOJI" to "Emoji", "CLIPBOARD" to "Clipboard",
                "VOICE" to "Voice input", "SETTINGS" to "Settings", "ONE_HANDED" to "One-handed mode", "OFF" to "None"
            )
            ChoiceItem(
                Icons.Rounded.TouchApp, "Long-press language key",
                globeActions.firstOrNull { it.first == settings.globeLongPress }?.second ?: "Language list",
                globeActions, settings.globeLongPress
            ) { v -> KeyboardPrefs.edit(context) { putString("globe_long_press", v) } }
        }
        ToggleItem(Icons.Rounded.MoreHoriz, "Comma and period on the ?123 page", "По бокам пробела. Удержание — другие знаки: ; : ' \" ? ! …", settings.punctuationKeys) {
            KeyboardPrefs.edit(context) { putBoolean("punctuation_keys", it) }
        }
        ToggleItem(Icons.Rounded.EmojiEmotions, "Emoji key", null, settings.showEmojiKey) {
            KeyboardPrefs.edit(context) { putBoolean("show_emoji_key", it) }
        }
        ToggleItem(
            Icons.Rounded.MoreHoriz, "Comma on the main keyboard", "Left от пробела. Удержание — ; : ' \" и другие знаки",
            settings.spaceLeftKey == ","
        ) { on -> KeyboardPrefs.edit(context) { putString("space_left_key", if (on) "," else "") } }
        ToggleItem(
            Icons.Rounded.MoreHoriz, "Period on the main keyboard", "Right of the spacebar. Long-press for ? ! … and other symbols",
            settings.spaceRightKey == "."
        ) { on -> KeyboardPrefs.edit(context) { putString("space_right_key", if (on) "." else "") } }
        SpaceSlotItem(
            Icons.Rounded.SpaceBar, "Custom key left of the spacebar", settings.spaceLeftKey, "space_left_key",
            "For example, use a comma instead of the language key if you switch languages by swiping on the spacebar"
        )
        SpaceSlotItem(
            Icons.Rounded.SpaceBar, "Custom key right of the spacebar", settings.spaceRightKey, "space_right_key",
            "Long-press comma or period for more symbols, like in Gboard"
        )
        ToggleItem(Icons.Outlined.Mic, "Microphone button", null, settings.showMicButton) {
            KeyboardPrefs.edit(context) { putBoolean("show_mic_button", it) }
        }
        ToggleItem(Icons.Rounded.Lightbulb, "Suggestion strip", "When disabled, the toolbar is always shown at the top", settings.showSuggestions) {
            KeyboardPrefs.edit(context) { putBoolean("show_suggestions", it) }
        }
    }
}

/** Своя клавиша у пробела на буквенной раскладке: нет, «,», «.» или любой символ (до 4 знаков). */
@Composable
private fun SpaceSlotItem(icon: ImageVector, title: String, value: String, prefKey: String, subtitle: String) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    val selected = when (value) {
        "", ",", "." -> value
        else -> "custom"
    }
    ChoiceItem(
        icon, title, if (selected == "custom") tr("Custom: “{0}”", value) else subtitle,
        listOf("" to "None", "," to ",", "." to ".", "custom" to "Custom…"), selected
    ) { v ->
        if (v == "custom") dialog = true else KeyboardPrefs.edit(context) { putString(prefKey, v) }
    }
    if (dialog) {
        TextInputDialog(title, if (selected == "custom") value else "", "Symbol, letter, or emoji — up to 4 characters", onDismiss = { dialog = false }) { v ->
            KeyboardPrefs.edit(context) { putString(prefKey, v.trim().take(4)) }
            dialog = false
        }
    }
}

// ----------------------------------------------------------------------- ввод

@Composable
internal fun TypingPage(settings: KeyboardSettings, part: SettingsPage) {
    val context = LocalContext.current
    var giphyDialog by remember { mutableStateOf(false) }
    var klipyDialog by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf<String?>(null) }
    // поддерживает ли телефон исправление на устройстве (Gemini Nano) — для подписи
    var nanoStatus by remember { mutableStateOf<Int?>(-1) }
    LaunchedEffect(part) {
        if (part == SettingsPage.TYPING) nanoStatus = AiFix.nanoStatus(context, "en")
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Speed") {
        ToggleItem(Icons.Rounded.Bolt, "Instant input", "Characters appear on touch instead of when you lift your finger", settings.instantInput) {
            KeyboardPrefs.edit(context) { putBoolean("instant_input", it) }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Glide typing") {
        ToggleItem(Icons.Rounded.Gesture, "Glide typing", "Slide your finger from letter to letter to enter a word; alternatives appear in suggestions", settings.glideTyping) {
            KeyboardPrefs.edit(context) { putBoolean("glide_typing", it) }
        }
        AnimatedVisibility(settings.glideTyping) {
            ToggleItem(Icons.Rounded.Timeline, "Finger trail", null, settings.glideTrail) {
                KeyboardPrefs.edit(context) { putBoolean("glide_trail", it) }
            }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Convenience") {
        ToggleItem(Icons.Rounded.North, "Swipe up on a key", "Enters the secondary symbol without a long press", settings.swipeUpHint) {
            KeyboardPrefs.edit(context) { putBoolean("swipe_up_hint", it) }
        }
        val spaceActions = listOf(
            "PICKER" to "Choose keyboard", "AI_FIX" to "Improve text", "VOICE" to "Voice input", "EMOJI" to "Emoji",
            "CLIPBOARD" to "Clipboard", "TOOLBAR" to "Toolbar", "SETTINGS" to "Settings", "OFF" to "None"
        )
        ChoiceItem(
            Icons.Rounded.SpaceBar, "Spacebar long-press action",
            spaceActions.firstOrNull { it.first == settings.spaceLongAction }?.second ?: "Choose keyboard",
            spaceActions, settings.spaceLongAction
        ) { v ->
            KeyboardPrefs.edit(context) {
                putString("space_long_action", v)
                putBoolean("space_long_press", v != "OFF")
            }
        }
        ToggleItem(Icons.Rounded.DataArray, "Paired brackets", "( [ { « are automatically closed, with the cursor placed inside", settings.autoPair) {
            KeyboardPrefs.edit(context) { putBoolean("auto_pair", it) }
        }
        ToggleItem(Icons.Rounded.VisibilityOff, "Incognito", "Don't learn new words or save clipboard history. Also available on the toolbar", settings.incognito) {
            KeyboardPrefs.edit(context) { putBoolean("incognito", it) }
        }
    }
    if (part == SettingsPage.TYPING) SettingsSection("AI text correction") {
        InfoCard(
            Icons.Rounded.AutoFixHigh,
            "The ✨ toolbar button corrects selected text, or the entire field if nothing is selected. " +
                "“Auto”: try on-device (Gemini Nano), then a configured API service, then the free LanguageTool checker. " +
                "Cloud services receive the text being corrected."
        )
        val providers = AiProvider.entries.map { it.id to it.title }
        val nanoText = when (nanoStatus) {
            -1 -> "Checking…"
            FEATURE_AVAILABLE -> "Ready"
            FEATURE_DOWNLOADABLE, FEATURE_DOWNLOADING -> "Will download on first use"
            else -> "Not supported on this device"
        }
        ChoiceItem(
            Icons.Rounded.AutoFixHigh, "Correction provider",
            when (AiProvider.from(settings.aiProvider)) {
                AiProvider.AUTO -> tr("Try on-device first, then a configured API service, then LanguageTool")
                AiProvider.NANO -> tr("Gemini Nano on-device: offline, English and 6 more languages (not Russian) — {0}", tr(nanoText))
                AiProvider.GEMINI -> tr("Google Gemini — get a free key from Google AI Studio")
                AiProvider.CLAUDE -> tr("Anthropic Claude — paid API key")
                AiProvider.OPENAI -> tr("OpenAI — paid API key")
                AiProvider.GROQ -> tr("Groq — fast, free API key available")
                AiProvider.LANGUAGETOOL -> tr("Free checking without an API key (not AI)")
            },
            providers, settings.aiProvider
        ) { v -> KeyboardPrefs.edit(context) { putString("ai_provider", v) } }
        ActionItem(
            Icons.Rounded.Key, "Gemini API key",
            if (settings.geminiKey.isBlank()) "Free at aistudio.google.com → Get API key" else "Configured",
            onClick = { keyDialog = "gemini_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "Claude API key",
            if (settings.anthropicKey.isBlank()) "console.anthropic.com" else "Configured",
            onClick = { keyDialog = "anthropic_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "OpenAI API key",
            if (settings.openaiKey.isBlank()) "platform.openai.com" else "Configured",
            onClick = { keyDialog = "openai_key" }
        )
        ActionItem(
            Icons.Rounded.Key, "Groq API key",
            if (settings.groqKey.isBlank()) "Free at console.groq.com" else "Configured",
            onClick = { keyDialog = "groq_key" }
        )
    }
    keyDialog?.let { k ->
        val (title, current, hint) = when (k) {
            "gemini_key" -> Triple("Gemini API key", settings.geminiKey, "API key from aistudio.google.com")
            "openai_key" -> Triple("OpenAI API key", settings.openaiKey, "API key from platform.openai.com")
            "groq_key" -> Triple("Groq API key", settings.groqKey, "API key from console.groq.com")
            else -> Triple("Claude API key", settings.anthropicKey, "API key from console.anthropic.com")
        }
        TextInputDialog(title, current, hint, onDismiss = { keyDialog = null }) { v ->
            KeyboardPrefs.edit(context) { putString(k, v.trim()) }
            keyDialog = null
        }
    }
    if (part == SettingsPage.TYPING) SettingsSection("Corrections") {
        ToggleItem(
            Icons.Rounded.Spellcheck, "Autocorrect",
            "Corrects typos on space only when the word isn't in the dictionary. Press backspace immediately to undo; the word is learned",
            settings.autoCorrect
        ) {
            KeyboardPrefs.edit(context) { putBoolean("auto_correct_enabled", it) }
        }
        AnimatedVisibility(settings.autoCorrect) {
            ChoiceItem(
                Icons.Rounded.Tune, "Correction strength",
                if (settings.autoCorrectStrength == "NORMAL") "Also corrects ambiguous typos, like Gboard"
                else "Only obvious typos: adjacent key, missing letter, or extra letter",
                listOf("SAFE" to "Conservative", "NORMAL" to "Aggressive"), settings.autoCorrectStrength
            ) { v -> KeyboardPrefs.edit(context) { putString("auto_correct_strength", v) } }
        }
        val spellName = remember { SystemSpell.currentName(context) }
        ToggleItem(
            Icons.Rounded.Spellcheck, "System spell checker",
            (if (spellName != null) tr("Current: {0}.", spellName) + " " else "") +
                tr("Corrections and suggestions come from your phone's spell checker, usually Gboard. Configure it in Settings → System → Languages & input → Spell checker"),
            settings.systemSpellcheck
        ) {
            KeyboardPrefs.edit(context) { putBoolean("system_spellcheck", it) }
        }
        ToggleItem(Icons.Rounded.MoreHoriz, "Smart punctuation", "“word ,” → “word,” — moves the space after the comma or period", settings.smartPunctuation) {
            KeyboardPrefs.edit(context) { putBoolean("smart_punctuation", it) }
        }
        ToggleItem(Icons.Rounded.KeyboardCapslock, "Capitalize sentence starts", null, settings.autoCapitalize) {
            KeyboardPrefs.edit(context) { putBoolean("auto_capitalize", it) }
        }
        ToggleItem(Icons.Rounded.MoreHoriz, "Period on double-space", null, settings.doubleSpacePeriod) {
            KeyboardPrefs.edit(context) { putBoolean("double_space_period", it) }
        }
    }
    if (part == SettingsPage.GESTURES) SettingsSection("Gestures") {
        ChoiceItem(
            Icons.Rounded.Swipe, "Swipe on spacebar",
            when (settings.spaceSwipe) {
                "CURSOR" -> "Slide your finger on the spacebar to move the cursor"
                "LANGUAGE" -> "Swipe to switch languages"
                else -> "Gesture disabled"
            },
            listOf("CURSOR" to "Cursor", "LANGUAGE" to "Language", "OFF" to "Off"), settings.spaceSwipe
        ) { v -> KeyboardPrefs.edit(context) { putString("space_swipe", v) } }
        ToggleItem(Icons.AutoMirrored.Rounded.Backspace, "Swipe left on backspace", "Deletes whole words", settings.backspaceSwipe) {
            KeyboardPrefs.edit(context) { putBoolean("backspace_swipe", it) }
        }
        SliderItem(
            icon = Icons.Rounded.Timer, title = "Long-press delay",
            value = settings.longPressMs, range = 180f..700f,
            format = { "${it.roundToInt()} " + tr("ms") },
            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("long_press_ms", v) } }
        )
    }
    if (part == SettingsPage.CLIPBOARD) SettingsSection("Clipboard") {
        ToggleItem(Icons.Rounded.ContentPaste, "Clipboard history", "Stored in memory only; passwords are excluded", settings.clipboardHistory) {
            KeyboardPrefs.edit(context) { putBoolean("clipboard_history", it) }
        }
        AnimatedVisibility(settings.clipboardHistory) {
            SliderItem(
                icon = Icons.Rounded.History, title = "Retention period",
                value = settings.clipboardSize.toFloat(), range = 5f..100f,
                format = { "${it.roundToInt()}" },
                onCommit = { v -> KeyboardPrefs.edit(context) { putInt("clipboard_size", v.roundToInt()) } }
            )
        }
    }
    if (part == SettingsPage.CLIPBOARD) SettingsSection("GIF") {
        val builtIn = BuildConfig.KLIPY_API_KEY.isNotBlank() || BuildConfig.GIPHY_API_KEY.isNotBlank()
        if (builtIn) InfoCard(Icons.Rounded.GifBox, "GIF search already works; no API key is needed. You can add your own if you prefer.")
        ActionItem(
            Icons.Rounded.GifBox, "KLIPY API key",
            if (settings.klipyKey.isBlank()) "Free at partner.klipy.com — search all GIFs" else "Configured",
            onClick = { klipyDialog = true }
        )
        ActionItem(
            Icons.Rounded.GifBox, "GIPHY API key",
            if (settings.giphyKey.isBlank()) "Optional — fallback provider" else "Configured",
            onClick = { giphyDialog = true }
        )
    }
    if (klipyDialog) {
        TextInputDialog("KLIPY API key", settings.klipyKey, "API key from partner.klipy.com", onDismiss = { klipyDialog = false }) { v ->
            KeyboardPrefs.edit(context) { putString("klipy_key", v.trim()) }
            klipyDialog = false
        }
    }
    if (giphyDialog) {
        TextInputDialog("GIPHY API key", settings.giphyKey, "API key from developers.giphy.com", onDismiss = { giphyDialog = false }) { v ->
            KeyboardPrefs.edit(context) { putString("giphy_key", v.trim()) }
            giphyDialog = false
        }
    }
}

// ----------------------------------------------------------------------- вибрация

@Composable
internal fun FeedbackPage(settings: KeyboardSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // свой звук: копируем файл к себе — доступ к выбранному файлу потом пропадает
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = java.io.File(context.filesDir, HapticsEngine.SOUND_FILE)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size in 1..1_048_576)
                        out.writeBytes(bytes)
                    } ?: error("no input")
                    true
                }.getOrDefault(false)
            }
            if (ok) KeyboardPrefs.edit(context) { putLong("custom_sound_version", System.currentTimeMillis()) }
            else Toast.makeText(context, tr("Could not open sound (file must be under 1 MB)"), Toast.LENGTH_LONG).show()
        }
    }
    SettingsSection("Vibration") {
        ToggleItem(Icons.Rounded.Vibration, "Vibration on keypress", null, settings.hapticsEnabled) {
            KeyboardPrefs.edit(context) { putBoolean("haptics_enabled", it) }
            if (it) HapticsEngine.preview(context, HapticMode.fromId(settings.hapticMode), settings.vibrationMs, settings.vibrationAmplitude)
        }
        AnimatedVisibility(
            visible = settings.hapticsEnabled,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ChoiceItem(
                    Icons.Rounded.TouchApp, "Feedback style", null,
                    HapticMode.entries.map { it.id to it.title }, settings.hapticMode
                ) { id ->
                    KeyboardPrefs.edit(context) { putString("haptic_mode", id) }
                    HapticsEngine.preview(context, HapticMode.fromId(id), settings.vibrationMs, settings.vibrationAmplitude)
                }
                AnimatedVisibility(visible = settings.hapticMode == HapticMode.CUSTOM.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SliderItem(
                            icon = Icons.Rounded.Timer, title = "Duration",
                            value = settings.vibrationMs, range = 1f..80f,
                            format = { "${it.roundToInt()} " + tr("ms") },
                            onDrag = { v -> HapticsEngine.preview(context, HapticMode.CUSTOM, v, settings.vibrationAmplitude) },
                            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("vibration_ms", v) } }
                        )
                        SliderItem(
                            icon = Icons.Rounded.Bolt, title = "Strength",
                            value = settings.vibrationAmplitude, range = 1f..255f,
                            format = { "${(it / 2.55f).roundToInt()}%" },
                            onDrag = { v -> HapticsEngine.preview(context, HapticMode.CUSTOM, settings.vibrationMs, v) },
                            onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("vibration_amplitude", v) } }
                        )
                    }
                }
                ToggleItem(Icons.Rounded.Keyboard, "Stronger feedback on Shift, Enter, and space", null, settings.strongSpecialKeys) {
                    KeyboardPrefs.edit(context) { putBoolean("strong_special_keys", it) }
                }
                ToggleItem(Icons.Rounded.SwapVert, "Feedback on key release", null, settings.vibrateOnRelease) {
                    KeyboardPrefs.edit(context) { putBoolean("vibrate_on_release", it) }
                }
            }
        }
    }
    SettingsSection("Sound") {
        ToggleItem(Icons.Rounded.MusicNote, "Keypress sound", null, settings.soundEnabled) {
            KeyboardPrefs.edit(context) { putBoolean("sound_enabled", it) }
        }
        AnimatedVisibility(settings.soundEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SliderItem(
                    icon = Icons.Rounded.GraphicEq, title = "Volume",
                    value = settings.soundVolume, range = 0.05f..1f,
                    format = { "${(it * 100).roundToInt()}%" },
                    onCommit = { v -> KeyboardPrefs.edit(context) { putFloat("sound_volume", v) } }
                )
                ActionItem(
                    Icons.Rounded.AudioFile,
                    if (settings.customSoundVersion != 0L) "Change custom sound" else "Custom keypress sound",
                    "Short audio file (up to 1 MB): mp3, ogg, wav",
                    onClick = { soundLauncher.launch("audio/*") }
                )
                if (settings.customSoundVersion != 0L) {
                    ActionItem(Icons.Rounded.RestartAlt, "Restore system sound", null, onClick = {
                        java.io.File(context.filesDir, HapticsEngine.SOUND_FILE).delete()
                        KeyboardPrefs.edit(context) { putLong("custom_sound_version", 0L) }
                    })
                }
            }
        }
    }
}

// ----------------------------------------------------------------------- плагины

@Composable
internal fun PluginsPage() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val plugins = PluginRepository.plugins
    val errors = PluginRepository.errors
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<KeyboardPlugin?>(null) }
    var pendingNetwork by remember { mutableStateOf<KeyboardPlugin?>(null) }

    fun onImported(r: Result<KeyboardPlugin>) {
        r.onSuccess { p ->
            Toast.makeText(context, tr("Plugin “{0}” installed", p.name), Toast.LENGTH_SHORT).show()
            expanded = p.id
            if (p.usesNetwork || "read_text" in p.permissions || "clipboard" in p.permissions) pendingNetwork = p
        }.onFailure { Toast.makeText(context, tr("Error: {0}", it.message), Toast.LENGTH_LONG).show() }
    }
    val modExampleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/javascript")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.assets.open("examples/my-mod.js").use { it.readBytes() }
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) tr("Example mod saved — edit and import it") else tr("Could not save"), Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { onImported(withContext(Dispatchers.IO) { PluginRepository.importFrom(context, uri) }) }
    }
    val exampleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(PluginRepository.EXAMPLE.toByteArray()) } }.isSuccess
            }
            Toast.makeText(context, if (ok) tr("Example saved — edit and import it") else tr("Could not save"), Toast.LENGTH_SHORT).show()
        }
    }

    InfoCard(
        Icons.Rounded.Extension,
        "To install: tap “Import from file” and choose a plugin (.json, .js, or .zip), or copy its text " +
            "and tap “Import from clipboard”. The plugin will appear below, where you can enable, disable, or remove it.\\n\\n" +
            "To use it: tap the grid button at the top left of the keyboard, then 🧩 to open plugin tabs " +
            "with their buttons and panels. Pinned plugin buttons appear directly on the toolbar.\\n\\n" +
            "To create your own: save the example below, edit it, and import it. See PLUGINS.md for the format."
    )
    SettingsSection("Installation") {
        ActionItem(Icons.Rounded.FileOpen, "Import from file", ".js (mod), .zip (mod with images), or .json", onClick = {
            importLauncher.launch(arrayOf("application/json", "application/javascript", "text/javascript", "application/zip", "text/plain", "application/octet-stream", "*/*"))
        })
        ActionItem(Icons.Rounded.Code, "Save example mod (.js)", "Custom panel, button, word triggers, and storage", onClick = {
            modExampleLauncher.launch("my-mod.js")
        })
        ActionItem(Icons.Rounded.ContentPaste, "Import from clipboard", "Copy plugin JSON or mod code, then tap", onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (text.isBlank()) Toast.makeText(context, tr("Clipboard is empty"), Toast.LENGTH_SHORT).show()
            else onImported(PluginRepository.importText(context, text))
        })
        ActionItem(Icons.Rounded.Download, "Save example plugin", "Template demonstrating all format features", onClick = {
            exampleLauncher.launch("my-plugin.json")
        })
    }
    if (errors.isNotEmpty()) {
        SettingsSection("Failed to load") {
            errors.forEach { e ->
                GroupItem {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = cs.error)
                        Text(e, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                }
            }
        }
    }
    val user = plugins.filter { !it.builtIn }
    val builtIn = plugins.filter { it.builtIn }
    if (user.isNotEmpty()) {
        SettingsSection("My plugins") {
            user.forEach { p ->
                PluginCard(p, expanded == p.id, onExpand = { expanded = if (expanded == p.id) null else p.id }, onDelete = { confirmDelete = p })
            }
        }
    }
    SettingsSection("Built-in") {
        builtIn.forEach { p ->
            PluginCard(p, expanded == p.id, onExpand = { expanded = if (expanded == p.id) null else p.id }, onDelete = null)
        }
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(tr("Delete «{0}»?", p.name)) },
            text = { Text(tr("The plugin file will be deleted. You can import it again.")) },
            confirmButton = {
                Button(onClick = {
                    PluginRepository.delete(context, p.id)
                    confirmDelete = null
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Cancel")) } }
        )
    }
    pendingNetwork?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingNetwork = null },
            icon = { Icon(Icons.Rounded.Security, null) },
            title = { Text(tr("Plugin permissions")) },
            text = {
                Text(
                    tr("“{0}” requests:", p.name) + "\n\n" + buildList {
                        if ("read_text" in p.permissions) add("• " + tr("read typed text and the text in the input field"))
                        if ("clipboard" in p.permissions) add("• " + tr("read clipboard history"))
                        if ("network" in p.permissions) add(
                            "• " + tr("выходить в интернет") + p.actions.filter { it.type == PluginActionType.HTTP }
                                .joinToString("") { "\n   ${it.title}: ${Uri.parse(it.url).host}" }
                        )
                    }.joinToString("\n") + "\n\n" + tr("Устанавливайте моды только из источников, которым доверяете. Оставить плагин включённым?")
                )
            },
            confirmButton = { Button(onClick = { pendingNetwork = null }) { Text(tr("Оставить")) } },
            dismissButton = {
                TextButton(onClick = {
                    PluginRepository.setEnabled(context, p.id, false)
                    pendingNetwork = null
                }) { Text(tr("Disable")) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginCard(p: KeyboardPlugin, expanded: Boolean, onExpand: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val settings by KeyboardPrefs.observe()
    val enabled = PluginRepository.isEnabled(p.id) && p.id !in PluginRepository.disabled
    val spec = remember(p.id) {
        val presets = ExpressiveShapePreset.entries
        val pr = presets[(p.id.hashCode() and 0x7fffffff) % presets.size]
        WavySpec(pr.profile, pr.defaultWaves, pr.defaultAmplitude / 100f)
    }
    GroupItem(onClick = onExpand) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(48.dp).wavyBackground(spec, if (enabled) cs.primaryContainer else cs.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { SpecIcon(p.icon, if (enabled) cs.onPrimaryContainer else cs.onSurfaceVariant, 24.dp) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.usesNetwork) Icon(Icons.Rounded.Public, "Использует сеть", tint = cs.tertiary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        listOf("v${p.version}", p.author, p.summary).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { PluginRepository.setEnabled(context, p.id, it) },
                    thumbContent = if (enabled) {
                        { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else null
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    val perms = p.permissions.filter { it in PluginParser.PERMISSIONS }
                    if (perms.isNotEmpty()) {
                        Text(tr("Разрешения"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(
                            perms.joinToString("  ·  ") {
                                when (it) {
                                    "read_text" -> tr("читает текст")
                                    "clipboard" -> tr("буфер обмена")
                                    else -> tr("интернет")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall, color = cs.tertiary
                        )
                    }
                    if (p.actions.isNotEmpty()) {
                        Text(tr("Действия"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(
                            p.actions.joinToString("  ·  ") { if (it.icon.startsWith("@") || it.icon.startsWith("data:")) it.title else "${it.icon} ${it.title}" },
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    if (p.script != null) {
                        val log = ScriptHost.logs[p.id].orEmpty()
                        Text(tr("Журнал скрипта"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(
                            if (log.isEmpty()) tr(if (enabled) "Пусто" else "Мод выключен") else log.takeLast(12).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest).padding(10.dp)
                        )
                    }
                    if (p.shortcuts.isNotEmpty()) {
                        Text(tr("Сокращения (по пробелу)"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(
                            p.shortcuts.entries.take(12).joinToString("\n") { (k, v) -> "$k → ${v.replace("\n", "⏎").take(40)}" } +
                                if (p.shortcuts.size > 12) "\n… " + tr("ещё {0}", p.shortcuts.size - 12) else "",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    if (p.symbols.isNotEmpty()) {
                        Text(tr("Symbols"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(p.symbols.joinToString("  ·  ") { "${it.icon} ${it.title} (${it.items.size})" }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (p.layouts.isNotEmpty()) {
                        Text(tr("Раскладки"), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Text(
                            p.layouts.entries.joinToString("\n") { (id, rows) -> "$id: " + rows.joinToString(" / ") { r -> r.keys.joinToString("") { it.label.take(1) } } },
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis
                        )
                    }
                    val skinOn = p.skin != null && settings.skinPlugin == p.id
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.hasLook && !skinOn) {
                            FilledTonalButton(onClick = {
                                if (!enabled) PluginRepository.setEnabled(context, p.id, true)
                                PluginRepository.applyLook(context, p)
                                Toast.makeText(context, tr("Оформление «{0}» применено", p.name), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Rounded.FormatPaint, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Применить оформление"))
                            }
                        }
                        if (skinOn) {
                            OutlinedButton(onClick = { PluginRepository.removeSkin(context) }) {
                                Icon(Icons.Rounded.FormatColorReset, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Снять оформление"))
                            }
                        }
                        if (onDelete != null) {
                            OutlinedButton(onClick = onDelete) {
                                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp), tint = cs.error)
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Delete"), color = cs.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------- резервная копия

@Composable
internal fun BackupPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(KeyboardPrefs.exportJson(context).toByteArray()) } }.isSuccess
            }
            Toast.makeText(context, if (ok) tr("Settings saved") else tr("Could not save"), Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Файл не открылся")
                    KeyboardPrefs.importJson(context, raw)
                }
            }
            r.onSuccess { Toast.makeText(context, tr("Восстановлено параметров: {0}", it), Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, tr("Error: {0}", it.message), Toast.LENGTH_LONG).show() }
        }
    }
    SettingsSection("Settings") {
        ActionItem(Icons.Rounded.Upload, "Экспорт настроек", "Сохранить все параметры в .json", onClick = {
            exportLauncher.launch("expressive-keyboard-settings.json")
        })
        ActionItem(Icons.Rounded.Download, "Импорт настроек", "Восстановить из файла", onClick = {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        })
        ActionItem(Icons.Rounded.RestartAlt, "Reset настройки", "Словарь, плагины и недавние эмодзи сохранятся", onClick = { confirm = "settings" })
    }
    SettingsSection("Данные") {
        ActionItem(Icons.Rounded.AutoFixHigh, "Clear learned words", "Suggestions will forget your learned words and word pairs", onClick = { confirm = "dict" })
        ActionItem(Icons.Rounded.History, "Clear recent emoji", null, onClick = {
            EmojiRecents.clear(context)
            Toast.makeText(context, tr("Done"), Toast.LENGTH_SHORT).show()
        })
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text(tr(if (what == "dict") "Clear learned words?" else "Reset настройки?")) },
            text = {
                Text(
                    tr(if (what == "dict") "Все выученные слова будут удалены."
                    else "Dimensions, colors, theme, font, background, and vibration will be restored to their defaults.")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (what == "dict") {
                        WordPredictorEngine.clearUserData()
                        CjkUser.clear()
                    } else KeyboardPrefs.resetAll(context)
                    confirm = null
                }) { Text(tr(if (what == "dict") "Clear" else "Reset")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel")) } }
        )
    }
}

// ----------------------------------------------------------------------- о приложении

@Composable
internal fun AboutPage() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val spec = remember { WavySpec(ShapeProfile.LOBES, 6, 0.1f) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(96.dp).wavyBackground(spec, cs.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Keyboard, null, tint = cs.onPrimary, modifier = Modifier.size(44.dp))
        }
        Text("Expressive Keyboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = cs.onPrimaryContainer)
        Text(tr("Версия {0}", version) + " · Material 3 Expressive", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
    }
    SettingsSection("Возможности") {
        listOf(
            Icons.Rounded.Palette to "Material You: цвета из обоев или из любого своего цвета",
            Icons.Rounded.Extension to "Моды на JavaScript: свои панели, кнопки, клавиши, события и подсказки",
            Icons.Rounded.Language to tr("{0} languages with frequency dictionaries, pinyin, kana, hangul, and Telex", Languages.all.size),
            Icons.Rounded.EmojiEmotions to "Все эмодзи Unicode 16 с поиском и встроенными стилями",
            Icons.Rounded.Brush to "Свои иконки для любых кнопок и вкладок эмодзи",
            Icons.Rounded.Swipe to "Курсор свайпом по пробелу, удаление слов свайпом по ⌫",
            Icons.Rounded.SwapHoriz to "One-handed mode",
            Icons.Rounded.Translate to "Translator, GIF, clipboard, and voice input",
            Icons.Rounded.Spellcheck to "Подсказки и автоисправление, которые учатся вашим словам"
        ).forEach { (icon, text) ->
            GroupItem {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon)
                    Text(tr(text), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                }
            }
        }
    }
    SettingsSection("Лицензии") {
        GroupItem {
            Text(
                "Twemoji © Twitter / jdecked — CC-BY 4.0\n" +
                    "Noto Emoji, Blobmoji © Google — Apache 2.0\n" +
                    "OpenMoji © HfG Schwäbisch Gmünd — CC BY-SA 4.0\n" +
                    "Emoji names: Unicode CLDR — Unicode License\n" +
                    "Pinyin: mozillazg/pinyin-data, phrase-pinyin-data — MIT; OpenCC — Apache 2.0\n" +
                    "Word frequencies: hermitdave/FrequencyWords — MIT\n" +
                    "Nunito font — SIL OFL 1.1",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
    }
}
