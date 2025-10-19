package com.hikemvp.group.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Foreground BLE-only group sharing:
 *  - Advertises the latest GPS fix in service data (compact 16-byte payload).
 *  - Scans for others advertising the same service UUID.
 *
 * UI: keep your existing GroupActivity/MapActivity unchanged; they can bind to this service later
 * if you wish to surface live peers. For now we just log discoveries.
 */
class GroupBleService : Service() {

    companion object {
        private const val TAG = "GroupBleService"
        private const val NOTIF_CHANNEL_ID = "group_ble"
        private const val NOTIF_ID = 4217

        // Custom 128-bit UUID for service data (choose a project-stable UUID)
        val SERVICE_UUID: UUID = UUID.fromString("0000A11E-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_UUID_16 = 0xA11E // used for ScanFilter on some devices
    }

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val btMgr by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val btAdapter by lazy { btMgr.adapter }
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var lastPoint: BleCodec.Point? = null
    private var advCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Initialisation du groupe (BLE)…"))
        Log.i(TAG, "Service created")
        startBle()
        requestLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBle()
        Log.i(TAG, "Service destroyed")
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Groupe (Bluetooth)",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Groupe (Bluetooth)")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startBle() {
        if (btAdapter == null || !btAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled; cannot start BLE group")
            stopSelf()
            return
        }
        advertiser = btAdapter.bluetoothLeAdvertiser
        scanner = btAdapter.bluetoothLeScanner
        startScan()
        // Advertising starts once we have a first location fix (see updateAdvertising())
    }

    private fun stopBle() {
        stopAdvertise()
        stopScan()
    }

    private fun startScan() {
        if (scanner == null) return
        if (scanCallback != null) return
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScan(result)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScan(it) }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Scan failed: $errorCode")
            }
        }
        try {
            scanner?.startScan(filters, settings, scanCallback)
            Log.i(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE scan permission", e)
        }
    }

    private fun stopScan() {
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        scanCallback = null
    }

    private fun handleScan(result: ScanResult) {
        val record = result.scanRecord ?: return
        val data = record.getServiceData(android.os.ParcelUuid(SERVICE_UUID)) ?: return
        val p = BleCodec.decode(data) ?: return
        Log.d(TAG, "Peer ${result.device.address} -> ${p.lat},${p.lon} alt=${p.altitudeMeters}m v=${p.speedMps}m/s brg=${p.bearingDeg}")
        // TODO: bridge to your in-app group overlay (e.g., via LocalBroadcastManager or shared store)
    }

    private fun stopAdvertise() {
        try {
            advCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        advCallback = null
    }

    private fun updateAdvertising() {
        val pt = lastPoint ?: return
        val payload = BleCodec.encode(pt)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .addServiceData(android.os.ParcelUuid(SERVICE_UUID), payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        if (advCallback == null) {
            advCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.i(TAG, "Advertising started")
                    updateNotif("Partage GPS actif (BLE)")
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.w(TAG, "Advertising failed: $errorCode")
                    updateNotif("BLE inactif (erreur pub $errorCode)")
                }
            }
        } else {
            // restart to refresh payload
            stopAdvertise()
        }
        try {
            advertiser?.startAdvertising(settings, data, advCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE advertise permission", e)
        }
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        val cb: (Location) -> Unit = { loc ->
            val pt = BleCodec.Point(
                lat = loc.latitude,
                lon = loc.longitude,
                altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
                speedMps = if (loc.hasSpeed()) loc.speed else null,
                bearingDeg = if (loc.hasBearing()) loc.bearing else null,
                seconds = ((System.currentTimeMillis() / 1000) % 60).toInt()
            )
            lastPoint = pt
            updateAdvertising()
        }

        // last known first
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) cb(loc)
            }
        } catch (_: SecurityException) {}

        // subscribe to updates
        try {
            val client = fused
            client.requestLocationUpdates(
                req,
                { runnable -> runnable.run() } // direct executor
            ) { location ->
                if (location != null) cb(location)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission", e)
        }
    }
}