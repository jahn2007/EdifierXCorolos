package io.github.jahn2007.edifierx.hooks.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import io.github.libxposed.api.XposedModule;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import io.github.jahn2007.edifierx.xposed.HookSites;
import io.github.jahn2007.edifierx.util.MainThread;
import java.lang.reflect.*;
import java.util.*;

public final class HostInstaller {
    private final XposedModule module;
    private final HookSites hooks;
    private final Map<Object,NativePreferencePanel> panels=new IdentityHashMap<>();
    private final Set<NativePreferencePanel> retired=Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile boolean stopping;
    public HostInstaller(XposedModule module,HookSites hooks){this.module=module;this.hooks=hooks;}
    private boolean enabled(){try{return !stopping&&module.getRemotePreferences("config").getBoolean("enabled",false);}catch(Throwable t){return false;}}
    private void diagnostic(String code){if(stopping)return;try{if(module.getRemotePreferences("config").getBoolean("logs",false))module.log(android.util.Log.INFO,"EdifierX",code);}catch(Throwable ignored){}}
    public void install(ClassLoader loader)throws ReflectiveOperationException{
        Class<?> fragment=ColorOs15Compat.find(loader,"androidx.fragment.app.Fragment");
        if(fragment==null){diagnostic("HOST_ANDROIDX_FRAGMENT_ABSENT");return;}
        for(String lifecycle:List.of("onResume","onPause","onDestroyView")){
            Method method=fragment.getDeclaredMethod(lifecycle);
            if(method.getReturnType()!=void.class)continue;
            hooks.bind("edifierx.host.fragment."+lifecycle,method,chain->{Object result=chain.proceed();try{
                if(stopping)return result;
                Object owner=chain.getThisObject();
                if("onResume".equals(lifecycle)){if(enabled())attach(owner);}
                else {var panel=panels.remove(owner);if(panel!=null){panel.close();retired.add(panel);}prune();}
            }catch(Throwable t){diagnostic("HOST_LIFECYCLE_SIGNATURE_MISMATCH");}return result;});
        }
    }
    private void prune(){retired.removeIf(panel->{if(!panel.isStopped())return false;panel.close();return true;});}
    private void attach(Object fragment)throws ReflectiveOperationException{
        if(stopping||panels.containsKey(fragment))return;
        prune();if(panels.size()+retired.size()>=8){diagnostic("HOST_PANEL_WORKER_LIMIT");return;}
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
    /** Restore already-visible screens: package/fragment lifecycle callbacks are not replayed.
     * Optional ActivityThread reflection is bounded; failure falls back to the next real resume. */
    public void restoreActivePages(){
        if(!MainThread.runAndWait(()->{
            if(!enabled())return;
            try{
                Class<?> thread=Class.forName("android.app.ActivityThread");
                Object current=thread.getDeclaredMethod("currentActivityThread").invoke(null);
                Field records=thread.getDeclaredField("mActivities");records.setAccessible(true);
                if(!Map.class.isAssignableFrom(records.getType()))return;
                Object value=records.get(current);if(!(value instanceof Map<?,?> map)||map.size()>32)return;
                int[] remaining={64};Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                for(Object record:map.values()){
                    Field field=record.getClass().getDeclaredField("activity");field.setAccessible(true);
                    Object a=field.get(record);
                    if(a instanceof Activity activity&&!activity.isFinishing()&&!activity.isDestroyed())try{walk(ColorOs15Compat.call(activity,"getSupportFragmentManager"),remaining,seen,0);}catch(ReflectiveOperationException ignored){}
                }
            }catch(ReflectiveOperationException|RuntimeException e){diagnostic("HOT_RELOAD_REOPEN_PAGE_TO_ATTACH");}
        }))diagnostic("HOT_RELOAD_UI_RESTORE_DEFERRED");
    }
    private void walk(Object manager,int[] remaining,Set<Object> seen,int depth)throws ReflectiveOperationException{
        if(stopping||depth>4||remaining[0]<=0)return;
        Object value=ColorOs15Compat.call(manager,"getFragments");if(!(value instanceof List<?> list))return;
        for(Object fragment:list){if(fragment==null||remaining[0]--<=0||!seen.add(fragment))continue;
            if(Boolean.TRUE.equals(ColorOs15Compat.call(fragment,"isResumed")))attach(fragment);
            walk(ColorOs15Compat.call(fragment,"getChildFragmentManager"),remaining,seen,depth+1);
        }
    }
    public boolean stopAndAwait(){
        stopping=true;List<NativePreferencePanel> closing=new ArrayList<>();
        if(!MainThread.runAndWait(()->{for(var panel:panels.values()){panel.close();retired.add(panel);}panels.clear();closing.addAll(retired);}))return false;
        boolean stopped=true;for(var panel:closing)stopped=panel.awaitStopped()&stopped;
        // A final main-thread barrier removes posts made just before worker termination.
        boolean ui=MainThread.runAndWait(()->{for(var panel:closing)panel.close();prune();});
        return stopped&&ui&&closing.stream().allMatch(NativePreferencePanel::isStopped);
    }
}
