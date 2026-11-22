// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv;

import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

/** Fixed system-service entry: open only our power-permission-protected idle screen. */
@android.annotation.SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi"})
public final class SaverBridge {
    public static void main(String[] args) {
        try {
            boolean probe=args.length==1 && args[0].equals("probe");
            if(Process.myUid()!=Process.SYSTEM_UID || (args.length!=0 && !probe))throw new SecurityException();
            Looper.prepareMainLooper();
            Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            java.lang.reflect.Method start=Class.forName("android.app.IActivityTaskManager").getMethod("startActivityAsUser",
                Class.forName("android.app.IApplicationThread"),String.class,String.class,Intent.class,String.class,
                android.os.IBinder.class,String.class,int.class,int.class,Class.forName("android.app.ProfilerInfo"),android.os.Bundle.class,int.class);
            if(probe) {Log.i("CouchySaverBridge","system activity service ready");System.exit(0);}
            Intent intent=new Intent().setClassName(AppIdentity.PACKAGE,"com.conreo.couchytv.ScreenSaverActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
            // This standalone system process has no registered ApplicationThread.
            // Use a null caller, the real system package, and an explicit user.
            int result=(Integer)start.invoke(service,null,"android",null,intent,null,null,null,0,0,null,null,0);
            if(result<0)throw new IllegalStateException("Activity start result="+result);
            Log.i("CouchySaverBridge","protected saver started; result="+result);
        } catch(Throwable e) {Log.e("CouchySaverBridge","cannot open idle screen",e);}
        System.exit(0);
    }
}
