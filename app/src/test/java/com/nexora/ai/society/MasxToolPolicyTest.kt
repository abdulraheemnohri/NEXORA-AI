package com.nexora.ai.society

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasxToolPolicyTest {
    @Test fun approvalGrantIsBoundToOneInvocationAndCannotBeReplayed() {
        val grant=MasxToolApprovalIssuer().approveAfterExplicitUserConfirmation("invoke-1")
        assertTrue(grant.consume("invoke-1"))
        assertEquals(false,grant.consume("invoke-1"))
        assertEquals(false,grant.consume("invoke-2"))
    }
}
