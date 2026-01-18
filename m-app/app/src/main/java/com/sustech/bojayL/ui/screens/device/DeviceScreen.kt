package com.sustech.bojayL.ui.screens.device

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.rokid.*
import com.sustech.bojayL.ui.components.*
import com.sustech.bojayL.ui.theme.*

/**
 * 设备管理页面
 * 
 * 根据 PRD 要求：
 * - DEV-01: 连接状态监测
 * - DEV-02: 二维码配对生成
 * - DEV-03: 配对码输入
 * - DEV-04: 参数配置面板
 * - 调试：眼镜实时画面预览
 */
@Composable
fun DeviceScreen(
    deviceState: DeviceState,
    onUpdateParams: (SdkParams) -> Unit = {},
    // Rokid 蓝牙连接相关
    rokidConnectionState: RokidConnectionState = RokidConnectionState(),
    scannedDevices: List<ScannedDevice> = emptyList(),
    isScanning: Boolean = false,
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onConnectDevice: (ScannedDevice) -> Unit = {},
    onDisconnect: () -> Unit = {},
    // 连接后自动配对
    isPaired: Boolean = false,
    // 调试预览：眼镜相机画面
    latestGlassesFrame: Bitmap? = null,
    latestFrameTimestamp: Long = 0L,
    modifier: Modifier = Modifier
) {
    var qrBrightness by remember { mutableFloatStateOf(1f) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题
        Text(
            text = "设备管理",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 连接状态卡片
        DeviceConnectionCard(
            deviceState = deviceState,
            rokidConnectionState = rokidConnectionState,
            isPaired = isPaired,
            onDisconnect = onDisconnect
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 蓝牙扫描连接区域（仅未连接时显示）
        if (deviceState.connectionType == ConnectionType.DISCONNECTED && 
            rokidConnectionState.status != ConnectionStatus.CONNECTED) {
            BluetoothScanCard(
                scannedDevices = scannedDevices,
                isScanning = isScanning,
                connectionState = rokidConnectionState,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onConnectDevice = onConnectDevice
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 二维码配对区域
            QRCodePairingCard(
                brightness = qrBrightness,
                onBrightnessChange = { qrBrightness = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // SDK 参数配置（已连接时显示）
        if (isPaired || deviceState.connectionType != ConnectionType.DISCONNECTED) {
            // 眼镜画面预览（调试用）
            GlassesPreviewCard(
                latestFrame = latestGlassesFrame,
                frameTimestamp = latestFrameTimestamp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SdkParamsCard(
                params = deviceState.sdkParams,
                onUpdateParams = onUpdateParams
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 设备信息
            DeviceInfoCard(deviceState = deviceState)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 设备连接状态卡片
 */
@Composable
private fun DeviceConnectionCard(
    deviceState: DeviceState,
    rokidConnectionState: RokidConnectionState = RokidConnectionState(),
    isPaired: Boolean = false,
    onDisconnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 判断是否已连接（原有连接或 Rokid 蓝牙连接）
    val isConnected = deviceState.connectionType != ConnectionType.DISCONNECTED || 
                      rokidConnectionState.status == ConnectionStatus.CONNECTED
    val isRokidConnected = rokidConnectionState.status == ConnectionStatus.CONNECTED
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 连接状态图标
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isConnected -> StatusConnected.copy(alpha = 0.2f)
                            rokidConnectionState.status == ConnectionStatus.CONNECTING -> CyanPrimary.copy(alpha = 0.2f)
                            else -> StatusDisconnected.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (rokidConnectionState.status == ConnectionStatus.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = CyanPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isRokidConnected -> Icons.Default.Bluetooth
                            deviceState.connectionType == ConnectionType.USB -> Icons.Default.Usb
                            deviceState.connectionType == ConnectionType.BLUETOOTH -> Icons.Default.Bluetooth
                            deviceState.connectionType == ConnectionType.WIFI -> Icons.Default.Wifi
                            else -> Icons.Default.LinkOff
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isConnected) StatusConnected else StatusDisconnected
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        rokidConnectionState.status == ConnectionStatus.CONNECTING -> "正在连接..."
                        isPaired -> "已连接"
                        deviceState.connectionType == ConnectionType.USB -> "USB 已连接"
                        deviceState.connectionType == ConnectionType.BLUETOOTH -> "蓝牙已连接"
                        deviceState.connectionType == ConnectionType.WIFI -> "WiFi 已连接"
                        else -> "未连接设备"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPaired) StatusConnected else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                when {
                    isPaired -> {
                        Text(
                            text = "Rokid AR 眼镜已就绪",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusConnected
                        )
                    }
                    isConnected -> {
                        Text(
                            text = deviceState.deviceName ?: "Rokid AR 眼镜",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    rokidConnectionState.status == ConnectionStatus.ERROR -> {
                        Text(
                            text = "连接失败: ${rokidConnectionState.errorMessage ?: "未知错误"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusDisconnected
                        )
                    }
                    else -> {
                        Text(
                            text = "请扫描蓝牙设备或使用二维码配对",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }
            
            // 电量显示或断开按钮
            if (isConnected) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    BatteryIndicator(
                        batteryLevel = if (isRokidConnected) rokidConnectionState.batteryLevel else deviceState.batteryLevel,
                        isCharging = deviceState.isCharging
                    )
                    if (isRokidConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = StatusDisconnected
                            )
                        ) {
                            Text("断开")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 蓝牙扫描连接卡片
 */
@Composable
private fun BluetoothScanCard(
    scannedDevices: List<ScannedDevice>,
    isScanning: Boolean,
    connectionState: RokidConnectionState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (ScannedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "蓝牙连接",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                // 扫描按钮
                Button(
                    onClick = if (isScanning) onStopScan else onStartScan,
                    enabled = connectionState.status != ConnectionStatus.CONNECTING,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) StatusDisconnected else CyanPrimary
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止扫描")
                    } else {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("扫描设备")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (scannedDevices.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isScanning) "正在扫描 Rokid 设备..." else "点击“扫描设备”开始搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                // 设备列表
                Column {
                    scannedDevices.forEach { device ->
                        ScannedDeviceItem(
                            device = device,
                            isConnecting = connectionState.status == ConnectionStatus.CONNECTING,
                            onClick = { onConnectDevice(device) }
                        )
                        if (device != scannedDevices.last()) {
                            HorizontalDivider(
                                color = TextTertiary.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 扫描到的设备项
 */
@Composable
private fun ScannedDeviceItem(
    device: ScannedDevice,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isConnecting, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 蓝牙图标
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = CyanPrimary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        // 信号强度
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SignalCellularAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = when {
                    device.rssi > -50 -> StatusConnected
                    device.rssi > -70 -> CyanPrimary
                    else -> TextSecondary
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 连接按钮
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "连接",
            tint = TextSecondary
        )
    }
}

/**
 * 二维码配对卡片
 */
@Composable
private fun QRCodePairingCard(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 生成配对信息
    val pairingInfo = remember {
        PairingInfo(
            wifiSsid = "AR_Classroom_5G",
            wifiPassword = "rokid2023",
            serverIp = "192.168.1.100",
            serverPort = 8080,
            token = "abc123xyz",
            expiresAt = System.currentTimeMillis() + 300000
        )
    }
    
    // 生成二维码内容
    val qrContent = remember(pairingInfo) {
        """{"ssid":"${pairingInfo.wifiSsid}","pwd":"${pairingInfo.wifiPassword}","ip":"${pairingInfo.serverIp}","port":${pairingInfo.serverPort},"token":"${pairingInfo.token}"}"""
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "扫码配对",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = CyanPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 二维码
            QRCodeImage(
                content = qrContent,
                size = 200,
                brightness = brightness
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "请使用 Rokid 眼镜扫描此二维码完成配对",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 亮度调节
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessLow,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary
                    )
                )
                
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Text(
                text = "调节二维码亮度以适配眼镜摄像头",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

/**
 * 二维码图片组件
 */
@Composable
private fun QRCodeImage(
    content: String,
    size: Int,
    brightness: Float,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(content, size, brightness) {
        generateQRCode(content, size, brightness)
    }
    
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TextPrimary),
        contentAlignment = Alignment.Center
    ) {
        qrBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "配对二维码",
                modifier = Modifier.size((size - 16).dp)
            )
        }
    }
}

/**
 * 生成二维码
 */
private fun generateQRCode(content: String, size: Int, brightness: Float): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        
        val pixels = IntArray(size * size)
        val darkColor = DarkBackground.toArgb()
        val lightColor = android.graphics.Color.rgb(
            (255 * brightness).toInt().coerceIn(0, 255),
            (255 * brightness).toInt().coerceIn(0, 255),
            (255 * brightness).toInt().coerceIn(0, 255)
        )
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (bitMatrix[x, y]) darkColor else lightColor
            }
        }
        
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * SDK 参数配置卡片
 */
@Composable
private fun SdkParamsCard(
    params: SdkParams,
    onUpdateParams: (SdkParams) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "识别参数",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 识别阈值
            Text(
                text = "识别阈值",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecognitionThreshold.entries.forEach { threshold ->
                    FilterChip(
                        selected = params.recognitionThreshold == threshold,
                        onClick = {
                            onUpdateParams(params.copy(recognitionThreshold = threshold))
                        },
                        label = { Text("${threshold.label} (${threshold.value})") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPrimary
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 采集间隔
            Text(
                text = "采集间隔",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptureInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = params.captureInterval == interval,
                        onClick = {
                            onUpdateParams(params.copy(captureInterval = interval))
                        },
                        label = { Text(interval.label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPrimary
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 显示亮度
            Text(
                text = "眼镜显示亮度: ${params.displayBrightness}%",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = params.displayBrightness.toFloat(),
                onValueChange = {
                    onUpdateParams(params.copy(displayBrightness = it.toInt()))
                },
                valueRange = 20f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 准心开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "显示准心",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "屏幕中央的对焦辅助点",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Switch(
                    checked = params.showReticle,
                    onCheckedChange = {
                        onUpdateParams(params.copy(showReticle = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanPrimary.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

/**
 * 设备信息卡片
 */
@Composable
private fun DeviceInfoCard(
    deviceState: DeviceState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow("设备名称", deviceState.deviceName ?: "未知")
            InfoRow("设备 ID", deviceState.deviceId ?: "未知")
            InfoRow("固件版本", deviceState.firmwareVersion ?: "未知")
            if (deviceState.connectionType == ConnectionType.BLUETOOTH) {
                InfoRow("信号强度", "${deviceState.signalStrength}%")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DeviceScreenPreview() {
    MobileappTheme {
        DeviceScreen(
            deviceState = DeviceState(
                connectionType = ConnectionType.USB,
                batteryLevel = 85,
                deviceName = "Rokid CXR-M",
                deviceId = "RK-001234",
                firmwareVersion = "v2.1.3"
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DeviceScreenDisconnectedPreview() {
    MobileappTheme {
        DeviceScreen(
            deviceState = DeviceState(
                connectionType = ConnectionType.DISCONNECTED
            )
        )
    }
}
