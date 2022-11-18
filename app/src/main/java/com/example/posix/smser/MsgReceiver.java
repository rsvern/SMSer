package com.example.posix.smser;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.SortedSet;
import java.util.TreeSet;

public class MsgReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSer";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            SmsMessage message = GetMessage(intent);
            String address = message.getOriginatingAddress();
            String body = message.getMessageBody().trim();
            // FIXME: this is a static address on current network (phone fixed at 192.168.1.20)
            String urlstr = "http://192.168.1.6/";

            Log.i(TAG, "Received SMS from: " + address);

            // FIXME: move all this preference junk into some other shared class thingy so it's
            //        not duplicated everywhere with hard coded values/counts/etc.
            SharedPreferences sharedpreferences = context.getSharedPreferences(SmsAsync.myprefs, 0);
            SortedSet<String> allowPhones = new TreeSet<>();
            for (int i = 0; i < 3; i++) {
                String allow = sharedpreferences.getString(SmsAsync.keybase+i, null);
                if (allow != null && !allow.isEmpty()) allowPhones.add(allow);
            }

            // No allow(s) set means anyone, otherwise only allowed (in theory)
            Log.d(TAG, "allowed: " + allowPhones);
            if ((allowPhones.isEmpty()) || allowPhones.contains(address)) {
                String firstword = (body.contains(" ") ? body.split(" ")[0] : body).toUpperCase();
                Log.i(TAG, "Allowed sender: " + address + ", Firstword: " + firstword);
                Log.d(TAG, "Act on body: " + body);
                if (firstword.equals("DAMNIT")) {
                    Log.i(TAG, "Attempting application restart");
                    doRestart(context);
                    return; // restart failed
                } else if (firstword.equals("ON") || firstword.equals("OFF")) {
                    urlstr += ("avr1913/MainZone/index.put.asp?cmd0=PutZone_OnOff/" + firstword +
                               "%26cmd1=PutMasterVolumeSet/-34%26ZoneName=ZONE2");
                } else {
                    urlstr += ("rpi2b/cgi-bin/garagedoor.py?txtonly=1&cmd=" + firstword);
                }
                SmsAsync smsAsync = new SmsAsync(context);
                smsAsync.execute(urlstr, address, "yes");
            } else {
                Log.i(TAG, "Ignoring sender: " + address);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error - exception somewhere");
            Log.e(TAG, "Exception", e);
        }
    }

    // doRestart() taken from StackOverflow:
    // https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
    // FIXME: Further google'n indicates System.exit() on Android may not be doing exactly what
    //        is needed with the other threads and things going on.  Seems to work at least some
    //        of the time for now.
    public static void doRestart(Context c) {
        try {
            //check if the context is given
            if (c != null) {
                //fetch the packagemanager so we can get the default launch activity
                // (you can replace this intent with any other activity if you want
                PackageManager pm = c.getPackageManager();
                //check if we got the PackageManager
                if (pm != null) {
                    //create the intent with the default start activity for your application
                    Intent mStartActivity = pm.getLaunchIntentForPackage(
                            c.getPackageName()
                    );
                    if (mStartActivity != null) {
                        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        // We use an AlarmManager to call this intent in 100ms
                        int mPendingIntentId = 223344;
                        PendingIntent mPendingIntent = PendingIntent
                                .getActivity(c, mPendingIntentId, mStartActivity,
                                        PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, mPendingIntent);
                        //kill the application
                        System.exit(0);
                    } else {
                        Log.e(TAG, "Was not able to restart application, mStartActivity null");
                    }
                } else {
                    Log.e(TAG, "Was not able to restart application, PM null");
                }
            } else {
                Log.e(TAG, "Was not able to restart application, Context null");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Was not able to restart application");
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
