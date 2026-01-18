package com.sustech.bojayL.rokid

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.BluetoothStatusCallback
import com.rokid.cxr.client.extend.callbacks.GlassInfoResultCallback
import com.rokid.cxr.client.extend.listeners.BatteryLevelUpdateListener
import com.rokid.cxr.client.extend.listeners.BrightnessUpdateListener
import com.rokid.cxr.client.extend.listeners.VolumeUpdateListener
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rokid 眼镜连接管理器
 * 
 * 封装 CxrApi，提供蓝牙扫描、连接、状态监控等功能
 */
class RokidManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RokidManager"
        
        // Rokid 蓝牙服务 UUID
        const val SERVICE_UUID = "00009100-0000-1000-8000-00805f9b34fb"
        
        // Rokid 设备名称前缀（用于名称过滤）
        val ROKID_NAME_PREFIXES = listOf("Rokid", "RK", "rokid", "Max", "Air")
        
        // 认证信息 - 从ar.rokid.com 获取
        // Client ID: C8774FAB129D453F8611490B5A932316
        // Accesskey: ee7c8aee-f131-11f0-961e-043f72fdb9c8
        const val CLIENT_SECRET = "ee7b66bb-f131-11f0-961e-043f72fdb9c8"
    }
    
    // 连接状态
    private val _connectionState = MutableStateFlow(RokidConnectionState())
    val connectionState: StateFlow<RokidConnectionState> = _connectionState.asStateFlow()
    
    // 扫描到的设备列表
    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()
    
    // 是否正在扫描
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // 眼镜信息
    private val _glassInfo = MutableStateFlow<GlassInfo?>(null)
    val glassInfo: StateFlow<GlassInfo?> = _glassInfo.asStateFlow()
    
    // 当前连接的设备 UUID 和 MAC
    private var currentUuid: String? = null
    private var currentMacAddress: String? = null
    
    // SN 认证文件（需要放在 res/raw/ 目录）
    private var snBytes: ByteArray? = null
    
    // 蓝牙扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                // 检查设备名称是否匹配 Rokid 设备
                val deviceName = device.name ?: ""
                val isRokidDevice = ROKID_NAME_PREFIXES.any { prefix ->
                    deviceName.startsWith(prefix, ignoreCase = true)
                }
                
                if (isRokidDevice || deviceName.isNotEmpty()) {
                    // Rokid设备或有名称的设备都添加
                    addScannedDevice(device, result.rssi, isRokidDevice)
                }
            }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                result.device?.let { device ->
                    val deviceName = device.name ?: ""
                    val isRokidDevice = ROKID_NAME_PREFIXES.any { prefix ->
                        deviceName.startsWith(prefix, ignoreCase = true)
                    }
                    if (isRokidDevice || deviceName.isNotEmpty()) {
                        addScannedDevice(device, result.rssi, isRokidDevice)
                    }
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                else -> "Unknown error: $errorCode"
            }
            Log.e(TAG, "Scan failed: $errorMsg")
            _isScanning.value = false
        }
    }
    
    // 蓝牙连接状态回调
    private val bluetoothStatusCallback = object : BluetoothStatusCallback {
        override fun onConnectionInfo(uuid: String?, macAddress: String?, p2: String?, glassesType: Int) {
            Log.d(TAG, "onConnectionInfo: uuid=$uuid, mac=$macAddress, type=$glassesType")
            currentUuid = uuid
            currentMacAddress = macAddress
            
            _connectionState.update {
                it.copy(
                    deviceUuid = uuid,
                    macAddress = macAddress,
                    glassesType = if (glassesType == 1) GlassesType.WITH_SCREEN else GlassesType.WITHOUT_SCREEN
                )
            }
            
            // 继续建立 Socket 连接
            connectSocket()
        }
        
        override fun onConnected() {
            Log.d(TAG, "Bluetooth connected")
            _connectionState.update {
                it.copy(status = ConnectionStatus.CONNECTED)
            }
            
            // 获取设备信息
            fetchGlassInfo()
            
            // 设置监听器
            setupListeners()
        }
        
        override fun onDisconnected() {
            Log.d(TAG, "Bluetooth disconnected")
            _connectionState.update {
                it.copy(status = ConnectionStatus.DISCONNECTED)
            }
        }
        
        override fun onFailed(errorCode: ValueUtil.CxrBluetoothErrorCode?) {
            Log.e(TAG, "Connection failed: $errorCode")
            _connectionState.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = errorCode?.name ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * 初始化 SN 认证文件
     * 
     * @param snResourceId SN 文件的资源 ID (R.raw.sn_xxx)
     */
    fun initSnFile(snResourceId: Int) {
        try {
            snBytes = context.resources.openRawResource(snResourceId).readBytes()
            Log.d(TAG, "SN file loaded, size=${snBytes?.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SN file", e)
        }
    }
    
    /**
     * 开始扫描 Rokid 设备
     * 
     * 扫描策略：
     * 1. 不使用UUID过滤，因为不同型号的Rokid设备可能使用不同UUID
     * 2. 扫描所有BLE设备，然后根据名称过滤
     * 3. Rokid设备通常以 Rokid/RK/Max/Air 等开头
     */
    fun startScan() {
        if (_isScanning.value) {
            Log.w(TAG, "Already scanning")
            return
        }
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null) {
            Log.e(TAG, "Bluetooth adapter not available")
            return
        }
        
        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }
        
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return
        }
        
        Log.d(TAG, "Starting BLE scan (no UUID filter, name-based matching)")
        _scannedDevices.value = emptyList()
        _isScanning.value = true
        
        try {
            // 不使用UUID过滤，扫描所有BLE设备
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)  // 立即报告
                .build()
            
            // 无过滤器扫描
            scanner.startScan(null, settings, scanCallback)
            
            Log.d(TAG, "BLE scan started successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permission", e)
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            _isScanning.value = false
        }
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        if (!_isScanning.value) return
        
        Log.d(TAG, "Stopping BLE scan")
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val scanner = bluetoothManager.adapter?.bluetoothLeScanner
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
        _isScanning.value = false
    }
    
    /**
     * 连接到设备
     */
    fun connect(device: ScannedDevice) {
        Log.d(TAG, "Connecting to device: ${device.name}")
        
        _connectionState.update {
            it.copy(status = ConnectionStatus.CONNECTING)
        }
        
        try {
            CxrApi.getInstance().initBluetooth(
                context,
                device.bluetoothDevice,
                bluetoothStatusCallback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init bluetooth", e)
            _connectionState.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }
    
    /**
     * 建立 Socket 连接
     */
    private fun connectSocket() {
        val uuid = currentUuid ?: return
        val mac = currentMacAddress ?: return
        val sn = snBytes
        
        if (sn == null) {
            Log.e(TAG, "SN file not loaded")
            _connectionState.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = "SN 认证文件未加载"
                )
            }
            return
        }
        
        Log.d(TAG, "Connecting socket: uuid=$uuid, mac=$mac")
        
        try {
            CxrApi.getInstance().connectBluetooth(
                context,
                uuid,
                mac,
                bluetoothStatusCallback,
                sn,
                CLIENT_SECRET.replace("-", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect socket", e)
            _connectionState.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting")
        try {
            CxrApi.getInstance().deinitBluetooth()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect", e)
        }
        
        _connectionState.update {
            RokidConnectionState()
        }
    }
    
    /**
     * 获取眼镜信息
     */
    private fun fetchGlassInfo() {
        CxrApi.getInstance().getGlassInfo(GlassInfoResultCallback { status, info ->
            if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED && info != null) {
                _glassInfo.value = GlassInfo(
                    deviceName = info.deviceName ?: "",
                    deviceId = info.deviceId ?: "",
                    systemVersion = info.systemVersion ?: "",
                    batteryLevel = info.batteryLevel,
                    isCharging = info.isCharging,
                    brightness = info.brightness,
                    volume = info.volume
                )
                
                _connectionState.update {
                    it.copy(batteryLevel = info.batteryLevel)
                }
                
                Log.d(TAG, "Glass info: ${_glassInfo.value}")
            }
        })
    }
    
    /**
     * 设置状态监听器
     */
    private fun setupListeners() {
        // 电量监听
        CxrApi.getInstance().setBatteryLevelUpdateListener(
            BatteryLevelUpdateListener { level, isCharging ->
                _connectionState.update {
                    it.copy(batteryLevel = level)
                }
                _glassInfo.update { info ->
                    info?.copy(batteryLevel = level, isCharging = isCharging)
                }
            }
        )
        
        // 亮度监听
        CxrApi.getInstance().setBrightnessUpdateListener(
            BrightnessUpdateListener { level ->
                _glassInfo.update { info ->
                    info?.copy(brightness = level)
                }
            }
        )
        
        // 音量监听
        CxrApi.getInstance().setVolumeUpdateListener(
            VolumeUpdateListener { level ->
                _glassInfo.update { info ->
                    info?.copy(volume = level)
                }
            }
        )
    }
    
    /**
     * 添加扫描到的设备
     * 
     * @param device 蓝牙设备
     * @param rssi 信号强度
     * @param isRokidDevice 是否确认为Rokid设备
     */
    private fun addScannedDevice(device: BluetoothDevice, rssi: Int, isRokidDevice: Boolean = false) {
        val deviceName = try {
            device.name ?: "Unknown"
        } catch (e: SecurityException) {
            "Unknown"
        }
        
        val existing = _scannedDevices.value.find { it.address == device.address }
        if (existing != null) {
            // 更新 RSSI 和 Rokid 标记
            _scannedDevices.value = _scannedDevices.value.map {
                if (it.address == device.address) {
                    it.copy(rssi = rssi, isRokidDevice = it.isRokidDevice || isRokidDevice)
                } else it
            }
        } else {
            // 添加新设备
            val newDevice = ScannedDevice(
                name = deviceName,
                address = device.address,
                rssi = rssi,
                bluetoothDevice = device,
                isRokidDevice = isRokidDevice
            )
            _scannedDevices.value = _scannedDevices.value + newDevice
            Log.d(TAG, "Found device: $deviceName (${device.address}) isRokid=$isRokidDevice")
        }
    }
    
    /**
     * 扩展函数：更新 StateFlow
     */
    private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }
}

/**
 * 连接状态
 */
data class RokidConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val deviceUuid: String? = null,
    val macAddress: String? = null,
    val glassesType: GlassesType = GlassesType.UNKNOWN,
    val batteryLevel: Int = 0,
    val errorMessage: String? = null
)

/**
 * 连接状态枚举
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 眼镜类型
 */
enum class GlassesType {
    UNKNOWN,
    WITHOUT_SCREEN,
    WITH_SCREEN
}

/**
 * 扫描到的设备
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bluetoothDevice: BluetoothDevice,
    val isRokidDevice: Boolean = false  // 是否确认为 Rokid 设备
)

/**
 * 眼镜信息
 */
data class GlassInfo(
    val deviceName: String,
    val deviceId: String,
    val systemVersion: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val brightness: Int,
    val volume: Int
)
