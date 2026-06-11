package com.rgbledsync

data class ColorEntry(
    val name: String,
    val rgb: Int,
    val necCode: Long
)

object ColorMapping {
    val colors = listOf(
        ColorEntry("Orange",      0xFFFF9800, 0xF70810EFL),
        ColorEntry("Light Green", 0xFF8BC34A, 0xF708906FL),
        ColorEntry("Dark Blue",   0xFF1A237E, 0xF70850AFL),
        ColorEntry("Deep Yellow", 0xFFFFC107, 0xF70830CFL),
        ColorEntry("Cyan",        0xFF00BCD4, 0xF708B04FL),
        ColorEntry("Purple",      0xFF9C27B0, 0xF708708FL),
        ColorEntry("Light Yellow",0xFFFFF9C4, 0xF70808F7L),
        ColorEntry("Teal",        0xFF009688, 0xF7088877L),
        ColorEntry("Magenta",     0xFFE91E63, 0xF70848B7L),
    )

    val modes = listOf(
        ColorEntry("FLASH",  0xFFFFFFFF, 0xF708D02FL),
        ColorEntry("STROBE", 0xFFFFFFFF, 0xF708F00FL),
        ColorEntry("FADE",   0xFFFFFFFF, 0xF708C837L),
    )

    fun findClosestColor(targetRgb: Int): ColorEntry {
        val tr = (targetRgb shr 16) and 0xFF
        val tg = (targetRgb shr 8) and 0xFF
        val tb = targetRgb and 0xFF

        return colors.minBy { entry ->
            val er = (entry.rgb shr 16) and 0xFF
            val eg = (entry.rgb shr 8) and 0xFF
            val eb = entry.rgb and 0xFF

            val dr = (tr - er).toDouble()
            val dg = (tg - eg).toDouble()
            val db = (tb - eb).toDouble()

            0.3 * dr * dr + 0.59 * dg * dg + 0.11 * db * db
        }
    }
}
