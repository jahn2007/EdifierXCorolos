package io.github.jahn2007.edifierx.diagnostics;
import java.util.*;
public final class Diagnostics {
    private static final ArrayDeque<String> EVENTS=new ArrayDeque<>();
    private static volatile boolean enabled;
    public static void enabled(boolean value){enabled=value;if(!value)synchronized(EVENTS){EVENTS.clear();}}
    public static void event(String component,String code){if(!enabled)return;synchronized(EVENTS){if(EVENTS.size()==128)EVENTS.removeFirst();EVENTS.addLast(System.currentTimeMillis()+" "+clean(component)+" "+clean(code));}}
    public static String clean(String text){String clean=text.replaceAll("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}","[address]").replaceAll("[\\r\\n]"," ");return clean.substring(0,Math.min(200,clean.length()));}
    public static String report(){synchronized(EVENTS){return String.join("\n",EVENTS);}}
}
