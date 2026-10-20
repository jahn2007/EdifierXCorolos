package io.github.jahn2007.edifierx.ui;
import android.app.Application;
import io.github.libxposed.service.*;
public final class ModuleApplication extends Application {
    public static volatile XposedService framework;
    public static volatile String hotReloadStatus="未请求热重载";
    public static volatile String frameworkStatus="未收到框架服务：请检查 LSPosed 启用状态";
    @Override public void onCreate(){super.onCreate();io.github.jahn2007.edifierx.diagnostics.Diagnostics.init(this);XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener(){
        public void onServiceBind(XposedService service){framework=service;frameworkStatus=service.getFrameworkName()+" API "+service.getApiVersion();publish();}
        public void onServiceDied(XposedService service){framework=null;frameworkStatus="框架服务已断开";}
    });}
    public void publish(){var service=framework;if(service==null)return;var local=getSharedPreferences("local",0);try{
        service.getRemotePreferences("config").edit().putBoolean("enabled",local.getBoolean("enabled",false)).putBoolean("battery",local.getBoolean("battery",false)).putBoolean("logs",local.getBoolean("logs",false)).commit();
    }catch(RuntimeException e){frameworkStatus="框架配置同步失败："+e.getClass().getSimpleName();}}
}
