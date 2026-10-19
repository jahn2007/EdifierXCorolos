package io.github.jahn2007.edifierx.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public class SessionTest {
    static final class Fake implements Transport {
        Consumer<byte[]> receiver;Consumer<Throwable> failure;final List<byte[]> sent=new CopyOnWriteArrayList<>();volatile boolean closed;
        public CompletionStage<Void> connect(Consumer<byte[]> r,Consumer<Throwable> f){receiver=r;failure=f;return CompletableFuture.completedFuture(null);}
        public CompletionStage<Void> send(byte[] b){sent.add(b.clone());return CompletableFuture.completedFuture(null);}
        public void close(){closed=true;}
    }
    static void await(BooleanSupplier condition)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(5);assertTrue("condition timed out",condition.getAsBoolean());}
    @Test public void commandQueueSerializesAndCorrelates()throws Exception {
        var loop=Executors.newSingleThreadScheduledExecutor();var t=new Fake();var q=new CommandQueue(loop,t,Duration.ofSeconds(2),f->{},()->{});
        try{var first=q.submit(0xCC,new byte[]{1});var second=q.submit(0xF2,new byte[]{2});await(()->t.sent.size()==1);q.accept(new WireCodec.Frame(0x08,new byte[]{1}));assertFalse(first.isDone());q.accept(new WireCodec.Frame(0xCC,new byte[]{1}));first.get(1,TimeUnit.SECONDS);await(()->t.sent.size()==2);q.accept(new WireCodec.Frame(0xF2,new byte[]{2}));second.get(1,TimeUnit.SECONDS);}finally{q.close();loop.shutdown();}
    }
    @Test public void timeoutPoisonsChannelAndCancelsQueuedCommand()throws Exception {
        var loop=Executors.newSingleThreadScheduledExecutor();var t=new Fake();var poison=new AtomicBoolean();var q=new CommandQueue(loop,t,Duration.ofMillis(40),f->{},()->poison.set(true));
        try{var one=q.submit(1,new byte[]{1});var two=q.submit(1,new byte[]{2});assertThrows(ExecutionException.class,()->one.get(2,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->two.get(2,TimeUnit.SECONDS));await(poison::get);assertTrue(t.closed);assertEquals(1,t.sent.size());}finally{q.close();loop.shutdown();}
    }
    @Test public void queueIsBounded()throws Exception {
        var loop=Executors.newSingleThreadScheduledExecutor();var t=new Fake();var q=new CommandQueue(loop,t,Duration.ofSeconds(2),f->{},()->{});
        try{List<CompletableFuture<WireCodec.Frame>> out=new ArrayList<>();for(int i=0;i<25;i++)out.add(q.submit(1,new byte[]{1}));await(()->out.stream().filter(CompletableFuture::isCompletedExceptionally).count()>0);assertEquals(1,t.sent.size());}finally{q.close();loop.shutdown();}
    }
    @Test public void staleCommandsRejectedAndDisconnectClearsBattery()throws Exception {
        var c=new EarbudsController();var t=new Fake();try{c.connect("A",DeviceProfile.find("evo-pro").orElseThrow(),t);await(()->!t.sent.isEmpty());
            t.receiver.accept(HexFormat.of().parseHex("BBECF20006A6C1C7A5A6B4CC"));await(()->t.sent.size()==2);t.receiver.accept(HexFormat.of().parseHex("BBECCC0002BEA3D6"));await(()->c.state().phase()==DeviceState.Phase.READY);
            assertEquals(98,c.state().battery().systemLevel());assertThrows(ExecutionException.class,()->c.setNoiseMode("B",c.state().generation(),"ANC").get(1,TimeUnit.SECONDS));
            c.disconnect();await(()->c.state().phase()==DeviceState.Phase.DISCONNECTED);assertEquals(Battery.UNKNOWN,c.state().battery());assertFalse(c.state().identified());assertTrue(t.closed);
        }finally{c.close();}
    }
    @Test public void oldSocketCannotPolluteNewDeviceAndReconnectWorks()throws Exception {
        var c=new EarbudsController();var old=new Fake();var replacement=new Fake();try{
            c.connect("A",DeviceProfile.find("evo-pro").orElseThrow(),old);await(()->!old.sent.isEmpty());
            c.connect("B",DeviceProfile.find("evo-pro").orElseThrow(),replacement);await(()->!replacement.sent.isEmpty());old.receiver.accept(HexFormat.of().parseHex("BBECF20006A6C1C7A5A6B4CC"));
            assertEquals(-1,c.state().battery().systemLevel());assertEquals("B",c.state().address());assertTrue(old.closed);
            replacement.receiver.accept(HexFormat.of().parseHex("BBECF20006A6C1C7A5A6B4CC"));await(()->replacement.sent.size()==2);replacement.receiver.accept(HexFormat.of().parseHex("BBECCC0002BEA3D6"));await(()->c.state().phase()==DeviceState.Phase.READY);assertEquals("B",c.state().address());
        }finally{c.close();}
    }
    @Test public void wrongAncSlotNeverUnlocksWrites()throws Exception {
        var c=new EarbudsController();var t=new Fake();try{
            c.connect("A",DeviceProfile.find("evo-pro").orElseThrow(),t);await(()->!t.sent.isEmpty());
            t.receiver.accept(HexFormat.of().parseHex("BBECF20006A6C1C7A5A6B4CC"));await(()->t.sent.size()==2);
            assertFalse(c.state().identified());
            t.receiver.accept(HexFormat.of().parseHex("BBECCC0002B5A0CA"));
            await(()->c.state().phase()==DeviceState.Phase.ERROR);assertFalse(c.state().identified());assertEquals(Battery.UNKNOWN,c.state().battery());
            assertThrows(ExecutionException.class,()->c.setNoiseMode("A",c.state().generation(),"ANC").get(1,TimeUnit.SECONDS));
        }finally{c.close();}
    }
    @Test public void writeRequiresAckAndReadbackNotOptimisticState()throws Exception {
        var c=new EarbudsController();var t=new Fake();try{
            c.connect("A",DeviceProfile.find("evo-pro").orElseThrow(),t);await(()->!t.sent.isEmpty());t.receiver.accept(HexFormat.of().parseHex("BBECF20006A6C1C7A5A6B4CC"));await(()->t.sent.size()==2);t.receiver.accept(HexFormat.of().parseHex("BBECCC0002BEA3D6"));await(()->c.state().phase()==DeviceState.Phase.READY);
            var result=c.setNoiseMode("A",c.state().generation(),"ANC");await(()->t.sent.size()==3);assertEquals("OFF",c.state().noise());assertFalse(result.isDone());
            assertThrows(ExecutionException.class,()->c.setNoiseMode("A",c.state().generation(),"AMBIENT").get(1,TimeUnit.SECONDS));
            // Synthetic ACK/state vectors built from documented frame format, not local hardware capture.
            byte[] ack=WireCodec.request(WireCodec.Dialect.BES,0xC1,new byte[]{0x1B,1},true);ack[0]=(byte)0xBB;ack[ack.length-1]+=0x11;t.receiver.accept(ack);
            await(()->t.sent.size()==4);byte[] read=WireCodec.request(WireCodec.Dialect.BES,0xCC,new byte[]{0x1B,1},true);read[0]=(byte)0xBB;read[read.length-1]+=0x11;t.receiver.accept(read);result.get(1,TimeUnit.SECONDS);assertEquals("ANC",c.state().noise());
        }finally{c.close();}
    }
}
