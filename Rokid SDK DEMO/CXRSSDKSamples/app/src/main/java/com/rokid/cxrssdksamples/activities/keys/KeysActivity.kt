package com.rokid.cxrssdksamples.activities.keys

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

class KeysActivity : ComponentActivity() {

    private val viewModel: KeysViewModel by viewModels()
    private var latestKeyType by mutableStateOf<KeyType?>(null)

    private val keyReceiver = KeyReceiver().apply {
        listener = object : KeyReceiverListener {
            override fun onReceive(keyType: KeyType) {
                // 更新状态以在UI中显示最新的按键类型
                latestKeyType = keyType

                when (keyType) {
                    KeyType.CLICK -> {
                        // 处理按键点击事件
                    }
                    KeyType.BUTTON_DOWN -> {
                        // 处理按键按下事件
                    }
                    KeyType.BUTTON_UP -> {
                        // 处理按键抬起事件
                    }
                    KeyType.DOUBLE_CLICK -> {
                        // 处理按键双击事件
                    }
                    KeyType.AI_START -> {
                        // 处理AI开始事件
                    }
                    KeyType.LONG_PRESS -> {
                        // 处理按键长按事件
                    }
                }
            }
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 设置屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContent {
            CXRSSDKSamplesTheme {
                KeysScreen(latestKeyType = latestKeyType?.name ?: "")
            }
        }
        registerReceiver(keyReceiver, IntentFilter().apply {
            addAction(KeyType.CLICK.action)
            addAction(KeyType.BUTTON_DOWN.action)
            addAction(KeyType.BUTTON_UP.action)
            addAction(KeyType.DOUBLE_CLICK.action)
            addAction(KeyType.AI_START.action)
            addAction(KeyType.LONG_PRESS.action)
            priority = 100
        })
    }

    override fun onDestroy() {
        unregisterReceiver(keyReceiver)
        super.onDestroy()
    }
}

@Composable
fun KeysScreen(latestKeyType: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "在该界面可以屏蔽所有的可屏蔽按键进行自定义操作", color = Color.Green)
        Text(text = "Key = $latestKeyType", color = Color.Green)

    }
}

@Preview(showBackground = true)
@Composable
fun KeysScreenPreview() {
    CXRSSDKSamplesTheme {
        KeysScreen("")
    }
}