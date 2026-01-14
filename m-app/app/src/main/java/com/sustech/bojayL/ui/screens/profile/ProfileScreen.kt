package com.sustech.bojayL.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.ui.theme.*

/**
 * 个人中心页面
 * 
 * 根据 PRD 信息架构：
 * - 账户信息
 * - 通用设置
 * - 数据同步
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier
) {
    var vibrationEnabled by remember { mutableStateOf(true) }
    var notificationEnabled by remember { mutableStateOf(true) }
    var autoSyncEnabled by remember { mutableStateOf(true) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 标题
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 用户信息卡片
        UserInfoCard()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 通用设置
        SettingsSection(title = "通用设置") {
            // 识别成功震动
            SettingsToggleItem(
                icon = Icons.Default.Vibration,
                title = "识别成功震动",
                subtitle = "识别成功时手机震动提醒",
                checked = vibrationEnabled,
                onCheckedChange = { vibrationEnabled = it }
            )
            
            HorizontalDivider(color = Divider)
            
            // 消息通知
            SettingsToggleItem(
                icon = Icons.Default.Notifications,
                title = "消息通知",
                subtitle = "接收异常预警和重要提醒",
                checked = notificationEnabled,
                onCheckedChange = { notificationEnabled = it }
            )
            
            HorizontalDivider(color = Divider)
            
            // 自动同步
            SettingsToggleItem(
                icon = Icons.Default.Sync,
                title = "自动同步",
                subtitle = "自动同步学生数据和考勤记录",
                checked = autoSyncEnabled,
                onCheckedChange = { autoSyncEnabled = it }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 数据管理
        SettingsSection(title = "数据管理") {
            SettingsClickItem(
                icon = Icons.Default.CloudDownload,
                title = "数据同步",
                subtitle = "上次同步: 今天 14:30",
                onClick = { /* TODO: 手动同步 */ }
            )
            
            HorizontalDivider(color = Divider)
            
            SettingsClickItem(
                icon = Icons.Default.Storage,
                title = "缓存管理",
                subtitle = "已缓存: 128 MB",
                onClick = { /* TODO: 清理缓存 */ }
            )
            
            HorizontalDivider(color = Divider)
            
            SettingsClickItem(
                icon = Icons.Default.Assessment,
                title = "考勤统计",
                subtitle = "查看本学期考勤数据",
                onClick = { /* TODO: 查看统计 */ }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 关于与帮助
        SettingsSection(title = "关于与帮助") {
            SettingsClickItem(
                icon = Icons.Default.Help,
                title = "使用帮助",
                subtitle = "查看使用指南和常见问题",
                onClick = { /* TODO: 帮助页面 */ }
            )
            
            HorizontalDivider(color = Divider)
            
            SettingsClickItem(
                icon = Icons.Default.Info,
                title = "关于应用",
                subtitle = "版本 1.0.0",
                onClick = { /* TODO: 关于页面 */ }
            )
            
            HorizontalDivider(color = Divider)
            
            SettingsClickItem(
                icon = Icons.Default.Feedback,
                title = "意见反馈",
                subtitle = "帮助我们改进产品",
                onClick = { /* TODO: 反馈页面 */ }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 退出登录按钮
        OutlinedButton(
            onClick = { /* TODO: 退出登录 */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AccentRed
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(AccentRed)
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("退出登录")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 用户信息卡片
 */
@Composable
private fun UserInfoCard(
    modifier: Modifier = Modifier
) {
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
            // 头像
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = CyanPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "张老师",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "高三年级 · 数学教师",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "南方科技大学附属中学",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            
            IconButton(onClick = { /* TODO: 编辑资料 */ }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑资料",
                    tint = TextSecondary
                )
            }
        }
    }
}

/**
 * 设置区块
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

/**
 * 可切换的设置项
 */
@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = CyanPrimary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyanPrimary,
                checkedTrackColor = CyanPrimary.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * 可点击的设置项
 */
@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = CyanPrimary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ProfileScreenPreview() {
    MobileappTheme {
        ProfileScreen()
    }
}
