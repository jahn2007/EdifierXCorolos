package io.github.jahn2007.edifierx.core;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static io.github.jahn2007.edifierx.core.DeviceState.Phase;

/** Single authoritative state source. Epoch guards isolate old sockets and pending callbacks. */
public final class EarbudsController implements AutoCloseable {
    private final ScheduledExecutorService loop=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"edifier-session");t.setDaemon(true);return t;});
    private final CopyOnWriteArrayList<Consumer<DeviceState>> listeners=new CopyOnWriteArrayList<>();
    private volatile DeviceState state=DeviceState.empty(0);
    private DeviceProfile profile;
    private Transport transport;
    private CommandQueue commands;
    private long epoch;
    private long lastWrite;
    private ScheduledFuture<?> connectionTimer;
    public DeviceState state(){return state;}
    public void addListener(Consumer<DeviceState> listener){listeners.add(listener);listener.accept(state);}
    public void removeListener(Consumer<DeviceState> listener){listeners.remove(listener);}
    private void emit(DeviceState next){state=next;for(var l:listeners)try{l.accept(next);}catch(RuntimeException ignored){}}
    private void phase(Phase next,String error){var s=state;emit(new DeviceState(s.generation(),s.address(),s.profileId(),next,s.battery(),s.noise(),s.game(),s.identified(),error));}
    public void connect(String address,DeviceProfile selected,Transport channel) {
        loop.execute(()->{
            release();long token=++epoch;profile=selected;transport=channel;
            emit(new DeviceState(token,address,selected.id(),Phase.CONNECTING,Battery.UNKNOWN,"UNKNOWN",null,false,""));
            WireCodec decoder=new WireCodec(selected.dialect());
            commands=new CommandQueue(loop,channel,Duration.ofSeconds(5),f->{if(token==epoch)onFrame(f);},()->{if(token==epoch)failed("PROTOCOL_TIMEOUT_OR_IO");});
            connectionTimer=loop.schedule(()->{if(token==epoch)failed("CONNECT_TIMEOUT");},12,TimeUnit.SECONDS);
            channel.connect(bytes->{if(token==state.generation())for(var f:decoder.offer(bytes))commandsFor(token,f);},e->loop.execute(()->{if(token==epoch)failed("TRANSPORT_DISCONNECTED");}))
                .whenComplete((ok,error)->loop.execute(()->{
                    if(token!=epoch)return;connectionTimer.cancel(false);
                    if(error!=null){failed("CONNECT_FAILED");return;}
                    phase(Phase.READING,"");refreshOnLoop();
                }));
        });
    }
    private void commandsFor(long token,WireCodec.Frame frame){loop.execute(()->{if(token==epoch&&commands!=null)commands.accept(frame);});}
    private void onFrame(WireCodec.Frame frame) {
        var s=state;Battery battery=Battery.parse(profile,frame);String noise=s.noise();Boolean game=s.game();
        byte[] data=profile.decode(frame.payload());
        if((frame.command()==0xCC||frame.command()==0xC1)&&!profile.readModes().isEmpty()) {
            int offset=profile.ancSlot()<0?0:1;
            if(data.length>offset&&(offset==0||(data[0]&255)==profile.ancSlot()))noise=profile.readModes().getOrDefault(data[offset]&255,noise);
        }
        if(profile.game()&&(frame.command()==0x08||frame.command()==0x09)&&data.length==1&&(data[0]&255)<=1)game=data[0]!=0;
        Battery nextBattery=battery==null?s.battery():battery;
        boolean identified=nextBattery.systemLevel()>=0&&(profile.readModes().isEmpty()||!noise.equals("UNKNOWN"));
        emit(new DeviceState(s.generation(),s.address(),s.profileId(),s.phase(),nextBattery,noise,game,identified,s.error()));
    }
    public void refreshState(){loop.execute(()->{if(commands!=null&&state.phase()==Phase.READY)refreshOnLoop();});}
    private void refreshOnLoop() {
        long token=epoch;phase(Phase.READING,"");
        CommandQueue active=commands;DeviceProfile selected=profile;
        CompletableFuture<?> f=active.submit(selected.batteryCommand(),selected.query(selected.batteryCommand())).thenApply(frame->{
            if(Battery.parse(selected,frame)==null)throw new CompletionException(new IllegalArgumentException("invalid battery response"));return frame;
        });
        if(!selected.readModes().isEmpty())f=f.thenCompose(x->active.submit(0xCC,selected.query(0xCC))).thenApply(frame->{
            byte[] b=selected.decode(frame.payload());int offset=selected.ancSlot()<0?0:1;
            if(b.length<=offset||(offset==1&&(b[0]&255)!=selected.ancSlot())||!selected.readModes().containsKey(b[offset]&255))throw new CompletionException(new IllegalArgumentException("ANC slot mismatch"));return frame;
        });
        if(selected.game())f=f.thenCompose(x->active.submit(0x08,selected.query(0x08))).thenApply(frame->{
            byte[] b=selected.decode(frame.payload());if(b.length!=1||(b[0]&255)>1)throw new CompletionException(new IllegalArgumentException("game response mismatch"));return frame;
        });
        f.whenComplete((ok,e)->loop.execute(()->{if(token!=epoch)return;if(e!=null)failed("READ_FAILED");else if(state.identified())phase(Phase.READY,"");else failed("PROFILE_RESPONSE_MISMATCH");}));
    }
    public CompletableFuture<Void> setNoiseMode(String expectedAddress,long expectedGeneration,String mode){return write(expectedAddress,expectedGeneration,mode,null);}
    public CompletableFuture<Void> setGameMode(String expectedAddress,long expectedGeneration,boolean enabled){return write(expectedAddress,expectedGeneration,null,enabled);}
    private CompletableFuture<Void> write(String address,long generation,String mode,Boolean game) {
        CompletableFuture<Void> result=new CompletableFuture<>();
        loop.execute(()->{
            if(commands==null||state.phase()!=Phase.READY||!state.identified()||!address.equals(state.address())||generation!=epoch) {result.completeExceptionally(new IllegalStateException("stale or unready device"));return;}
            long now=System.nanoTime();
            if(now-lastWrite<TimeUnit.MILLISECONDS.toNanos(2000)){result.completeExceptionally(new RejectedExecutionException("write cooldown"));return;}
            byte[] packet;
            try {packet=mode!=null?profile.noise(mode):profile.game(Boolean.TRUE.equals(game));}
            catch(IllegalArgumentException e){result.completeExceptionally(e);return;}
            lastWrite=now;long token=epoch;phase(Phase.WRITING,"");
            int response=mode!=null?0xC1:0x09;
            commands.submit(response,packet).whenComplete((ack,error)->loop.execute(()->{
                if(token!=epoch){result.completeExceptionally(new CancellationException("device changed"));return;}
                if(error!=null){failed("WRITE_ACK_FAILED");result.completeExceptionally(error);return;}
                // No optimistic state: query actual state after device voice-prompt cooldown.
                loop.schedule(()->{
                    if(token!=epoch){result.completeExceptionally(new CancellationException());return;}
                    int query=mode!=null?0xCC:0x08;
                    commands.submit(query,profile.query(query)).whenComplete((read,e)->loop.execute(()->{
                        if(token!=epoch){result.completeExceptionally(new CancellationException());return;}
                        if(e!=null){failed("WRITE_READBACK_FAILED");result.completeExceptionally(e);return;}
                        byte[] returned=profile.decode(read.payload());
                        int offset=profile.ancSlot()<0?0:1;
                        boolean valid=mode!=null
                            ?returned.length>offset&&(offset==0||(returned[0]&255)==profile.ancSlot())&&profile.readModes().containsKey(returned[offset]&255)
                            :returned.length==1&&(returned[0]&255)<=1;
                        if(!valid){failed("WRITE_READBACK_INVALID");result.completeExceptionally(new IllegalStateException("invalid readback payload"));return;}
                        boolean match=mode!=null?mode.equals(profile.readModes().get(returned[offset]&255)):game.equals(returned[0]!=0);
                        phase(Phase.READY,match?"":"DEVICE_REJECTED_VALUE");
                        if(match)result.complete(null);else result.completeExceptionally(new IllegalStateException("readback mismatch"));
                    }));
                },2000,TimeUnit.MILLISECONDS);
            }));
        });return result;
    }
    private void release(){if(connectionTimer!=null)connectionTimer.cancel(false);if(commands!=null)commands.close();if(transport!=null)transport.close();commands=null;transport=null;}
    private void failed(String reason){release();long token=++epoch;var s=state;emit(new DeviceState(token,s.address(),s.profileId(),Phase.ERROR,Battery.UNKNOWN,"UNKNOWN",null,false,reason));}
    public void disconnect(){loop.execute(()->{release();emit(DeviceState.empty(++epoch));});}
    @Override public void close(){loop.execute(()->{release();emit(DeviceState.empty(++epoch));listeners.clear();loop.schedule(loop::shutdown,0,TimeUnit.MILLISECONDS);});}
}
