package io.github.jahn2007.edifierx.hooks.settings;

import android.content.Context;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import io.github.jahn2007.edifierx.core.DeviceProfile;

/** Host-owned AndroidX/COUI preferences: no module Activity masquerading as native UI. */
public final class NativePreferencePanel implements AutoCloseable {
    private final Context context;
    private final Object screen;
    private final String address;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"edifier-host-ui");t.setDaemon(true);return t;});
    private Object category,summary,noise,game;
    private Class<?> preferenceType;
    private volatile boolean closed;
    private long generation;
    private volatile boolean enabled;
    private final java.util.function.BooleanSupplier safety;
    private final java.util.function.Consumer<String> failure;
    public NativePreferencePanel(Context context,Object screen,String address,java.util.function.BooleanSupplier safety,java.util.function.Consumer<String> failure){this.context=context;this.screen=screen;this.address=address;this.safety=safety;this.failure=failure;worker.scheduleWithFixedDelay(this::fetch,0,2,TimeUnit.SECONDS);}
    private void fetch(){
        if(closed)return;
        try {if(!safety.getAsBoolean()){main.post(this::close);return;}
            Bundle b=context.getContentResolver().call(ControlProvider.URI,"snapshot",address,null);
            if(b!=null)main.post(()->{if(closed)return;try{render(b);}catch(Throwable t){failure.accept("PREFERENCE_SIGNATURE_MISMATCH");close();}});
        }catch(Throwable t){failure.accept("IPC_UNAVAILABLE");main.post(this::close);}
    }
    private Object create(String simple)throws ReflectiveOperationException{
        ClassLoader loader=screen.getClass().getClassLoader();
        Class<?> type=ColorOs15Compat.find(loader,"com.coui.appcompat.preference.COUI"+simple);
        if(type==null)type=Class.forName("androidx.preference."+simple,false,loader);
        return type.getConstructor(Context.class).newInstance(context);
    }
    private void set(Object object,String name,Class<?> type,Object value)throws ReflectiveOperationException{ColorOs15Compat.call(object,name,new Class<?>[]{type},value);}
    private void add(Object parent,Object child)throws ReflectiveOperationException{ColorOs15Compat.call(parent,"addPreference",new Class<?>[]{preferenceType},child);}
    private void render(Bundle b)throws ReflectiveOperationException{
        if(!address.equals(b.getString("address"))||!b.getBoolean("identified")||!b.getBoolean("enabled")||!b.getBoolean("service")){if(category!=null)remove();return;}
        var profile=DeviceProfile.find(b.getString("profile","")).orElse(null);if(profile==null)return;
        generation=b.getLong("generation");enabled="READY".equals(b.getString("phase"));
        if(category==null){
            preferenceType=Class.forName("androidx.preference.Preference",false,screen.getClass().getClassLoader());
            category=create("PreferenceCategory");set(category,"setKey",String.class,"edifierx.controls");set(category,"setTitle",CharSequence.class,"EdifierXCorolos · "+profile.model());
            summary=create("Preference");set(summary,"setSelectable",boolean.class,false);set(summary,"setIcon",int.class,android.R.drawable.ic_lock_silent_mode_off);add(category,summary);
            String[] modes=b.getStringArray("modes");
            if(modes!=null&&modes.length>0){noise=create("ListPreference");set(noise,"setPersistent",boolean.class,false);set(noise,"setTitle",CharSequence.class,"降噪控制");set(noise,"setKey",String.class,"edifierx.noise");
                CharSequence[] entries=Arrays.stream(modes).map(NativePreferencePanel::label).toArray(CharSequence[]::new);
                set(noise,"setEntries",CharSequence[].class,entries);set(noise,"setEntryValues",CharSequence[].class,modes);listen(noise,(value)->command("noise","mode",String.valueOf(value)));add(category,noise);
            }
            if(b.getBoolean("supportsGame")){game=create("SwitchPreference");set(game,"setPersistent",boolean.class,false);set(game,"setTitle",CharSequence.class,"耳机游戏低延迟模式");listen(game,value->command("game","enabled",value));add(category,game);}
            add(screen,category);
        }
        String battery=b.getInt("aggregate",-1)>=0?"整机 "+b.getInt("aggregate")+"%":"左 "+percent(b.getInt("left",-1))+" · 右 "+percent(b.getInt("right",-1))+" · 盒 "+percent(b.getInt("case",-1));
        set(summary,"setTitle",CharSequence.class,profile.model());set(summary,"setSummary",CharSequence.class,b.getString("phase")+" · "+battery+(b.getString("error","").isEmpty()?"":" · "+b.getString("error")));
        if(noise!=null){String value=b.getString("noise","UNKNOWN");set(noise,"setValue",String.class,value);set(noise,"setSummary",CharSequence.class,label(value));set(noise,"setEnabled",boolean.class,enabled&&!"UNKNOWN".equals(value));}
        if(game!=null){set(game,"setChecked",boolean.class,b.getBoolean("game"));set(game,"setEnabled",boolean.class,enabled&&b.containsKey("game"));}
    }
    private void listen(Object preference,java.util.function.Consumer<Object> action)throws ReflectiveOperationException{
        Class<?> listener=Class.forName("androidx.preference.Preference$OnPreferenceChangeListener",false,preference.getClass().getClassLoader());
        Object proxy=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(p,m,args)->{
            if(m.getDeclaringClass()==Object.class)return switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==args[0];default->"EdifierPreferenceListener";};
            if("onPreferenceChange".equals(m.getName())&&enabled&&safety.getAsBoolean())action.accept(args[1]);return false;
        });set(preference,"setOnPreferenceChangeListener",listener,proxy);
    }
    private void command(String method,String key,Object value){
        if(closed||!enabled)return;enabled=false;long token=generation;
        worker.execute(()->{try{Bundle args=new Bundle();args.putLong("generation",token);if(value instanceof Boolean on)args.putBoolean(key,on);else args.putString(key,String.valueOf(value));context.getContentResolver().call(ControlProvider.URI,method,address,args);}catch(Throwable t){failure.accept("CONTROL_IPC_FAILED");}});
    }
    private static String percent(int p){return p<0?"未知":p+"%";}
    private static String label(String key){return switch(key){case "ANC"->"主动降噪";case "WIND"->"抗风噪";case "AMBIENT"->"通透";case "OFF"->"关闭";case "COMFORT"->"舒适降噪";case "LIGHT"->"轻度降噪";default->"未知";};}
    private void remove(){try{ColorOs15Compat.call(screen,"removePreference",new Class<?>[]{preferenceType},category);}catch(ReflectiveOperationException ignored){}category=null;summary=null;noise=null;game=null;}
    public void close(){if(closed)return;closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);if(category!=null)remove();}
}
