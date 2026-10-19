package io.github.jahn2007.edifierx.core;

import java.util.*;

public record DeviceProfile(String id, String model, Set<String> names, WireCodec.Dialect dialect,
                            boolean xor, int batteryCommand, int ancSlot,
                            Map<Integer,String> readModes, Map<String,Integer> writeModes, boolean game) {
    public static final String SPP="edf00000-edfe-dfed-fedf-edfedfedfedf";
    public DeviceProfile { names=Set.copyOf(names); readModes=Map.copyOf(readModes); writeModes=Map.copyOf(writeModes); }
    public static String normalize(String name) { return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", ""); }
    public boolean candidate(String name, Set<String> uuids) {
        return names.contains(normalize(name)) && uuids.stream().anyMatch(SPP::equalsIgnoreCase);
    }
    public static final List<DeviceProfile> ALL=List.of(
        new DeviceProfile("evo-pro","花再 Evo Pro",Set.of("edifierevopro","evopro","花再evopro","漫步者花再evopro"),WireCodec.Dialect.BES,true,0xF2,0x1B,
            Map.of(1,"ANC",2,"ANC",3,"ANC",4,"WIND",5,"AMBIENT",6,"OFF"),Map.of("ANC",1,"WIND",4,"AMBIENT",5,"OFF",6),false),
        new DeviceProfile("w860nb-pro","EDIFIER W860NB PRO",Set.of("edifierw860nbpro","w860nbpro"),WireCodec.Dialect.BES,true,0xD0,0x10,
            Map.of(1,"ANC",2,"COMFORT",3,"WIND",4,"AMBIENT",5,"OFF"),Map.of("ANC",1,"WIND",3,"AMBIENT",4,"OFF",5),false),
        new DeviceProfile("w820nb","EDIFIER W820NB",Set.of("edifierw820nb","w820nb"),WireCodec.Dialect.LEGACY,false,0xD0,-1,
            Map.of(1,"OFF",2,"ANC",3,"AMBIENT"),Map.of("OFF",1,"ANC",2,"AMBIENT",3),true),
        new DeviceProfile("fitclip-ultra","EDIFIER FitClip Ultra",Set.of("edifierfitclipultra","fitclipultra"),WireCodec.Dialect.BES,true,0xF2,-1,Map.of(),Map.of(),true),
        new DeviceProfile("fitbuds-turbo","EDIFIER FitBuds Turbo",Set.of("edifierfitbudsturbo","fitbudsturbo"),WireCodec.Dialect.BES,false,0xF2,0x1B,
            Map.of(1,"ANC",2,"ANC",3,"ANC",4,"WIND",5,"AMBIENT",6,"OFF"),Map.of("ANC",1,"WIND",4,"AMBIENT",5,"OFF",6),true)
    );
    public static Optional<DeviceProfile> find(String id) { return ALL.stream().filter(p->p.id.equals(id)).findFirst(); }
    public static Optional<DeviceProfile> match(String name, Set<String> uuids) { return ALL.stream().filter(p->p.candidate(name,uuids)).findFirst(); }
    public byte[] query(int command) {
        if (command!=batteryCommand && command!=0xCC && command!=0x08) throw new IllegalArgumentException("query not allowlisted");
        if (command==0xCC && readModes.isEmpty() || command==0x08 && !game) throw new IllegalArgumentException("unsupported query");
        return WireCodec.request(dialect,command,new byte[0],false);
    }
    public byte[] noise(String mode) {
        Integer value=writeModes.get(mode);
        if (value==null) throw new IllegalArgumentException("unsupported mode");
        byte[] b=ancSlot<0 ? new byte[]{value.byteValue()} : new byte[]{(byte)ancSlot,value.byteValue()};
        return WireCodec.request(dialect,0xC1,b,xor);
    }
    public byte[] game(boolean on) {
        if (!game) throw new IllegalArgumentException("game not validated for this model");
        return WireCodec.request(dialect,0x09,new byte[]{(byte)(on?1:0)},xor);
    }
    public byte[] decode(byte[] raw) {
        byte[] b=raw.clone();if(xor)for(int i=0;i<b.length;i++)b[i]^=(byte)0xA5;return b;
    }
}
