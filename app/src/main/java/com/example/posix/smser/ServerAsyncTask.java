package com.example.posix.smser;

import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ServerAsyncTask extends AsyncTask<Socket, Void, String> {
    private static final String TAG = "SMSer";

    @Override
    protected String doInBackground(Socket... params) {
        String address = null;
        String msg = "";
        Socket mySocket = params[0];
        try {
            String line;
            PrintWriter out = new PrintWriter(mySocket.getOutputStream(), true);
            out.println("Hello from SMSer\r");
            BufferedReader br = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
            address = br.readLine();
            if (address != null && ((MainActivity.allowPhones == null) ||
                                    (MainActivity.allowPhones.contains(address)))) {
                out.println("Address OK\r");
            } else {
                Log.d(TAG, "address bad: " + address);
                out.println("Address BAD\r");
                mySocket.close();
                return "done";
            }
            while ((line = br.readLine()) != null) {
                if (line.equals(".")) {
                    Log.d(TAG, "received lone . - done reading");
                    break;
                } else {
                    msg += (line + "\n");
                }
            }
            Log.d(TAG, "tcp to: " + address);
            Log.d(TAG, "tcp msg: " + msg);
            if (line == null) {
                Log.d(TAG, "null: premature close");
                mySocket.close();
                return "done";
            }
            if (msg != null && msg.length() > 0) {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(address, null, msg, null, null);
                out.println("SMS sent OK\r");
            } else {
                out.println("SMS not sent\r");
            }
            mySocket.close();
        } catch (IOException e) {
            Log.d(TAG, "socket receive exception");
            e.printStackTrace();
        }
        return "done";
    }
}
