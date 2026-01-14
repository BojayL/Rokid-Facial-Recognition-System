package com.sustech.bojayL.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.annotation.WorkerThread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageCrop {
    /**
     * 根据裁剪 UI 的变换参数，从原图裁出与裁剪框对应的区域。
     * @param containerWidth 裁剪容器宽（像素）
     * @param containerHeight 裁剪容器高（像素）
     * @param scale 用户缩放因子
     * @param offsetX 用户平移 X（像素，容器坐标系）
     * @param offsetY 用户平移 Y（像素，容器坐标系）
     * @param isCircle 是否输出为圆形 PNG（透明背景）
     * @return 裁剪后图片的临时 Uri（cache 目录）
     */
    @WorkerThread
    fun cropImage(
        context: Context,
        imageUri: Uri,
        containerWidth: Int,
        containerHeight: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        isCircle: Boolean
    ): Uri {
        require(containerWidth > 0 && containerHeight > 0) { "Invalid container size" }

        val source = decodeBitmap(context, imageUri)
        val imgW = source.width.toFloat()
        val imgH = source.height.toFloat()

        val cw = containerWidth.toFloat()
        val ch = containerHeight.toFloat()

        // 与 UI 保持一致：ContentScale.Fit 的适配
        val fitScale = min(cw / imgW, ch / imgH)
        val fittedW = imgW * fitScale
        val fittedH = imgH * fitScale
        val dispW = fittedW * scale
        val dispH = fittedH * scale

        val startX = cw / 2f - dispW / 2f + offsetX
        val startY = ch / 2f - dispH / 2f + offsetY

        val cropSize = min(cw, ch) * 0.7f
        val cropLeft = (cw - cropSize) / 2f
        val cropTop = (ch - cropSize) / 2f

        // 容器 -> 图像归一化坐标，再 -> 原图像素
        val relLeft = (cropLeft - startX) / dispW
        val relTop = (cropTop - startY) / dispH
        val relRight = (cropLeft + cropSize - startX) / dispW
        val relBottom = (cropTop + cropSize - startY) / dispH

        var srcLeft = (relLeft * imgW).roundToInt()
        var srcTop = (relTop * imgH).roundToInt()
        var srcRight = (relRight * imgW).roundToInt()
        var srcBottom = (relBottom * imgH).roundToInt()

        // 裁剪边界修正
        srcLeft = max(0, srcLeft)
        srcTop = max(0, srcTop)
        srcRight = min(source.width, srcRight)
        srcBottom = min(source.height, srcBottom)

        val width = srcRight - srcLeft
        val height = srcBottom - srcTop
        require(width > 0 && height > 0) { "裁剪区域超出图片范围" }

        val cropped = Bitmap.createBitmap(source, srcLeft, srcTop, width, height)
        source.recycle()

        val finalBmp = if (isCircle) applyCircleMask(cropped) else cropped
        val outFile = java.io.File(context.cacheDir, if (isCircle) {
            "crop_${System.currentTimeMillis()}.png"
        } else {
            "crop_${System.currentTimeMillis()}.jpg"
        })

        if (isCircle) {
            java.io.FileOutputStream(outFile).use { fos ->
                finalBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } else {
            java.io.FileOutputStream(outFile).use { fos ->
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
        }
        if (finalBmp !== cropped) cropped.recycle()

        return Uri.fromFile(outFile)
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                // 禁用 hardware bitmap，避免 "software rendering doesn't support hardware bitmaps" 错误
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input) ?: throw IllegalArgumentException("无法解码图片")
            }
        }
    }

    private fun applyCircleMask(src: Bitmap): Bitmap {
        val size = min(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val srcRect = if (src.width == src.height) Rect(0, 0, src.width, src.height) else {
            // 居中裁成正方形再画圆
            val left = (src.width - size) / 2
            val top = (src.height - size) / 2
            Rect(left, top, left + size, top + size)
        }
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, srcRect, rect, paint)
        return out
    }
}
