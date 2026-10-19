package io.github.jahn2007.edifierx.protocol.rfcomm;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import io.github.jahn2007.edifierx.core.Transport;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Public, authenticated SDP route only: no hidden channel-number fallback. */
@SuppressLint("MissingPermission")
public final class RfcommTransport implements Transport {
    private final BluetoothDevice device;
    private final UUID service;
    private final ExecutorService io=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"edifier-rfcomm");t.setDaemon(true);return t;});
    private volatile BluetoothSocket socket;
    private volatile boolean closed;
    public RfcommTransport(BluetoothDevice device,UUID service){this.device=device;this.service=service;}
    public CompletionStage<Void> connect(Consumer<byte[]> receiver,Consumer<Throwable> failure){
        return CompletableFuture.runAsync(()->{
            try {
                BluetoothSocket created=device.createRfcommSocketToServiceRecord(service);
                synchronized(this){if(closed){created.close();throw new IllegalStateException("closed");}socket=created;}
                created.connect();
                io.execute(()->{
                    byte[] b=new byte[1024];
                    try {while(!closed){int n=created.getInputStream().read(b);if(n<0)throw new java.io.EOFException();receiver.accept(Arrays.copyOf(b,n));}}
                    catch(Throwable e){if(!closed)failure.accept(e);}
                });
            }catch(Exception e){throw new CompletionException(e);}
        },io);
    }
    public CompletionStage<Void> send(byte[] bytes){return CompletableFuture.runAsync(()->{try{BluetoothSocket s=socket;if(s==null||closed)throw new IllegalStateException("closed");s.getOutputStream().write(bytes);s.getOutputStream().flush();}catch(Exception e){throw new CompletionException(e);}},io);}
    public synchronized void close(){closed=true;try{if(socket!=null)socket.close();}catch(java.io.IOException ignored){}io.shutdownNow();}
}
