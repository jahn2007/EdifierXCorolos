package io.github.jahn2007.edifierx.core;

import java.util.*;

/** Official-app enrollment is a separate trust boundary, never a general control caller. */
public final class OfficialIdentityPolicy {
    public static final String PACKAGE="com.edifier.edifierconnect";
    public static final String CERTIFICATE="c897b7355f389365c7c0f21dc242626ebc39552c7f97de6a7116a16d09dc5a4c";
    public static boolean trusted(String packageName,Collection<String> certificates){
        return PACKAGE.equals(packageName)&&certificates.stream().anyMatch(CERTIFICATE::equalsIgnoreCase);
    }
    public static Optional<DeviceProfile> profile(String productUuid,int version,int encryption){
        if(productUuid==null||version!=2)return Optional.empty();
        String id=AdvertisementIdentity.PRODUCTS.get(productUuid.toLowerCase(Locale.ROOT));
        return DeviceProfile.find(id).filter(p->encryption==(p.xor()?0x10:0));
    }
    private OfficialIdentityPolicy(){}
}
