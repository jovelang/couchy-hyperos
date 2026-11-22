// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.foundation.layout.heightIn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.conreo.couchytv.Actions
import com.conreo.couchytv.R
import com.conreo.couchytv.data.AppEntry
import com.conreo.couchytv.data.AppRepository
import com.conreo.couchytv.data.CalendarLabels
import com.conreo.couchytv.data.CategoryCfg
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.data.DATE_FORMATS
import com.conreo.couchytv.data.LAYOUT_DOCK
import com.conreo.couchytv.data.LAYOUT_GRID
import com.conreo.couchytv.data.LauncherConfig
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.Json

private val format = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

private enum class SettingsScreen { Main, Wallpaper, Display, StatusBar, Apps, Categories, Launcher, Inputs, Remote, Layout, About, Screensaver, WebControl }

/**
 * Settings panel built entirely from focusable rows and buttons — every
 * element is reachable with the D-pad alone. Text entry only ever happens
 * inside a dedicated dialog (opened with the pencil button).
 */
@Composable
fun SettingsSheet(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onDismiss: () -> Unit,
    onWallpaperChanged: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(SettingsScreen.Main) }
    var mainSelection by remember {mutableStateOf("电视设置")}
    var layoutSelection by remember {mutableStateOf("显示与布局")}
    fun navigate(next:SettingsScreen,label:String) {
        if(screen==SettingsScreen.Main) mainSelection=label
        if(screen==SettingsScreen.Layout) layoutSelection=label
        screen=next
    }
    fun goBack() {screen=when(screen) {
        SettingsScreen.Display,SettingsScreen.Apps,SettingsScreen.Categories -> SettingsScreen.Layout
        else -> SettingsScreen.Main
    }}

    // Same document-picker flow as videos: opens the system file manager,
    // works with USB drives and network storage providers.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            scope.launch {
                store.update {
                    it.copy(
                        photoUris = uris.map { it.toString() }, photoIndex = 0,
                        useCustomWallpaper = true,
                        useVideoWallpaper = false,
                        useBuiltinAerials = false,
                    )
                }
                onWallpaperChanged()
            }
        }
    }

    // Video wallpaper: keep a persistable read grant and stream in place —
    // aerial files are hundreds of MB, never copied.
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            scope.launch {
                store.update {
                    it.copy(
                        videoUri = uris.first().toString(), videoUris = uris.map { it.toString() }, videoIndex = 0,
                        useVideoWallpaper = true,
                        useCustomWallpaper = false,
                        useBuiltinAerials = false,
                    )
                }
            }
        }
    }

    val glassFrame = LocalGlassFrame.current
    val glassRoot = LocalGlassRoot.current
    Dialog(
        onDismissRequest = {
            if (screen != SettingsScreen.Main) goBack() else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassFrame provides glassFrame, LocalGlassRoot provides glassRoot) {
        // Force the dialog window to fill the screen — Compose dialogs otherwise
        // keep a small platform margin, so the panel wouldn't sit flush against
        // the right edge.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(dialogView) {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.apply { setDimAmount(0f);setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)) }?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
            onDispose {}
        }
        // A Dialog spawns its own window that re-provides the platform density,
        // so the launcher's ScaledUi doesn't reach here — re-apply it. The
        // dialog's LocalConfiguration is the real (unscaled) device width.
        ScaledUi(uiScaleFactor(config.uiScale, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            GlassSurface(Modifier.width(440.dp).fillMaxHeight()) {
                // One active page at a time: outgoing page focus must not override
                // the remembered destination after Back, and no full-panel slide is needed.
                androidx.compose.runtime.key(screen) {
                when (screen) {

                    SettingsScreen.Main -> MainScreen(
                        selected=mainSelection,
                        onInputs={navigate(SettingsScreen.Inputs,"信号源")},
                        onRemote={navigate(SettingsScreen.Remote,"遥控器按键")},
                        onWebControl={navigate(SettingsScreen.WebControl,"远程控制")},
                        onWallpaper={navigate(SettingsScreen.Wallpaper,"背景壁纸")},
                        onScreensaver={navigate(SettingsScreen.Screensaver,"屏保与休眠")},
                        onLayout={navigate(SettingsScreen.Layout,"外观与应用")},
                        onStatusBar={navigate(SettingsScreen.StatusBar,"时间与天气")},
                        onLauncher={navigate(SettingsScreen.Launcher,"更多设置")},
                        onAndroidSettings={mainSelection="电视设置";Actions.openSystemSettings(context)},
                        onAbout={navigate(SettingsScreen.About,"关于桌面")})
                    SettingsScreen.Layout -> LayoutMenu(layoutSelection,
                        {navigate(SettingsScreen.Display,"显示与布局")},
                        {navigate(SettingsScreen.Apps,"应用管理")},
                        {navigate(SettingsScreen.Categories,"应用分组")})
                    SettingsScreen.Screensaver -> ScreenSaverSettings(config,store) {onDismiss()}
                    SettingsScreen.Remote -> RemoteSettings(config,store) {goBack()}
                    SettingsScreen.WebControl -> RemoteControlPage(config) {goBack()}
                    SettingsScreen.Wallpaper -> WallpaperScreen(
                        config = config,
                        onSelectBuiltinAerials = {
                            scope.launch {
                                store.update {
                                    it.copy(
                                        useBuiltinAerials = true,
                                        useVideoWallpaper = false,
                                        useCustomWallpaper = false,
                                    )
                                }
                            }
                        },
                        onSelectBuiltinSource = { v ->
                            scope.launch { store.update { it.copy(builtinSource = v) } }
                        },
                        onSetScrim = { v ->
                            scope.launch { store.update { it.copy(scrimMode = v) } }
                        },
                        onRandomVideo={scope.launch {store.update {it.copy(randomVideoRefresh=!it.randomVideoRefresh)}}},
                        onSetSpeed = { v ->
                            scope.launch { store.update { it.copy(videoSpeed = v) } }
                        },
                        onPreset = { i ->
                            scope.launch {
                                store.update {
                                    it.copy(
                                        wallpaper = i,
                                        useCustomWallpaper = false,
                                        useVideoWallpaper = false,
                                        useBuiltinAerials = false,
                                    )
                                }
                            }
                        },
                        onPickPhoto = {
                            runCatching { photoPicker.launch(arrayOf("image/*")) }
                                .onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) }
                        },
                        onPickVideo = {
                            runCatching { videoPicker.launch(arrayOf("video/*")) }
                                .onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) }
                        },
                    )
                    SettingsScreen.Inputs -> InputSettings(config, store) {goBack()}
                    SettingsScreen.Display -> DisplayScreen(
                        config = config,
                        store = store,
                        onBack = { goBack() },
                    )
                    SettingsScreen.StatusBar -> StatusBarScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { goBack() },
                    )
                    SettingsScreen.Apps -> AppsScreen(
                        apps = apps,
                        config = config,
                        onBack = { goBack() },
                        onLaunch = { pkg -> Actions.launchApp(context, pkg) },
                        onAppInfo = { pkg -> Actions.openAppInfo(context, pkg) },
                        onUninstall = { pkg -> Actions.uninstall(context, pkg) },
                        onToggleHide = { pkg ->
                            scope.launch {
                                store.update { c ->
                                    c.copy(hidden = if (pkg in c.hidden) c.hidden - pkg else c.hidden + pkg)
                                }
                            }
                        },
                    )
                    SettingsScreen.Categories -> CategoriesScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { goBack() },
                    )
                    SettingsScreen.About -> AboutScreen(
                        onBack = { goBack() },
                    )
                    SettingsScreen.Launcher -> LauncherSettingsSubscreen(
                        config = config,
                        store = store,
                        onBack = { goBack() },
                        onRerunWizard = onRerunWizard,
                    )
                }
                }
            }
        }
        }
    }
    }
}

