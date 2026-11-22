// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.conreo.couchytv.data.*
import kotlinx.coroutines.*

/** Filters only the two requested remote keys. No window-content access. */
class RemoteKeyService:AccessibilityService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var config=LauncherConfig()
    private var foreground=""
    private val router=RemoteKeyRouter({config}) { action -> perform(action) }
    override fun onServiceConnected() { scope.launch { ConfigStore(this@RemoteKeyService).flow.collect {config=it} } }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) foreground=event.packageName?.toString().orEmpty()
    }
    override fun onKeyEvent(event:KeyEvent):Boolean {
        if(!config.globalRemoteKeys && !LauncherVisibility.resumed && !router.isTracking(event.keyCode)) return false
        return router.handle(event)
    }
    private fun perform(action:Int) {
        android.util.Log.i("CouchyRemote","action=$action foreground=$foreground")
        when(action) {
            2 -> Actions.openSystemSettings(this)
            in 4..11 -> scope.launch {
                runCatching { SourceController.perform(this@RemoteKeyService,action,config) }
                    .onSuccess { input -> if(input!=null) ConfigStore(this@RemoteKeyService).update {it.copy(lastInput=input.id)} }
                    .onFailure { Actions.toast(this@RemoteKeyService,"无法切换信号源") }
            }
            1,3 -> startActivity(Intent(this,MainActivity::class.java).setAction(com.conreo.couchytv.AppIdentity.REMOTE_ACTION)
                .putExtra("remote_action",action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
    override fun onInterrupt() {router.cancel()}
    override fun onDestroy() {router.cancel();scope.cancel();super.onDestroy()}
}
