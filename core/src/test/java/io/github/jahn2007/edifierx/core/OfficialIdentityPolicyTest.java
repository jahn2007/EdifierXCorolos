package io.github.jahn2007.edifierx.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class OfficialIdentityPolicyTest {
    private static final String EVO="00001e00-0000-1000-8000-00805f9b34fb";
    @Test public void officialPackageAndKnownCertificateBothRequired(){assertTrue(OfficialIdentityPolicy.trusted(OfficialIdentityPolicy.PACKAGE,List.of(OfficialIdentityPolicy.CERTIFICATE)));}
    @Test public void samePackageResignedByAnotherKeyRejected(){assertFalse(OfficialIdentityPolicy.trusted(OfficialIdentityPolicy.PACKAGE,List.of("0".repeat(64))));}
    @Test public void certificateAloneDoesNotAuthorizeAnotherPackage(){assertFalse(OfficialIdentityPolicy.trusted("com.example.other",List.of(OfficialIdentityPolicy.CERTIFICATE)));}
    @Test public void missingCertificateRejected(){assertFalse(OfficialIdentityPolicy.trusted(OfficialIdentityPolicy.PACKAGE,List.of()));}
    @Test public void knownProductAndProtocolCanOfferEvoCandidate(){assertEquals("evo-pro",OfficialIdentityPolicy.profile(EVO,2,16).orElseThrow().id());}
    @Test public void sharedSppIsNotAnImportedProductIdentity(){assertTrue(OfficialIdentityPolicy.profile(DeviceProfile.SPP,2,16).isEmpty());}
    @Test public void unsupportedProtocolAndEncryptionCannotBeImported(){assertTrue(OfficialIdentityPolicy.profile(EVO,1,16).isEmpty());assertTrue(OfficialIdentityPolicy.profile(EVO,2,49).isEmpty());assertTrue(OfficialIdentityPolicy.profile(EVO,2,0).isEmpty());}
    @Test public void unknownProductNeverCreatesAProfile(){assertTrue(OfficialIdentityPolicy.profile("0000ffff-0000-1000-8000-00805f9b34fb",2,16).isEmpty());assertTrue(OfficialIdentityPolicy.profile(null,2,16).isEmpty());}
    @Test public void observationAddressMatchDoesNotReplaceFullIdentityChecks(){byte[] data=HexFormat.of().parseHex("123456789ABC");assertTrue(AdvertisementIdentity.targetAddress("12:34:56:78:9A:BC",data));assertFalse(AdvertisementIdentity.identify("12:34:56:78:9A:BC",List.of(EVO),2016,data).matched());}
    @Test public void observationCannotMergeNeighboringOrInvalidAddresses(){byte[] data=HexFormat.of().parseHex("123456789ABC0210");assertFalse(AdvertisementIdentity.targetAddress("12:34:56:78:9A:BD",data));assertFalse(AdvertisementIdentity.targetAddress(null,data));assertFalse(AdvertisementIdentity.targetAddress("12:34:56:78:9A:BC",new byte[5]));}
}