/* ------------------------------ main menu ------------------------------ */

private data class SettingDestination(val title:String,val description:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val click:()->Unit)

@Composable
private fun DestinationMenu(title:String,selected:String,entries:List<SettingDestination>) {
    val focus=remember {FocusRequester()}
    LaunchedEffect(selected) {focus.requestWhenReady()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(start=10.dp,bottom=14.dp))
        entries.forEach {entry ->
            SettingsItem(selected=false,onClick=entry.click,
                modifier=if(entry.title==selected) Modifier.focusRequester(focus) else Modifier,
                headlineContent={Text(entry.title)},supportingContent={Text(entry.description)},
                leadingContent={Icon(entry.icon,null)},trailingContent={Icon(AppIcons.Chevron,null,Modifier.size(16.dp))})
        }
    }
}

@Composable
private fun MainScreen(selected:String,onInputs:()->Unit,onRemote:()->Unit,onWebControl:()->Unit,onWallpaper:()->Unit,onLayout:()->Unit,
    onStatusBar:()->Unit,onLauncher:()->Unit,onAndroidSettings:()->Unit,onAbout:()->Unit,onScreensaver:()->Unit) {
    DestinationMenu("桌面设置",selected,listOf(
        SettingDestination("电视设置","画面、声音与系统网络",AppIcons.Television,onAndroidSettings),
        SettingDestination("背景壁纸","图片、视频和切换方式",AppIcons.Image,onWallpaper),
        SettingDestination("屏保与休眠","屏保画面、熄屏和休眠",AppIcons.Display,onScreensaver),
        SettingDestination("时间与天气","日期、农历、天气和昵称",AppIcons.Clock,onStatusBar),
        SettingDestination("外观与应用","卡片样式、应用与分组",AppIcons.Apps,onLayout),
        SettingDestination("信号源","接口命名与窗口预览",AppIcons.Input,onInputs),
        SettingDestination("遥控器按键","信号源键与菜单键",AppIcons.Remote,onRemote),
        SettingDestination("远程控制","手机与电脑控制电视",AppIcons.Remote,onWebControl),
        SettingDestination("更多设置","语言、ADB 与备份",AppIcons.Display,onLauncher),
        SettingDestination("关于桌面","版本、开源许可与数据来源",AppIcons.Info,onAbout)))
}

