// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*

/** The row owns remote focus; the decorative switch never adds a second stop. */
@Composable
internal fun SettingSwitch(checked:Boolean) {
    val position=animateFloatAsState(if(checked)1f else 0f,tween(150),label="switch-thumb")
    Canvas(Modifier.size(42.dp,25.dp).clearAndSetSemantics {}) {
        val r=size.height/2
        drawRoundRect(if(checked)Color(0xFF34C759)else Color(0xFF68717C),cornerRadius=CornerRadius(r,r))
        drawCircle(Color.White,r-3.dp.toPx(),Offset(r+(size.width-size.height)*position.value,r))
    }
}

@Composable
internal fun ToggleSetting(title:String,checked:Boolean,onChange:(Boolean)->Unit,
    modifier:Modifier=Modifier,description:String?=null,enabled:Boolean=true,
    leading:(@Composable BoxScope.()->Unit)?=null) {
    SettingsItem(onClick={onChange(!checked)},enabled=enabled,
        modifier=modifier.semantics(mergeDescendants=true) {
            role=Role.Switch;toggleableState=if(checked)ToggleableState.On else ToggleableState.Off
            stateDescription=if(checked)"已开启"else"已关闭"
        }.onPreviewKeyEvent {e->
            if(!enabled || e.type!=KeyEventType.KeyDown)false
            else when(e.key) {Key.DirectionLeft->{if(checked)onChange(false);true};Key.DirectionRight->{if(!checked)onChange(true);true};else->false}
        },headlineContent={Text(title)},supportingContent=description?.let {{Text(it)}},
        leadingContent=leading,trailingContent={SettingSwitch(checked)})
}

@Composable
internal fun ChoiceSetting(title:String,options:List<String>,selected:Int,onSelect:(Int)->Unit,
    modifier:Modifier=Modifier,description:String?=null,enabled:Boolean=true) {
    var open by remember {mutableStateOf(false)}
    val current=selected.coerceIn(0,options.lastIndex)
    SettingsItem(onClick={open=true},enabled=enabled,modifier=modifier.onPreviewKeyEvent {e->
        if(!enabled || e.type!=KeyEventType.KeyDown)false
        else when(e.key) {
            Key.DirectionLeft->{if(current>0)onSelect(current-1);true}
            Key.DirectionRight->{if(current<options.lastIndex)onSelect(current+1);true}
            else->false
        }
    },headlineContent={Text(title)},supportingContent=description?.let {{Text(it)}},
        trailingContent={Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(options[current]);Icon(AppIcons.Chevron,null,Modifier.size(15.dp))
        }})
    if(open) Dialog(onDismissRequest={open=false}) {
        val focus=remember {FocusRequester()}
        LaunchedEffect(Unit) {focus.requestWhenReady()}
        GlassSurface(Modifier.width(420.dp).heightIn(max=530.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(12.dp))
                val list=rememberLazyListState(initialFirstVisibleItemIndex=(current-2).coerceAtLeast(0))
                val loop=rememberLazyMenuLoop(options.size,list)
                LazyColumn(state=list) {
                    items(options.size,key={it}) {index->
                        SettingsItem(onClick={onSelect(index);open=false},
                            modifier=loop.item(index).then(if(index==current)Modifier.focusRequester(focus)else Modifier),
                            headlineContent={Text(options[index])},
                            trailingContent={if(index==current)Icon(AppIcons.Check,null,Modifier.size(20.dp))})
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsLink(title:String,value:String="",onClick:()->Unit,modifier:Modifier=Modifier) {
    SettingsItem(onClick=onClick,modifier=modifier,headlineContent={Text(title)},
        trailingContent={Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(value.isNotEmpty())Text(value);Icon(AppIcons.Chevron,null,Modifier.size(15.dp))
        }})
}
