package com.example.posix.smser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.SortedSet;
import java.util.TreeSet;

public class MsgReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSer";
    private static final String myprefs = "myprefs";
    private static final String keybase = "allowKey";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            SmsMessage message = GetMessage(intent);
            String address = message.getOriginatingAddress();
            String body = message.getMessageBody().trim();
            // IP address fixed by using a static IP address on the host.
            String urlstr = "http://192.168.42.12/";

            Log.i(TAG, "Received SMS from: " + address);

            // FIXME: move all this preference junk into some other shared class thingy so it's
            //        not duplicated everywhere with hard coded values/counts/etc.
            SharedPreferences sharedpreferences = context.getSharedPreferences(myprefs, 0);
            SortedSet<String> allowPhones = new TreeSet<>();
            for (int i = 0; i < 3; i++) {
                String allow = sharedpreferences.getString(keybase+i, null);
                if (allow != null && !allow.isEmpty()) allowPhones.add(allow);
            }

            // No allow set means anyone, otherwise only allowed (in theory)
            Log.d(TAG, "allowed: " + allowPhones);
            if ((allowPhones.isEmpty()) || allowPhones.contains(address)) {
                String firstword = (body.contains(" ") ? body.split(" ")[0] : body).toUpperCase();
                Log.i(TAG, "Awesome sender: " + address + ", Firstword: " + firstword);
                Log.d(TAG, "Act on body: " + body);
                if (firstword.equals("ON") || firstword.equals("OFF")) {
                    urlstr += ("avr1913/MainZone/index.put.asp?cmd0=PutZone_OnOff/" + firstword +
                               "%26cmd1=PutMasterVolumeSet/-34%26ZoneName=ZONE2");
                } else {
                    urlstr += ("rpi2b/cgi-bin/garagedoor.py?txtonly=1&cmd=" + firstword);
                }
                SmsAsync smsAsync = new SmsAsync();
                smsAsync.execute(urlstr, address);
            } else {
                Log.i(TAG, "Ignoring sender: " + address);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error - exception somewhere");
            Log.e(TAG, "Exception", e);
        }
    }

    private SmsMessage GetMessage(Intent intent)
    {
        SmsMessage message;
        if (Build.VERSION.SDK_INT >= 19) {
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            message = msgs[0];
        } else {
            Bundle bundle = intent.getExtras();
            Object[] pdus = (Object[]) bundle.get("pdus");
            message = SmsMessage.createFromPdu((byte[]) pdus[0]);
        }
        return message;
    }

}