@Composable
private fun LayoutMenu(selected:String,onDisplay:()->Unit,onApps:()->Unit,onCategories:()->Unit) {
    DestinationMenu("外观与应用",selected,listOf(
        SettingDestination("显示与布局","布局模式、卡片大小、间距与圆角",AppIcons.Display,onDisplay),
        SettingDestination("应用管理","显示、隐藏与应用信息",AppIcons.Apps,onApps),
        SettingDestination("应用分组","新建、重命名和调整分组内容",AppIcons.Folder,onCategories)))
}

@Composable
private fun AboutScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val version=remember {runCatching {context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrDefault("?")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("关于桌面",style=MaterialTheme.typography.titleLarge)
        Icon(AppIcons.Desktop,null,Modifier.size(46.dp))
        Text("Couchy HyperOS",fontSize=23.sp)
        Text("为客厅大屏和遥控器设计",color=Color.White.copy(alpha=.65f))
        Text("桌面版本  $version",fontSize=15.sp)
        Text("设备  ${android.os.Build.MODEL}\nAndroid ${android.os.Build.VERSION.RELEASE}",fontSize=14.sp,color=Color.White.copy(alpha=.75f))
        SectionLabel("开源与许可")
        Text("基于 conreo/couchy-launcher 的非官方衍生版本\nCopyright © 2026 Couchy Launcher contributors\n修改：Couchy HyperOS contributors · 2026-11-23\nGNU GPLv3 · 无任何担保，可按许可共享与修改",fontSize=14.sp)
        Text("上游：https://github.com/conreo/couchy-launcher",fontSize=12.sp,color=Color.White.copy(alpha=.65f))
        Text("衍生版本：https://github.com/jovelang/couchy-hyperos",fontSize=12.sp,color=Color.White.copy(alpha=.65f))
        var legal by remember {mutableStateOf(false)}
        SettingsItem(onClick={legal=true},headlineContent={Text("查看 GPLv3 与第三方声明")},leadingContent={Icon(AppIcons.Info,null)})
        if(legal) {
            val notice=remember {context.assets.open("third-party/NOTICE.txt").bufferedReader().use {it.readText()}}
            val license=remember {context.assets.open("third-party/GPL-3.0.txt").bufferedReader().use {it.readText()}}
            Dialog(onDismissRequest={legal=false}) {
                GlassSurface(Modifier.width(620.dp).heightIn(max=650.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("开源许可",fontSize=24.sp)
                        Text(notice+"\n\n"+license,fontSize=12.sp)
                        SettingsItem(onClick={legal=false},modifier=Modifier.focusRequester(initialFocus()),headlineContent={Text("关闭")})
                    }
                }
            }
        }
        Text("天气数据：中国天气网 / Open-Meteo\n动态背景：Apple、Amazon 与社区素材",fontSize=12.sp,color=Color.White.copy(alpha=.65f))
        Text("使用 Jetpack Compose、Media3 与 OkHttp 等开源组件",fontSize=12.sp,color=Color.White.copy(alpha=.55f))
        SettingsItem(selected=false,onClick=onBack,modifier=Modifier.focusRequester(initialFocus()),
            headlineContent={Text("返回桌面设置")},leadingContent={Icon(AppIcons.Back,null)})
    }
}

/* ------------------------------ display ------------------------------ */

