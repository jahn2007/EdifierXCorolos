package io.github.jahn2007.edifierx.diagnostics;
import android.content.Context;
import io.github.jahn2007.edifierx.core.DiagnosticBuffer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class Diagnostics {
    private static final DiagnosticBuffer EVENTS=new DiagnosticBuffer();
    private static final AtomicInteger DROPPED=new AtomicInteger();
    private static final ThreadPoolExecutor WRITER=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"edifier-log");t.setDaemon(true);return t;},(r,e)->DROPPED.incrementAndGet());
    private static volatile boolean enabled;
    private static volatile Path file;
    public static void init(Context context){file=context.getFilesDir().toPath().resolve("diagnostics.log");enabled=context.getSharedPreferences("local",0).getBoolean("logs",false);}
    public static void enabled(boolean value){enabled=value;}
    public static boolean enabled(){return enabled;}
    public static void event(String component,String code){
        if(!enabled)return;
        String line=EVENTS.append(System.currentTimeMillis(),component,code);
        WRITER.execute(()->{try{Path p=file;if(p==null)return;if(Files.exists(p)&&Files.size(p)>262144)Files.move(p,p.resolveSibling("diagnostics.previous.log"),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(p,line+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(IOException e){DROPPED.incrementAndGet();}});
    }
    public static void failure(String component,String stage,Throwable error){
        StringBuilder b=new StringBuilder(stage);Throwable t=error;
        for(int cause=0;t!=null&&cause<4;cause++,t=t.getCause()){
            b.append(" | ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            StackTraceElement[] stack=t.getStackTrace();for(int i=0;i<Math.min(stack.length,8);i++)b.append(" <- ").append(stack[i]);
        }event(component,b.toString());
    }
    public static String clean(String text){return DiagnosticBuffer.clean(text);}
    public static String summary(){return "详细采集="+enabled+" 内存事件="+EVENTS.size()+" 丢弃/IO失败="+DROPPED.get()+"；关闭只停止采集，清空按钮才删除日志";}
    public static String report(){return EVENTS.text();}
    /** Called from the export worker, never a system/UI thread. */
    public static String export(){
        CompletableFuture<String> result=new CompletableFuture<>();
        WRITER.execute(()->{try{StringBuilder b=new StringBuilder();Path p=file;
            if(p!=null)for(Path part:new Path[]{p.resolveSibling("diagnostics.previous.log"),p})if(Files.exists(part))b.append(Files.readString(part)).append('\n');
            result.complete(b.toString());
        }catch(IOException e){result.completeExceptionally(e);}});
        try{return summary()+"\n"+result.get(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return summary()+"\nEXPORT_INTERRUPTED\n"+EVENTS.text();}
        catch(ExecutionException|TimeoutException e){return summary()+"\nDISK_LOG_UNAVAILABLE; MEMORY_FALLBACK\n"+EVENTS.text();}
    }
    public static void clear(){EVENTS.clear();DROPPED.set(0);WRITER.execute(()->{try{Path p=file;if(p!=null){Files.deleteIfExists(p);Files.deleteIfExists(p.resolveSibling("diagnostics.previous.log"));}}catch(IOException e){DROPPED.incrementAndGet();}});}
}
