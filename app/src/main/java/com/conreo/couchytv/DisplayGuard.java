// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv;

import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;

/** Fixed display-only helper, started by the TV's own system service. No command interpreter.
 * Restores the panel on socket EOF, timeout, or parent-process death. */
// This entry point runs under app_process/system UID, not the app's target-SDK VM.
@android.annotation.SuppressLint({"BlockedPrivateApi", "PrivateApi", "SoonBlockedPrivateApi"})
public final class DisplayGuard {
    private static Object power() throws Exception {
        Class<?> sm=Class.forName("android.os.ServiceManager");
        IBinder b=(IBinder)sm.getMethod("getService",String.class).invoke(null,"power");
        return Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
    }
    private static boolean interactive() throws Exception {
        return (Boolean)Class.forName("android.os.IPowerManager").getMethod("isInteractive").invoke(power());
    }
    private static void sleep() throws Exception {
        Class.forName("android.os.IPowerManager").getMethod("goToSleep",long.class,int.class,int.class)
            .invoke(power(),SystemClock.uptimeMillis(),0,0);
    }
    public static void main(String[] args) {
        Object display=null;Method setPower=null;boolean restore=false;
        try {
            // Android 14 moved the physical-display methods into services.jar.
            // Initialization follows scrcpy's Apache-2.0 DisplayControl wrapper.
            Class<?> factory=Class.forName("com.android.internal.os.ClassLoaderFactory");
            String cp=System.getenv("SYSTEMSERVERCLASSPATH");if(cp==null)cp="/system/framework/services.jar";
            ClassLoader loader=(ClassLoader)factory.getDeclaredMethod("createClassLoader",String.class,String.class,String.class,ClassLoader.class,int.class,boolean.class,String.class)
                .invoke(null,cp,null,null,ClassLoader.getSystemClassLoader(),0,true,null);
            Class<?> control=loader.loadClass("com.android.server.display.DisplayControl");
            Method load=Runtime.class.getDeclaredMethod("loadLibrary0",Class.class,String.class);load.setAccessible(true);load.invoke(Runtime.getRuntime(),control,"android_servers");
            long[] ids=(long[])control.getMethod("getPhysicalDisplayIds").invoke(null);
            if(ids.length==0)throw new IllegalStateException("No physical display");
            display=control.getMethod("getPhysicalDisplayToken",long.class).invoke(null,ids[0]);
            setPower=Class.forName("android.view.SurfaceControl").getMethod("setDisplayPowerMode",IBinder.class,int.class);
            if(args.length==1 && args[0].equals("probe")){Log.i("CouchyDisplay","probe ready; physical="+ids[0]);System.exit(0);}
            if(args.length!=2 || !args[1].matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
            int port=Integer.parseInt(args[0]);if(port<1024 || port>65535)throw new IllegalArgumentException();
            try(Socket socket=new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1",port),3000);
                socket.setSoTimeout(15000);
                socket.getOutputStream().write((args[1]+"\n").getBytes("UTF-8"));socket.getOutputStream().flush();
                if(socket.getInputStream().read()!='A')throw new SecurityException("Parent did not authenticate");
                restore=true;setPower.invoke(null,display,0);
                socket.getOutputStream().write('R');socket.getOutputStream().flush();
                Log.i("CouchyDisplay","panel off; CPU interactive="+interactive());
                while(true) {
                    int command=socket.getInputStream().read();
                    if(command=='H')continue;
                    if(command=='S') {sleep();restore=interactive();Log.i("CouchyDisplay","standby; interactive="+restore);}
                    break;
                }
            }
        } catch(Throwable e) {Log.e("CouchyDisplay","display guard failed",e);}
        finally {
            try {if(!interactive())restore=false;}catch(Throwable ignored) {}
            if(restore && display!=null && setPower!=null)try {setPower.invoke(null,display,2);Log.i("CouchyDisplay","panel restored");}catch(Throwable e){Log.e("CouchyDisplay","restore failed",e);}
        }
        System.exit(0);
    }
}
