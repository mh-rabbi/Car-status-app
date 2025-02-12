package com.demoapp.carstatus;

import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "OBD2_APP";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice obdDevice;
    private UUID OBD_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private InputStream inputStream;
    private OutputStream outputStream;

    private TextView speedValue, rpmValue, mileageValue;
    private Button refreshButton;

    private TextView dtcCodes;
    private Button checkDTC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        // Link UI elements
        speedValue = findViewById(R.id.speedValue);
        rpmValue = findViewById(R.id.rpmValue);
        mileageValue = findViewById(R.id.mileageValue);
        refreshButton = findViewById(R.id.refreshButton);
        dtcCodes = findViewById(R.id.dtcCodes);
        checkDTC = findViewById(R.id.checkDTC);

        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }

        // Connect and fetch data when button is clicked
        refreshButton.setOnClickListener(v -> {
            connectToOBDDevice();
            sendOBDCommand("010D"); // Get Speed
            sendOBDCommand("010C"); // Get RPM
            sendOBDCommand("0121"); // Get Mileage
        });

        // Check DTC codes when button is clicked
        checkDTC.setOnClickListener(v -> {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                sendOBDCommand("03"); // Fetch DTC codes
            } else {
                Toast.makeText(this, "OBD-II not connected", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void connectToOBDDevice() {
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().contains("OBD") || device.getName().contains("ELM")) {
                obdDevice = device;
                break;
            }
        }

        if (obdDevice == null) {
            Toast.makeText(this, "OBD-II device not found. Pair it first.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            bluetoothSocket = obdDevice.createRfcommSocketToServiceRecord(OBD_UUID);
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            Toast.makeText(this, "Connected to OBD-II", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Connection failed", e);
            Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendOBDCommand(String command) {
        try {
            outputStream.write((command + "\r").getBytes());
            outputStream.flush();

            // Read response
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            String response = new String(buffer, 0, bytesRead);

            processOBDResponse(command, response);

        } catch (IOException e) {
            Log.e(TAG, "Error sending command", e);
        }
    }

    private void processOBDResponse(String command, String response) {
        String[] parts = response.split(" ");
        if (command.equals("010D") && parts.length >= 3) { // Speed
            int speed = Integer.parseInt(parts[2], 16);
            speedValue.setText(speed + " km/h");
        } else if (command.equals("010C") && parts.length >= 4) { // RPM
            int rpm = (Integer.parseInt(parts[2], 16) * 256 + Integer.parseInt(parts[3], 16)) / 4;
            rpmValue.setText(String.valueOf(rpm));
        } else if (command.equals("0121") && parts.length >= 6) { // Mileage
            int mileage = Integer.parseInt(parts[2], 16) * 16777216 +
                    Integer.parseInt(parts[3], 16) * 65536 +
                    Integer.parseInt(parts[4], 16) * 256 +
                    Integer.parseInt(parts[5], 16);
            mileageValue.setText(mileage + " km");
        }

        if (command.equals("03")) { // DTC Error Codes
            String dtcMessage = decodeDTC(response);
            dtcCodes.setText(dtcMessage);
        }
    }

    // Function to Decode DTC Codes
    private String decodeDTC(String response) {
        Map<String, String> dtcDescriptions = new HashMap<>();
        dtcDescriptions.put("P0300", "Random/Multiple Cylinder Misfire Detected");
        dtcDescriptions.put("P0420", "Catalytic Converter Efficiency Below Threshold");
        dtcDescriptions.put("P0171", "System Too Lean (Bank 1)");
        dtcDescriptions.put("P0172", "System Too Rich (Bank 1)");
        dtcDescriptions.put("P0455", "Evaporative Emission System Leak Detected");
        dtcDescriptions.put("P0113", "Intake Air Temperature Sensor High Input");
        dtcDescriptions.put("P0500", "Vehicle Speed Sensor Malfunction");
        dtcDescriptions.put("P0700", "Transmission Control System Malfunction");

        if (response.startsWith("43")) {
            StringBuilder dtcResult = new StringBuilder();
            String[] parts = response.split(" ");

            for (int i = 1; i < parts.length; i += 2) {
                if (i + 1 < parts.length) {
                    int firstByte = Integer.parseInt(parts[i], 16);
                    int secondByte = Integer.parseInt(parts[i + 1], 16);

                    // DTC Code Prefix Mapping
                    char type;
                    switch ((firstByte & 0xC0) >> 6) {
                        case 0: type = 'P'; break; // Powertrain
                        case 1: type = 'C'; break; // Chassis
                        case 2: type = 'B'; break; // Body
                        case 3: type = 'U'; break; // Network
                        default: type = 'P'; break;
                    }

                    int code = ((firstByte & 0x3F) << 8) | secondByte;
                    String dtcCode = type + String.format("%04d", code);
                    String description = dtcDescriptions.getOrDefault(dtcCode, "Unknown Code");

                    dtcResult.append(dtcCode).append(" - ").append(description).append("\n");
                }
            }

            return dtcResult.length() > 0 ? dtcResult.toString() : "No errors found";
        }
        return "No DTC codes found";
    }



}