package io.github.jahn2007.edifierx.core;

import java.util.*;

/** Bounded diagnostics. Device names and raw advertising data are never passed to this sink. */
public final class DiagnosticBuffer {
    private final ArrayDeque<String> lines=new ArrayDeque<>();
    private int characters;
    public static String clean(String text){
        if(text==null)return "";
        String s=text.substring(0,Math.min(text.length(),8192))
            .replaceAll("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])","[address]")
            .replaceAll("(?i)\\b[0-9a-f]{12}\\b","[identifier]")
            .replaceAll("(?i)\\b(password|token|secret|link[_ ]?key|irk|account[_ ]?key)\\s*[:=]\\s*\\S+","$1=[redacted]")
            .replaceAll("[\\r\\n\\p{Cntrl}]"," ");
        return s.substring(0,Math.min(s.length(),2048));
    }
    public synchronized String append(long time,String component,String message){
        String line=time+" "+clean(component)+" "+clean(message);
        lines.addLast(line);characters+=line.length();
        while(lines.size()>512||characters>262144)characters-=lines.removeFirst().length();
        return line;
    }
    public synchronized String text(){return String.join("\n",lines);}
    public synchronized void clear(){lines.clear();characters=0;}
    public synchronized int size(){return lines.size();}
}
