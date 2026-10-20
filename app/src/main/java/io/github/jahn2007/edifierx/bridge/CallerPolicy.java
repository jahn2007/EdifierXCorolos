package io.github.jahn2007.edifierx.bridge;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.*;
import java.util.Set;
import io.github.jahn2007.edifierx.core.OfficialIdentityPolicy;
public final class CallerPolicy {
    public static final Set<String> HOSTS=Set.of("com.android.settings","com.oplus.wirelesssettings","com.oplus.melody","com.heytap.mydevices","com.android.bluetooth");
    public static boolean scope(String pkg){return HOSTS.contains(pkg)||OfficialIdentityPolicy.PACKAGE.equals(pkg);}
    public static void enforceOfficial(Context context,int uid){
        try{
            var info=context.getPackageManager().getPackageInfo(OfficialIdentityPolicy.PACKAGE,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            if(info.applicationInfo==null||info.applicationInfo.uid!=uid||info.signingInfo==null)throw new SecurityException("Official identity caller UID mismatch");
            var signatures=info.signingInfo.hasMultipleSigners()?info.signingInfo.getApkContentsSigners():info.signingInfo.getSigningCertificateHistory();
            java.util.List<String> digests=new java.util.ArrayList<>();
            for(var signature:signatures)digests.add(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
            if(OfficialIdentityPolicy.trusted(info.packageName,digests))return;
        }catch(android.content.pm.PackageManager.NameNotFoundException|java.security.NoSuchAlgorithmException e){throw new SecurityException("Official identity certificate unavailable",e);}
        throw new SecurityException("Official identity certificate mismatch");
    }
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