@Composable
private fun DisplayScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope=rememberCoroutineScope()
    fun update(f:(LauncherConfig)->LauncherConfig) {scope.launch {store.update(f)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("显示与布局",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(8.dp))
        ChoiceSetting("应用布局",listOf("分类横排","应用网格","单行应用"),config.layout.coerceIn(0,2),{v->update {it.copy(layout=v)}},Modifier.focusRequester(initialFocus()))
        if(config.layout!=LAYOUT_DOCK)ToggleSetting("显示分组名称",config.showCategoryNames,{v->update {it.copy(showCategoryNames=v)}})
        SectionLabel("大小与间距")
        ChoiceSetting("界面大小",listOf("自动","75%","90%","100%","115%","130%"),config.uiScale.coerceIn(0,5),{v->update {it.copy(uiScale=v)}})
        ChoiceSetting("卡片大小",listOf("小","较小","标准","较大","大"),config.iconScale.coerceIn(0,4),{v->update {it.copy(iconScale=v)}})
        ChoiceSetting("卡片间距",listOf("紧凑","较紧凑","标准","宽松","更宽松"),config.spacing.coerceIn(0,4),{v->update {it.copy(spacing=v)}})
        ChoiceSetting("卡片圆角",listOf("最小","小","标准","大","最大"),config.cornerRadius.coerceIn(0,4),{v->update {it.copy(cornerRadius=v)}})
        SectionLabel("图标")
        ToggleSetting("统一功能图标",config.uniformSystemIcons,{v->update {it.copy(uniformSystemIcons=v)}},description="应用保留品牌 Logo")
    }
}

/**
 * D-pad selector row: Left/Right changes the value directly, OK cycles.
 * The dots show the position within the available steps.
 */
@Composable
internal fun SelectorRow(
    label: String,
    value: String,
    steps: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    SettingsItem(
        selected = false,
        onClick = { onSelect((current + 1) % steps) },
        headlineContent = { Text(label) },
        supportingContent = { if (description != null) Text(description) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("◄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value)
                Text("►", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onSelect((current - 1 + steps) % steps); true }
                Key.DirectionRight -> { onSelect((current + 1) % steps); true }
                else -> false
            }
        },
    )
}

/* --------------- app list: icon + name launches, ⓘ = app info --------------- */

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onToggleHide: (String) -> Unit,
) {
    val catNames = remember(config.categories) { config.categories.associate { it.id to it.name } }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            stringResource(R.string.item_app),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp, start = 8.dp),
        )
        Text(
            stringResource(R.string.app_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        val list=rememberLazyListState()
        val loop=rememberLazyMenuLoop(apps.size,list)
        LazyColumn(
            Modifier.fillMaxSize(),
            state=list,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(apps.size, key = { apps[it].pkg }) { i ->
                val app = apps[i]
                Row(
                    Modifier.fillMaxWidth().then(loop.item(i)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SettingsItem(
                            selected = false,
                            onClick = { onLaunch(app.pkg) },
                            modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                val names = AppRepository.sectionsOf(app, config)
                                    .mapNotNull { catNames[it] }
                                val suffix = if (app.pkg in config.hidden) " · " + stringResource(R.string.hidden_tag) else ""
                                Text(names.joinToString(" · ").ifEmpty { stringResource(R.string.no_section) } + suffix)
                            },
                            leadingContent = { AppThumb(app) },
                        )
                    }
                    val isHidden = app.pkg in config.hidden
                    SmallIconButton(
                        if (isHidden) AppIcons.Hide else AppIcons.Show,
                        stringResource(if (isHidden) R.string.menu_unhide else R.string.menu_hide),
                    ) { onToggleHide(app.pkg) }
                    SmallIconButton(AppIcons.Info, stringResource(R.string.menu_app_info)) { onAppInfo(app.pkg) }
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.menu_uninstall)) { onUninstall(app.pkg) }
                }
            }
        }
    }
}

/** Small leading artwork for list rows: banner if the app ships one, else icon. */
@Composable
private fun AppThumb(app: AppEntry) {
    when {
        app.banner != null -> Image(
            bitmap = app.banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
        app.icon != null -> Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        else -> Box(
            Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(5.dp))
        )
    }
}

/* ---------- section apps picker: vertical grid, checked = included ---------- */

