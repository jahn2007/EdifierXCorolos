package io.github.jahn2007.edifierx.diagnostics;

import android.content.Context;
import android.os.Bundle;
import io.github.libxposed.api.XposedModule;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.util.MainThread;
import java.util.concurrent.*;

/** Bounded async forwarding over the already UID-authenticated provider. No Binder I/O in UI hooks. */
public final class HostDiagnostics {
    private final XposedModule module;
    private volatile Context context;
    private volatile boolean stopped;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"edifier-host-log");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
    public HostDiagnostics(XposedModule module){this.module=module;}
    public void context(Context value){if(value!=null)context=value.getApplicationContext();}
    public void event(String code,String detail){
        if(stopped)return;
        try{if(!module.getRemotePreferences("config").getBoolean("logs",false))return;
            String safe=Diagnostics.clean(detail);module.log(android.util.Log.INFO,"EdifierX",code+" "+safe);
            worker.execute(()->{if(stopped)return;try{
                Context c=context;
                if(c==null)c=(Context)Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
                if(c==null)return;
                Bundle args=new Bundle();args.putString("code",code);args.putString("detail",safe);
                c.getContentResolver().call(ControlProvider.URI,"diagnostic",null,args);
            }catch(Throwable ignored){/* LSPosed copy remains available if the provider cannot be reached. */}});
        }catch(Throwable ignored){}
    }
    public boolean stopAndAwait(){stopped=true;worker.shutdownNow();boolean done=MainThread.awaitStopped(worker);if(done)context=null;return done;}
}
