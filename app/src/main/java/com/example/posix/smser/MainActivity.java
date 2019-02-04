package com.example.posix.smser;

import android.Manifest;
import android.content.SharedPreferences;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.SortedSet;
import java.util.TreeSet;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SMSer";
    private SharedPreferences sharedpreferences;
    private static final String myprefs = "myprefs";
    private static final String keybase = "allowKey";

    public static SortedSet<String> allowPhones;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // To allow network activity on main thread (rather than SmsAsync)
        //if (android.os.Build.VERSION.SDK_INT > 9) {
        //    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        //    StrictMode.setThreadPolicy(policy);
        //}
        requestAllPermissions();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Creating ServerSocket on 8484");
                    ServerSocket socServer = new ServerSocket(8484);
                    Socket socClient = null;
                    while (true) {
                        socClient = socServer.accept();
                        //For each client new instance of AsyncTask will be created
                        Log.d(TAG, "creating new ServerAsyncTask");
                        ServerAsyncTask serverAsyncTask = new ServerAsyncTask();
                        serverAsyncTask.execute(new Socket[] {socClient});
                    }
                } catch (IOException e) {
                    Log.e(TAG, "exception on ServerSocket");
                    e.printStackTrace();
                }
            }
        }).start();
        sharedpreferences = getApplicationContext().getSharedPreferences(myprefs, 0);
        EditText[] edits = new EditText[]{
                (EditText) findViewById(R.id.phoneOut2),
                (EditText) findViewById(R.id.phoneOut3),
                (EditText) findViewById(R.id.phoneOut4),
        };
        allowPhones = new TreeSet<String>();
        SharedPreferences.Editor editor = sharedpreferences.edit();
        for (int i = 0; i < 3; i++) {
            String allow = sharedpreferences.getString(keybase+i, null);
            edits[i].setText(allow);
            if (allow != null && !allow.isEmpty()) allowPhones.add(allow);
        }
        Log.i(TAG, "allowPhones: " + allowPhones);
    }

    // test SmsAsync handler
    public void SmsAsyncTest(View view) {
        EditText edit = (EditText)findViewById(R.id.phoneOut);
        String to = edit.getText().toString();
        if (to.isEmpty()) {
            Log.e(TAG, "Empty phone number");
        } else {
            SmsAsync smsAsync = new SmsAsync();
            smsAsync.execute("http://192.168.1.3/rpi2b/cgi-bin/garagedoor.py?cmd=status&txtonly=1", to);
            edit.getText().clear();
        }
    }

    // test SmsSend
    public void SmsSendTest(View view) {
        EditText edit = (EditText)findViewById(R.id.phoneOut);
        String to = edit.getText().toString();
        if (to.isEmpty()) {
            Log.e(TAG, "Empty phone number");
        } else {
            String msg = "Test SMS send message";
            Log.i(TAG, "sending test SMS to: " + to);
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(to, null, msg, null, null);
            edit.getText().clear();
        }
    }

    public void SavePhone(View view) {
        EditText[] edits = new EditText[]{
                (EditText) findViewById(R.id.phoneOut2),
                (EditText) findViewById(R.id.phoneOut3),
                (EditText) findViewById(R.id.phoneOut4),
        };
        allowPhones = new TreeSet<String>();
        SharedPreferences.Editor editor = sharedpreferences.edit();
        for (int i = 0; i < 3; i++) {
            String allow = edits[i].getText().toString();
            edits[i].getText().clear();
            editor.putString(keybase+i, null);
            if (!allow.isEmpty()) allowPhones.add(allow);
        }
        Log.i(TAG, "allowPhones: " + allowPhones);
        int i = 0;
        for (String allow : allowPhones) {
            edits[i].setText(allow);
            editor.putString(keybase+i, allow);
            i++;
        }
        editor.commit();
    }

    private void requestAllPermissions() {
        String permission1 = Manifest.permission.RECEIVE_SMS;
        String permission2 = Manifest.permission.SEND_SMS;
        String permission3 = Manifest.permission.INTERNET;

        String[] permission_list = new String[3];
        permission_list[0] = permission1;
        permission_list[1] = permission2;
        permission_list[2] = permission3;
        ActivityCompat.requestPermissions(this, permission_list, 2);
    }
}
