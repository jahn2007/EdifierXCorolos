package io.github.jahn2007.edifierx.hooks;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;

/** Bounded structural checks. Candidate names are evidence from reference hosts, not a claim
 * that the same classes exist on PHK110 ColorOS 15. */
public final class ColorOs15Compat {
    public static boolean platform(){return Build.VERSION.SDK_INT==35&&Set.of("oneplus","oppo","realme").contains(Build.BRAND.toLowerCase(Locale.ROOT));}
    public static Class<?> find(ClassLoader loader,String name){try{return Class.forName(name,false,loader);}catch(ClassNotFoundException|LinkageError e){return null;}}
    public static Object call(Object object,String name,Class<?>[] types,Object...args)throws ReflectiveOperationException{return object.getClass().getMethod(name,types).invoke(object,args);}
    public static Object call(Object object,String name)throws ReflectiveOperationException{return call(object,name,new Class<?>[0]);}
    public static String address(Bundle args){
        if(args==null)return "";
        for(String key:List.of("device","android.bluetooth.device.extra.DEVICE")){
            Object v=args.get(key);if(v instanceof BluetoothDevice d)return d.getAddress();
        }
        for(String key:List.of("device_address","deviceAddress","bluetooth_device_address","address")){
            Object v=args.get(key);if(v instanceof String text&&android.bluetooth.BluetoothAdapter.checkBluetoothAddress(text))return text;
        }return "";
    }
    public static boolean anchor(Object screen){
        for(String key:List.of("bluetooth_device_header","bt_rename","rename_device","pref_noise_switch","pref_more_setting","EqualizerItem"))try{
            if(call(screen,"findPreference",new Class<?>[]{CharSequence.class},key)!=null)return true;
        }catch(ReflectiveOperationException ignored){}return false;
    }
}
