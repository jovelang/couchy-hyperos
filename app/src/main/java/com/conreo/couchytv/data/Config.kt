// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.conreo.couchytv.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.runtime.Immutable

@Serializable
data class CategoryCfg(
    val id: String,
    val name: String,
)

@Immutable
@Serializable
data class LauncherConfig(
    val categories: List<CategoryCfg> = listOf(
        CategoryCfg("streaming", "Streaming"),
        CategoryCfg("games", "Games"),
        CategoryCfg("music", "Music"),
        CategoryCfg("apps", "Apps"),
    ),
    /** package -> section ids the user assigned (an app may be in several) */
    val sections: Map<String, Set<String>> = emptyMap(),
    /** category id -> explicit package order */
    val order: Map<String, List<String>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** packages seen on the last scan — new installs are auto-added to the first section */
    val knownApps: Set<String> = emptySet(),
    val wallpaper: Int = 1, // Aurora
    val useCustomWallpaper: Boolean = false,
    /** video wallpaper: persisted content-URI of a local video, looped muted */
    val videoUri: String = "",
    val useVideoWallpaper: Boolean = false,
    /** built-in aerials source */
    val useBuiltinAerials: Boolean = false,
    /** which built-in collection (index into BuiltinAerials.SOURCES; 0 = all) */
    val builtinSource: Int = 0,
    val randomVideoRefresh: Boolean = true,
    val pinnedAerial: String = "",
    val saverEnabled:Boolean=false,
    /** 0=follow system, 1=desktop only; never interrupts another foreground app. */
    val saverOwner:Int=0,
    /** 0=gradient, 1=photos, 2=aerial video. */
    val saverMode:Int=0,
    val saverAfterMinutes:Int=5,
    val saverScreenOffMinutes:Int=0,
    val saverStandbyMinutes:Int=0,
    val saverGradient:Int=1,
    val saverGradientColors:List<String> = listOf("13547A","2B825B","80D0C7"),
    val saverFlow:Boolean=false,
    val saverPhotos:List<String> = emptyList(),
    val saverPhotoRandom:Boolean=false,
    val saverVideoSource:Int=0,
    val saverVideoUri:String="",
    val saverVideoRandom:Boolean=false,
    /** video wallpaper playback speed: index into VIDEO_SPEEDS */
    val videoSpeed: Int = 3, // 1x
    val accent: Int = 0,
    val h24: Boolean = true,
    val showHidden: Boolean = false,
    val setupDone: Boolean = false,
    // ----- status bar -----
    val showStatusBar: Boolean = true,
    /** package of the app the VPN icon opens; empty = system VPN settings */
    val vpnApp: String = "",
    val showVpnButton: Boolean = true,
    /** wrap the status-bar icons in the same glass panel as dock mode */
    val statusBarGlass: Boolean = true,
    /** index into DATE_FORMATS; 0 = no date shown */
    val dateFormat: Int = 0,
    val showLunar: Boolean = false,
    val uniformSystemIcons: Boolean = true,
    val keepAdbEnabled: Boolean = false,
    val showGreeting: Boolean = true,
    val nickname: String = "",
    val showNickname: Boolean = false,
    val showWeather: Boolean = false,
    val weatherCity: String = "",
    val weatherLatitude: Double = 0.0,
    val weatherLongitude: Double = 0.0,
    val weatherStation: String = "",
    val inputAliases: Map<String, String> = emptyMap(),
    val inputPreview: Boolean = true,
    val lastInput: String = "",
    val sourceTapAction: Int = 1,
    val sourceHoldAction: Int = 4,
    val menuTapAction: Int = 3,
    val menuHoldAction: Int = 2,
    val globalRemoteKeys: Boolean = false,
    val remoteControlUrl: String = "",
    val remoteControlLanUrl: String = "",
    val remoteControlCode: String = "",
    val photoUris: List<String> = emptyList(),
    val photoIndex: Int = 0,
    val videoUris: List<String> = emptyList(),
    val videoIndex: Int = 0,
    val originalBanners: Set<String> = emptySet(),
    // ----- display options -----
    /** wallpaper dimming: 0 = top & bottom, 1 = top, 2 = bottom, 3 = full, 4 = off */
    val scrimMode: Int = 1, // Top
    val showCategoryNames: Boolean = true,
    val showAppLabels: Boolean = true,
    /** spacing step 0..4 (see GAP_SIZES) */
    val spacing: Int = 1, // Small
    /** icon size step 0..4 (see ICON_SIZES) */
    val iconScale: Int = 1, // Small
    /** icon/panel corner roundness step 0..4 (see CORNER_RADII) */
    val cornerRadius: Int = 3, // Large
    /** UI scale: 0 = Auto (compact high-DPI TVs), 1..5 = fixed (see UI_SCALES) */
    val uiScale: Int = 0, // Auto
    /** language code (e.g. "fr", "de"); empty = system default */
    val language: String = "",
    /** 0 = carousel (fixed selection), 1 = grid, 2 = dock */
    val layout: Int = 2, // Dock
)

const val LAYOUT_CAROUSEL = 0
const val LAYOUT_GRID = 1
const val LAYOUT_DOCK = 2

/** Status bar date formats (SimpleDateFormat patterns); index 0 = off. */
val DATE_FORMATS = listOf("", "EEE d", "EEE d MMM", "d MMM yyyy", "dd/MM", "MM/dd", "yyyy-MM-dd", "M月d日 E")

/** Sorted by user base — the display name is localized in the UI via string resource. */
val LANGUAGES = listOf(
    "",     // system default
    "zh", "es", "ja", "de", "fr",   // tier 1
    "pt", "ru", "ko", "ar", "it",   // tier 2
    "tr", "pl", "nl", "hi", "th", "in", "vi", // tier 3
)

private val Context.dataStore by preferencesDataStore(name = "launcher")
private val KEY_CONFIG = stringPreferencesKey("config")
private val json = Json { ignoreUnknownKeys = true }

class ConfigStore(private val context: Context) {

    val flow: Flow<LauncherConfig> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIG]?.let {
            runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
        } ?: LauncherConfig(categories = defaultCategories(context))
    }

    private fun defaultCategories(ctx: Context) = listOf(
        CategoryCfg("streaming", ctx.getString(R.string.cat_streaming)),
        CategoryCfg("games", ctx.getString(R.string.cat_games)),
        CategoryCfg("music", ctx.getString(R.string.cat_music)),
        CategoryCfg("apps", ctx.getString(R.string.cat_apps)),
    )

    suspend fun update(transform: (LauncherConfig) -> LauncherConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CONFIG]?.let {
                runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
            } ?: LauncherConfig()
            prefs[KEY_CONFIG] = json.encodeToString(transform(current))
        }
    }
}
