package com.rgbledsync

import android.graphics.Bitmap
import android.graphics.Color

object ColorAnalyzer {

    fun analyzeBitmap(bitmap: Bitmap): Int {
        val sampleSize = 4
        val w = bitmap.width
        val h = bitmap.height

        val cx = w / 2
        val cy = h / 2
        val regionSize = minOf(w, h) / 3

        val left = (cx - regionSize / 2).coerceAtLeast(0)
        val top = (cy - regionSize / 2).coerceAtLeast(0)
        val right = (cx + regionSize / 2).coerceAtMost(w)
        val bottom = (cy + regionSize / 2).coerceAtMost(h)

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                rSum += Color.red(pixel)
                gSum += Color.green(pixel)
                bSum += Color.blue(pixel)
                count++
                x += sampleSize
            }
            y += sampleSize
        }

        if (count == 0) return 0

        return Color.rgb(
            (rSum / count).toInt(),
            (gSum / count).toInt(),
            (bSum / count).toInt()
        )
    }
}
