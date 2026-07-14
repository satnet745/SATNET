package org.servalproject.bitcoin.lightning;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LightningPaymentHandlerTest {

    @Test
    public void generatesAndParsesInvoiceRoundTrip() {
        LightningPaymentHandler handler = new LightningPaymentHandler();
        String invoice = handler.generateInvoice(1000L, "coffee", 300);
        assertTrue(invoice.startsWith("lnsatnet1."));

        LightningPaymentHandler.LightningInvoice parsed = handler.parseInvoice(invoice);
        assertEquals(1000L, parsed.amountMsat);
        assertEquals("coffee", parsed.description);
        assertTrue(parsed.expiryTime > (System.currentTimeMillis() / 1000L));
        assertTrue(parsed.payeeNodeId != null && !parsed.payeeNodeId.isEmpty());
    }

    @Test
    public void queuesAndBroadcastsPayments() {
        LightningPaymentHandler handler = new LightningPaymentHandler();
        String invoice = handler.generateInvoice(2500L, "voucher redemption", 300);

        handler.queueOfflinePayment(invoice, 2500L);
        assertEquals(1, handler.getQueuedPaymentCount());

        handler.broadcastQueuedPayments();
        assertEquals(0, handler.getQueuedPaymentCount());
    }
}

