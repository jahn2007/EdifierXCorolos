package io.github.jahn2007.edifierx.core;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** All methods except submit/accept/close run on one event loop. Timeout poisons the channel:
 * no later response can satisfy a subsequent command with the same opcode. */
public final class CommandQueue implements AutoCloseable {
    private record Task(int response, byte[] bytes, CompletableFuture<WireCodec.Frame> result) {}
    private final ScheduledExecutorService loop;
    private final Transport transport;
    private final Duration timeout;
    private final Consumer<WireCodec.Frame> telemetry;
    private final Runnable poison;
    private final ArrayDeque<Task> queue=new ArrayDeque<>();
    private Task flight;
    private ScheduledFuture<?> timer;
    private boolean closed;
    public CommandQueue(ScheduledExecutorService loop,Transport transport,Duration timeout,Consumer<WireCodec.Frame> telemetry,Runnable poison) {
        this.loop=loop;this.transport=transport;this.timeout=timeout;this.telemetry=telemetry;this.poison=poison;
    }
    public CompletableFuture<WireCodec.Frame> submit(int response,byte[] bytes) {
        CompletableFuture<WireCodec.Frame> out=new CompletableFuture<>();
        loop.execute(()->{
            if(closed||queue.size()>=8) {out.completeExceptionally(new RejectedExecutionException("channel closed or queue full"));return;}
            queue.add(new Task(response,bytes.clone(),out));pump();
        });return out;
    }
    private void pump() {
        if(closed||flight!=null||queue.isEmpty())return;
        flight=queue.remove();Task task=flight;
        timer=loop.schedule(()->fail(new TimeoutException("command response timeout")),timeout.toMillis(),TimeUnit.MILLISECONDS);
        try {transport.send(task.bytes).whenComplete((ok,error)->{if(error!=null)loop.execute(()->fail(error));});}
        catch(Throwable t){fail(t);}
    }
    public void accept(WireCodec.Frame frame) {
        loop.execute(()->{
            if(closed)return;
            telemetry.accept(frame);
            if(flight!=null&&flight.response==frame.command()) {
                Task done=flight;flight=null;timer.cancel(false);done.result.complete(frame);pump();
            }
        });
    }
    private void fail(Throwable t) { closeOnLoop(t);poison.run(); }
    private void closeOnLoop(Throwable t) {
        if(closed)return;closed=true;if(timer!=null)timer.cancel(false);
        transport.close();
        if(flight!=null){flight.result.completeExceptionally(t);flight=null;}
        while(!queue.isEmpty())queue.remove().result.completeExceptionally(t);
    }
    @Override public void close() {loop.execute(()->closeOnLoop(new CancellationException("device session closed")));}
}
