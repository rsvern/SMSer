package com.example.posix.smser;

import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SmsAsync extends AsyncTask<String, Void, Void> { // Params, Progress, Result
    private static final String TAG="SMSer";

    @Override
    protected Void doInBackground(String... args) {
        String urlstr = args[0];
        String address = args[1];
        String reply;
        HttpURLConnection conn = null;

        Log.i(TAG, "url: " + urlstr);
        try {
            URL url = new URL(urlstr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(6000);
            conn.setConnectTimeout(6000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.setDoOutput(false);
            InputStream in = conn.getInputStream();
            reply = convertStreamToString(in);
            Log.i(TAG, "Success getting URL [" + reply.length() + "]");
            reply = truncate(reply, 160, true);
            if (reply == null || reply.length() == 0) {
                reply = conn.getResponseMessage();
                Log.i(TAG, "response: " + reply);
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL exception");
            reply = "Malformed URL exception.";
        } catch (SocketTimeoutException e) {
            reply = "Socket timeout getting URL.";
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            reply = "IOException: status ";
            if (conn != null) {
                try {
                    int status = conn.getResponseCode();
                    Log.e(TAG, "conn response status: " + status);
                    reply += status;
                } catch (IOException e2) {
                    Log.e(TAG, "IOException2", e2);
                    reply += "<unknown>";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected URL exception: ", e);
            reply = "UnexpectedURLException: " + e.getMessage();
        }

        Log.i(TAG, "reply: " + reply);
        if (reply != null && reply.length() > 0) {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(address, null, reply, null, null);
        }
        return null;
    }

    private static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "IO Exception 1");
            //Log.e(TAG, e.getMessage(), e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "IO Exception 2");
                //Log.e(TAG, e.getMessage(), e);
            }
        }
        return sb.toString();
    }

    private static String truncate(String str, int len, boolean pre) {
        if (str.length() <= len) {
            return str;
        }
        if (pre) {
            return str.substring(0, (len - 3)) + "...";
        } else {
            return "..." + str.substring(str.length() - len + 3);
        }
    }
}