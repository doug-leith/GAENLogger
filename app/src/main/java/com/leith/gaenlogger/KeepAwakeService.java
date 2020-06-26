package com.leith.gaenlogger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// see https://developer.android.com/reference/android/app/Service#startForeground(int,%20android.app.Notification)

public class KeepAwakeService extends Service {

    private NotificationManager mNM;
    private final int NOTIFICATION = 771579;
    private LocalBroadcastManager localBroadcastManager = null;
    private BLELogger ble = null;
    private Context context = null;
    private Notification notification = null;
    private static final boolean DEBUGGING = false;  // generate extra debug output ?

    class LocalBinder extends Binder {
        KeepAwakeService getService() {
            return KeepAwakeService.this;
        }
    }

    @Override
    public void onCreate() {
        Debug.println( "Keep awake service onCreate()");
        context  = this.getApplicationContext();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        if (ble == null) {
            ble = BLELogger.getInstance(context ,localBroadcastManager);
        }
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        CharSequence name = "Keep Awake";
        NotificationChannel channel = new NotificationChannel("Keep Awake Channel",
                name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Dummy notification to keep app awake");
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setShowBadge(false);
        mNM.createNotificationChannel(channel);

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Debug.println( "Keep awake service received start id " + startId + ": " + intent);
        if (intent==null) {
            // service has been killed and restarted by system, sigh
            Log.i("DL","KeepAwakeService restarted by system");
            // let's assume we were running adverts and restart them ...
            return START_STICKY;
        }
        String cmd = intent.getStringExtra("cmd");
        Log.i("DL", "cmd "+cmd);
        if (cmd != null) {
            switch(cmd) {
                 case "startOpportunisticScan":
                    Log.d("DL","startOpportunisticScan");
                    ble.startOpportunisticScan();
                    break;
                case "stopOpportunisticScan":
                    ble.stopOpportunisticScan();
                    break;
                default:
                    Log.e("DL","KeepAwakeService received unknown command "+cmd);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // called when system stops service, so should be rare
        mNM.cancel(NOTIFICATION);
        // Tell the user we stopped.
        //Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = "Keep Awake";

        //The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
       notification = new Notification.Builder(this, "Keep Awake Channel")
                .setSmallIcon(R.drawable.ic_notification)  // the status icon
                .setTicker(text)  // the status text
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setContentTitle("BLEAdvertiser")  // the label of the entry
                .setContentText("Dummy notification to keep app awake")  // the contents of the entry
                .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                .build();

        // Send the notification.
        startForeground(NOTIFICATION, notification);
    }

    /***************************************************************************************/
    // debugging
    private static class Debug {
        static void println(String s) {
            if (DEBUGGING) Log.d("DL","KeepAwakeService: "+s);
        }
    }
}
