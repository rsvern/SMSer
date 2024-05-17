package com.example.posix.smser;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SMSer";
    private SharedPreferences sharedpreferences;

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

        UpdateServer(null);

        boolean isChecked = sharedpreferences.getString(SmsAsync.no_ok_resp, "false").equals("true");
        CheckBox cb = findViewById(R.id.checkbox1);
        cb.setChecked(isChecked);
        saveNoOkResp(isChecked);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                saveNoOkResp(isChecked);
            }
        });

        // self restart after error so retry last URL
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
            sharedpreferences = getApplicationContext().getSharedPreferences(SmsAsync.myprefs, 0);
            String savedRemote = sharedpreferences.getString(SmsAsync.server, "unset");
            Log.i(TAG, "savedRemote = " + savedRemote);
            if (savedRemote != "unset") {
                SmsAsync smsAsync = new SmsAsync(getApplicationContext());
                smsAsync.execute("http://" + savedRemote + "/rpi2b/cgi-bin/garagedoor.py?cmd=status&txtonly=1", to, "no");
            }
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

    public void saveNoOkResp(boolean isChecked) {
        SharedPreferences.Editor editor = sharedpreferences.edit();
        if (isChecked) {
            editor.putString(SmsAsync.no_ok_resp, "true");
        } else {
            editor.putString(SmsAsync.no_ok_resp, "false");
        }
        editor.apply();
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

    public String intToIp(int addr) {
        return  ((addr & 0xFF) + "." +
                ((addr >>>= 8) & 0xFF) + "." +
                ((addr >>>= 8) & 0xFF) + "." +
                ((addr >>>= 8) & 0xFF));
    }

    public void UpdateServer(View view) {
        Log.i(TAG, "update server information");

        EditText local = findViewById(R.id.tetherLocal);
        EditText remote = findViewById(R.id.tetherRemote);

        sharedpreferences = getApplicationContext().getSharedPreferences(SmsAsync.myprefs, 0);
        String savedRemote = sharedpreferences.getString(SmsAsync.server, "unset");
        Log.i(TAG, "savedRemote = " + savedRemote);

        String newRemote = remote.getText().toString();
        if (newRemote == null || newRemote.length() == 0) {
            newRemote = null;
        }
        Log.i(TAG, "newRemote = " + newRemote);

        local.getText().clear();
        remote.getText().clear();

        try {
            NetworkInterface ni = null;
            String ifname = null;
            InetAddress localAddr = null;

            // try USB tether first (rndis0)
            ifname = "rndis0";
            ni = NetworkInterface.getByName(ifname);
            Log.i(TAG, "ni(" + ifname + ") = " + ni);
            if (ni == null || !ni.isUp()) {
                // try wifi next (wlan0)
                ifname = "wlan0";
                ni = NetworkInterface.getByName(ifname);
                Log.i(TAG, "ni(" + ifname + ") = " + ni);
            }
            if (ni == null || !ni.isUp()) {
                local.setText("interface");
                remote.setText("failure");
                return;
            }

            List<InetAddress> addrs = Collections.list(ni.getInetAddresses());
            for (InetAddress addr : addrs) {
                Log.i(TAG, "ipAddress: " + addr.getHostAddress());
                if (addr instanceof Inet4Address) {
                    localAddr = addr;
                    local.setText(addr.getHostAddress());
                    // FIXME: might want to also save local address to persistent storage

                    //byte[] octets = localAddr.getAddress();
                    //Log.i(TAG, "last octet = " + octets[3]);
                    break;
                }
            }
            if (localAddr == null) {
                local.setText("missing");
                remote.setText("ipv4address");
                return;
            }

            if (ifname == "rndis0") { // USB tether
                // FIXME: currently no code to determine server IP address so make the edit text
                //        active so it can be set manually (then UPDATE pressed).
                remote.setEnabled(true);
                return;
            }

            if (ifname == "wlan0") { // WiFi interface is easy, assume gateway
                final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                DhcpInfo d = wm.getDhcpInfo();
                newRemote = intToIp(d.gateway);
            }

            if (newRemote != null) {
                Log.i(TAG, "savedRemote " + savedRemote + " -> " + newRemote);
                remote.setText(newRemote);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString(SmsAsync.server, newRemote);
                editor.apply();
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
        String permission4 = Manifest.permission.ACCESS_WIFI_STATE;

        String[] permission_list = new String[4];
        permission_list[0] = permission1;
        permission_list[1] = permission2;
        permission_list[2] = permission3;
        permission_list[3] = permission4;
        ActivityCompat.requestPermissions(this, permission_list, 2);
    }
}
