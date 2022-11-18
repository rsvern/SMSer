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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SMSer";
    private SharedPreferences sharedpreferences;
    private static final String myprefs = "myprefs";
    private static final String keybase = "allowKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // To allow network activity on main thread (rather than SmsAsync)
        // if (android.os.Build.VERSION.SDK_INT > 9) {
        //     StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        //     StrictMode.setThreadPolicy(policy);
        // }
        requestAllPermissions();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Creating ServerSocket on 8484");
                    ServerSocket socServer = new ServerSocket(8484);
                    Socket socClient;
                    while (true) {
                        socClient = socServer.accept();
                        //For each client new instance of AsyncTask will be created
                        Log.d(TAG, "creating new ServerAsyncTask");
                        ServerAsyncTask serverAsyncTask = new ServerAsyncTask();
                        serverAsyncTask.execute(socClient);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "exception on ServerSocket");
                    e.printStackTrace();
                }
            }
        }).start();

        EditText local = findViewById(R.id.tetherLocal);
        EditText remote = findViewById(R.id.tetherRemote);
        local.setEnabled(false);
        remote.setEnabled(false);

        EditText[] edits = new EditText[]{
                findViewById(R.id.phoneOut2),
                findViewById(R.id.phoneOut3),
                findViewById(R.id.phoneOut4),
        };
        sharedpreferences = getApplicationContext().getSharedPreferences(SmsAsync.myprefs, 0);
        SortedSet<String> allowPhones = new TreeSet<>();
        for (int i = 0; i < 3; i++) {
            String allow = sharedpreferences.getString(SmsAsync.keybase+i, null);
            edits[i].setText(allow);
            if (allow != null && !allow.isEmpty()) allowPhones.add(allow);
        }
        Log.i(TAG, "allowPhones: " + allowPhones);

        String address = sharedpreferences.getString(SmsAsync.retry_addr, null);
        String urlstr = sharedpreferences.getString(SmsAsync.retry_url, null);
        if ((address != null) && (urlstr != null)) {
            Log.i(TAG, "retrying previous URL: " + urlstr);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.remove(SmsAsync.retry_addr);
            editor.remove(SmsAsync.retry_url);
            editor.apply();
            SmsAsync smsAsync = new SmsAsync(getApplicationContext());
            smsAsync.execute(urlstr, address, "no");
        }
    }

    // test SmsAsync handler
    public void SmsAsyncTest(View view) {
        EditText edit = findViewById(R.id.phoneOut);
        String to = edit.getText().toString();
        if (to.isEmpty()) {
            Log.e(TAG, "Empty phone number");
        } else {
            SmsAsync smsAsync = new SmsAsync(getApplicationContext());
            smsAsync.execute("http://192.168.1.6/rpi2b/cgi-bin/garagedoor.py?cmd=status&txtonly=1", to);
            edit.getText().clear();
        }
    }

    // test SmsSend
    public void SmsSendTest(View view) {
        EditText edit = findViewById(R.id.phoneOut);
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
                findViewById(R.id.phoneOut2),
                findViewById(R.id.phoneOut3),
                findViewById(R.id.phoneOut4),
        };
        SortedSet<String> allowPhones = new TreeSet<>();
        SharedPreferences.Editor editor = sharedpreferences.edit();
        for (int i = 0; i < 3; i++) {
            String allow = edits[i].getText().toString();
            edits[i].getText().clear();
            editor.putString(SmsAsync.keybase+i, null);
            if (!allow.isEmpty()) allowPhones.add(allow);
        }
        Log.i(TAG, "allowPhones: " + allowPhones);
        int i = 0;
        for (String allow : allowPhones) {
            edits[i].setText(allow);
            editor.putString(SmsAsync.keybase+i, allow);
            i++;
        }
        editor.apply();
    }

    public void UpdateTether(View view) {
        Log.i(TAG, "update tether information");

        EditText local = findViewById(R.id.tetherLocal);
        EditText remote = findViewById(R.id.tetherRemote);

        local.getText().clear();
        remote.getText().clear();

        try {
            String ifname = "wlan0"; // rndis0
            NetworkInterface ni = NetworkInterface.getByName(ifname);
            Log.i(TAG, "ni: " + ni);
            if (ni != null && ni.isUp()) {
                List<InetAddress> addrs = Collections.list(ni.getInetAddresses());
                for (InetAddress addr : addrs) {
                    Log.i(TAG, "ipAddress: " + addr.getHostAddress());
                    if (addr instanceof Inet4Address) {
                        byte[] octets = addr.getAddress();
                        local.setText(addr.getHostAddress());
                        Log.i(TAG, "last octet = " + octets[3]);

                        // FIXME: find remote or kick off finding remote
                        return;
                    }
                }
                Log.i(TAG, "no IPv4 address found");
                local.setText("no IPv4");
                remote.setText("address");
            } else {
                Log.i(TAG, "network " + ifname + " interface not found");
                local.setText(ifname);
                remote.setText("missing");
            }
        } catch (SocketException se) {
            Log.e(TAG, "socket exception getting network interface");
            local.setText("error");
            remote.setText("exception");
        }
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