@Composable
private fun SectionAppsDialog(
    cat: CategoryCfg,
    apps: List<AppEntry>,
    config: LauncherConfig,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassSurface(Modifier.width(720.dp).height(520.dp)) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.section_apps_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val f = initialFocus()
                val done=remember {FocusRequester()}
                val grid=rememberLazyGridState()
                val scope=rememberCoroutineScope()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state=grid,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps.size, key = { apps[it].pkg }) { i ->
                        val app = apps[i]
                        val inSection = cat.id in AppRepository.sectionsOf(app, config)
                        Surface(
                            onClick = { onToggle(app) },
                            modifier = if (i == 0) Modifier.focusRequester(f).onPreviewKeyEvent {event->
                                if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionUp) {
                                    scope.launch {grid.scrollToItem(apps.lastIndex);done.requestWhenReady()};true
                                } else false
                            } else Modifier,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (app.banner != null) {
                                        Image(
                                            bitmap = app.banner,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(6.dp)),
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (app.icon != null) {
                                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                                            }
                                        }
                                    }
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                    CheckMark(checked = inSection)
                                }
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.padding(top = 12.dp).focusRequester(done).onPreviewKeyEvent {event->
                    if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionDown && apps.isNotEmpty()) {
                        scope.launch {grid.scrollToItem(0);f.requestWhenReady()};true
                    } else false
                }) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}


/* ------------------------------ launcher settings ------------------------------ */

@Composable
private fun LauncherSettingsSubscreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var langScreen by remember { mutableStateOf(false) }

    if (langScreen) {
        LanguageScreen(config = config, store = store, onBack = { langScreen = false })
        return
    }

    fun saveConfig() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    File(dir, "CouchyBackup.json").writeText(format.encodeToString(LauncherConfig.serializer(), config))
                }
            }
            Actions.toast(context, "Saved to Downloads/CouchyBackup.json")
        }
    }

    val loadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            format.decodeFromString(LauncherConfig.serializer(), String(input.readBytes()))
                        }
                    }.getOrNull()
                }
                if (loaded != null) {
                    store.update { loaded.copy(knownApps = config.knownApps, setupDone = true) }
                    Actions.toast(context, context.getString(R.string.toast_config_loaded))
                } else {
                    Actions.toast(context, context.getString(R.string.toast_config_bad))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_launcher_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        ToggleSetting("保持 ADB 开启",config.keepAdbEnabled,{v->scope.launch {store.update {it.copy(keepAdbEnabled=v)}}})
        SettingsItem(
            selected = false,
            onClick = { langScreen = true },
            headlineContent = { Text(stringResource(R.string.item_language)) },
            supportingContent = { Text(stringResource(R.string.item_language_sub)) },
            leadingContent = { Icon(AppIcons.Language, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { saveConfig() },
            headlineContent = { Text(stringResource(R.string.item_save_config)) },
            supportingContent = { Text(stringResource(R.string.item_save_config_sub)) },
            leadingContent = { Icon(AppIcons.Save, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { runCatching { loadPicker.launch(arrayOf("application/json", "*/*")) }.onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) } },
            headlineContent = { Text(stringResource(R.string.item_load_config)) },
            supportingContent = { Text(stringResource(R.string.item_load_config_sub)) },
            leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = onRerunWizard,
            headlineContent = { Text(stringResource(R.string.rerun_wizard)) },
            supportingContent = { Text(stringResource(R.string.rerun_wizard_sub)) },
            leadingContent = { Icon(AppIcons.Play, contentDescription = null) },
        )
    }
}

/* ------------------------------ language ------------------------------ */

@Composable
private fun LanguageScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_language),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        com.conreo.couchytv.data.LANGUAGES.forEachIndexed { i, code ->
            // Endonym: each language named in its own tongue (no per-language strings)
            val label = if (code.isEmpty()) stringResource(R.string.lang_system)
            else java.util.Locale(code).let { it.getDisplayName(it) }
                .replaceFirstChar { it.uppercase() }
            SettingsItem(
                selected = false,
                onClick = {
                    // Just persist it; LauncherApp mirrors it and recreates.
                    if (code != config.language) scope.launch {
                        store.update { it.copy(language = code) }
                    }
                },
                headlineContent = { Text(label) },
                trailingContent = { if(config.language == code)Icon(AppIcons.Check,null) },
                modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
            )
        }
    }
}

/* ------------------------------ status bar ------------------------------ */

