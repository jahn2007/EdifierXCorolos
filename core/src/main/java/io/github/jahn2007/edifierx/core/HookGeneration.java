package io.github.jahn2007.edifierx.core;

import java.util.*;
import java.util.function.*;

/** Tracks API-neutral handles so reload replacement/rollback can be tested on the JVM.
 * In-flight old calls are handled by the framework; callers must separately quiesce resources. */
public final class HookGeneration<H> {
    public interface Operations<H> { String id(H handle); void unhook(H handle); }
    private final Operations<H> operations;
    private final List<H> old=new ArrayList<>();
    private final List<H> current=new ArrayList<>();
    public HookGeneration(Collection<H> previous,Operations<H> operations){this.operations=operations;old.addAll(previous);}
    public synchronized H bind(String id,Predicate<H> sameExecutable,Function<H,H> replace,Supplier<H> create){
        Objects.requireNonNull(id);H candidate=null;
        for(H handle:old)if(id.equals(operations.id(handle))&&sameExecutable.test(handle)){candidate=handle;break;}
        H result;
        if(candidate==null)result=create.get();
        else {
            try{result=replace.apply(candidate);old.remove(candidate);}
            catch(RuntimeException|Error failure){operations.unhook(candidate);old.remove(candidate);result=create.get();}
        }
        current.add(result);return result;
    }
    public synchronized void finish(){removeAll(old);}
    public synchronized void rollback(){
        RuntimeException error=null;
        try{removeAll(current);}catch(RuntimeException e){error=e;}
        try{removeAll(old);}catch(RuntimeException e){if(error==null)error=e;else error.addSuppressed(e);}
        if(error!=null)throw error;
    }
    private void removeAll(List<H> handles){
        RuntimeException error=null;
        for(var it=handles.iterator();it.hasNext();){H h=it.next();try{operations.unhook(h);it.remove();}catch(RuntimeException|Error e){if(error==null)error=new IllegalStateException("Hook cleanup failed");error.addSuppressed(e);}}
        if(error!=null)throw error;
    }
}
