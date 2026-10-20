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
    private IdentityDiscovery discovery;
    private final ExecutorService reloadWorker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"edifier-reload-ui");t.setDaemon(true);return t;});
    private final AtomicBoolean reloadPending=new AtomicBoolean();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){renderStatus();main.postDelayed(this,1500);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);discovery=new IdentityDiscovery(this);
        var scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(32,24,32,24);scroll.addView(content);setContentView(scroll);
        text("EdifierXCorolos "+BuildConfig.VERSION_NAME+"\nColorOS 15 · API 102 · 优先花再 Evo Pro",22);
        text("先启用 LSPosed 模块，再开启服务并选择已配对设备。控制区位于系统原有蓝牙详情页。未经匹配的设备不发送私有命令。",16);
        toggle("安全总开关", "enabled",()->{if(!getSharedPreferences("local",0).getBoolean("enabled",false)){discovery.cancel();stopService(new Intent(this,ControlService.class));}});
        toggle("系统电量桥接（实验，默认关闭；需勾选蓝牙作用域并重启其进程）","battery",()->{});
        toggle("详细诊断（默认关闭，记录到本机；关闭停止采集，保留已采日志）","logs",()->Diagnostics.enabled(getSharedPreferences("local",0).getBoolean("logs",false)));
        toggle("从官方App采集首次身份（可选，默认关闭）","officialIdentity",()->{if(getSharedPreferences("local",0).getBoolean("officialIdentity",false))show("请在LSPosed额外勾选 com.edifier.edifierconnect 作用域，再强行停止并重新打开官方8.5.3 App，连接耳机并打开控制页。本模块只观察已通过CRC的D8与产品/连接参数。收到身份后，强行停止官方App释放SPP，再回本模块选耳机；仍需本模块协议验证。成功READY后可关闭采集并取消官方App作用域。");});
        button("清空已采集诊断日志",()->{Diagnostics.clear();show("已清空本机诊断日志");});
        button("授权并启动控制服务",()->{if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.POST_NOTIFICATIONS},10);return;}try{if(!getSharedPreferences("local",0).getBoolean("enabled",false))throw new IllegalStateException("请先打开安全总开关");startForegroundService(new Intent(this,ControlService.class));}catch(RuntimeException e){show(e.getMessage());}});
        button("选择已配对耳机并连接",this::selectDevice);
        button("热重载已更新的宿主代码（API 102）",this::chooseReloadTarget);
        button("记住已验证设备（支持修改蓝牙名称）",()->{DeviceRuntime.get(this).rememberVerified();show("仅 READY 且协议响应匹配的设备会被记住");});
        button("断开并停止服务",()->{discovery.cancel();stopService(new Intent(this,ControlService.class));});
        button("导出脱敏诊断报告",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"EdifierXCorolos-diagnostics.txt");startActivityForResult(i,20);});
        status=text("",15);
    }
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setPadding(0,10,0,10);content.addView(view);return view;}
    private void button(String name,Runnable action){Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());content.addView(b);}
    private void toggle(String name,String key,Runnable after){Switch s=new Switch(this);s.setText(name);s.setChecked(getSharedPreferences("local",0).getBoolean(key,false));s.setOnCheckedChangeListener((view,on)->{getSharedPreferences("local",0).edit().putBoolean(key,on).apply();((ModuleApplication)getApplication()).publish();after.run();});content.addView(s);}
    private void show(String message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定",null).show();}
    private void selectDevice(){
        discovery.cancel();
        if(!getSharedPreferences("local",0).getBoolean("enabled",false)||!DeviceRuntime.get(this).running()){show("请先开启安全总开关，并授权、启动控制服务");return;}
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){show("请先授权并启动服务");return;}
        BluetoothAdapter adapter=getSystemService(BluetoothManager.class).getAdapter();if(adapter==null||!adapter.isEnabled()){show("请先打开蓝牙");return;}
        List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        String[] labels=devices.stream().map(d->Objects.toString(d.getName(),"未命名设备")).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("已配对设备").setItems(labels,(dialog,index)->{
            BluetoothDevice d=devices.get(index);DeviceRuntime runtime=DeviceRuntime.get(this);
            var known=runtime.remembered(d.getAddress());
            if(known.isPresent()){runtime.identityStatus("VERIFIED_BINDING profile="+known.get().id()+"; display name ignored");confirmDevice(d,known.get());return;}
            var offered=runtime.offeredByOfficial(d.getAddress());
            if(offered.isPresent()){runtime.identityStatus("OFFICIAL_IDENTITY_CANDIDATE profile="+offered.get().id()+"; own readback required");confirmDevice(d,offered.get());return;}
            Set<String> uuids=new HashSet<>();if(d.getUuids()!=null)for(var uuid:d.getUuids())uuids.add(uuid.toString());
            runtime.identityStatus("SDP_CACHE uuidCount="+uuids.size()+" edifierSpp="+uuids.stream().anyMatch(DeviceProfile.SPP::equalsIgnoreCase)+" recognizedProducts="+uuids.stream().map(uuid->AdvertisementIdentity.PRODUCTS.get(uuid.toLowerCase(Locale.ROOT))).filter(Objects::nonNull).distinct().toList());
            // Keep the existing legacy model route separate from BES advertisement identification.
            var legacy=DeviceProfile.match(d.getName(),uuids).filter(p->p.id().equals("w820nb"));
            if(legacy.isPresent()){runtime.identityStatus("LEGACY_NAME_AND_SDP_CANDIDATE");confirmDevice(d,legacy.get());return;}
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){
                runtime.identityStatus("IDENTITY_SCAN_PERMISSION_REQUIRED");requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN},11);show("识别改名设备需要附近设备扫描权限。授权后重新选择耳机；只进行一次12秒定向识别。");return;
            }
            runtime.identityStatus("IDENTITY_SCANNING cachedUuidCount="+uuids.size()+"; display name ignored");
            Toast.makeText(this,"正在进行12秒产品识别，请保持耳机已连接。识别进度和拒绝原因可导出到详细日志。",Toast.LENGTH_LONG).show();
            discovery.start(d.getAddress(),(code,identity)->{
                if(isDestroyed()||isFinishing())return;
                runtime.identityStatus(code);
                if(identity!=null&&identity.matched()){runtime.acceptIdentity(d.getAddress(),identity);confirmDevice(d,DeviceProfile.find(identity.profileId()).orElseThrow());}
                else show(code+"\n尚未建立耳机控制连接。若官方App可正常控制，可启用“从官方App采集首次身份”备用路径；否则请开启详细诊断复现并导出。不必恢复原显示名称或重复提供官方APK。");
            });
        }).show();
    }
    private void confirmDevice(BluetoothDevice device,DeviceProfile profile){
        new AlertDialog.Builder(this).setMessage("已识别："+profile.model()+"。请确认是所选耳机；连接后先读取电量和真实模式，验证成功才允许控制。")
            .setNegativeButton("取消",null).setPositiveButton("确认并连接",(dialog,which)->{try{DeviceRuntime.get(this).connect(device.getAddress(),profile.id());}catch(RuntimeException e){Diagnostics.failure("connect","CONNECT_REJECTED",e);show(Diagnostics.clean(e.getMessage()));}}).show();
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
                List<HookedTarget> targets=service.getRunningTargets().stream().filter(t->CallerPolicy.scope(t.getProcessName().split(":",2)[0])).toList();
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
        var runtime=DeviceRuntime.get(this);var s=runtime.controller.state();StringBuilder b=new StringBuilder("EdifierXCorolos "+BuildConfig.VERSION_NAME+" code="+BuildConfig.VERSION_CODE+" source="+BuildConfig.SOURCE_REVISION+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" SDK "+Build.VERSION.SDK_INT+"\n构建："+Build.DISPLAY+"\n"+ModuleApplication.frameworkStatus+"\n识别："+runtime.identityStatus+"\n服务："+runtime.running()+"；安全开关："+getSharedPreferences("local",0).getBoolean("enabled",false)+"\nProfile: "+s.profileId()+"\n状态："+s.phase()+" "+s.error()+"\n电量："+s.battery()+"\n降噪："+s.noise()+"\n");
        b.append("权限 CONNECT=").append(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED).append(" SCAN=").append(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED).append('\n');
        for(String pkg:List.of("com.android.settings","com.oplus.wirelesssettings","com.oplus.melody","com.heytap.mydevices","com.heytap.accessory","com.android.bluetooth",OfficialIdentityPolicy.PACKAGE))try{var p=getPackageManager().getPackageInfo(pkg,0);b.append(pkg).append(' ').append(p.versionName).append('/').append(p.getLongVersionCode()).append('\n');}catch(PackageManager.NameNotFoundException e){b.append(pkg).append(" 未安装\n");}
        b.append("官方身份采集：").append(getSharedPreferences("local",0).getBoolean("officialIdentity",false)).append('\n');
        b.append("热重载：").append(ModuleApplication.hotReloadStatus).append('\n');
        b.append("系统电量：实验接口，实机未验证；官方弹窗：未实现，待 ColorOS 15 宿主证据\nEQ/手势/OTA：无本型号安全命令证据，禁用\n").append(Diagnostics.summary());return b.toString();
    }
    private void renderStatus(){if(status!=null)status.setText(report());}
    @Override protected void onResume(){super.onResume();main.post(refresh);}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);discovery.close();reloadWorker.shutdownNow();super.onDestroy();}
    @Override protected void onPause(){main.removeCallbacks(refresh);discovery.cancel();super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==20&&result==RESULT_OK&&data!=null&&data.getData()!=null){
        var uri=data.getData();String metadata=report();var context=getApplicationContext();WeakReference<MainActivity> reference=new WeakReference<>(this);
        submitReload(()->{try(var out=context.getContentResolver().openOutputStream(uri)){
            if(out==null)throw new java.io.IOException("No output stream");StringBuilder report=new StringBuilder(metadata).append("\n\n=== Detailed events (local, redacted) ===\n").append(Diagnostics.export());
            var framework=ModuleApplication.framework;if(framework!=null)try{report.append("\n=== Framework running targets ===\n");for(var target:framework.getRunningTargets())if(CallerPolicy.scope(target.getProcessName().split(":",2)[0]))report.append(target.getProcessName()).append(' ').append(target.getState()).append(" loadedCode=").append(target.getLoadedVersionCode()).append('\n');}catch(RuntimeException e){report.append("TARGET_QUERY_FAILED ").append(e.getClass().getSimpleName());}
            out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));postReloadUi(reference,a->a.show("详细诊断报告已导出"));
        }catch(java.io.IOException|RuntimeException e){Diagnostics.failure("export","EXPORT_FAILED",e);postReloadUi(reference,a->a.show("导出失败："+e.getClass().getSimpleName()));}});
    }}
}
