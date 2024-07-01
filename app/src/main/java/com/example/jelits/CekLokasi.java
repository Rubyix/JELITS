package com.example.jelits;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CekLokasi extends AppCompatActivity {
    private FirebaseFirestore db;
    private WifiManager wifiManager;
    private ListView listView;
    private final int PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1;
    private Handler handler;
    private Runnable wifiScanRunnable;
    private static final int SCAN_INTERVAL_MS = 30000; // Increase interval to 7 seconds
    private static final int RETRY_DELAY_MS = 30000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cek_lokasi);

        listView = findViewById(R.id.listView);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        db = FirebaseFirestore.getInstance();

        handler = new Handler();
        wifiScanRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Running wifiScanRunnable");
                if (!wifiManager.startScan()) {
                    Log.d(TAG, "WiFi scan failed, will retry after delay");
                    handler.postDelayed(this, RETRY_DELAY_MS);
                } else {
                    Log.d(TAG, "WiFi scan initiated successfully");
                    handler.postDelayed(this, SCAN_INTERVAL_MS);
                }
            }
        };

        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION);
        } else {
            // Permissions already granted, start WiFi scanning
            startWifiScanning();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, start WiFi scanning
                startWifiScanning();
            } else {
                // Permissions denied, show error message
                Toast.makeText(this, "Permission denied to access location. Cannot scan WiFi.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startWifiScanning() {
        Log.d(TAG, "Starting WiFi scanning");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);
        handler.post(wifiScanRunnable); // Start the first WiFi scan
    }

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess();
            } else {
                scanFailure();
            }
        }
    };

    private void scanSuccess() {
        Log.d(TAG, "WiFi scan succeeded");
        List<ScanResult> results = wifiManager.getScanResults();
        if (results != null) {
            updateWifiList(results);
        } else {
            Log.d(TAG, "No WiFi networks found on success");
            displayMacAddressInfo("No WiFi found", "No WiFi networks available.");
        }
    }

    private void scanFailure() {
        Log.d(TAG, "WiFi scan failed");
        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null || results.isEmpty()) {
            Log.d(TAG, "No WiFi networks found on failure");
            displayMacAddressInfo("No WiFi found", "No WiFi networks available.");
        } else {
            updateWifiList(results);
        }
    }

    private void updateWifiList(List<ScanResult> results) {
        // Urutkan hasil pemindaian berdasarkan kekuatan sinyal
        results.sort((o1, o2) -> Integer.compare(o2.level, o1.level));

        String[] wifiList = new String[results.size()];
        List<String> macAddresses = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            ScanResult scanResult = results.get(i);
            String ssid = scanResult.SSID;
            String bssid = scanResult.BSSID;
            int rssi = scanResult.level;  // Get the signal strength
            wifiList[i] = "SSID: " + ssid + "\nBSSID: " + bssid + "\nSignal Strength: " + rssi + " dBm";

            macAddresses.add(bssid.toUpperCase()); // Tambahkan MAC address ke daftar
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, wifiList);
        listView.setAdapter(adapter);

        if (!macAddresses.isEmpty()) {
            checkMacAddressesInFirestore(macAddresses); // Mulai cek MAC address
        } else {
            Log.d(TAG, "No WiFi networks found");
            displayMacAddressInfo("No WiFi found", "No WiFi networks available.");
        }
    }

    private void checkMacAddressesInFirestore(final List<String> macAddresses) {
        if (macAddresses.isEmpty()) {
            Log.d(TAG, "No MAC addresses to check");
            displayMacAddressInfo("No MAC found", "No WiFi networks available.");
            return;
        }

        String macAddress = macAddresses.remove(0); // Ambil MAC address terkuat pertama
        Log.d(TAG, "Checking MAC address in Firestore: " + macAddress);
        db.collection("jelits")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            boolean macAddressFound = false;
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                if (document.exists()) {
                                    Object macField = document.get("MAC");
                                    if (macField instanceof List) {
                                        List<?> macList = (List<?>) macField;
                                        for (Object macEntry : macList) {
                                            if (macEntry instanceof Map) {
                                                Map<String, String> macMap = (Map<String, String>) macEntry;
                                                if (macMap.get("MAC").equals(macAddress)) {
                                                    macAddressFound = true;
                                                    String location = document.getString("Lokasi");
                                                    Log.d(TAG, "MAC address found: " + macAddress + " Location: " + location);
                                                    displayMacAddressInfo(macAddress, location);
                                                    return; // Keluar dari fungsi setelah menemukan kecocokan
                                                }
                                            } else {
                                                Log.d(TAG, "Unexpected data type in MAC list: " + macEntry.getClass().getName());
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "Unexpected data type for MAC field: " + macField.getClass().getName());
                                    }
                                }
                            }
                            if (!macAddressFound) {
                                Log.d(TAG, "MAC address not found in Firestore. Checking next MAC address...");
                                checkMacAddressesInFirestore(macAddresses); // Cek MAC address berikutnya
                            }
                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    private void displayMacAddressInfo(String macAddress, String info) {
        Log.d(TAG, "Displaying MAC address info: " + macAddress + " Info: " + info);
        TextView textViewStrongestSignal = findViewById(R.id.textViewStrongestSignal);
        textViewStrongestSignal.setText("MAC Address: " + macAddress + "\nInfo: " + info);

        // Simpan lokasi ke SharedPreferences
        saveLocationToSharedPreferences(info);
    }

    private void saveLocationToSharedPreferences(String location) {
        SharedPreferences sharedPreferences = getSharedPreferences("location_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("current_location", location);
        editor.apply();

        // Kirim broadcast untuk memperbarui lokasi di Maps
        Intent intent = new Intent("com.example.jelits.UPDATE_LOCATION");
        intent.putExtra("current_location", location);
        sendBroadcast(intent);
    }

}