package io.github.jahn2007.edifierx.core;

import java.util.*;

/** Strict, bounded decoders for two incompatible Edifier wire dialects. */
public final class WireCodec {
    public enum Dialect { LEGACY, BES }
    public record Frame(int command, byte[] payload) {
        public Frame { payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    private final Dialect dialect;
    private byte[] pending = new byte[0];
    public WireCodec(Dialect dialect) { this.dialect = dialect; }
    public static byte[] request(Dialect dialect, int command, byte[] payload, boolean xor) {
        if (command < 0 || command > 255 || payload.length > 250) throw new IllegalArgumentException("frame bounds");
        boolean bes = dialect == Dialect.BES;
        int offset = bes ? 5 : 3;
        byte[] frame = new byte[payload.length + (bes ? 6 : 5)];
        frame[0] = (byte) 0xAA;
        frame[1] = (byte) (bes ? 0xEC : payload.length + 1);
        frame[2] = (byte) command;
        if (bes) { frame[3] = 0; frame[4] = (byte) payload.length; }
        for (int i = 0; i < payload.length; i++) frame[offset+i] = (byte) ((payload[i] & 255) ^ (xor ? 0xA5 : 0));
        checksum(frame, bes);
        return frame;
    }
    private static void checksum(byte[] b, boolean bes) {
        int sum = bes ? 0 : 0x2019;
        int n = b.length - (bes ? 1 : 2);
        for (int i=0;i<n;i++) sum += b[i] & 255;
        b[b.length-1] = (byte) sum;
        if (!bes) b[b.length-2] = (byte) (sum >>> 8);
    }
    public synchronized List<Frame> offer(byte[] chunk) {
        if (chunk.length > 4096 || pending.length + chunk.length > 8192) { pending = new byte[0]; return List.of(); }
        byte[] all = Arrays.copyOf(pending, pending.length+chunk.length);
        System.arraycopy(chunk,0,all,pending.length,chunk.length);
        List<Frame> out = new ArrayList<>();
        int start=0;
        boolean bes = dialect == Dialect.BES;
        while (all.length-start >= (bes?6:5)) {
            int head=all[start]&255;
            if (head!=0xBB && head!=0xCC) { start++; continue; }
            if (bes && (all[start+1]&255)!=0xEC) { start++; continue; }
            int size=bes ? ((all[start+3]&255)<<8 | (all[start+4]&255)) : (all[start+1]&255)-1;
            if (size<0 || size>250) { start++; continue; }
            int total=size+(bes?6:5);
            if (all.length-start<total) break;
            byte[] candidate=Arrays.copyOfRange(all,start,start+total);
            byte[] checked=candidate.clone(); checksum(checked,bes);
            if (checked[total-1]!=candidate[total-1] || (!bes && checked[total-2]!=candidate[total-2])) { start++; continue; }
            out.add(new Frame(candidate[2]&255,Arrays.copyOfRange(candidate,bes?5:3,total-(bes?1:2))));
            start+=total;
        }
        pending=Arrays.copyOfRange(all,start,all.length);
        return out;
    }
    public synchronized void reset() { pending=new byte[0]; }
}
