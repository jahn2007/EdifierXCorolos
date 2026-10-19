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
    public final EarbudsController controller=new EarbudsController();
    private final Context context;
    private final android.content.SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String selectedAddress="",selectedProfile="";
    private boolean running;
    private long reconnectEpoch;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,0)!=BluetoothAdapter.STATE_ON){reconnectEpoch++;controller.disconnect();}return;}
        BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
        if(device==null||!device.getAddress().equals(selectedAddress))return;
        if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())){reconnectEpoch++;controller.disconnect();}
        else if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())&&running){long token=++reconnectEpoch;handler.postDelayed(()->{if(token==reconnectEpoch&&running&&!selectedAddress.isEmpty())connect(selectedAddress,selectedProfile);},1500);}
    }};
    private DeviceRuntime(Context c){context=c;prefs=c.getSharedPreferences("local",0);Diagnostics.enabled(prefs.getBoolean("logs",false));
        controller.addListener(state->{Diagnostics.event("session",state.phase()+":"+state.error());c.getContentResolver().notifyChange(ControlProvider.URI,null);});
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
        boolean trusted=profileId.equals(stored)&&uuids.stream().anyMatch(DeviceProfile.SPP::equalsIgnoreCase);
        if(!trusted&&!profile.candidate(device.getName(),uuids))throw new IllegalArgumentException("Exact model and advertised Edifier service required");
        selectedAddress=address;selectedProfile=profileId;
        controller.connect(address,profile,new RfcommTransport(device,UUID.fromString(DeviceProfile.SPP)));
    }
    public void rememberVerified(){var s=controller.state();if(s.phase()==DeviceState.Phase.READY&&s.identified())prefs.edit().putString("verified."+s.address(),s.profileId()).apply();}
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
