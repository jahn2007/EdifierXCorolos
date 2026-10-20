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
    private int filteredReports,observationReports,emptyRecords,targetRecords;
    private boolean observation;
    private final java.util.concurrent.atomic.AtomicInteger delivered=new java.util.concurrent.atomic.AtomicInteger();
    private final Map<AdvertisementIdentity.Status,Integer> rejected=new EnumMap<>(AdvertisementIdentity.Status.class);
    public IdentityDiscovery(Context context){this.context=context.getApplicationContext();}
    public void start(String selectedAddress,BiConsumer<String,AdvertisementIdentity.Result> done){
        cancel();long now=SystemClock.elapsedRealtime();
        synchronized(IdentityDiscovery.class){if(lastStart>=0&&now-lastStart<20000){done.accept("IDENTITY_SCAN_COOLDOWN",null);return;}lastStart=now;}
        completion=done;reports=0;filteredReports=0;observationReports=0;emptyRecords=0;targetRecords=0;observation=false;delivered.set(0);rejected.clear();long token=++generation;
        try{
            BluetoothAdapter adapter=context.getSystemService(BluetoothManager.class).getAdapter();
            if(adapter==null||!adapter.isEnabled()||(scanner=adapter.getBluetoothLeScanner())==null){finish("IDENTITY_BLUETOOTH_UNAVAILABLE",null);return;}
            callback=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult result){if(delivered.incrementAndGet()>2048)return;main.post(()->{
                    if(token!=generation||completion==null)return;
                    ScanRecord record=result.getScanRecord();if(record==null){emptyRecords++;return;}
                    reports++;
                    if(observation)observationReports++;else filteredReports++;
                    List<String> services=record.getServiceUuids()==null?List.of():record.getServiceUuids().stream().map(Object::toString).toList();
                    byte[] manufacturer=record.getManufacturerSpecificData(AdvertisementIdentity.MANUFACTURER);
                    if(AdvertisementIdentity.targetAddress(selectedAddress,manufacturer)&&targetRecords++==0){
                        String ids=services.stream().filter(s->s.matches("(?i)[0-9a-f]{8}-0000-1000-8000-00805f9b34fb")).map(s->s.substring(0,8)).limit(12).toList().toString();
                        Diagnostics.event("identity","TARGET_ADVERTISEMENT manufacturerLength="+manufacturer.length+" serviceCount="+services.size()+" bluetoothAssignedServiceIds="+ids+" protocol="+(manufacturer.length>=8?(manufacturer[manufacturer.length-2]&255):-1)+" selector="+(manufacturer.length>=8?(manufacturer[manufacturer.length-1]&255):-1));
                    }
                    var identity=AdvertisementIdentity.identify(selectedAddress,services,manufacturer==null?-1:AdvertisementIdentity.MANUFACTURER,manufacturer);
                    if(identity.matched()){finish("IDENTITY_ADVERTISEMENT_MATCHED",identity);return;}
                    rejected.merge(identity.status(),1,Integer::sum);
                });}
                @Override public void onBatchScanResults(List<ScanResult> results){for(var result:results.stream().limit(128).toList())onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,result);}
                @Override public void onScanFailed(int error){main.post(()->{if(token==generation)finish("IDENTITY_SCAN_FAILED_"+error,null);});}
            };
            List<ScanFilter> filters=AdvertisementIdentity.PRODUCTS.keySet().stream().map(uuid->new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()).toList();
            Diagnostics.event("identity","SCAN_START filteredProducts="+filters.size()+" durationMs=12000; names are not used");
            android.location.LocationManager location=context.getSystemService(android.location.LocationManager.class);android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);
            Diagnostics.event("identity","SCAN_ENV offloadedFiltering="+adapter.isOffloadedFilteringSupported()+" locationEnabled="+(location!=null&&location.isLocationEnabled())+" interactive="+(power!=null&&power.isInteractive())+" powerSave="+(power!=null&&power.isPowerSaveMode())+" neverForLocation=true");
            scanner.startScan(filters,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
            main.postDelayed(()->{if(token==generation&&reports==0&&completion!=null&&Diagnostics.enabled())try{
                scanner.stopScan(callback);observation=true;Diagnostics.event("identity","SCAN_OBSERVATION_START remainingMs=6000; aggregate nearby counts only, strict identity checks unchanged");
                scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
            }catch(RuntimeException e){Diagnostics.failure("identity","SCAN_OBSERVATION_FAILED",e);finish("IDENTITY_SCAN_PLATFORM_ERROR",null);}},6000);
            main.postDelayed(()->{if(token==generation)finish("IDENTITY_SCAN_TIMEOUT",null);},12000);
        }catch(RuntimeException e){Diagnostics.failure("identity","SCAN_START_FAILED",e);finish(e instanceof SecurityException?"IDENTITY_SCAN_PERMISSION_REQUIRED":"IDENTITY_SCAN_PLATFORM_ERROR",null);}
    }
    private void finish(String status,AdvertisementIdentity.Result result){
        var done=completion;completion=null;stop();
        Diagnostics.event("identity",status+" reports="+reports+" filtered="+filteredReports+" observation="+observationReports+" emptyRecords="+emptyRecords+" targetRecords="+targetRecords+" delivered="+delivered.get()+" rejected="+rejected+(result==null?"":" profile="+result.profileId()));
        if(done!=null)done.accept(status,result);
    }
    private void stop(){generation++;main.removeCallbacksAndMessages(null);try{if(scanner!=null&&callback!=null)scanner.stopScan(callback);}catch(RuntimeException e){Diagnostics.failure("identity","SCAN_STOP_FAILED",e);}scanner=null;callback=null;}
    public void cancel(){if(completion!=null)Diagnostics.event("identity","SCAN_CANCELLED");completion=null;stop();}
    @Override public void close(){cancel();}
}
