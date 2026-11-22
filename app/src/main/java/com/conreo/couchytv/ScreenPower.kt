// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.content.Context
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** TV service access is confined to fixed commands and a parent-checked display guard. */
object ScreenPower {
    private val token=android.os.Binder()
    private val suppressedOwners=java.util.Collections.synchronizedSet(mutableSetOf<android.os.IBinder>())
    private val io=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var connection:Socket?=null
    @Volatile private var waitingServer:ServerSocket?=null
    @Volatile private var pendingConnection:Socket?=null
    private val requestLock=Any()
    private var generation=0
    val dark=MutableStateFlow(false)
    @Volatile var lastError=""
    @Volatile var suppressed=false
    val integrated=kotlinx.coroutines.flow.MutableStateFlow(false)
    private val modeLock=kotlinx.coroutines.sync.Mutex()
    private const val SYSTEM_SAVER="com.xiaomi.mitv.hyper.screensaver/.MainActivity"
    private fun backend()=Class.forName("mitv.impl.util.TvStateGc")
    fun implementation()=runCatching {val c=Class.forName("mitv.powermanagement.ScreenSaverManager");c.getMethod("getInstance").invoke(null).javaClass.name}.getOrDefault("unavailable")
    fun available()=runCatching {backend();true}.getOrDefault(false)
    private fun fixedCommand(command:String):Boolean {
        val c=backend();c.getMethod("getScreenSaverTimeout").invoke(null)
        val f=c.getDeclaredField("mTvService").apply {isAccessible=true}
        val service=requireNotNull(f.get(null))
        return Class.forName("mitv.internal.ITvService").getMethod("runSystemCommand",String::class.java).invoke(service,command) as Boolean
    }
    private fun helperCommand(ctx:Context,args:String):Boolean {
        val apk=ctx.applicationInfo.sourceDir
        require(apk.matches(Regex("[a-zA-Z0-9/_~.+=-]+")))
        return fixedCommand("/system/bin/env CLASSPATH=$apk /system/bin/app_process /system/bin com.conreo.couchytv.DisplayGuard $args")
    }
    fun commandProbe():Boolean=runCatching {fixedCommand("/system/bin/log -t CouchyPowerProbe authorized-fixed-command-probe")}.getOrElse {lastError=it.cause?.message ?: it.toString();false}
    fun displayProbe(ctx:Context)=runCatching {helperCommand(ctx,"probe")}.getOrElse {lastError=it.cause?.message ?: it.toString();false}
    fun screenOn(context:Context):Boolean=(!dark.value && context.getSystemService(android.os.PowerManager::class.java).isInteractive)
    /** Retain Xiaomi idle/playback detection, replacing only its visual activity. */
    suspend fun configureSystemSaver(context:Context,own:Boolean,minutes:Int)=withContext(Dispatchers.IO) {
        modeLock.lock()
        try {
            val ctx=context.applicationContext
            val prefs=ctx.getSharedPreferences("system-saver-owner",Context.MODE_PRIVATE)
            val component=android.content.ComponentName.unflattenFromString(SYSTEM_SAVER)!!
            val pm=ctx.packageManager
            fun timeout()=android.provider.Settings.System.getLong(ctx.contentResolver,"screen_saver_timeout",120000L)
            fun setTimeout(value:Long) {backend().getMethod("setScreenSaverTimeoutMillis",Long::class.javaPrimitiveType).invoke(null,value)}
            fun change(state:Int):Boolean {
                if(pm.getComponentEnabledSetting(component)==state)return true
                val verb=when(state){0->"default-state";1->"enable";else->"disable"}
                if(!fixedCommand("/system/bin/pm $verb --user 0 $SYSTEM_SAVER"))return false
                repeat(25) {if(pm.getComponentEnabledSetting(component)==state)return true;Thread.sleep(80)}
                return false
            }
            if(own) {
                pm.getActivityInfo(component,android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
                if(!prefs.getBoolean("saved",false))prefs.edit().putBoolean("saved",true)
                    .putInt("component",pm.getComponentEnabledSetting(component)).putLong("timeout",timeout()).commit()
                val desired=if(minutes>0)minutes*60000L else -1L
                check(change(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED)){"无法切换系统屏保入口"}
                if(timeout()!=desired)setTimeout(desired)
                check(timeout()==desired){"无法设置系统闲置时间"}
                integrated.value=true
            } else {
                if(prefs.getBoolean("saved",false)) {
                    check(change(prefs.getInt("component",0))){"无法恢复系统屏保入口"}
                    setTimeout(prefs.getLong("timeout",120000L));prefs.edit().clear().commit()
                }
                integrated.value=false
            }
        } catch(e:Exception) {
            integrated.value=false;lastError=e.cause?.message ?: e.toString()
            // Never strand the device without a saver when integration is unavailable.
            runCatching {
                val prefs=context.getSharedPreferences("system-saver-owner",Context.MODE_PRIVATE)
                if(prefs.getBoolean("saved",false)) {
                    val verb=if(prefs.getInt("component",0)==0)"default-state"else"enable"
                    fixedCommand("/system/bin/pm $verb --user 0 $SYSTEM_SAVER")
                    backend().getMethod("setScreenSaverTimeoutMillis",Long::class.javaPrimitiveType).invoke(null,prefs.getLong("timeout",120000L))
                }
            }
            android.util.Log.w("CouchySaver","system integration failed",e)
        }
        finally {modeLock.unlock()}
    }
    fun launchSystemIdleSaver(context:Context,probe:Boolean=false):Boolean=runCatching {
        require(context.packageName==AppIdentity.PACKAGE)
        val apk=context.applicationInfo.sourceDir
        require(apk.matches(Regex("[a-zA-Z0-9/_~.+=-]+")))
        fixedCommand("/system/bin/env CLASSPATH=$apk /system/bin/app_process /system/bin com.conreo.couchytv.SaverBridge"+if(probe)" probe"else"")
    }.getOrElse {lastError=it.toString();false}
    fun suppressSystem(context:Context,suppress:Boolean,owner:android.os.IBinder=token):Boolean=runCatching {
        backend().getMethod("setScreenSaverEnabled",android.os.IBinder::class.java,Boolean::class.javaPrimitiveType).invoke(null,owner,!suppress)
        if(suppress)suppressedOwners.add(owner)else suppressedOwners.remove(owner)
        suppressed=suppressedOwners.isNotEmpty();true
    }.getOrElse {lastError=it.cause?.message ?: it.message ?: "不可用";false}
    suspend fun pictureOff(context:Context):Boolean {
        if(!context.getSystemService(android.os.PowerManager::class.java).isInteractive || !(LauncherVisibility.resumed || LauncherVisibility.saverResumed)) {lastError="请先唤醒电视并打开屏保";return false}
        val request=synchronized(requestLock) {
            if(!com.conreo.couchytv.ui.ScreenSaverRuntime.active.value) {lastError="屏保已退出，本次熄屏已取消";return false}
            ++generation
        }
        withContext(Dispatchers.Main) {WallpaperLaunchGate.prepare()}
        val result=withContext(Dispatchers.IO) {
        if(dark.value)return@withContext true
        val bytes=ByteArray(32).also {java.security.SecureRandom().nextBytes(it)}
        val nonce=bytes.joinToString("") {"%02x".format(it)}
        var accepted:Socket?=null
        val server=ServerSocket(0,3,InetAddress.getByName("127.0.0.1")).apply {soTimeout=4000};waitingServer=server
        try {
            synchronized(requestLock) {check(request==generation && com.conreo.couchytv.ui.ScreenSaverRuntime.active.value){"Screen-off request cancelled"}}
            check(helperCommand(context,"${server.localPort} $nonce"))
            accepted=server.accept().apply {soTimeout=4000}
            synchronized(requestLock) {check(request==generation && com.conreo.couchytv.ui.ScreenSaverRuntime.active.value){"Screen-off request cancelled"};pendingConnection=accepted}
            val auth=ByteArray(65)
            var read=0
            while(read<auth.size) {val n=accepted.inputStream.read(auth,read,auth.size-read);check(n>0);read+=n}
            check(String(auth,Charsets.UTF_8)==nonce+"\n") {"Wrong helper nonce"}
            accepted.outputStream.write('A'.code);accepted.outputStream.flush()
            check(accepted.inputStream.read()=='R'.code){"Display helper did not confirm"}
            synchronized(requestLock) {
                check(request==generation && com.conreo.couchytv.ui.ScreenSaverRuntime.active.value){"Screen-off request cancelled"}
                connection=accepted;pendingConnection=null;dark.value=true;lastError=""
                handler.post(heartbeat)
            }
            true
        } catch(e:Exception) {runCatching {accepted?.close()};lastError=e.cause?.message ?: e.toString();false}
        finally {
            runCatching {server.close()}
            synchronized(requestLock) {
                if(waitingServer===server)waitingServer=null
                if(pendingConnection===accepted)pendingConnection=null
            }
        }
        }
        if(!result)withContext(Dispatchers.Main) {WallpaperLaunchGate.failed()}
        return result
    }

    private val heartbeat=object:Runnable {
        override fun run() {
            val socket=connection ?: return
            io.launch {runCatching {socket.outputStream.write('H'.code);socket.outputStream.flush()}.onFailure {if(connection===socket)wakeDisplay()}}
            handler.postDelayed(this,4000)
        }
    }
    fun wakeDisplay() {
        handler.removeCallbacks(heartbeat)
        val state=synchronized(requestLock) {
            generation++
            val snapshot=Triple(connection,pendingConnection,waitingServer)
            connection=null;pendingConnection=null;waitingServer=null;dark.value=false
            snapshot
        }
        io.launch {
            runCatching {state.first?.outputStream?.write('W'.code);state.first?.close()}
            runCatching {state.second?.close()};runCatching {state.third?.close()}
        }
        if(state.first!=null || state.third!=null)handler.post {WallpaperLaunchGate.failed()}
    }
    fun standby():Boolean {
        val socket=connection ?: return false
        connection=null;dark.value=false;handler.removeCallbacks(heartbeat)
        io.launch {runCatching {socket.outputStream.write('S'.code);socket.outputStream.flush();socket.close()}.onFailure {lastError=it.toString()}}
        return true
    }
}
