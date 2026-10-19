package io.github.jahn2007.edifierx.util;

import android.os.*;
import java.util.concurrent.*;

public final class MainThread {
    private MainThread(){}
    public static boolean runAndWait(Runnable action){
        if(Looper.myLooper()==Looper.getMainLooper()){action.run();return true;}
        FutureTask<Void> task=new FutureTask<>(action,null);
        Handler handler=new Handler(Looper.getMainLooper());
        if(!handler.post(task))return false;
        try{task.get(3,TimeUnit.SECONDS);return true;}
        catch(InterruptedException e){Thread.currentThread().interrupt();task.cancel(false);handler.removeCallbacks(task);return false;}
        catch(ExecutionException|TimeoutException e){task.cancel(false);handler.removeCallbacks(task);return false;}
    }
    public static boolean awaitStopped(ExecutorService worker){
        if(worker.isTerminated())return true;
        if(Looper.myLooper()==Looper.getMainLooper())return false;
        try{return worker.awaitTermination(2,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
    }
}
