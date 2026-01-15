package com.sustech.bojayL.glasses.input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * 按键类型
 */
enum class KeyType(val action: String) {
    /** 单击 */
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    /** 按下 */
    BUTTON_DOWN("com.android.action.ACTION_SPRITE_BUTTON_DOWN"),
    /** 抬起 */
    BUTTON_UP("com.android.action.ACTION_SPRITE_BUTTON_UP"),
    /** 双击 */
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    /** AI 启动 */
    AI_START("com.android.action.ACTION_AI_START"),
    /** 长按 */
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS")
}

/**
 * 按键监听器接口
 */
interface KeyReceiverListener {
    fun onKeyEvent(keyType: KeyType)
}

/**
 * 触摸板按键广播接收器
 * 
 * 监听 Rokid 眼镜触摸板的各种手势事件
 */
class KeyReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "KeyReceiver"
        private const val RECEIVER_PRIORITY = 100
        
        /**
         * 创建 IntentFilter
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(KeyType.CLICK.action)
                addAction(KeyType.BUTTON_DOWN.action)
                addAction(KeyType.BUTTON_UP.action)
                addAction(KeyType.DOUBLE_CLICK.action)
                addAction(KeyType.AI_START.action)
                addAction(KeyType.LONG_PRESS.action)
                priority = RECEIVER_PRIORITY
            }
        }
    }
    
    var listener: KeyReceiverListener? = null
    
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        
        Log.d(TAG, "Received action: $action")
        
        when (action) {
            KeyType.CLICK.action -> {
                listener?.onKeyEvent(KeyType.CLICK)
                abortBroadcast()  // 截断广播，阻止系统处理
            }
            KeyType.BUTTON_DOWN.action -> {
                listener?.onKeyEvent(KeyType.BUTTON_DOWN)
                abortBroadcast()
            }
            KeyType.BUTTON_UP.action -> {
                listener?.onKeyEvent(KeyType.BUTTON_UP)
                abortBroadcast()
            }
            KeyType.DOUBLE_CLICK.action -> {
                // 双击事件 - 用于重置状态
                listener?.onKeyEvent(KeyType.DOUBLE_CLICK)
                // 不截断，允许系统处理（可能用于退出）
            }
            KeyType.AI_START.action -> {
                listener?.onKeyEvent(KeyType.AI_START)
                abortBroadcast()
            }
            KeyType.LONG_PRESS.action -> {
                // 长按 - 用于切换模式
                listener?.onKeyEvent(KeyType.LONG_PRESS)
                abortBroadcast()
            }
        }
    }
}

/**
 * 滑动手势检测器
 * 
 * 基于 onKeyDown 事件序列检测前滑/后滑
 */
class SwipeDetector {
    
    companion object {
        private const val TAG = "SwipeDetector"
    }
    
    private var lastKeyCode = -1
    
    /**
     * 处理按键事件
     * 
     * @param keyCode 当前按键码
     * @return 检测到的滑动方向，null 表示不是滑动
     */
    fun onKeyDown(keyCode: Int): SwipeDirection? {
        val result = when {
            // keycode 22 -> 20: 前滑
            lastKeyCode == 22 && keyCode == 20 -> SwipeDirection.FORWARD
            // keycode 21 -> 19: 后滑
            lastKeyCode == 21 && keyCode == 19 -> SwipeDirection.BACKWARD
            else -> null
        }
        
        lastKeyCode = keyCode
        
        if (result != null) {
            Log.d(TAG, "Detected swipe: $result")
        }
        
        return result
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        lastKeyCode = -1
    }
}

/**
 * 滑动方向
 */
enum class SwipeDirection {
    /** 前滑（向镜腿方向） */
    FORWARD,
    /** 后滑（向镜片方向） */
    BACKWARD
}
