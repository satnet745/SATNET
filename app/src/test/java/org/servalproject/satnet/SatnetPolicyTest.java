package org.servalproject.satnet;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SatnetPolicyTest {

    @Test
    public void buildPolicyIsCompliantForDefaultConfiguration() {
        assertTrue(SatnetPolicy.isBuildPolicyCompliant());
    }

    @Test
    public void policyViolationReasonIsNullWhenDefaultsAreSafe() {
        assertNull(SatnetPolicy.getPolicyViolationReason());
    }

    @Test
    public void defaultPilotTestnetConfigurationIsPermitted() {
        assertTrue(SatnetPolicy.isMainnetSettlementPermitted());
    }
}



