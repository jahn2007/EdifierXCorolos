package io.github.jahn2007.edifierx.bridge;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.*;
import java.util.Set;
public final class CallerPolicy {
    public static final Set<String> HOSTS=Set.of("com.android.settings","com.oplus.wirelesssettings","com.oplus.melody","com.heytap.mydevices","com.android.bluetooth");
    public static void enforce(Context c,int uid){
        if(uid==android.os.Process.myUid())return;
        String[] packages=c.getPackageManager().getPackagesForUid(uid);
        if(packages!=null)for(String pkg:packages)if(HOSTS.contains(pkg))try {
            var app=c.getPackageManager().getApplicationInfo(pkg,0);
            if(app.uid==uid&&(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)return;
        }catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        throw new SecurityException("Only module UID and installed system hosts may use the bridge");
    }
}
