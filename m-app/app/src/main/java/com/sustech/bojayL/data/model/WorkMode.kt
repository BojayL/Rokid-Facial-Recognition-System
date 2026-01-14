package com.sustech.bojayL.data.model

/**
 * 工作模式
 * 
 * 定义应用的两种工作模式：
 * - GLASSES: 眼镜模式，通过 WebSocket 接收眼镜端识别结果
 * - PHONE_CAMERA: 手机相机模式，使用手机摄像头进行本地实时识别
 */
enum class WorkMode(val label: String, val description: String) {
    /**
     * 眼镜模式
     * 需要连接 AR 眼镜，识别结果由眼镜端推送
     */
    GLASSES("眼镜模式", "连接 AR 眼镜进行识别"),
    
    /**
     * 手机相机模式
     * 使用手机摄像头进行本地实时人脸检测和识别
     */
    PHONE_CAMERA("手机模式", "使用手机摄像头识别")
}
