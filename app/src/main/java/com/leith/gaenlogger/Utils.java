package com.leith.gaenlogger;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

class Utils {
    private static Utils instance = null;

    static Utils getInstance(){
        if (instance == null) {
            instance = new Utils();
        }
        return instance;
    }

    boolean checkPerm(Activity activity, String permission) {
        if (activity == null) {
            Log.e("DL","checkPerm() called with activity eq null");
            return false;
        }
        int res = ContextCompat.checkSelfPermission(activity, permission);
        if (res != PackageManager.PERMISSION_GRANTED) {
            Log.i("DL", permission+" permission needed "+res+" ("+PackageManager.PERMISSION_DENIED+")");
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{permission}, 10);
            return false;
        } else {
            Log.d("DL",permission+" permission ok");
            return true;
        }
    }

    boolean checkScannerPerms(Activity activity) {
        if (activity == null) {
            Log.e("DL","checkScannerPerms() called with activity eq null");
            return false;
        }

        if (!Utils.getInstance().checkPerm(activity, Manifest.permission.BLUETOOTH)) {
            return false;
        }
        // need ACCESS_FINE_LOCATION to do a scan at all
        if (!Utils.getInstance().checkPerm(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            // need this extra permission on Android 10 to keep scan running in background thread (else it gets killed)
            return checkPerm(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        } else {
            Log.i("DL","SDK version "+Build.VERSION.SDK_INT+" <29, no need for ACCESS_BACKGROUND_LOCATION permission");
            return true;
        }
    }

    boolean startOpportunisticScanWrapper(Activity activity) {
        // start BLE scanning
        if (activity == null) {
            Log.e("DL","startOpportunisticScanWrapper() called with activity eq null");
            return false;
        }
        if (checkScannerPerms(activity)) {
            Log.d("DL", "startOpportunisticScanWrapper()");
            Intent intentKeepAwake = new Intent(activity, KeepAwakeService.class);
            intentKeepAwake.putExtra("cmd", "startOpportunisticScan");
            activity.startForegroundService(intentKeepAwake);
            return true;
        } else {
            return false;
        }
    }

    void stopOpportunisticScanWrapper(Activity activity) {
        if (activity == null) {
            return;
        }
        Log.d("DL", "stopOpportunisticScanWrapper()");
        Intent intentKeepAwake = new Intent(activity, KeepAwakeService.class);
        intentKeepAwake.putExtra("cmd", "stopOpportunisticScan");
        activity.startForegroundService(intentKeepAwake);
    }

    String datetimeString() {
        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.UK );
        return dateFormat.format(Date.from(Instant.now()));
    }

    void writeFile(Activity activity, String pathname, String fname, String msg, boolean log) {
        if (activity != null) { // otherwise, we just hope we have permissions already
            checkPerm(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        try {
            // log to /sdcard/SDLogging/
            File path = new File(pathname);
            if (!path.mkdirs()) {
                //Log.d("DL", "Problem creating folder "+path.getAbsolutePath()+", already exists?");
            }
            File scanfile = new File(path,fname);
            try {
                FileOutputStream file = new FileOutputStream(scanfile, true);
                DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.UK );
                String date = dateFormat.format(Date.from(Instant.now()));
                file.write((date+" ").getBytes());
                file.write((msg).getBytes());
                file.close();
            } catch (Exception e) {
                Log.e("DL","Problem writing to file: "+e.getMessage());
            }
        } catch (Exception e) {
            FileOutputStream logfile = null;
            Log.e("DL","Problem opening file: "+e.getMessage());
        }
        if (log) { // also echo to logcat
            Log.i("DL", msg);
        }
    }

    String pathname() {
        return Environment.getExternalStorageDirectory().getAbsolutePath()+"/GAENLogging";
    }

    String fnameDate() {
        DateFormat dateFormat = new SimpleDateFormat("ddMMyyyy", Locale.UK );
        return dateFormat.format(Date.from(Instant.now()));
    }

    String opportunisticScannerFilename(String rpiStr) {
         return "opportunistic_scanner_"+rpiStr+"_"+fnameDate()+".txt";
    }

    void tryUICallback(String callback, String msg, LocalBroadcastManager localBroadcastManager) {
        // execute callback on UI thread if possible, so UI updates
        if ((callback == null) || (localBroadcastManager == null)) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(callback);
        intent.putExtra("msg", msg);
        localBroadcastManager.sendBroadcast(intent);
    }
 }
