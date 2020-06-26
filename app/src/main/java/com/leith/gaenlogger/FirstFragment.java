package com.leith.gaenlogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FirstFragment extends Fragment {

    private TextView textBox;
    private SharedPreferences sharedPref;
    private Intent intentKeepAwake = null;
    private final int REQUEST_ENABLE_BT = 11;
    private Activity activity = null;
    private LocalBroadcastManager localBroadcastManager = null;
    private Utils u = Utils.getInstance();
    private OpportunisticScanRepository opportunisticScandb = null;

    // configuration flags
    private static final boolean DEBUGGING = false;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    void updateText() {
        new Thread( new Runnable() { @Override public void run() {
            // get results over last day
            List<OpportunisticScanEntity> res
                    = opportunisticScandb.opportunisticScanDao().getSince(Instant.now().getEpochSecond()-24*60*60);
            String output="";
            for (int i=0; i<res.size(); i=i+1) {
                OpportunisticScanEntity r = res.get(i);
                //DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.UK );
                DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.UK );
                String d = dateFormat.format(Date.from(Instant.ofEpochSecond(r.timestamp)));
                output = output+r.rpi+" (t: "+d+" lat: "+r.latitude+" long: "+r.longitude+")\n";
            }
            final String output_display = output;
            getActivity().runOnUiThread( new Runnable() {
                @Override
                public void run() {
                    textBox.setText(output_display);
                }
            });
        } } ).start();
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Debug.println("mReceiver() with action "+action);
            if (action == null) { return; }
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                //event where bluetooth state changes, e.g. switched on/off
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                Debug.println("bluetooth state "+state);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Debug.println("Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        //Debug.println("Turning Bluetooth off...");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Debug.println("Bluetooth on");
                        // start/restart background scanner is enabled
                        u.syncOpportunisticScannerState(getActivity());
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        //Debug.println("Turning Bluetooth on...");
                        break;
                }
            } else if (action.equals(BLELogger.bleOffCallback)) {
                Debug.println("bleOffCallback");
                Debug.println( "Ask user to turn bluetooth on");
                // ask user to switch on bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else if (action.equals(BLELogger.scanCallback)) {
                // new scan result update UI
                Log.d("DL","new scan result");
                updateText();
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Debug.println( "First fragment onActivityResult() reqCode " + requestCode + " "+resultCode);
    }

    @SuppressLint("BatteryLife")
    private void excludeFromBatteryOptimization() {
        // disable battery optimisation for this app, so we don't get put to sleep quite so quickly
        u.checkPerm(getActivity(),Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);

        Debug.println( "excludeFromBatteryOptimization");
        PowerManager powerManager =
                (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        if (powerManager == null) {
            Log.e("DL", "Problem, powerManager is null");
            return;
        }
        String packageName = activity.getPackageName();
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Debug.println("Not on Battery Optimization whitelist");
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            activity.startActivity(intent);
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener configListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Debug.println("onSharedPreferenceChanged");
            u.syncOpportunisticScannerState(getActivity());
        }
    };

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        activity = getActivity();
        sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        sharedPref.registerOnSharedPreferenceChangeListener(configListener);

        if (opportunisticScandb == null) {
            opportunisticScandb = OpportunisticScanRepository.getInstance(activity);
        }

        Button exitButton = view.findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activity.stopService(intentKeepAwake);
                activity.finish();
            }
        });

        textBox = view.findViewById(R.id.textBox);
        textBox.setMovementMethod(new ScrollingMovementMethod());
        updateText();

        IntentFilter filter = new IntentFilter(BLELogger.scanCallback);
        filter.addAction(BLELogger.bleOffCallback);
        localBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
        localBroadcastManager.registerReceiver(mReceiver, filter);
        // to catch bluetooth state changes we need to use register receiver globally
        // *not* with localBroadcastManager (else we don't see broadcasts)
        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        getActivity().registerReceiver(mReceiver, filter);

        // ask permission to disable battery optimisation (which puts app to sleep)
        excludeFromBatteryOptimization();
        // ask for permission to write to storage
        u.checkPerm(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        // fire up scanner
        u.checkScannerPerms(getActivity());
        u.syncOpportunisticScannerState(getActivity());

    }

    @Override
    public void onResume() {
        super.onResume();
        u.checkScannerPerms(getActivity());
        u.checkPerm(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        updateText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            activity.unregisterReceiver(mReceiver);
            localBroadcastManager.unregisterReceiver(mReceiver);
        }  catch (Exception e) {

         }
    }

    /***************************************************************************************/
    // debugging
    private static class Debug {
        static void println(String s) {
            if (DEBUGGING) Log.d("DL","FirstFrag: "+s);
        }
    }
}
