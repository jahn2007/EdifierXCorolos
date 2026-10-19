package io.github.jahn2007.edifierx.xposed;

import android.app.Application;
import android.os.Bundle;
import io.github.libxposed.api.*;
import io.github.libxposed.api.XposedModuleInterface.*;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import io.github.jahn2007.edifierx.hooks.settings.HostInstaller;
import io.github.jahn2007.edifierx.hooks.bluetooth.SystemBatteryInstaller;
import io.github.jahn2007.edifierx.bridge.CallerPolicy;
import io.github.jahn2007.edifierx.core.HookGeneration;
import java.util.*;

public final class ModuleEntry extends XposedModule {
    private String hostPackage;
    private int generation=1;
    private volatile boolean quiescing;
    private HostInstaller host;
    private SystemBatteryInstaller battery;
    private HookGeneration<XposedInterface.HookHandle> handles=registry(List.of());
    private HookGeneration<XposedInterface.HookHandle> registry(List<XposedInterface.HookHandle> previous){
        return new HookGeneration<>(previous,new HookGeneration.Operations<>(){
            public String id(XposedInterface.HookHandle h){return h.getId();}
            public void unhook(XposedInterface.HookHandle h){h.unhook();}
        });
    }
    private void bind(String id,java.lang.reflect.Method method,XposedInterface.Hooker interceptor){
        XposedInterface.Hooker guarded=chain->quiescing?chain.proceed():interceptor.intercept(chain);
        handles.bind(id,h->method.equals(h.getExecutable()),h->h.replaceHook(guarded),()->hook(method).setId(id).intercept(guarded));
    }
    @Override public void onPackageReady(PackageReadyParam param){
        String pkg=param.getPackageName();
        if(hostPackage!=null||!ColorOs15Compat.platform()||!param.isFirstPackage()||!CallerPolicy.HOSTS.contains(pkg))return;
        hostPackage=pkg;
        try{install(param.getClassLoader(),null);}
        catch(Throwable t){cleanup();try{handles.rollback();}catch(RuntimeException e){report("HOST_UNHOOK_FAILED_RESTART_REQUIRED");}report("HOST_UNSUPPORTED "+t.getClass().getSimpleName());}
    }
    private void install(ClassLoader loader,Application application)throws ReflectiveOperationException{
        if(hostPackage.equals("com.android.bluetooth")){
            battery=new SystemBatteryInstaller(this,this::bind);battery.install(loader,application);
        }else{
            host=new HostInstaller(this,this::bind);host.install(loader);
            if(application!=null)host.restoreActivePages();
        }
    }
    private static Application application(){
        try{return (Application)Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);}
        catch(ReflectiveOperationException|LinkageError e){return null;}
    }
    @Override public boolean onHotReloading(HotReloadingParam param){
        Application app=application();
        if(hostPackage==null||app==null||!hostPackage.equals(app.getPackageName())||!CallerPolicy.HOSTS.contains(hostPackage))return false;
        Bundle state=new Bundle();state.putInt("schema",1);state.putString("package",hostPackage);state.putInt("generation",generation+1);
        try{param.setSavedInstanceState(state);}catch(RuntimeException e){report("HOT_RELOAD_SAVE_FAILED");return false;}
        boolean stopped=cleanup();
        report(stopped?"HOT_RELOAD_PREPARED":"HOT_RELOAD_CLEANUP_PENDING_RETRY_OR_RESTART");
        return stopped;
    }
    @Override public void onHotReloaded(HotReloadedParam param){
        handles=registry(param.getOldHookHandles());Application app=application();
        try{
            if(!(param.getSavedInstanceState() instanceof Bundle state)||state.getInt("schema")!=1||app==null)throw new IllegalStateException("Reload state/application unavailable");
            hostPackage=state.getString("package");generation=state.getInt("generation",1);
            if(!ColorOs15Compat.platform()||!CallerPolicy.HOSTS.contains(hostPackage)||!app.getPackageName().equals(hostPackage))throw new IllegalStateException("Reload host mismatch");
            install(app.getClassLoader(),app);handles.finish();
            report("HOT_RELOAD_SUCCEEDED generation="+generation);
        }catch(Throwable failure){
            cleanup();
            try{handles.rollback();}catch(RuntimeException e){failure.addSuppressed(e);}
            throw new IllegalStateException("Hot reload restore failed; restart the affected host",failure);
        }
    }
    private boolean cleanup(){
        quiescing=true;boolean stopped=true;
        try{if(host!=null)stopped=host.stopAndAwait()&stopped;}catch(RuntimeException e){stopped=false;}
        try{if(battery!=null)stopped=battery.stopAndAwait()&stopped;}catch(RuntimeException e){stopped=false;}
        return stopped;
    }
    private void report(String code){try{log(android.util.Log.INFO,"EdifierX",code);}catch(Throwable ignored){}}
}
