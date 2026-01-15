package com.sustech.bojayL.glasses.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 逆时针旋转 90 度的布局包装器
 * 
 * 用于将横屏系统下的 UI 旋转为纵向显示，
 * 适配 Rokid AR 眼镜的物理屏幕方向。
 * 
 * 特点：
 * - 宽高自动交换
 * - 内容居中显示
 * - 占满旋转后的可用空间
 */
@Composable
fun RotatedLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 获取当前容器的约束（横屏下宽 > 高）
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        
        // 旋转后，内容的宽度变成容器的高度，高度变成容器的宽度
        // 这样可以让内容占满旋转后的可用空间
        Box(
            modifier = Modifier
                // 交换宽高：让内容区域使用旋转后的尺寸
                .size(width = containerHeight, height = containerWidth)
                // 逆时针旋转 90 度
                .graphicsLayer {
                    rotationZ = -90f
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * 可配置角度的旋转布局
 */
@Composable
fun RotatedLayout(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        
        // 判断是否需要交换宽高（90度或270度旋转时）
        val needSwap = (rotationDegrees.toInt() % 180) != 0
        
        Box(
            modifier = Modifier
                .size(
                    width = if (needSwap) containerHeight else containerWidth,
                    height = if (needSwap) containerWidth else containerHeight
                )
                .graphicsLayer {
                    rotationZ = rotationDegrees
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
