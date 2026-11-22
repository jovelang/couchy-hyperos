// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.conreo.couchytv.data.LauncherConfig

/** One action per press; releasing a hold never also fires its tap action. */
class RemoteKeyRouter(private val config: () -> LauncherConfig, private val action: (Int) -> Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var held=-1
    private var longFired=false
    private var shortAction=0
    private var longAction=0
    private val hold=Runnable { if(held!=-1 && !longFired) {longFired=true;android.util.Log.i("CouchyKeys","hold action=$longAction");action(longAction)} }
    fun handle(event:KeyEvent):Boolean {
        if(event.keyCode!=KeyEvent.KEYCODE_MENU && event.keyCode!=KeyEvent.KEYCODE_TV_INPUT) return false
        android.util.Log.i("CouchyKeys","code=${event.keyCode} event=${event.action} flags=${event.flags} held=$held")
        if(event.action==KeyEvent.ACTION_DOWN) {
            if(held!=event.keyCode) {
                cancel();held=event.keyCode
                val c=config();val menu=event.keyCode==KeyEvent.KEYCODE_MENU
                shortAction=if(menu)c.menuTapAction else c.sourceTapAction
                longAction=if(menu)c.menuHoldAction else c.sourceHoldAction
                handler.postDelayed(hold,550)
            }
            if(event.isLongPress || event.repeatCount>0) hold.run()
            return true
        }
        if(event.action==KeyEvent.ACTION_UP) {
            handler.removeCallbacks(hold)
            if(held==event.keyCode && !longFired && !(event.isCanceled && !(android.os.Build.MANUFACTURER.equals("Xiaomi",true) && event.flags and 0x7000 == 0x7000))) {android.util.Log.i("CouchyKeys","tap action=$shortAction");action(shortAction)}
            held=-1;return true
        }
        return true
    }
    fun isTracking(keyCode:Int) = held==keyCode
    fun cancel() { handler.removeCallbacks(hold);held=-1;longFired=false }
}
