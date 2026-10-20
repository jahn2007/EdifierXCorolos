package io.github.jahn2007.edifierx.core;

import java.util.*;

/** Facts from official 8.5.3 BLEManager and products_release.json, not name heuristics.
 * Advertisement is evidence, not authentication: bonding and protocol readback remain mandatory. */
public final class AdvertisementIdentity {
    public static final int MANUFACTURER=2016;
    public static final Map<String,String> PRODUCTS=Map.of(
        "00001e00-0000-1000-8000-00805f9b34fb","evo-pro",
        "00009300-0000-1000-8000-00805f9b34fb","w860nb-pro",
        "00001303-0000-1000-8000-00805f9b34fb","fitclip-ultra",
        "00005503-0000-1000-8000-00805f9b34fb","fitbuds-turbo");
    public enum Status { MATCHED, UNKNOWN_PRODUCT, AMBIGUOUS_PRODUCT, WRONG_MANUFACTURER,
        TRUNCATED_DATA, ADDRESS_MISMATCH, UNSUPPORTED_PROTOCOL }
    public record Result(Status status,String profileId) { public boolean matched(){return status==Status.MATCHED;} }
    public static Result identify(String selectedAddress,Collection<String> services,int manufacturer,byte[] data){
        Set<String> models=new HashSet<>();
        for(String service:services){String id=PRODUCTS.get(service.toLowerCase(Locale.ROOT));if(id!=null)models.add(id);}
        if(models.isEmpty())return new Result(Status.UNKNOWN_PRODUCT,"");
        if(models.size()!=1)return new Result(Status.AMBIGUOUS_PRODUCT,"");
        String id=models.iterator().next();
        if(manufacturer!=MANUFACTURER)return new Result(Status.WRONG_MANUFACTURER,id);
        if(data==null||data.length<8)return new Result(Status.TRUNCATED_DATA,id);
        String address=selectedAddress==null?"":selectedAddress.replace(":","").toLowerCase(Locale.ROOT);
        if(!address.matches("[0-9a-f]{12}")||!HexFormat.of().formatHex(data,0,6).equals(address))return new Result(Status.ADDRESS_MISMATCH,id);
        DeviceProfile profile=DeviceProfile.find(id).orElseThrow();
        // Selector 0x31 has another protocol path in the official app. Never treat it as plaintext.
        int encryption=profile.xor()?0x10:0;
        if((data[data.length-2]&255)!=2||(data[data.length-1]&255)!=encryption)return new Result(Status.UNSUPPORTED_PROTOCOL,id);
        return new Result(Status.MATCHED,id);
    }
    private AdvertisementIdentity(){}
}
