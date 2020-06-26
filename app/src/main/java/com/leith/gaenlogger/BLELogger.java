package com.leith.gaenlogger;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class BLELogger {

    private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private Utils u = Utils.getInstance();
    static String bleOffCallback = "BLEOff";
    static String scanCallback = "scanResult";
    private LocalBroadcastManager localBroadcastManager;
    private Cipher cipherCTR = null;

    //opportunistic scanner
    private MyOpportunisticScanCallback opportunisticScanCallback = null;
    private OpportunisticScanRepository opportunisticScandb = null;

    // configuration flags
    private static final boolean DEBUGGING = false;  // generate extra debug output ?
    private final boolean restartCTR = false; // false = keep CTR counter rolling

    private static BLELogger instance = null;

    static BLELogger getInstance(Context c, LocalBroadcastManager b) {
        if (instance == null) {
            instance = new BLELogger(c,b);
        }
        return instance;
    }

    BLELogger(Context c, LocalBroadcastManager b) {
        localBroadcastManager = b;
        if (opportunisticScandb == null) {
            opportunisticScandb = OpportunisticScanRepository.getInstance(c);
        }
    }

    static String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    static String encodeHexString(byte[] byteArray) {
        StringBuilder hexStringBuffer = new StringBuilder();
        for (byte b : byteArray) {
            hexStringBuffer.append(byteToHex(b));
        }
        return hexStringBuffer.toString();
    }

    static String encodeDecString(byte[] byteArray) {
        int[] decBuffer = new int[byteArray.length];
        for (int i = 0; i < byteArray.length; i++) {
            decBuffer[i] = (int)(byteArray[i]);
        }
        return Arrays.toString(decBuffer);
    }

    static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private byte[] newRPIK(byte[] TEK) {
        // get RPI key
        //RPIKi ← HKDF(teki, NULL, UTF8("EN-RPIK"),16)
        //key, salt, info, outputlen
        try {
            Hkdf hkdf = new Hkdf(Hash.SHA256,null);
            SecretKey prk = hkdf.extract(null, TEK);
            // extract
            byte[] temp = hkdf.expand(prk, "EN-RPIK".getBytes(StandardCharsets.UTF_8), 16);
            byte[] RPIK = new byte[16];
            System.arraycopy(temp, 0, RPIK, 0, 16);
            Debug.println("RPIK "+ encodeHexString(RPIK));
            return RPIK;
        } catch (Exception e) {
            Log.e("DL", "problem getting SHA-256 hash for RPIK: "+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private byte[] getRPI(byte[] RPIK, byte[] interval) {
        //RPIi,j ← AES128(RPIKi, PaddedDataj)
        //Key, Data
        try {
            @SuppressLint("GetInstance") Cipher cipher = Cipher.getInstance("AES_128/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(RPIK, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] paddedData = new byte[16];
            byte[] salt = "EN-RPI".getBytes(StandardCharsets.UTF_8);
            for(int i = 0; i < 6; i++) { paddedData[i]=salt[i]; }
            for(int i = 6; i < 12; i++) { paddedData[i]=0; }
            for(int i = 12; i < 16; i++) { paddedData[i]=interval[i-12]; }

            byte[] RPI = cipher.doFinal(paddedData,0,16);
            u.writeFile(null,u.pathname(),u.logFilename(),
                    "RPI "+ encodeHexString(RPI)+"\n",true);
            return RPI;
        } catch (Exception e) {
            Log.e("DL", "problem getting AES-128 hash for RPI: "+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private byte[] newAEMK(byte[] TEK) {
        //Associated Encrypted Metadata Key
        //AEMKi ← HKDF(teki, NULL, UTF8("EN-AEMK"),16)
        //Key, Salt, Info, OutputLength)
        try {
            Hkdf hkdf = new Hkdf(Hash.SHA256,null);
            SecretKey prk = hkdf.extract(null, TEK);
            // extract
            byte[] temp = hkdf.expand(prk, "EN-AEMK".getBytes(StandardCharsets.UTF_8), 16);
            byte[] AEMK = new byte[16];
            for (int i=0; i<16; i++) {
                AEMK[i]= temp[i];
            }
            // initialise a new AES-CTR encoder for use in getAEM()
            if (!restartCTR) {
                cipherCTR = Cipher.getInstance("AES/CTR/NoPadding");
            }
            Debug.println("AEMK "+ encodeHexString(AEMK));
            return AEMK;
        } catch (Exception e) {
            Log.e("DL", "problem getting SHA-256 hash for AEMK: "+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    byte[] decryptAEM(byte[] TEK, byte[] RPI, byte[] AEM) {
        // extract metadata from beacon encrypted AEM
        try {
            Cipher cipherCTR = Cipher.getInstance("AES/CTR/NoPadding");
            byte[] AEMK = newAEMK(TEK);
            SecretKeySpec keySpec = new SecretKeySpec(AEMK, "AES");
            cipherCTR.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(RPI));
            byte[] metadata = cipherCTR.doFinal(AEM,0,4);
            Debug.println("decrypted metadata "+ encodeHexString(metadata));
            return metadata;
        } catch (Exception e) {
            Log.e("DL", "problem getting AES-128 hash for AEM: "+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // opportunistic scanner, can be left running all the time
    void startOpportunisticScan() {
        // is bluetooth switched on?
        if (!adapter.isEnabled()) {
            Log.e("DL", "start opportunistic scanner called with bluetooth off.");
            u.tryUICallback(bleOffCallback,"",localBroadcastManager);
            return;
        }
        if (opportunisticScanCallback != null) {
            Debug.println("start opportunistic scanner called with opportunistic scanner already running");
            return;
        }
        Log.i("DL","Starting opportunistic scan");
        ScanFilter filter = (new ScanFilter.Builder())
                .setServiceUuid(new ParcelUuid(UUID.fromString("0000FD6F-0000-1000-8000-00805f9b34fb")))
                .build();
        List<ScanFilter> opportunisticScanFilters = new ArrayList<>();
        opportunisticScanFilters.add(filter);

        ScanSettings scanSettings = (new ScanSettings.Builder())
                .setLegacy(false)
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC)
                .build();
        opportunisticScanCallback = new MyOpportunisticScanCallback();
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        scanner.startScan(opportunisticScanFilters, scanSettings, opportunisticScanCallback);
    }

    void stopOpportunisticScan() {
        if (opportunisticScanCallback != null) {
            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
            if (!adapter.isEnabled() || (scanner == null)) {
                Log.i("DL", "Stop opportunistic scan called with bluetooth off");
                u.tryUICallback(bleOffCallback,"",localBroadcastManager);
            } else {
                scanner.stopScan(opportunisticScanCallback);
            }
            opportunisticScanCallback = null;
            Log.i("DL","Opportunistic scanner stopped");
        } else {
            Debug.println("stop opportunistic scanner called with scanner already stopped (callback eq null)");
        }
    }

    class MyOpportunisticScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            byte[] serviceData = result.getScanRecord()
                    .getServiceData(new ParcelUuid(UUID.fromString("0000FD6F-0000-1000-8000-00805f9b34fb")));
            byte[] rpi = new byte[16];
            for(int i = 0; i < 16; i++) { rpi[i]=serviceData[i]; }
            final String rpiStr = encodeHexString(rpi);
            final String fname = u.opportunisticScannerFilename(rpiStr);
            final String pname = u.pathname()+"/"+u.fnameDate();
            u.writeFile(null, pname,fname,
                    "opportunistic scanner: "+result.toString()+"\n", true); // save to sdcard
            // keep a log of which file contains scan results for this RPI,
            // then we'll be able to answer queries for a given RPI and
            // also for a given TEK (we generate all the RPIs for the TEK then
            // lookup database for files containing scan results);
            new Thread( new Runnable() { @Override public void run() {
                OpportunisticScanEntity s = new OpportunisticScanEntity();
                s.rpi = rpiStr;
                s.fname = pname+"/"+fname;
                s.timestamp = Instant.now().getEpochSecond();
                opportunisticScandb.opportunisticScanDao().insert(s);
                u.tryUICallback(scanCallback,"",localBroadcastManager);
            } } ).start();
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("DL", "Opportunistic scan failed with error code "+errorCode);
        }
    }

    String getRPIFile(String rpiStr) {
        // get name of file containing scan results for specified RPI
        List<OpportunisticScanEntity> res = opportunisticScandb.opportunisticScanDao().getRPI(rpiStr);
        if (res.size() >0) {
            return res.get(0).fname;
        } else {
            return "<no file>";
        }
    }

    public class RPIEntity{
        String rpiStr;
        long interval;
    }
    List<RPIEntity> getRPIsForTEK(String tekStr, int startInterval, int duration) {
        // get the RPIs for a given TEK in a specified interval
        byte[] TEK = hexStringToByteArray(tekStr);
        byte[] RPIK = newRPIK(TEK);
        List<RPIEntity> rpis = new ArrayList<RPIEntity>();
        for(int j = 0; j < duration; j++) {
            // time since epoch in 10min intervals
            long RPIinterval = startInterval + j;
            // convert to little endian uint32
            byte[] temp = ByteBuffer.allocate(Long.BYTES).putLong(RPIinterval).array();
            byte[] interval = new byte[4];
            for (int i = 0; i < 4; i++) {
                interval[i] = temp[Long.BYTES - 1 - i];
            }
            // get RPI for interval
            byte[] rpi = getRPI(RPIK, interval);
            RPIEntity r = new RPIEntity();
            r.rpiStr = encodeHexString(rpi);
            r.interval = RPIinterval;
            rpis.add(r);
        }
        return rpis;
    }

    /***************************************************************************************/
    // debugging
    private static class Debug {
        static void println(String s) {
            if (DEBUGGING) Log.d("DL","BLEAdvertiser: "+s);
        }
    }
}
