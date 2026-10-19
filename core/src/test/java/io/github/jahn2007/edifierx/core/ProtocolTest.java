package io.github.jahn2007.edifierx.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ProtocolTest {
    private static byte[] hex(String s){return HexFormat.of().parseHex(s.replace(" ",""));}
    private static DeviceProfile evo(){return DeviceProfile.find("evo-pro").orElseThrow();}
    @Test public void besQueryMatchesPublicVector(){assertArrayEquals(hex("AA EC F2 00 00 88"),evo().query(0xF2));}
    @Test public void legacyQueryUsesTwoByteChecksum(){var p=DeviceProfile.find("w820nb").orElseThrow();assertArrayEquals(hex("AA 01 D0 21 94"),p.query(0xD0));}
    @Test public void evoBatteryCapture(){var f=new WireCodec(WireCodec.Dialect.BES).offer(hex("BB EC F2 00 06 A6 C1 C7 A5 A6 B4 CC")).get(0);var b=Battery.parse(evo(),f);assertNotNull(b);assertEquals(100,b.left());assertEquals(98,b.right());assertEquals(-1,b.caseLevel());assertEquals(-1,b.aggregate());assertEquals(98,b.systemLevel());}
    @Test public void evoNoiseCapture(){var f=new WireCodec(WireCodec.Dialect.BES).offer(hex("BB EC CC 00 02 BE A3 D6")).get(0);assertArrayEquals(hex("1B 06"),evo().decode(f.payload()));}
    @Test public void fragmentedAndCoalescedFrames(){var d=new WireCodec(WireCodec.Dialect.BES);assertTrue(d.offer(hex("00 BB EC CC 00")).isEmpty());assertEquals(2,d.offer(hex("02 BE A3 D6 BB EC CC 00 02 BE A3 D6")).size());}
    @Test public void corruptChecksumRejected(){assertTrue(new WireCodec(WireCodec.Dialect.BES).offer(hex("BB EC CC 00 02 BE A3 D7")).isEmpty());}
    @Test public void echoedRequestsNeverAccepted(){assertTrue(new WireCodec(WireCodec.Dialect.BES).offer(evo().query(0xF2)).isEmpty());}
    @Test public void decoderBoundedAndResyncs(){var d=new WireCodec(WireCodec.Dialect.BES);assertTrue(d.offer(new byte[9000]).isEmpty());assertEquals(1,d.offer(hex("BB EC CC FF FF BB EC CC 00 02 BE A3 D6")).size());}
    @Test public void wrongAppCodeRejected(){assertTrue(new WireCodec(WireCodec.Dialect.BES).offer(hex("BB ED CC 00 02 BE A3 D7")).isEmpty());}
    @Test public void unknownBatteryNeverInvented(){assertEquals(-1,Battery.UNKNOWN.systemLevel());assertEquals(0,new Battery(0,-1,-1,-1,false).systemLevel());}
    @Test public void unavailableEarClearsOldValue(){var p=evo();var f=new WireCodec.Frame(0xF2,hex("A6 A5 C7 A5 A6 B4"));var b=Battery.parse(p,f);assertNotNull(b);assertEquals(-1,b.left());assertEquals(98,b.right());}
    @Test public void caseChargingSample(){var f=new WireCodec(WireCodec.Dialect.BES).offer(hex("BB EC F2 00 06 A6 9C A3 E3 A4 B4 BF")).get(0);var b=Battery.parse(evo(),f);assertEquals(70,b.caseLevel());assertTrue(b.caseCharging());assertEquals(6,b.systemLevel());}
    @Test public void invalidBatteryDoesNotBecomeDisconnected(){assertNull(Battery.parse(evo(),new WireCodec.Frame(0xF2,hex("A6 5A C7 A5 A6 B4"))));}
    @Test public void truncatedTwsRejected(){assertNull(Battery.parse(evo(),new WireCodec.Frame(0xF2,hex("A6 C1 C7"))));}
    @Test public void nameAloneInsufficient(){assertTrue(DeviceProfile.match("EDIFIER Evo Pro",Set.of()).isEmpty());assertTrue(DeviceProfile.match("EDIFIER Other",Set.of(DeviceProfile.SPP)).isEmpty());}
    @Test public void exactEvoPlusSdp(){assertEquals("evo-pro",DeviceProfile.match("EDIFIER Evo Pro",Set.of(DeviceProfile.SPP.toUpperCase(Locale.ROOT))).orElseThrow().id());}
    @Test public void aliasesAreNotSubstringMatches(){assertTrue(DeviceProfile.match("My Evo Pro Speaker",Set.of(DeviceProfile.SPP)).isEmpty());}
    @Test public void noAncProbeForFitclip(){var p=DeviceProfile.find("fitclip-ultra").orElseThrow();assertThrows(IllegalArgumentException.class,()->p.query(0xCC));assertThrows(IllegalArgumentException.class,()->p.noise("ANC"));}
    @Test public void evoGameNotBorrowedFromOtherModels(){assertFalse(evo().game());assertThrows(IllegalArgumentException.class,()->evo().game(true));}
    @Test public void unknownWritesRejected(){assertThrows(IllegalArgumentException.class,()->evo().noise("FACTORY_RESET"));assertThrows(IllegalArgumentException.class,()->evo().query(7));}
    @Test public void fitbudsPlaintextNotEvoXor(){var p=DeviceProfile.find("fitbuds-turbo").orElseThrow();assertArrayEquals(hex("AA EC C1 00 02 1B 01 75"),p.noise("ANC"));assertFalse(Arrays.equals(p.noise("ANC"),evo().noise("ANC")));}
    @Test public void frameDefensiveCopy(){byte[] original={1};var f=new WireCodec.Frame(1,original);original[0]=2;assertEquals(1,f.payload()[0]);var copy=f.payload();copy[0]=3;assertEquals(1,f.payload()[0]);}
    @Test public void dialectsDoNotMix(){var legacy=DeviceProfile.find("w820nb").orElseThrow();assertTrue(new WireCodec(WireCodec.Dialect.BES).offer(legacy.query(0xD0)).isEmpty());}
    @Test public void resetClearsPartialFrame(){var d=new WireCodec(WireCodec.Dialect.BES);d.offer(hex("BB EC CC 00"));d.reset();assertTrue(d.offer(hex("02 BE A3 D6")).isEmpty());}
}
