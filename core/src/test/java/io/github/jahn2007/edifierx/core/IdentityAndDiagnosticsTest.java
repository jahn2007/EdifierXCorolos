package io.github.jahn2007.edifierx.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class IdentityAndDiagnosticsTest {
    private static final String TARGET="12:34:56:78:9A:BC";
    private static final String EVO="00001e00-0000-1000-8000-00805f9b34fb";
    // Synthetic manufacturer payload from official layout; never a real user's recording.
    private static byte[] data(){return HexFormat.of().parseHex("123456789ABC0210");}
    @Test public void renamedEvoIdentifiesWithoutAnyNameOrCachedSpp(){var r=AdvertisementIdentity.identify(TARGET,List.of(EVO),2016,data());assertTrue(r.matched());assertEquals("evo-pro",r.profileId());}
    @Test public void advertisementForAnotherPairedDeviceCannotSelectTarget(){assertEquals(AdvertisementIdentity.Status.ADDRESS_MISMATCH,AdvertisementIdentity.identify("12:34:56:78:9A:BD",List.of(EVO),2016,data()).status());}
    @Test public void unknownManufacturerCannotBorrowEvoService(){assertEquals(AdvertisementIdentity.Status.WRONG_MANUFACTURER,AdvertisementIdentity.identify(TARGET,List.of(EVO),123,data()).status());}
    @Test public void truncatedManufacturerDoesNotGuessProtocol(){assertEquals(AdvertisementIdentity.Status.TRUNCATED_DATA,AdvertisementIdentity.identify(TARGET,List.of(EVO),2016,new byte[7]).status());}
    @Test public void ambiguousProductsAreRejected(){assertEquals(AdvertisementIdentity.Status.AMBIGUOUS_PRODUCT,AdvertisementIdentity.identify(TARGET,AdvertisementIdentity.PRODUCTS.keySet(),2016,data()).status());}
    @Test public void edifierSppAloneIsNotAProductIdentity(){assertEquals(AdvertisementIdentity.Status.UNKNOWN_PRODUCT,AdvertisementIdentity.identify(TARGET,List.of(DeviceProfile.SPP),2016,data()).status());}
    @Test public void wrongProtocolOrEncryptionCannotReuseEvoProfile(){for(byte[] payload:new byte[][]{HexFormat.of().parseHex("123456789ABC0110"),HexFormat.of().parseHex("123456789ABC0200"),HexFormat.of().parseHex("123456789ABC0231")})assertEquals(AdvertisementIdentity.Status.UNSUPPORTED_PROTOCOL,AdvertisementIdentity.identify(TARGET,List.of(EVO),2016,payload).status());}
    @Test public void serviceUuidCaseIsInsensitiveAndNamesAreNotAnInput(){assertTrue(AdvertisementIdentity.identify(TARGET,List.of(EVO.toUpperCase(Locale.ROOT)),2016,data()).matched());}
    @Test public void plaintextModelCannotBorrowXorOrEncryption31(){String turbo="00005503-0000-1000-8000-00805f9b34fb";assertFalse(AdvertisementIdentity.identify(TARGET,List.of(turbo),2016,data()).matched());assertTrue(AdvertisementIdentity.identify(TARGET,List.of(turbo),2016,HexFormat.of().parseHex("123456789ABC0200")).matched());assertFalse(AdvertisementIdentity.identify(TARGET,List.of(turbo),2016,HexFormat.of().parseHex("123456789ABC0231")).matched());}
    @Test public void diagnosticsRedactMacCompactAddressAndCredentials(){String s=DiagnosticBuffer.clean("device=12:34:56:78:9A:BC compact=123456789abc token=example password:example\nnext");assertFalse(s.contains("12:34"));assertFalse(s.contains("123456789abc"));assertFalse(s.contains("example"));assertFalse(s.contains("\n"));assertTrue(s.contains("next"));}
    @Test public void detailedDiagnosticsAreBoundedInBytesAndEvents(){var b=new DiagnosticBuffer();for(int i=0;i<1000;i++)b.append(i,"host","x".repeat(2048));assertTrue(b.size()<=512);assertTrue(b.text().length()<=263000);assertTrue(b.text().contains("999 host"));assertFalse(b.text().startsWith("0 host"));}
    @Test public void longErrorsCannotFloodDiagnosticBuffer(){var b=new DiagnosticBuffer();b.append(1,"protocol","x".repeat(100000));assertTrue(b.text().length()<2100);b.clear();assertEquals("",b.text());}
    @Test public void decoderReportsRejectedChecksumWithoutDumpingPayload(){var codec=new WireCodec(WireCodec.Dialect.BES);codec.offer(HexFormat.of().parseHex("BBECCC0002BEA3D7"));assertTrue(codec.diagnostics().contains("badChecksum=1"));assertFalse(codec.diagnostics().contains("BEA3"));}
}
