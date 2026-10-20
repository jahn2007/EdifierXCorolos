package io.github.jahn2007.edifierx.hooks.official;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import android.os.*;
import io.github.libxposed.api.XposedModule;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.core.OfficialIdentityPolicy;
import io.github.jahn2007.edifierx.diagnostics.Diagnostics;
import io.github.jahn2007.edifierx.util.MainThread;
import io.github.jahn2007.edifierx.xposed.HookSites;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Passive observer for the actual 8.5.3 D8 receive path. Does not send or change a command. */
@SuppressLint("MissingPermission")
public final class OfficialIdentityInstaller {
    private static final String PREFIX="com.edifier.product_connect.";
    private final XposedModule module;
    private final HookSites hooks;
    private volatile boolean stopped;
    private long lastCapture=-1;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"edifier-official-identity");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
    public OfficialIdentityInstaller(XposedModule module,HookSites hooks){this.module=module;this.hooks=hooks;}
    private boolean enabled(){try{return !stopped&&module.getRemotePreferences("config").getBoolean("enabled",false)&&module.getRemotePreferences("config").getBoolean("officialIdentity",false);}catch(Throwable e){return false;}}
    private static Object call(Object owner,String name)throws ReflectiveOperationException{return owner.getClass().getMethod(name).invoke(owner);}
    private static Object singleton(ClassLoader loader,String name,String getter)throws ReflectiveOperationException{
        Class<?> type=Class.forName(PREFIX+name,false,loader);Object companion=type.getField("Companion").get(null);
        return companion.getClass().getMethod(getter).invoke(companion);
    }
    public void install(ClassLoader loader)throws ReflectiveOperationException{
        if(!enabled())return;
        Class<?> manager=Class.forName(PREFIX+"connect.ConnectManager",false,loader);
        Class<?> command=Class.forName(PREFIX+"command.CommandBean",false,loader);
        Method received=manager.getDeclaredMethod("receiveDataFromDevice",command);
        if(received.getReturnType()!=void.class||!Modifier.isPublic(received.getModifiers()))throw new NoSuchMethodException("Official receive signature changed");
        Method opcode=command.getMethod("getCommandIndex");if(opcode.getReturnType()!=int.class)throw new NoSuchMethodException("Official opcode signature changed");
        hooks.bind("edifierx.official.d8.identity",received,chain->{
            Object result=chain.proceed();
            try{if(((Number)opcode.invoke(chain.getArgs().get(0))).intValue()==0xD8&&enabled())capture(loader,chain.getThisObject(),chain.getArgs().get(0),command);}catch(Throwable error){report(null,"OFFICIAL_CAPTURE_FAILED",error.getClass().getSimpleName()+" "+error.getMessage());}
            return result;
        });
        report(null,"OFFICIAL_OBSERVER_INSTALLED","8.5.3 receiveDataFromDevice; passive D8 only");
    }
    private void capture(ClassLoader loader,Object manager,Object command,Class<?> commandType)throws ReflectiveOperationException{
        if(((Number)call(command,"getCommandIndex")).intValue()!=0xD8)return;
        Context context=(Context)call(manager,"getContext");if(context==null)return;
        try{var version=context.getPackageManager().getPackageInfo(OfficialIdentityPolicy.PACKAGE,0);
            if(!"8.5.3".equals(version.versionName)||version.getLongVersionCode()!=300){report(context,"OFFICIAL_VERSION_UNSUPPORTED",String.valueOf(version.versionName));return;}
        }catch(android.content.pm.PackageManager.NameNotFoundException e){return;}
        int header=((Number)call(command,"getHeader")).intValue();byte[] payload=(byte[])call(command,"getPayload");
        if((header!=0xBB&&header!=0xCC)||((Number)call(command,"getAppCode")).intValue()!=0xEC||payload.length<3||payload.length>250)return;
        Object codec=singleton(loader,"command.CommandManager","getInstance");
        if(!Boolean.TRUE.equals(codec.getClass().getMethod("checkCRC",commandType).invoke(codec,command)))return;
        if(!(call(manager,"getCurrentConnectType") instanceof Enum<?> transport)||!transport.name().equals("SPP"))return;
        Object spp=singleton(loader,"connect.spp.SPPManager","getGetInstance");
        Field socketField=spp.getClass().getDeclaredField("bluetoothSocket");
        if(socketField.getType()!=BluetoothSocket.class)return;socketField.setAccessible(true);
        if(!(socketField.get(spp) instanceof BluetoothSocket socket)||!socket.isConnected())return;
        if(!(call(manager,"getConnectedA2dpDevice") instanceof BluetoothDevice device))return;
        String address=device.getAddress();if(!address.equals(socket.getRemoteDevice().getAddress()))return;
        Object productManager=singleton(loader,"product.ProductManager","getInstance");Object product=call(productManager,"getConnectedProduct");if(product==null)return;
        String productUuid=(String)call(product,"getProductSearchUuid");int version=((Number)call(codec,"getBleVersion")).intValue(),encryption=((Number)call(codec,"getEncryption")).intValue();
        var profile=OfficialIdentityPolicy.profile(productUuid,version,encryption);
        if(profile.isEmpty()){report(context,"OFFICIAL_PROFILE_UNSUPPORTED","version="+version+" selector="+encryption);return;}
        synchronized(this){long now=SystemClock.elapsedRealtime();if(lastCapture>=0&&now-lastCapture<2000)return;lastCapture=now;}
        Bundle evidence=new Bundle();evidence.putString("address",address);evidence.putString("productUuid",productUuid);evidence.putInt("version",version);evidence.putInt("encryption",encryption);
        evidence.putInt("d8Length",payload.length);evidence.putString("source","VERIFIED_D8");
        // No payload, socket handle, device display name, account, or pairing material crosses IPC.
        worker.execute(()->{if(!enabled())return;try{
            Bundle reply=context.getContentResolver().call(ControlProvider.URI,"official_identity",null,evidence);
            report(context,"OFFICIAL_IDENTITY_RESULT",reply!=null&&reply.getBoolean("accepted")?"accepted profile="+profile.get().id():"not accepted");
        }catch(Throwable e){report(context,"OFFICIAL_IDENTITY_IPC_FAILED",e.getClass().getSimpleName()+" "+e.getMessage());}});
    }
    private void report(Context context,String code,String detail){
        try{module.log(android.util.Log.INFO,"EdifierX",code+" "+Diagnostics.clean(detail));}catch(Throwable ignored){}
        if(stopped)return;
        worker.execute(()->{try{
            Context app=context;
            if(app==null)app=(Context)Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
            if(app==null)return;
            Bundle args=new Bundle();args.putString("code",code);args.putString("detail",Diagnostics.clean(detail));
            app.getContentResolver().call(ControlProvider.URI,"official_diagnostic",null,args);
        }catch(Throwable ignored){}});
    }
    public boolean stopAndAwait(){stopped=true;worker.shutdownNow();return MainThread.awaitStopped(worker);}
}
