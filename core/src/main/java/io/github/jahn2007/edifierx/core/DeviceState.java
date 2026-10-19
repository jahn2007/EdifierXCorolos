package io.github.jahn2007.edifierx.core;

public record DeviceState(long generation, String address, String profileId, Phase phase,
                          Battery battery, String noise, Boolean game, boolean identified, String error) {
    public enum Phase { DISCONNECTED, CONNECTING, READING, READY, WRITING, ERROR, UNSUPPORTED }
    public static DeviceState empty(long generation) { return new DeviceState(generation,"","",Phase.DISCONNECTED,Battery.UNKNOWN,"UNKNOWN",null,false,""); }
}
