package io.github.jahn2007.edifierx.hooks.bluetooth;

import android.app.Application;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import io.github.libxposed.api.XposedModule;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;

/** Optional Android single-level bridge. OPlus multi-component ABI is deliberately not
 * invoked before ColorOS 15 evidence. Standard battery always takes precedence. */
public final class SystemBatteryInstaller {
    public static void install(XposedModule module,ClassLoader loader)throws ReflectiveOperationException{
        if(!module.getRemotePreferences("config").getBoolean("battery",false))return;
        Class<?> adapter=ColorOs15Compat.find(loader,"com.android.bluetooth.btservice.AdapterService");if(adapter==null)return;
        Method setter=adapter.getDeclaredMethod("setBatteryLevel",BluetoothDevice.class,int.class,boolean.class);
        if(setter.getReturnType()!=void.class)return;setter.setAccessible(true);
        Method getter=adapter.getDeclaredMethod("getAdapterService");if(!Modifier.isStatic(getter.getModifiers())||getter.getReturnType()!=adapter)return;getter.setAccessible(true);
        Method battery=BluetoothDevice.class.getDeclaredMethod("getBatteryLevel");battery.setAccessible(true);
        module.hook(android.app.Instrumentation.class.getMethod("callApplicationOnCreate",Application.class)).intercept(chain->{Object result=chain.proceed();try{
            Application app=(Application)chain.getArgs().get(0);
            ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"edifier-battery");t.setDaemon(true);return t;});
            worker.scheduleWithFixedDelay(new Runnable(){String owned="";int last=-1;boolean blocked;
                public void run(){try{
                    boolean enabled=module.getRemotePreferences("config").getBoolean("enabled",false)&&module.getRemotePreferences("config").getBoolean("battery",false);
                    Bundle s=app.getContentResolver().call(ControlProvider.URI,"snapshot","",null);Object service=getter.invoke(null);if(service==null)return;
                    boolean valid=enabled&&s!=null&&s.getBoolean("identified")&&s.getBoolean("service")&&"READY".equals(s.getString("phase"))&&s.getInt("systemBattery",-1)>=0;
                    String address=valid?s.getString("address",""):"";
                    BluetoothAdapter bt=app.getSystemService(BluetoothManager.class).getAdapter();if(bt==null)return;
                    if(!owned.isEmpty()&&!owned.equals(address)){BluetoothDevice old=bt.getRemoteDevice(owned);if((int)battery.invoke(old)==last)setter.invoke(service,old,-1,false);owned="";last=-1;blocked=false;}
                    if(!valid||!BluetoothAdapter.checkBluetoothAddress(address))return;
                    BluetoothDevice d=bt.getRemoteDevice(address);int standard=(int)battery.invoke(d);
                    if(!owned.equals(address)){blocked=standard>=0;owned=address;last=-1;}
                    if(blocked)return;
                    if(standard>=0&&standard!=last){blocked=true;return;}
                    int level=s.getInt("systemBattery");if(level!=last){setter.invoke(service,d,level,false);last=level;}
                }catch(Throwable t){/* Fail closed: keep native battery behavior and bounded polling. */}}
            },1,10,TimeUnit.SECONDS);
        }catch(Throwable ignored){}return result;});
    }
}
