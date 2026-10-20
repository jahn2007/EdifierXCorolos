package io.github.jahn2007.edifierx.core;

/** Unknown remains -1; a TWS report atomically replaces all component levels. */
public record Battery(int aggregate, int left, int right, int caseLevel, boolean caseCharging) {
    public static final Battery UNKNOWN=new Battery(-1,-1,-1,-1,false);
    public Battery { if (!valid(aggregate)||!valid(left)||!valid(right)||!valid(caseLevel)) throw new IllegalArgumentException("battery range"); }
    private static boolean valid(int p) {return p>=-1&&p<=100;}
    public int systemLevel() { return aggregate>=0?aggregate:left<0?right:right<0?left:Math.min(left,right); }
    public static Battery parse(DeviceProfile p, WireCodec.Frame frame) {
        if(frame.command()!=p.batteryCommand())return null;
        byte[] b=p.decode(frame.payload());
        if(p.batteryCommand()==0xD0) return b.length==1&&(b[0]&255)<=100?new Battery(b[0]&255,-1,-1,-1,false):null;
        if(b.length<5)return null;
        // Official parseTWSBattery: 1 = left only, 2 = right only, 3 = peer TWS.
        // Other layouts include single headphones (4) and speakers; they must not
        // unlock a TWS profile merely because their percentage bytes look valid.
        int layout=b[0]&255;
        if(layout<1||layout>3)return null;
        int left=b[1]&255,right=b[2]&255,box=b[3]&255,state=b[4]&255;
        if(left>100||right>100||box>100||state<1||state>3)return null;
        return new Battery(-1,layout==2||left==0?-1:left,layout==1||right==0?-1:right,state==3?-1:box,state==1);
    }
}
