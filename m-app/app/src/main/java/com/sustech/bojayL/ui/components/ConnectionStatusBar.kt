package com.sustech.bojayL.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.ConnectionType
import com.sustech.bojayL.data.model.DeviceState
import com.sustech.bojayL.ui.theme.*

/**
 * 设备连接状态栏
 * 
 * 根据 PRD 要求 DEV-01：
 * 首页顶部常驻显示眼镜连接状态（未连接/USB连接/蓝牙连接）及眼镜电量图标
 */
@Composable
fun ConnectionStatusBar(
    deviceState: DeviceState,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 连接状态图标
            ConnectionStatusIcon(
                connectionType = deviceState.connectionType,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 连接状态文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getConnectionStatusText(deviceState.connectionType),
                    style = MaterialTheme.typography.titleSmall,
                    color = getConnectionStatusColor(deviceState.connectionType)
                )
                if (deviceState.connectionType != ConnectionType.DISCONNECTED && deviceState.deviceName != null) {
                    Text(
                        text = deviceState.deviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            // 电量显示（仅已连接时显示）
            if (deviceState.connectionType != ConnectionType.DISCONNECTED) {
                BatteryIndicator(
                    batteryLevel = deviceState.batteryLevel,
                    isCharging = deviceState.isCharging
                )
            }
            
            // 箭头图标
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "查看详情",
                tint = TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 连接状态图标
 */
@Composable
fun ConnectionStatusIcon(
    connectionType: ConnectionType,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (connectionType) {
        ConnectionType.DISCONNECTED -> Pair(Icons.Default.LinkOff, StatusDisconnected)
        ConnectionType.USB -> Pair(Icons.Default.Usb, StatusConnected)
        ConnectionType.BLUETOOTH -> Pair(Icons.Default.Bluetooth, StatusConnected)
        ConnectionType.WIFI -> Pair(Icons.Default.Wifi, StatusConnected)
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = color
    )
}

/**
 * 电量指示器
 */
@Composable
fun BatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 电量图标
        val batteryIcon = when {
            isCharging -> Icons.Default.BatteryChargingFull
            batteryLevel >= 90 -> Icons.Default.BatteryFull
            batteryLevel >= 50 -> Icons.Default.Battery5Bar
            batteryLevel >= 20 -> Icons.Default.Battery3Bar
            else -> Icons.Default.Battery1Bar
        }
        
        val batteryColor = when {
            batteryLevel <= 20 -> AccentRed
            batteryLevel <= 50 -> AccentOrange
            else -> AccentGreen
        }
        
        Icon(
            imageVector = batteryIcon,
            contentDescription = "电量",
            modifier = Modifier.size(20.dp),
            tint = batteryColor
        )
        
        Text(
            text = "$batteryLevel%",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

/**
 * 获取连接状态文字
 */
private fun getConnectionStatusText(connectionType: ConnectionType): String {
    return when (connectionType) {
        ConnectionType.DISCONNECTED -> "未连接设备"
        ConnectionType.USB -> "USB 已连接"
        ConnectionType.BLUETOOTH -> "蓝牙已连接"
        ConnectionType.WIFI -> "WiFi 已连接"
    }
}

/**
 * 获取连接状态颜色
 */
private fun getConnectionStatusColor(connectionType: ConnectionType): androidx.compose.ui.graphics.Color {
    return when (connectionType) {
        ConnectionType.DISCONNECTED -> StatusDisconnected
        else -> StatusConnected
    }
}

/**
 * 紧凑版连接状态指示器
 * 用于顶部栏等空间有限的场景
 */
@Composable
fun CompactConnectionIndicator(
    connectionType: ConnectionType,
    batteryLevel: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 连接状态点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = getConnectionStatusColor(connectionType),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        
        // 连接状态图标
        ConnectionStatusIcon(
            connectionType = connectionType,
            modifier = Modifier.size(16.dp)
        )
        
        // 电量（已连接时）
        if (connectionType != ConnectionType.DISCONNECTED) {
            Text(
                text = "$batteryLevel%",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ConnectionStatusBarPreview() {
    MobileappTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusBar(
                deviceState = DeviceState(
                    connectionType = ConnectionType.USB,
                    batteryLevel = 85,
                    deviceName = "Rokid CXR-M"
                )
            )
            
            ConnectionStatusBar(
                deviceState = DeviceState(
                    connectionType = ConnectionType.DISCONNECTED
                )
            )
            
            ConnectionStatusBar(
                deviceState = DeviceState(
                    connectionType = ConnectionType.BLUETOOTH,
                    batteryLevel = 15,
                    deviceName = "Rokid CXR-S",
                    isCharging = true
                )
            )
        }
    }
}
