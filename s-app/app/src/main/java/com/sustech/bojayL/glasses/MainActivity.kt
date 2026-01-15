package com.sustech.bojayL.glasses

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.sustech.bojayL.glasses.input.*
import com.sustech.bojayL.glasses.ui.screens.HudScreen
import com.sustech.bojayL.glasses.ui.theme.GlassesAppTheme
import com.sustech.bojayL.glasses.viewmodel.HudViewModel

/**
 * AR 智慧课堂 - 眼镜端 MainActivity
 * 
 * 主要功能：
 * - 显示 AR HUD 界面
 * - 处理触摸板按键事件
 * - 与手机端通信
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "GlassesMainActivity"
    }
    
    private val viewModel: HudViewModel by viewModels()
    
    // 按键接收器
    private val keyReceiver = KeyReceiver()
    
    // 滑动检测器
    private val swipeDetector = SwipeDetector()
    
    // 相机权限请求
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
            initializeCamera()
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 设置透明窗口 (AR眼镜关键)
        setupTransparentWindow()
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 设置按键监听
        setupKeyReceiver()
        
        setContent {
            GlassesAppTheme {
                HudScreen(viewModel = viewModel)
            }
        }
        
        // 检查并请求相机权限
        checkCameraPermission()
        
        Log.d(TAG, "MainActivity created")
    }
    
    /**
     * 设置透明窗口
     * 
     * AR眼镜必须使用透明背景，让用户看到现实世界
     */
    private fun setupTransparentWindow() {
        // 设置窗口背景透明
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        
        // 设置窗口标志
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        
        // 让内容延伸到系统栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        Log.d(TAG, "Transparent window configured")
    }
    
    /**
     * 检查相机权限
     */
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted")
                initializeCamera()
            }
            else -> {
                Log.d(TAG, "Requesting camera permission")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * 初始化相机
     */
    private fun initializeCamera() {
        viewModel.initCamera(this, this)
    }
    
    /**
     * 设置按键广播接收器
     */
    private fun setupKeyReceiver() {
        keyReceiver.listener = object : KeyReceiverListener {
            override fun onKeyEvent(keyType: KeyType) {
                Log.d(TAG, "Key event received: $keyType")
                viewModel.onKeyEvent(keyType)
            }
        }
        
        // 注册广播接收器
        registerReceiver(keyReceiver, KeyReceiver.createIntentFilter())
    }
    
    /**
     * 处理物理按键事件（用于滑动检测）
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: $keyCode")
        
        // 检测滑动手势
        val swipe = swipeDetector.onKeyDown(keyCode)
        if (swipe != null) {
            viewModel.onSwipe(swipe)
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onResume() {
        super.onResume()
        // 可以在此处自动开始采集（可选）
        // viewModel.startCapture()
    }
    
    override fun onPause() {
        super.onPause()
        // 暂停采集
        viewModel.stopCapture()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 释放相机资源
        viewModel.releaseCamera()
        
        // 取消注册广播接收器
        try {
            unregisterReceiver(keyReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister receiver", e)
        }
        
        Log.d(TAG, "MainActivity destroyed")
    }
}
