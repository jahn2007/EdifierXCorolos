package io.github.jahn2007.edifierx.device;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import io.github.jahn2007.edifierx.core.AdvertisementIdentity;
import io.github.jahn2007.edifierx.diagnostics.Diagnostics;
import java.util.*;
import java.util.function.BiConsumer;

/** User-triggered, filtered 12-second identity scan. No GATT connection or private command. */
@SuppressLint("MissingPermission")
public final class IdentityDiscovery implements AutoCloseable {
    private static long lastStart=-1;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private BiConsumer<String,AdvertisementIdentity.Result> completion;
    private long generation;
    private int reports;
    private final Map<AdvertisementIdentity.Status,Integer> rejected=new EnumMap<>(AdvertisementIdentity.Status.class);
    public IdentityDiscovery(Context context){this.context=context.getApplicationContext();}
    public void start(String selectedAddress,BiConsumer<String,AdvertisementIdentity.Result> done){
        cancel();long now=SystemClock.elapsedRealtime();
        synchronized(IdentityDiscovery.class){if(lastStart>=0&&now-lastStart<20000){done.accept("IDENTITY_SCAN_COOLDOWN",null);return;}lastStart=now;}
        completion=done;reports=0;rejected.clear();long token=++generation;
        try{
            BluetoothAdapter adapter=context.getSystemService(BluetoothManager.class).getAdapter();
            if(adapter==null||!adapter.isEnabled()||(scanner=adapter.getBluetoothLeScanner())==null){finish("IDENTITY_BLUETOOTH_UNAVAILABLE",null);return;}
            callback=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult result){main.post(()->{
                    if(token!=generation||completion==null)return;
                    ScanRecord record=result.getScanRecord();if(record==null)return;
                    reports++;
                    List<String> services=record.getServiceUuids()==null?List.of():record.getServiceUuids().stream().map(Object::toString).toList();
                    byte[] manufacturer=record.getManufacturerSpecificData(AdvertisementIdentity.MANUFACTURER);
                    var identity=AdvertisementIdentity.identify(selectedAddress,services,manufacturer==null?-1:AdvertisementIdentity.MANUFACTURER,manufacturer);
                    if(identity.matched()){finish("IDENTITY_ADVERTISEMENT_MATCHED",identity);return;}
                    rejected.merge(identity.status(),1,Integer::sum);
                });}
                @Override public void onScanFailed(int error){main.post(()->{if(token==generation)finish("IDENTITY_SCAN_FAILED_"+error,null);});}
            };
            List<ScanFilter> filters=AdvertisementIdentity.PRODUCTS.keySet().stream().map(uuid->new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()).toList();
            Diagnostics.event("identity","SCAN_START filteredProducts="+filters.size()+" durationMs=12000; names are not used");
            scanner.startScan(filters,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
            main.postDelayed(()->{if(token==generation)finish("IDENTITY_SCAN_TIMEOUT",null);},12000);
        }catch(RuntimeException e){Diagnostics.failure("identity","SCAN_START_FAILED",e);finish(e instanceof SecurityException?"IDENTITY_SCAN_PERMISSION_REQUIRED":"IDENTITY_SCAN_PLATFORM_ERROR",null);}
    }
    private void finish(String status,AdvertisementIdentity.Result result){
        var done=completion;completion=null;stop();
        Diagnostics.event("identity",status+" reports="+reports+" rejected="+rejected+(result==null?"":" profile="+result.profileId()));
        if(done!=null)done.accept(status,result);
    }
    private void stop(){generation++;main.removeCallbacksAndMessages(null);try{if(scanner!=null&&callback!=null)scanner.stopScan(callback);}catch(RuntimeException e){Diagnostics.failure("identity","SCAN_STOP_FAILED",e);}scanner=null;callback=null;}
    public void cancel(){if(completion!=null)Diagnostics.event("identity","SCAN_CANCELLED");completion=null;stop();}
    @Override public void close(){cancel();}
}
