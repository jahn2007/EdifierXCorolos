package io.github.jahn2007.edifierx.hooks.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import io.github.libxposed.api.XposedModule;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import java.lang.reflect.*;
import java.util.*;

public final class HostInstaller {
    private final XposedModule module;
    private final Map<Object,NativePreferencePanel> panels=new IdentityHashMap<>();
    public HostInstaller(XposedModule module){this.module=module;}
    private boolean enabled(){try{return module.getRemotePreferences("config").getBoolean("enabled",false);}catch(Throwable t){return false;}}
    private void diagnostic(String code){try{if(module.getRemotePreferences("config").getBoolean("logs",false))module.log(android.util.Log.INFO,"EdifierX",code);}catch(Throwable ignored){}}
    public void install(ClassLoader loader)throws ReflectiveOperationException{
        // Stable Fragment base methods cover subclass overrides that call super; each callback
        // additionally requires a device-address argument and a native Bluetooth screen anchor.
        Class<?> fragment=ColorOs15Compat.find(loader,"androidx.fragment.app.Fragment");
        if(fragment==null){diagnostic("HOST_ANDROIDX_FRAGMENT_ABSENT");return;}
        for(String lifecycle:List.of("onResume","onPause","onDestroyView")){
            Method method=fragment.getDeclaredMethod(lifecycle);
            if(method.getReturnType()!=void.class)continue;
            module.hook(method).intercept(chain->{Object result=chain.proceed();try{
                Object owner=chain.getThisObject();
                if("onResume".equals(lifecycle)){if(enabled())attach(owner);}
                else {var panel=panels.remove(owner);if(panel!=null)panel.close();}
            }catch(Throwable t){diagnostic("HOST_LIFECYCLE_SIGNATURE_MISMATCH");}return result;});
        }
    }
    private void attach(Object fragment)throws ReflectiveOperationException{
        if(panels.containsKey(fragment))return;
        Object screen;
        try{screen=ColorOs15Compat.call(fragment,"getPreferenceScreen");}catch(NoSuchMethodException e){return;}
        if(screen==null||!ColorOs15Compat.anchor(screen))return;
        Bundle args=(Bundle)ColorOs15Compat.call(fragment,"getArguments");String address=ColorOs15Compat.address(args);
        if(address.isEmpty()){
            Object activity=ColorOs15Compat.call(fragment,"getActivity");
            if(activity instanceof Activity a)address=ColorOs15Compat.address(a.getIntent().getExtras());
        }
        if(address.isEmpty()){diagnostic("HOST_DEVICE_ADDRESS_NOT_FOUND");return;}
        Context context=(Context)ColorOs15Compat.call(fragment,"getContext");if(context==null)return;
        panels.put(fragment,new NativePreferencePanel(context,screen,address,this::enabled,this::diagnostic));
    }
}
