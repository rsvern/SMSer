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
        String address;
        String msg = "";
        Socket mySocket = params[0];
        try {
            String line;
            PrintWriter out = new PrintWriter(mySocket.getOutputStream(), true);
            out.println("Hello from SMSer\r");
            BufferedReader br = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
            address = br.readLine();
            if (address != null) {
                // FIXME: put back check to only allow sending to allowed phones once it's correct
                //        in MsgReceiver.java.  Ideally put it in one shared place/class to avoid
                //        duplicating code (including in MainActivity.java)
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
            mySocket.close();
            if (line == null) {
                Log.d(TAG, "null: premature close");
                return "done";
            }
            if (msg.length() > 0) {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(address, null, msg, null, null);
                out.println("SMS sent OK\r");
            } else {
                out.println("SMS not sent\r");
            }
        } catch (IOException e) {
            Log.d(TAG, "socket receive exception");
            e.printStackTrace();
        }
        return "done";
    }
}
