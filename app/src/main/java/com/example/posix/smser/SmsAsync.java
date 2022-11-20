package com.example.posix.smser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static com.example.posix.smser.MsgReceiver.doRestart;

public class SmsAsync extends AsyncTask<String, Void, Void> { // Params, Progress, Result
    private static final String TAG="SMSer";
    private final WeakReference<Context> mContextRef;

    public static final String myprefs = "myprefs";
    public static final String keybase = "allowKey";
    public static final String retry_addr = "retryAddress";
    public static final String retry_url = "retryUrl";
    public static final String server = "remoteAddress";

    public SmsAsync(final Context context) {
        mContextRef = new WeakReference<>(context);
    }

    @Override
    protected Void doInBackground(String... args) {
        String urlstr = args[0];
        String address = args[1];
        String restart = args[2];
        String reply;
        HttpURLConnection conn = null;
        SmsManager smsManager = SmsManager.getDefault();

        Log.i(TAG, "url: " + urlstr);
        try {
            URL url = new URL(urlstr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(3000);
            conn.setConnectTimeout(3000);
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
            // This seems to happen occasionally even though everything is fine (network wise) and
            //  only seems to recover by restarting the application.
            if (restart.equalsIgnoreCase("yes")) {
                Log.e(TAG, "Attempting a restart with retry.");
                reply = "URL timeout.  Restart with retry in progress...";
                smsManager.sendTextMessage(address, null, reply, null, null);
                Context context = mContextRef.get();
                if (context != null) {
                    SharedPreferences sharedpreferences = context.getSharedPreferences(myprefs, 0);
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    editor.putString(retry_addr, address);
                    editor.putString(retry_url, urlstr);
                    editor.commit();
                    // FIXME: What termination callbacks get called and would it be better to
                    //        cleanup and close the socket server first (somehow).
                    doRestart(context);
                    Log.e(TAG, "Failed to restart, retry not possible");
                    editor = sharedpreferences.edit();
                    editor.remove(retry_addr);
                    editor.remove(retry_url);
                    editor.apply();
                } else {
                    Log.e(TAG, "Failed to retrieve context, retry not possible.");
                }
            }
            Log.e(TAG, "Socket timeout exception: not restarting");
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
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        Log.i(TAG, "reply: " + reply);
        if (reply != null && reply.length() > 0) {
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