// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.conreo.couchytv.data.*
import kotlinx.coroutines.launch

val REMOTE_ACTIONS=listOf("无操作","显示信号源列表","电视设置","桌面设置","切换下一个信号源（全屏）") + SourceController.targets.map {"全屏切换到 $it"}

@Composable
fun InputSettings(config:LauncherConfig,store:ConfigStore,onBack:()->Unit) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    val sources=remember { SourceController.list(ctx) }
    val firstFocus=remember {androidx.compose.ui.focus.FocusRequester()}
    LaunchedEffect(Unit) {firstFocus.requestWhenReady()}
    var rename by remember { mutableStateOf<InputSource?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("信号源",fontSize=24.sp,color=Color.White)
        Text("接口名称与预览",fontSize=12.sp,color=Color.White.copy(alpha=.6f))
        ToggleSetting("实时预览",config.inputPreview,{v->scope.launch {store.update {it.copy(inputPreview=v)}}},Modifier.focusRequester(firstFocus),description="选中信号源时静音预览")
        Text("信号源名称",Modifier.padding(top=14.dp),fontSize=15.sp,color=Color.White.copy(alpha=.65f))
        sources.forEach { input ->
            ListItem(selected=false,onClick={rename=input},headlineContent={InputName(input,config)},
                supportingContent={Text("自定义名称，例如 Switch-${input.name}")},leadingContent={Icon(AppIcons.Input,null)})
        }
        ListItem(selected=false,onClick=onBack,headlineContent={Text("返回桌面设置")})
    }
    rename?.let { input ->
        var name by remember(input.name) {mutableStateOf(config.inputAliases[input.name].orEmpty())}
        val fieldFocus=remember {androidx.compose.ui.focus.FocusRequester()}
        val saveFocus=remember {androidx.compose.ui.focus.FocusRequester()}
        val keyboard=LocalSoftwareKeyboardController.current
        Dialog(onDismissRequest={rename=null}) {
            GlassSurface(Modifier.width(400.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("重命名 ${input.name}",fontSize=22.sp,color=Color.White)
                    Text("只需填写前缀，例如 Switch，显示为 Switch-${input.name}",fontSize=13.sp,color=Color.White.copy(alpha=.7f))
                    LaunchedEffect(Unit) {fieldFocus.requestWhenReady();keyboard?.show()}
                    BasicTextField(value=name,onValueChange={name=it.take(24)},singleLine=true,
                        textStyle=TextStyle(color=Color.White,fontSize=20.sp),cursorBrush=SolidColor(Color.White),
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={keyboard?.hide();scope.launch {saveFocus.requestWhenReady()}}),
                        modifier=Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha=.10f),SmoothCornerShape(10.dp))
                            .padding(horizontal=12.dp,vertical=12.dp).focusRequester(fieldFocus).onPreviewKeyEvent {e->
                                if(e.key==Key.DirectionDown) {
                                    if(e.type==KeyEventType.KeyUp) {keyboard?.hide();scope.launch {saveFocus.requestWhenReady()}}
                                    true
                                } else if(e.key==Key.DirectionCenter || e.key==Key.Enter) {
                                    if(e.type==KeyEventType.KeyUp) keyboard?.show()
                                    true
                                } else false
                            },
                        decorationBox={inner->Box {
                            if(name.isEmpty()) Text("自定义名称（留空恢复）",fontSize=17.sp,color=Color.White.copy(alpha=.45f))
                            inner()
                        }})
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Button(modifier=Modifier.focusRequester(saveFocus),onClick={val alias=name.trim();scope.launch {
                            store.update {it.copy(inputAliases=if(alias.isEmpty()) it.inputAliases-input.name else it.inputAliases+(input.name to alias))};rename=null
                        }}) {Text("保存")}
                        Button(onClick={rename=null}) {Text("取消")}
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteSettings(config:LauncherConfig,store:ConfigStore,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    val first=remember {androidx.compose.ui.focus.FocusRequester()}
    LaunchedEffect(Unit) {first.requestWhenReady()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("遥控器按键",fontSize=24.sp,color=Color.White)
        Text("选择按键动作，按确定修改。",fontSize=12.sp,color=Color.White.copy(alpha=.65f))
        Box(Modifier.focusRequester(first)) {
            ActionSetting("信号源键 · 点按",config.sourceTapAction) {v->scope.launch {store.update {it.copy(sourceTapAction=v)}}}
        }
        ActionSetting("信号源键 · 长按",config.sourceHoldAction) {v->scope.launch {store.update {it.copy(sourceHoldAction=v)}}}
        ActionSetting("菜单键 · 点按",config.menuTapAction) {v->scope.launch {store.update {it.copy(menuTapAction=v)}}}
        ActionSetting("菜单键 · 长按",config.menuHoldAction) {v->scope.launch {store.update {it.copy(menuHoldAction=v)}}}
        ToggleSetting("在其他应用中生效",config.globalRemoteKeys,{v->scope.launch {store.update {it.copy(globalRemoteKeys=v)}}},description="需要开启遥控器辅助服务")
        ListItem(selected=false,onClick=onBack,headlineContent={Text("返回桌面设置")},leadingContent={Icon(AppIcons.Back,null)})
    }
}

@Composable
private fun ActionSetting(label:String,value:Int,onChange:(Int)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    val focus=remember {androidx.compose.ui.focus.FocusRequester()}
    ListItem(selected=false,onClick={expanded=true},headlineContent={Text(label)},
        supportingContent={Text(REMOTE_ACTIONS.getOrElse(value){REMOTE_ACTIONS[0]})},trailingContent={Icon(AppIcons.Cycle,null)})
    if(expanded) Dialog(onDismissRequest={expanded=false}) {
        LaunchedEffect(Unit) {focus.requestWhenReady()}
        GlassSurface(Modifier.width(430.dp).heightIn(max=500.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,fontSize=21.sp,color=Color.White,modifier=Modifier.padding(bottom=10.dp))
                REMOTE_ACTIONS.forEachIndexed {i,name->
                    ListItem(selected=i==value,
                        modifier=if(i==value) Modifier.focusRequester(focus) else Modifier,
                        onClick={onChange(i);expanded=false},headlineContent={Text(name)},
                        trailingContent={if(i==value) Icon(AppIcons.Check,null)})
                }
            }
        }
    }
}