@Composable
private fun StatusBarScreen(config:LauncherConfig,apps:List<AppEntry>,store:ConfigStore,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    val ctx=LocalContext.current
    var pickVpn by remember {mutableStateOf(false)}
    var editNickname by remember {mutableStateOf(false)}
    var editCity by remember {mutableStateOf(false)}
    var searching by remember {mutableStateOf(false)}
    var cityOptions by remember {mutableStateOf<List<com.conreo.couchytv.data.WeatherPlace>?>(null)}
    fun update(change:(LauncherConfig)->LauncherConfig) {scope.launch {store.update(change)}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("时间与天气",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(start=8.dp,bottom=10.dp))
        SectionLabel("时间与日历")
        ToggleSetting("24 小时制",config.h24,{v->update {it.copy(h24=v)}},Modifier.focusRequester(initialFocus()))
        val format=config.dateFormat.coerceIn(0,DATE_FORMATS.size-1)
        val dateOptions=remember {DATE_FORMATS.indices.map {i->if(i==0)"不显示"else CalendarLabels.solar(java.util.Date(),i,java.util.Locale.CHINA)}}
        ChoiceSetting("日期格式",dateOptions,format,{v->update {it.copy(dateFormat=v)}})
        ToggleSetting("显示农历",config.showLunar,{v->update {it.copy(showLunar=v)}})
        SettingsLink("打开日历",onClick={Actions.launchApp(ctx,"com.xiaomi.mitv.calendar")})
        SectionLabel("天气")
        ToggleSetting("显示天气",config.showWeather,{v->update {it.copy(showWeather=v)}})
        if(config.showWeather) SettingsLink("天气城市",config.weatherCity.ifBlank {"尚未设置"},{editCity=true})
        SectionLabel("问候语")
        ToggleSetting("显示问候语",config.showGreeting,{v->update {it.copy(showGreeting=v)}})
        if(config.showGreeting) {
            ToggleSetting("加上昵称",config.showNickname,{v->update {it.copy(showNickname=v)}})
            if(config.showNickname) SettingsLink("昵称",config.nickname.ifEmpty {"未设置"},{editNickname=true})
        }
        SectionLabel("顶栏")
        ToggleSetting("显示时间与顶栏",config.showStatusBar,{v->update {it.copy(showStatusBar=v)}})
        ToggleSetting("显示 VPN 按钮",config.showVpnButton,{v->update {it.copy(showVpnButton=v)}})
        if(config.showVpnButton) SettingsLink("VPN 应用",apps.firstOrNull {it.pkg==config.vpnApp}?.label ?: "系统设置",{pickVpn=true})
    }

    if(editNickname) ValueEditor("昵称",config.nickname,"填写昵称，留空可清除",onSave={name->update {it.copy(nickname=name.trim().take(24))};editNickname=false},onDismiss={editNickname=false})
    if(editCity) ValueEditor("天气城市",config.weatherCity.substringBefore('·'),"输入城市或区县名称",buttonText="查找",busy=searching,onSave={name->
        searching=true
        scope.launch {
            runCatching {com.conreo.couchytv.data.WeatherRepository.findCity(name)}
                .onSuccess {places->if(places.isEmpty()) Actions.toast(ctx,"没有找到城市，请换一个名称") else {cityOptions=places;editCity=false}}
                .onFailure {Actions.toast(ctx,"暂时无法查找城市，请稍后重试")}
            searching=false
        }
    },onDismiss={editCity=false})
    cityOptions?.let {places->
        Dialog(onDismissRequest={cityOptions=null}) {
            GlassSurface(Modifier.width(390.dp)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("选择天气城市",fontSize=22.sp,modifier=Modifier.padding(bottom=12.dp))
                    places.forEachIndexed {i,place->SettingsItem(selected=false,
                        modifier=if(i==0) Modifier.focusRequester(initialFocus()) else Modifier,
                        onClick={update {it.copy(weatherCity=place.name.substringBefore(" · "),weatherLatitude=place.latitude,weatherLongitude=place.longitude,weatherStation=place.station)};cityOptions=null},
                        headlineContent={Text(place.name)},leadingContent={Icon(AppIcons.Location,null)})}
                }
            }
        }
    }
    if(pickVpn) Dialog(onDismissRequest={pickVpn=false}) {
        GlassSurface(Modifier.width(380.dp).height(480.dp)) {
            val list=rememberLazyListState()
            val loop=rememberLazyMenuLoop(apps.size+1,list,1)
            val first=initialFocus()
            LazyColumn(Modifier.fillMaxSize().padding(16.dp),state=list,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                item {Text("VPN 按钮打开",fontSize=22.sp)}
                item {SettingsItem(selected=false,onClick={update {it.copy(vpnApp="")};pickVpn=false},modifier=loop.item(0).focusRequester(first),headlineContent={Text("系统 VPN 设置")})}
                items(apps.size,key={apps[it].pkg}) {i->val app=apps[i]
                    SettingsItem(selected=false,onClick={update {it.copy(vpnApp=app.pkg)};pickVpn=false},modifier=loop.item(i+1),headlineContent={Text(app.label)},leadingContent={AppThumb(app)},trailingContent={if(config.vpnApp==app.pkg)Icon(AppIcons.Check,null)})}
            }
        }
    }
}

