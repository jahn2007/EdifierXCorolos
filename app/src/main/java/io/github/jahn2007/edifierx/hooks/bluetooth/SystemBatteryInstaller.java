package io.github.jahn2007.edifierx.hooks.bluetooth;

import android.app.Application;
import android.bluetooth.*;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.concurrent.*;
import io.github.libxposed.api.XposedModule;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import io.github.jahn2007.edifierx.xposed.HookSites;
import io.github.jahn2007.edifierx.util.MainThread;

/** Optional single-level bridge with a generation-owned, stoppable worker. */
public final class SystemBatteryInstaller {
    private final XposedModule module;
    private final HookSites hooks;
    private volatile boolean stopping;
    private ScheduledExecutorService worker;
    private Application app;
    private Method setter,getter,battery;
    private String owned="";
    private int last=-1;
    private boolean blocked;
    public SystemBatteryInstaller(XposedModule module,HookSites hooks){this.module=module;this.hooks=hooks;}
    public void install(ClassLoader loader,Application current)throws ReflectiveOperationException{
        if(!module.getRemotePreferences("config").getBoolean("battery",false))return;
        Class<?> adapter=ColorOs15Compat.find(loader,"com.android.bluetooth.btservice.AdapterService");if(adapter==null)return;
        setter=adapter.getDeclaredMethod("setBatteryLevel",BluetoothDevice.class,int.class,boolean.class);
        if(setter.getReturnType()!=void.class)return;setter.setAccessible(true);
        getter=adapter.getDeclaredMethod("getAdapterService");if(!Modifier.isStatic(getter.getModifiers())||getter.getReturnType()!=adapter)return;getter.setAccessible(true);
        battery=BluetoothDevice.class.getDeclaredMethod("getBatteryLevel");battery.setAccessible(true);
        hooks.bind("edifierx.battery.application",android.app.Instrumentation.class.getMethod("callApplicationOnCreate",Application.class),chain->{Object result=chain.proceed();try{start((Application)chain.getArgs().get(0));}catch(Throwable ignored){}return result;});
        if(current!=null)start(current);
    }
    private synchronized void start(Application application){
        if(stopping||worker!=null)return;app=application;
        worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"edifier-battery");t.setDaemon(true);return t;});
        worker.scheduleWithFixedDelay(this::tick,1,10,TimeUnit.SECONDS);
    }
    private void tick(){
        if(stopping)return;
        try{
            boolean enabled=module.getRemotePreferences("config").getBoolean("enabled",false)&&module.getRemotePreferences("config").getBoolean("battery",false);
            Bundle s=app.getContentResolver().call(ControlProvider.URI,"snapshot","",null);Object service=getter.invoke(null);if(service==null||stopping)return;
            boolean valid=enabled&&s!=null&&s.getBoolean("identified")&&s.getBoolean("service")&&"READY".equals(s.getString("phase"))&&s.getInt("systemBattery",-1)>=0;
            String address=valid?s.getString("address",""):"";
            BluetoothAdapter bt=app.getSystemService(BluetoothManager.class).getAdapter();if(bt==null)return;
            if(!owned.isEmpty()&&!owned.equals(address))clearOwned();
            if(!valid||!BluetoothAdapter.checkBluetoothAddress(address)||stopping)return;
            BluetoothDevice d=bt.getRemoteDevice(address);int standard=(int)battery.invoke(d);
            if(!owned.equals(address)){blocked=standard>=0;owned=address;last=-1;}
            if(blocked)return;
            if(standard>=0&&standard!=last){blocked=true;return;}
            int level=s.getInt("systemBattery");if(level!=last&&!stopping){setter.invoke(service,d,level,false);last=level;}
        }catch(Throwable ignored){/* Keep native battery behavior on unavailable vendor APIs. */}
    }
    private void clearOwned()throws ReflectiveOperationException{
        if(app!=null&&!owned.isEmpty()&&last>=0){
            BluetoothAdapter bt=app.getSystemService(BluetoothManager.class).getAdapter();Object service=getter.invoke(null);
            if(bt!=null&&service!=null){BluetoothDevice old=bt.getRemoteDevice(owned);if((int)battery.invoke(old)==last)setter.invoke(service,old,-1,false);}
        }
        owned="";last=-1;blocked=false;
    }
    public boolean stopAndAwait(){
        ScheduledExecutorService existing;
        synchronized(this){stopping=true;existing=worker;if(existing!=null)existing.shutdownNow();}
        if(existing!=null&&!MainThread.awaitStopped(existing))return false;
        try{clearOwned();app=null;return true;}catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
}
