package org.servalproject.relay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.servalproject.batphone.CallHandler;

public class SmsRelayReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsRelayReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        Object[] pdus = (Object[]) extras.get("pdus");
        String format = extras.getString("format");
        if (pdus == null || pdus.length == 0) {
            return;
        }

        StringBuilder body = new StringBuilder();
        String sender = null;
        for (Object pdu : pdus) {
            try {
                SmsMessage message = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? SmsMessage.createFromPdu((byte[]) pdu, format)
                        : SmsMessage.createFromPdu((byte[]) pdu);
                if (message == null) {
                    continue;
                }
                if (sender == null) {
                    sender = message.getOriginatingAddress();
                }
                body.append(message.getMessageBody());
            } catch (Exception e) {
                Log.w(TAG, "Unable to parse SMS relay PDU", e);
            }
        }

        if (body.length() == 0) {
            return;
        }

        String messageBody = body.toString();
        SmsRelayClient.getInstance(context.getApplicationContext()).handleReceivedSms(sender, messageBody);
        try {
            if (messageBody.startsWith(RelayPacket.MAGIC + "|")) {
                CallHandler.handleIncomingRelayPacket(RelayPacket.decode(messageBody.trim()));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to dispatch SMS relay packet", e);
        }
    }
}


