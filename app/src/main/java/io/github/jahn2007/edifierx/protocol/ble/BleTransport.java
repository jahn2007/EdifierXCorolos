package io.github.jahn2007.edifierx.protocol.ble;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.Context;
import io.github.jahn2007.edifierx.core.Transport;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Explicit endpoint only; not selected for Evo Pro (whose evidenced route is RFCOMM).
 * GATT discovery -> notification CCCD -> ready; one write outstanding, max 20 bytes. */
@SuppressLint("MissingPermission")
public final class BleTransport implements Transport {
    public static final UUID SERVICE=UUID.fromString("48093801-1a48-11e9-ab14-d663bd873d93");
    public static final UUID RX=UUID.fromString("48090002-1a48-11e9-ab14-d663bd873d93");
    public static final UUID TX=UUID.fromString("48090001-1a48-11e9-ab14-d663bd873d93");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Context context; private final BluetoothDevice device;
    private BluetoothGatt gatt; private BluetoothGattCharacteristic rx;
    private CompletableFuture<Void> connecting,writing;
    private Consumer<byte[]> receiver; private Consumer<Throwable> failure;
    private boolean closed;
    public BleTransport(Context context,BluetoothDevice device){this.context=context.getApplicationContext();this.device=device;}
    public synchronized CompletionStage<Void> connect(Consumer<byte[]> receiver,Consumer<Throwable> failure){
        if(closed||connecting!=null)return CompletableFuture.failedFuture(new IllegalStateException("closed or already connecting"));
        this.receiver=receiver;this.failure=failure;connecting=new CompletableFuture<>();
        gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);return connecting;
    }
    private void fail(String reason){var e=new IllegalStateException(reason);if(connecting!=null)connecting.completeExceptionally(e);if(writing!=null)writing.completeExceptionally(e);if(failure!=null&&!closed)failure.accept(e);close();}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){synchronized(BleTransport.this){if(closed)return;if(status!=0||state==BluetoothProfile.STATE_DISCONNECTED){fail("GATT disconnected");return;}if(state==BluetoothProfile.STATE_CONNECTED&&!g.discoverServices())fail("service discovery rejected");}}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){synchronized(BleTransport.this){
            if(closed)return;BluetoothGattService s=g.getService(SERVICE);rx=s==null?null:s.getCharacteristic(RX);var tx=s==null?null:s.getCharacteristic(TX);
            if(status!=0||rx==null||tx==null||(rx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE)==0||(tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0){fail("endpoint mismatch");return;}
            var descriptor=tx.getDescriptor(CCCD);
            if(descriptor==null||!g.setCharacteristicNotification(tx,true)||g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)!=BluetoothStatusCodes.SUCCESS)fail("notification subscription rejected");
        }}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){synchronized(BleTransport.this){if(closed)return;if(!CCCD.equals(d.getUuid())||status!=0)fail("CCCD failed");else connecting.complete(null);}}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){synchronized(BleTransport.this){if(!closed&&TX.equals(c.getUuid()))receiver.accept(value.clone());}}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){synchronized(BleTransport.this){if(closed||writing==null)return;if(status==0)writing.complete(null);else writing.completeExceptionally(new IllegalStateException("GATT write failed"));writing=null;}}
    };
    public synchronized CompletionStage<Void> send(byte[] bytes){
        if(closed||connecting==null||!connecting.isDone()||connecting.isCompletedExceptionally()||writing!=null||bytes.length>20)return CompletableFuture.failedFuture(new IllegalStateException("GATT not ready or frame too large"));
        CompletableFuture<Void> out=new CompletableFuture<>();writing=out;
        if(gatt.writeCharacteristic(rx,bytes.clone(),BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)!=BluetoothStatusCodes.SUCCESS){writing=null;out.completeExceptionally(new IllegalStateException("write rejected"));}return out;
    }
    public synchronized void close(){if(closed)return;closed=true;if(connecting!=null)connecting.completeExceptionally(new CancellationException());if(writing!=null)writing.completeExceptionally(new CancellationException());if(gatt!=null){gatt.disconnect();gatt.close();gatt=null;}}
}