@Composable
internal fun ValueEditor(title:String,initial:String,hint:String,buttonText:String="保存",busy:Boolean=false,onSave:(String)->Unit,onDismiss:()->Unit) {
    var value by remember {mutableStateOf(initial)}
    val field=remember {FocusRequester()};val save=remember {FocusRequester()}
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val scope=rememberCoroutineScope()
    Dialog(onDismissRequest=onDismiss) {
        GlassSurface(Modifier.width(390.dp)) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
                Text(title,fontSize=23.sp)
                Text(hint,fontSize=12.sp,color=Color.White.copy(alpha=.6f))
                LaunchedEffect(Unit) {field.requestWhenReady();keyboard?.show()}
                BasicTextField(value=value,onValueChange={value=it.take(24)},singleLine=true,
                    textStyle=TextStyle(color=Color.White,fontSize=20.sp),cursorBrush=SolidColor(Color.White),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={keyboard?.hide();scope.launch {save.requestWhenReady()}}),
                    modifier=Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha=.10f),SmoothCornerShape(10.dp))
                        .padding(12.dp).focusRequester(field).onPreviewKeyEvent {event->
                            if(event.key==Key.DirectionDown) {
                                if(event.type==KeyEventType.KeyUp) {keyboard?.hide();scope.launch {save.requestWhenReady()}}
                                true
                            } else if(event.key==Key.DirectionCenter || event.key==Key.Enter) {
                                if(event.type==KeyEventType.KeyUp) keyboard?.show()
                                true
                            } else false
                        })
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={keyboard?.hide();onSave(value)},enabled=!busy,modifier=Modifier.focusRequester(save)) {Text(if(busy) "查找中…" else buttonText)}
                    Button(onClick=onDismiss) {Text("取消")}
                }
            }
        }
    }
}

/* ------------------------------ sections ------------------------------ */

