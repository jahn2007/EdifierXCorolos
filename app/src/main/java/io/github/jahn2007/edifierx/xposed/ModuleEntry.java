package io.github.jahn2007.edifierx.xposed;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.jahn2007.edifierx.hooks.ColorOs15Compat;
import io.github.jahn2007.edifierx.hooks.settings.HostInstaller;
import io.github.jahn2007.edifierx.hooks.bluetooth.SystemBatteryInstaller;
import io.github.jahn2007.edifierx.bridge.CallerPolicy;
import java.util.HashSet;
import java.util.Set;
public final class ModuleEntry extends XposedModule {
    private final Set<String> installed=new HashSet<>();
    @Override public void onPackageReady(PackageReadyParam param){
        String pkg=param.getPackageName();if(!ColorOs15Compat.platform()||!param.isFirstPackage()||!CallerPolicy.HOSTS.contains(pkg)||!installed.add(pkg))return;
        try{
            if(pkg.equals("com.android.bluetooth"))SystemBatteryInstaller.install(this,param.getClassLoader());
            else new HostInstaller(this).install(param.getClassLoader());
        }catch(Throwable t){try{log(android.util.Log.WARN,"EdifierX","HOST_UNSUPPORTED: "+pkg+" "+t.getClass().getSimpleName());}catch(Throwable ignored){}}
    }
}
