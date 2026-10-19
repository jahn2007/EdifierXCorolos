package io.github.jahn2007.edifierx.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import io.github.jahn2007.edifierx.BuildConfig;
import io.github.jahn2007.edifierx.core.*;
import io.github.jahn2007.edifierx.device.*;
import io.github.jahn2007.edifierx.diagnostics.Diagnostics;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.WeakReference;
import io.github.libxposed.service.*;
import io.github.jahn2007.edifierx.bridge.CallerPolicy;

@SuppressLint("MissingPermission")
public final class MainActivity extends Activity {
    private LinearLayout content;
    private TextView status;
    private final ExecutorService reloadWorker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"edifier-reload-ui");t.setDaemon(true);return t;});
    private final AtomicBoolean reloadPending=new AtomicBoolean();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){renderStatus();main.postDelayed(this,1500);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        var scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(32,24,32,24);scroll.addView(content);setContentView(scroll);
        text("EdifierXCorolos "+BuildConfig.VERSION_NAME+"\nColorOS 15 · API 102 · 优先花再 Evo Pro",22);
        text("先启用 LSPosed 模块，再开启服务并选择已配对设备。控制区位于系统原有蓝牙详情页。未经匹配的设备不发送私有命令。",16);
        toggle("安全总开关", "enabled",()->{if(!getSharedPreferences("local",0).getBoolean("enabled",false))stopService(new Intent(this,ControlService.class));});
        toggle("系统电量桥接（实验，默认关闭；需勾选蓝牙作用域并重启其进程）","battery",()->{});
        toggle("诊断日志（默认关闭，最多 128 条脱敏事件）","logs",()->Diagnostics.enabled(getSharedPreferences("local",0).getBoolean("logs",false)));
        button("授权并启动控制服务",()->{if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS},10);return;}try{if(!getSharedPreferences("local",0).getBoolean("enabled",false))throw new IllegalStateException("请先打开安全总开关");startForegroundService(new Intent(this,ControlService.class));}catch(RuntimeException e){show(e.getMessage());}});
        button("选择已配对耳机并连接",this::selectDevice);
        button("热重载已更新的宿主代码（API 102）",this::chooseReloadTarget);
        button("记住已验证设备（支持修改蓝牙名称）",()->{DeviceRuntime.get(this).rememberVerified();show("仅 READY 且协议响应匹配的设备会被记住");});
        button("断开并停止服务",()->stopService(new Intent(this,ControlService.class)));
        button("导出脱敏诊断报告",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"EdifierXCorolos-diagnostics.txt");startActivityForResult(i,20);});
        status=text("",15);
    }
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setPadding(0,10,0,10);content.addView(view);return view;}
    private void button(String name,Runnable action){Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());content.addView(b);}
    private void toggle(String name,String key,Runnable after){Switch s=new Switch(this);s.setText(name);s.setChecked(getSharedPreferences("local",0).getBoolean(key,false));s.setOnCheckedChangeListener((view,on)->{getSharedPreferences("local",0).edit().putBoolean(key,on).apply();((ModuleApplication)getApplication()).publish();after.run();});content.addView(s);}
    private void show(String message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定",null).show();}
    private void selectDevice(){
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){show("请先授权并启动服务");return;}
        BluetoothAdapter adapter=getSystemService(BluetoothManager.class).getAdapter();if(adapter==null||!adapter.isEnabled()){show("请先打开蓝牙");return;}
        List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        String[] labels=devices.stream().map(d->Objects.toString(d.getName(),"未命名设备")).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("已配对设备").setItems(labels,(dialog,index)->{
            BluetoothDevice d=devices.get(index);Set<String> uuids=new HashSet<>();if(d.getUuids()!=null)for(var uuid:d.getUuids())uuids.add(uuid.toString());
            String known=getSharedPreferences("local",0).getString("verified."+d.getAddress(),"");
            var profile=DeviceProfile.find(known).or(()->DeviceProfile.match(d.getName(),uuids));
            if(profile.isEmpty()){show("型号名及 Edifier 服务 UUID 未同时匹配。不会尝试其他型号协议；请先通过官方 App 采集证据。");return;}
            new AlertDialog.Builder(this).setMessage("候选型号："+profile.get().model()+"。请核对耳机包装/官方 App；名称和 UUID 不能提供密码学身份保证。此步骤仅查询已知状态，成功后可在系统蓝牙详情页控制。")
                .setNegativeButton("取消",null).setPositiveButton("型号正确，连接",(a,b)->{try{DeviceRuntime.get(this).connect(d.getAddress(),profile.get().id());}catch(RuntimeException e){show(e.getMessage());}}).show();
        }).show();
    }
    private void submitReload(Runnable action){
        try{reloadWorker.execute(action);}catch(RejectedExecutionException e){reloadPending.set(false);}
    }
    private void chooseReloadTarget(){
        XposedService service=ModuleApplication.framework;
        if(service==null){show("框架服务不可用，请先在 LSPosed 启用模块");return;}
        if(!reloadPending.compareAndSet(false,true)){show("已有重载操作正在等待结果");return;}
        WeakReference<MainActivity> reference=new WeakReference<>(this);
        submitReload(()->{
            try{
                if(service.getApiVersion()<102)throw new IllegalStateException("框架服务不支持 API 102 热重载");
                List<HookedTarget> targets=service.getRunningTargets().stream().filter(t->CallerPolicy.HOSTS.stream().anyMatch(p->t.getProcessName().equals(p)||t.getProcessName().startsWith(p+":"))).toList();
                postReloadUi(reference,activity->{
                    activity.reloadPending.set(false);
                    if(targets.isEmpty()){activity.show("没有已注入的运行中宿主；先打开系统耳机详情页。初次启用仍需重启宿主进程。");return;}
                    String[] labels=targets.stream().map(t->t.getProcessName()+" · "+t.getState()+" · vCode "+t.getLoadedVersionCode()).toArray(String[]::new);
                    new AlertDialog.Builder(activity).setTitle("选择热重载目标").setItems(labels,(dialog,index)->activity.requestReload(service,targets.get(index))).show();
                });
            }catch(RuntimeException e){postReloadUi(reference,activity->{activity.reloadPending.set(false);ModuleApplication.hotReloadStatus="查询失败："+e.getClass().getSimpleName();activity.show(ModuleApplication.hotReloadStatus);});}
        });
    }
    private static void postReloadUi(WeakReference<MainActivity> reference,java.util.function.Consumer<MainActivity> action){
        new Handler(Looper.getMainLooper()).post(()->{MainActivity a=reference.get();if(a!=null&&!a.isDestroyed()&&!a.isFinishing())action.accept(a);});
    }
    private void requestReload(XposedService service,HookedTarget target){
        if(target.getState()==HookedTarget.State.UP_TO_DATE){show("此进程已加载当前代码。热重载用于覆盖安装新 APK 后换代；配置开关通过远程偏好同步，无需热重载。");return;}
        if(target.getState()==HookedTarget.State.RELOADING){show("框架报告此进程正在重载，请等待结果");return;}
        if(!reloadPending.compareAndSet(false,true))return;
        ModuleApplication.hotReloadStatus="正在请求："+target.getProcessName();
        WeakReference<MainActivity> reference=new WeakReference<>(this);
        main.postDelayed(()->postReloadUi(reference,a->{if(a.reloadPending.getAndSet(false))ModuleApplication.hotReloadStatus="尚未收到重载结果；重新查询目标状态或查看 LSPosed 日志，不能视为成功";}),15000);
        submitReload(()->{
            try{service.hotReloadModule(target,null,(finished,result)->postReloadUi(reference,activity->{
                activity.reloadPending.set(false);
                String reason=result.message()==null?(result.status()==HotReloadResult.Status.FAILED?"旧代拒绝清理，请关闭页面后重试或重启宿主":""):Diagnostics.clean(result.message());
                ModuleApplication.hotReloadStatus=finished.getProcessName()+"："+result.status()+(reason.isEmpty()?"":" · "+reason);
                activity.show(ModuleApplication.hotReloadStatus);
            }));}catch(RuntimeException e){postReloadUi(reference,activity->{activity.reloadPending.set(false);ModuleApplication.hotReloadStatus="请求失败："+e.getClass().getSimpleName();activity.show(ModuleApplication.hotReloadStatus);});}
        });
    }
    private String report(){
        var s=DeviceRuntime.get(this).controller.state();StringBuilder b=new StringBuilder("EdifierXCorolos "+BuildConfig.VERSION_NAME+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" SDK "+Build.VERSION.SDK_INT+"\n构建："+Build.DISPLAY+"\n"+ModuleApplication.frameworkStatus+"\nProfile: "+s.profileId()+"\n状态："+s.phase()+" "+s.error()+"\n电量："+s.battery()+"\n降噪："+s.noise()+"\n");
        for(String pkg:List.of("com.android.settings","com.oplus.wirelesssettings","com.oplus.melody","com.heytap.mydevices","com.heytap.accessory","com.android.bluetooth"))try{var p=getPackageManager().getPackageInfo(pkg,0);b.append(pkg).append(' ').append(p.versionName).append('/').append(p.getLongVersionCode()).append('\n');}catch(PackageManager.NameNotFoundException e){b.append(pkg).append(" 未安装\n");}
        b.append("热重载：").append(ModuleApplication.hotReloadStatus).append('\n');
        b.append("系统电量：实验接口，实机未验证；官方弹窗：未实现，待 ColorOS 15 宿主证据\nEQ/手势/OTA：无本型号安全命令证据，禁用\n").append(Diagnostics.report());return b.toString();
    }
    private void renderStatus(){if(status!=null)status.setText(report());}
    @Override protected void onResume(){super.onResume();main.post(refresh);}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);reloadWorker.shutdownNow();super.onDestroy();}
    @Override protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==20&&result==RESULT_OK&&data!=null&&data.getData()!=null)try(var out=getContentResolver().openOutputStream(data.getData())){if(out!=null)out.write(report().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException e){show("导出失败："+e.getClass().getSimpleName());}}
}
