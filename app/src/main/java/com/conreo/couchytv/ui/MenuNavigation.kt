// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keep normal spatial navigation; at a menu boundary use window-local tab wrapping. */
internal fun Modifier.loopingMenuNavigation():Modifier=composed {
    val focus=LocalFocusManager.current
    onKeyEvent {event->
        if(event.type!=KeyEventType.KeyDown)false
        else when(event.key) {
            Key.DirectionUp->focus.moveFocus(FocusDirection.Up) || focus.moveFocus(FocusDirection.Previous)
            Key.DirectionDown->focus.moveFocus(FocusDirection.Down) || focus.moveFocus(FocusDirection.Next)
            else->false
        }
    }
}

/** Explicit endpoints for virtual lists: the opposite item may not be composed yet. */
internal class LazyMenuLoop(private val count:Int,private val state:LazyListState,
    private val scope:CoroutineScope,private val itemOffset:Int) {
    private val focus=List(count) {FocusRequester()}
    private var jump:Job?=null
    fun item(index:Int):Modifier=Modifier.focusRequester(focus[index]).onPreviewKeyEvent {event->
        val target=when {
            event.type!=KeyEventType.KeyDown->-1
            event.key==Key.DirectionUp && index==0->count-1
            event.key==Key.DirectionDown && index==count-1->0
            else->-1
        }
        if(target<0)false else {
            if(target!=index && jump?.isActive!=true)jump=scope.launch {
                state.scrollToItem(itemOffset+target)
                focus[target].requestWhenReady()
            }
            true
        }
    }
}

@Composable
internal fun rememberLazyMenuLoop(count:Int,state:LazyListState,itemOffset:Int=0):LazyMenuLoop {
    val scope=rememberCoroutineScope()
    return remember(count,state,itemOffset) {LazyMenuLoop(count,state,scope,itemOffset)}
}