@Composable
private fun CategoriesScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renameFor by remember { mutableStateOf<CategoryCfg?>(null) }
    var appsFor by remember { mutableStateOf<CategoryCfg?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.item_sections),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        // Auto-filled "All apps" section: a single toggle, no per-app assignment.
        run {
            val hasAll = config.categories.any { it.id == AppRepository.ALL_APPS_ID }
            val allName = stringResource(R.string.cat_all_apps)
            SettingsItem(
                selected = false,
                onClick = {
                    scope.launch {
                        store.update { cfg ->
                            val rest = cfg.categories.filter { it.id != AppRepository.ALL_APPS_ID }
                            cfg.copy(
                                categories = if (hasAll) rest
                                else rest + CategoryCfg(AppRepository.ALL_APPS_ID, allName),
                            )
                        }
                    }
                },
                modifier = Modifier.focusRequester(f),
                headlineContent = { Text(allName) },
                supportingContent = { Text(stringResource(R.string.all_apps_sub)) },
                leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
                trailingContent = { CheckMark(checked = hasAll) },
            )
        }
        config.categories.forEachIndexed { i, cat ->
            if (cat.id == AppRepository.ALL_APPS_ID) return@forEachIndexed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SettingsItem(
                        selected = false,
                        onClick = { appsFor = cat },
                        headlineContent = { Text(cat.name) },
                        supportingContent = { Text(stringResource(R.string.section_row_sub)) },
                        leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
                    )
                }
                SmallIconButton(AppIcons.Pencil, stringResource(R.string.rename_section)) { renameFor = cat }
                SmallIconButton(AppIcons.Up, stringResource(R.string.cd_move_up)) {
                    if (i > 0) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i - 1]; l[i - 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                SmallIconButton(AppIcons.Down, stringResource(R.string.cd_move_down)) {
                    if (i < config.categories.size - 1) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i + 1]; l[i + 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                if (config.categories.size > 1) {
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.cd_delete_section)) {
                        scope.launch {
                            store.update { cfg ->
                                cfg.copy(categories = cfg.categories.filter { it.id != cat.id })
                            }
                        }
                    }
                }
            }
        }
        val newSectionName = stringResource(R.string.new_section)
        SettingsItem(
            selected = false,
            onClick = {
                scope.launch {
                    store.update {
                        it.copy(
                            categories = it.categories +
                                CategoryCfg("c${System.currentTimeMillis()}", newSectionName)
                        )
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.add_section)) },
            leadingContent = { Icon(AppIcons.Add, contentDescription = null) },
        )
    }

    appsFor?.let { cat ->
        // Keep the dialog's category object in sync with the live config
        val liveCat = config.categories.firstOrNull { it.id == cat.id } ?: cat
        SectionAppsDialog(
            cat = liveCat,
            apps = apps,
            config = config,
            onToggle = { app ->
                scope.launch {
                    store.update { cfg ->
                        val current = AppRepository.sectionsOf(app, cfg)
                        val next = if (liveCat.id in current) current - liveCat.id
                        else current + liveCat.id
                        cfg.copy(sections = cfg.sections + (app.pkg to next))
                    }
                }
            },
            onDismiss = { appsFor = null },
        )
    }

    renameFor?.let { cat ->
        RenameDialog(
            initial = cat.name,
            onSave = { newName ->
                scope.launch {
                    store.update { cfg ->
                        cfg.copy(categories = cfg.categories.map {
                            if (it.id == cat.id) it.copy(name = newName) else it
                        })
                    }
                }
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }
}

/** Pencil dialog: the ONLY place with a text field, so D-pad browsing never lands in an editor. */
@Composable
private fun RenameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface {
            Column(Modifier.width(340.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.rename_section), style = MaterialTheme.typography.titleMedium)
                val f = initialFocus()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (name.isNotBlank()) onSave(name.trim()) }
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
                    Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

/* ------------------------------ wallpaper ------------------------------ */

/**
 * ONE row per background mode, checkmark = active. Selecting any mode turns
 * the others off. Two groups: Static (color, photo) and Video (aerials, video).
 */
@Composable
private fun WallpaperScreen(
    config: LauncherConfig,
    onSelectBuiltinAerials: () -> Unit,
    onSelectBuiltinSource: (Int) -> Unit,
    onSetScrim: (Int) -> Unit,
    onSetSpeed: (Int) -> Unit,
    onRandomVideo:()->Unit,
    onPreset: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
) {
    val mode=when {config.useBuiltinAerials->2;config.useVideoWallpaper->3;config.useCustomWallpaper->1;else->0}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("背景壁纸",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(8.dp))
        ChoiceSetting("背景类型",listOf("渐变颜色","自己的图片","在线视频","自己的视频"),mode,{v->when(v) {
            0->onPreset(config.wallpaper);1->onPickPhoto();2->onSelectBuiltinAerials();3->onPickVideo()
        }},Modifier.focusRequester(initialFocus()))
        SectionLabel("当前背景")
        when(mode) {
            0->ChoiceSetting("渐变颜色",listOf("深夜","极光","日落","深海","炭黑"),config.wallpaper.coerceIn(0,4),onPreset)
            1->SettingsLink("更换图片","已选 ${config.photoUris.size} 张",onPickPhoto)
            2->ChoiceSetting("视频库",listOf("全部","Apple","Amazon","社区一","社区二"),config.builtinSource.coerceIn(0,4),onSelectBuiltinSource)
            3->SettingsLink("更换视频","已选 ${config.videoUris.size.coerceAtLeast(1)} 段",onPickVideo)
        }
        if(mode>=2) {
            ToggleSetting("自动换视频",config.randomVideoRefresh,{onRandomVideo()},description="关闭后循环当前视频，仍可手动切换")
            ChoiceSetting("播放速度",listOf("0.25×","0.5×","0.75×","1×"),config.videoSpeed.coerceIn(0,3),onSetSpeed)
        }
        SectionLabel("画面显示")
        ChoiceSetting("背景压暗",listOf("顶部与底部","仅顶部","仅底部","整个画面","不压暗"),config.scrimMode.coerceIn(0,4),onSetScrim)
    }
}

/* ------------------------------ widgets ------------------------------ */

/**
 * Every menu focuses its first item on entry (Back on the remote steps out,
 * so there are no on-screen Back buttons).
 */
@Composable
internal fun initialFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestWhenReady() }
    return fr
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 8.dp, bottom = 4.dp),
    )
}

@Composable
internal fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .background(
                if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(5.dp),
            )
            .border(
                width = 2.dp,
                color = if (checked) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A settings row with NO focus scale. tv-material's default focusedScale
 * scales the whole row — including its already-rasterized text — which looks
 * blurry on TV; the focused container colour alone signals selection.
 */
@Composable
internal fun SettingsItem(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    ListItem(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .padding(horizontal = 8.dp)
            .background(
                if (focused) Color(0xFFF1F2F4) else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = androidx.tv.material3.ListItemDefaults.colors(
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color(0xFF14151A),
        ),
    )
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.25f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            // label is the accessibility name announced by TalkBack.
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}
