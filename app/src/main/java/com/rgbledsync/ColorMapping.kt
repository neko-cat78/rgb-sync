package com.rgbledsync

data class ColorEntry(
    val name: String,
    val rgb: Int,
    val necCode: Long
)

object ColorMapping {
    private fun necCode(cmd: Int): Long {
        val addr = 0xF7L
        val addrInv = 0x08L
        val cmdL = cmd.toLong() and 0xFF
        val cmdInv = cmdL xor 0xFF
        return (cmdInv shl 24) or (cmdL shl 16) or (addrInv shl 8) or addr
    }

    val colors = listOf(
        ColorEntry("Green",       0xFF4CAF50L.toInt(), necCode(0xA0)),
        ColorEntry("Red",         0xFFF44336L.toInt(), necCode(0x20)),
        ColorEntry("Orange",      0xFFFF9800L.toInt(), necCode(0x10)),
        ColorEntry("Dark Blue",   0xFF1A237EL.toInt(), necCode(0x50)),
        ColorEntry("Deep Yellow", 0xFFFFC107L.toInt(), necCode(0x30)),
        ColorEntry("Cyan",        0xFF00BCD4L.toInt(), necCode(0xB0)),
        ColorEntry("Purple",      0xFF9C27B0L.toInt(), necCode(0x70)),
        ColorEntry("Light Yellow",0xFFFFF9C4L.toInt(), necCode(0x08)),
        ColorEntry("Teal",        0xFF009688L.toInt(), necCode(0x88)),
        ColorEntry("Magenta",     0xFFE91E63L.toInt(), necCode(0x48)),
    )

    val modes = listOf(
        ColorEntry("FLASH",  0xFFFFFFFFL.toInt(), necCode(0xD0)),
        ColorEntry("STROBE", 0xFFFFFFFFL.toInt(), necCode(0xF0)),
        ColorEntry("FADE",   0xFFFFFFFFL.toInt(), necCode(0xC8)),
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
