package com.example.posix.smser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class BinarySmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSer";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();

        if (null != bundle) {
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);

            for (int i=0; i<msgs.length; i++) {
                SmsMessage msg = msgs[i];
                String address = msg.getOriginatingAddress();
                byte[] data = msg.getUserData();

                Log.i(TAG, "Received binary SMS from: " + address);
                Log.i(TAG, "data length: " + data.length);
            }
        }
    }
}