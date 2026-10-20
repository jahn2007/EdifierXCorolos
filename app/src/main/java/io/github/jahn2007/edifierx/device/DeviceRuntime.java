package io.github.jahn2007.edifierx.device;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import io.github.jahn2007.edifierx.core.*;
import io.github.jahn2007.edifierx.protocol.rfcomm.RfcommTransport;
import io.github.jahn2007.edifierx.bridge.ControlProvider;
import io.github.jahn2007.edifierx.diagnostics.Diagnostics;
import java.util.*;

@SuppressLint("MissingPermission")
public final class DeviceRuntime {
    private static DeviceRuntime instance;
    public static synchronized DeviceRuntime get(Context c){if(instance==null)instance=new DeviceRuntime(c.getApplicationContext());return instance;}
    public final EarbudsController controller=new EarbudsController(message->Diagnostics.event("protocol",message));
    private final Context context;
    private final android.content.SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String selectedAddress="",selectedProfile="";
    private boolean running;
    private long reconnectEpoch;
    private record Proof(String profile,long expires){}
    private final Map<String,Proof> discoveries=new LinkedHashMap<>();
    public volatile String identityStatus="尚未选择设备";
    public synchronized void acceptIdentity(String address,AdvertisementIdentity.Result result){
        if(!result.matched())throw new IllegalArgumentException("identity rejected");
        if(discoveries.size()>=16)discoveries.remove(discoveries.keySet().iterator().next());
        discoveries.put(address,new Proof(result.profileId(),SystemClock.elapsedRealtime()+120000));
    }
    public synchronized Optional<DeviceProfile> remembered(String address){return DeviceProfile.find(prefs.getString("verified."+address,""));}
    public void identityStatus(String status){identityStatus=Diagnostics.clean(status);Diagnostics.event("identity",status);}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)!=BluetoothAdapter.STATE_ON){reconnectEpoch++;controller.disconnect();}return;}
        BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
        if(device==null||!device.getAddress().equals(selectedAddress))return;
        if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())){reconnectEpoch++;controller.disconnect();}
        else if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())&&running){long token=++reconnectEpoch;handler.postDelayed(()->{if(token==reconnectEpoch&&running&&!selectedAddress.isEmpty())try{connect(selectedAddress,selectedProfile);}catch(RuntimeException e){Diagnostics.failure("reconnect","RECONNECT_REJECTED",e);}},1500);}
    }};
    private DeviceRuntime(Context c){context=c;prefs=c.getSharedPreferences("local",0);Diagnostics.enabled(prefs.getBoolean("logs",false));
        controller.addListener(state->{Diagnostics.event("session","generation="+state.generation()+" profile="+state.profileId()+" phase="+state.phase()+" error="+state.error()+" battery="+state.battery()+" noise="+state.noise());
            if(state.phase()==DeviceState.Phase.READY&&state.identified())rememberVerified();
            c.getContentResolver().notifyChange(ControlProvider.URI,null);});
    }
    public synchronized boolean running(){return running;}
    public synchronized void start(){if(running)return;running=true;IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);poll();}
    private final Runnable periodic=new Runnable(){public void run(){if(!running)return;if(controller.state().phase()==DeviceState.Phase.READY)controller.refreshState();handler.postDelayed(this,30000);}};
    private void poll(){handler.removeCallbacks(periodic);handler.postDelayed(periodic,30000);}
    public synchronized void stop(){running=false;reconnectEpoch++;handler.removeCallbacks(periodic);try{context.unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}controller.disconnect();}
    public synchronized void connect(String address,String profileId){
        if(!running||!prefs.getBoolean("enabled",false))throw new IllegalStateException("Start control service and enable safety switch first");
        if(!BluetoothAdapter.checkBluetoothAddress(address))throw new IllegalArgumentException("address invalid");
        BluetoothAdapter adapter=context.getSystemService(BluetoothManager.class).getAdapter();
        if(adapter==null||!adapter.isEnabled())throw new IllegalStateException("Bluetooth unavailable");
        BluetoothDevice device=adapter.getRemoteDevice(address);
        if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Only paired devices supported");
        Set<String> uuids=new HashSet<>();if(device.getUuids()!=null)for(ParcelUuid uuid:device.getUuids())uuids.add(uuid.toString());
        DeviceProfile profile=DeviceProfile.find(profileId).orElseThrow();
        String stored=prefs.getString("verified."+address,"");
        Proof proof=discoveries.get(address);
        boolean advertised=proof!=null&&proof.profile().equals(profileId)&&proof.expires()>=SystemClock.elapsedRealtime();
        boolean trusted=profileId.equals(stored);
        // Legacy W820NB retains its separately documented name + SDP route. Never borrow it for Evo.
        boolean legacy=profileId.equals("w820nb")&&profile.candidate(device.getName(),uuids);
        if(!trusted&&!advertised&&!legacy)throw new IllegalArgumentException("IDENTITY_EVIDENCE_REQUIRED: 请重新识别广播产品与地址");
        identityStatus("CONNECT_GATE source="+(trusted?"verified-binding":advertised?"product-uuid+manufacturer+address+protocol":"legacy-name+sdp")+" profile="+profileId+" cachedUuidCount="+uuids.size());
        selectedAddress=address;selectedProfile=profileId;
        controller.connect(address,profile,new RfcommTransport(device,UUID.fromString(DeviceProfile.SPP)));
    }
    public void rememberVerified(){var s=controller.state();if(s.phase()==DeviceState.Phase.READY&&s.identified()&&!s.profileId().equals(prefs.getString("verified."+s.address(),""))){
        var edit=prefs.edit();var keys=prefs.getAll().keySet().stream().filter(k->k.startsWith("verified.")).sorted().toList();if(keys.size()>=32)edit.remove(keys.get(0));
        edit.putString("verified."+s.address(),s.profileId()).apply();Diagnostics.event("identity","VERIFIED_BINDING_SAVED profile="+s.profileId());}}
    public Bundle snapshot(String address){
        var state=controller.state();Bundle b=new Bundle();b.putBoolean("service",running);b.putBoolean("enabled",prefs.getBoolean("enabled",false));
        if(!address.isEmpty()&&!address.equals(state.address())){b.putString("phase","UNSUPPORTED");return b;}
        b.putString("address",state.address());b.putLong("generation",state.generation());b.putString("profile",state.profileId());b.putString("phase",state.phase().name());b.putBoolean("identified",state.identified());b.putString("noise",state.noise());
        b.putInt("aggregate",state.battery().aggregate());b.putInt("left",state.battery().left());b.putInt("right",state.battery().right());b.putInt("case",state.battery().caseLevel());b.putInt("systemBattery",state.battery().systemLevel());b.putBoolean("caseCharging",state.battery().caseCharging());
        if(state.game()!=null)b.putBoolean("game",state.game());b.putString("error",state.error());b.putLong("time",SystemClock.elapsedRealtime());
        DeviceProfile.find(state.profileId()).ifPresent(p->{b.putString("model",p.model());b.putStringArray("modes",p.writeModes().keySet().stream().sorted().toArray(String[]::new));b.putBoolean("supportsGame",p.game());});
        return b;
    }
}
