package io.github.jahn2007.edifierx.hooks.settings;

import android.content.Context;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import io.github.jahn2007.edifierx.core.DeviceProfile;
import io.github.jahn2007.edifierx.util.MainThread;

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
    private volatile boolean detached=true;
    private long generation;
    private volatile boolean enabled;
    private String lastGate="";
    private final java.util.function.BooleanSupplier safety;
    private final java.util.function.BiConsumer<String,String> failure;
    public NativePreferencePanel(Context context,Object screen,String address,java.util.function.BooleanSupplier safety,java.util.function.BiConsumer<String,String> failure){this.context=context;this.screen=screen;this.address=address;this.safety=safety;this.failure=failure;worker.scheduleWithFixedDelay(this::fetch,0,2,TimeUnit.SECONDS);}
    private void fetch(){
        if(closed)return;
        try {if(!safety.getAsBoolean()){main.post(this::close);return;}
            Bundle b=context.getContentResolver().call(ControlProvider.URI,"snapshot",address,null);
            if(b==null)failure.accept("HOST_SNAPSHOT_NULL","");
            if(b!=null&&!closed)main.post(()->{if(closed)return;try{render(b);}catch(Throwable t){failure.accept("PREFERENCE_SIGNATURE_MISMATCH",errorDetail(t));close();}});
        }catch(Throwable t){if(!closed){failure.accept("IPC_UNAVAILABLE",errorDetail(t));main.post(this::close);}}
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
        String gate=!address.equals(b.getString("address"))?"HOST_WAITING_SELECTED_DEVICE":!b.getBoolean("enabled")?"HOST_CONTROL_DISABLED":!b.getBoolean("service")?"HOST_WAITING_SERVICE":!b.getBoolean("identified")?"HOST_WAITING_PROTOCOL_IDENTITY":"HOST_IDENTITY_GATE_PASSED";
        if(!gate.equals(lastGate)){lastGate=gate;failure.accept(gate,"phase="+b.getString("phase","")+" profile="+b.getString("profile","")+" error="+b.getString("error",""));}
        if(!gate.equals("HOST_IDENTITY_GATE_PASSED")){if(category!=null)remove();return;}
        var profile=DeviceProfile.find(b.getString("profile","")).orElse(null);if(profile==null)return;
        generation=b.getLong("generation");enabled="READY".equals(b.getString("phase"));
        if(category==null){
            preferenceType=Class.forName("androidx.preference.Preference",false,screen.getClass().getClassLoader());
            detached=false;category=create("PreferenceCategory");set(category,"setKey",String.class,"edifierx.controls");set(category,"setTitle",CharSequence.class,"EdifierXCorolos · "+profile.model());
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
        try{worker.execute(()->{if(closed)return;try{Bundle args=new Bundle();args.putLong("generation",token);if(value instanceof Boolean on)args.putBoolean(key,on);else args.putString(key,String.valueOf(value));context.getContentResolver().call(ControlProvider.URI,method,address,args);}catch(Throwable t){if(!closed)failure.accept("CONTROL_IPC_FAILED",errorDetail(t));}});}catch(RejectedExecutionException ignored){}
    }
    private static String percent(int p){return p<0?"未知":p+"%";}
    private static String label(String key){return switch(key){case "ANC"->"主动降噪";case "WIND"->"抗风噪";case "AMBIENT"->"通透";case "OFF"->"关闭";case "COMFORT"->"舒适降噪";case "LIGHT"->"轻度降噪";default->"未知";};}
    private static String errorDetail(Throwable error){StringBuilder b=new StringBuilder();for(int i=0;error!=null&&i<3;i++,error=error.getCause()){b.append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append("; ");for(var frame:java.util.Arrays.stream(error.getStackTrace()).limit(4).toList())b.append(frame).append(" <- ");}return b.toString();}
    private void clearListener(Object preference)throws ReflectiveOperationException{
        if(preference==null)return;
        Class<?> listener=Class.forName("androidx.preference.Preference$OnPreferenceChangeListener",false,preference.getClass().getClassLoader());
        ColorOs15Compat.call(preference,"setOnPreferenceChangeListener",new Class<?>[]{listener},new Object[]{null});
    }
    private void remove(){
        try{clearListener(noise);clearListener(game);ColorOs15Compat.call(screen,"removePreference",new Class<?>[]{preferenceType},category);category=null;summary=null;noise=null;game=null;detached=true;}
        catch(ReflectiveOperationException|RuntimeException e){detached=false;if(!closed)failure.accept("UI_DETACH_FAILED",errorDetail(e));}
    }
    public boolean isStopped(){return worker.isTerminated()&&detached;}
    public boolean awaitStopped(){return MainThread.awaitStopped(worker);}
    public void close(){closed=true;enabled=false;worker.shutdownNow();main.removeCallbacksAndMessages(null);if(category!=null)remove();else detached=true;}
}
