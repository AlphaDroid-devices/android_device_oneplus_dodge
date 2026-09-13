/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui.banner

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemProperties
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.util.Locale

/**
 * Expand-edge inventory reads. No 1 Hz poller. Health is one SOH sample
 * (changes on a charge cycle). SKU is OP*, CPH*, or PJZ*; the model is always 13.
 */
data class BannerSnapshot(
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val storageUsedGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val batteryPct: Int = -1,
    val batteryTemp: Float = Float.NaN,
)

object BannerFacts {
    private const val SOH_UI = "/sys/class/oplus_chg/battery/ui_soh"
    private const val SOH_BATT = "/sys/class/oplus_chg/battery/battery_soh"
    private const val CHARGE_FULL = "/sys/class/power_supply/battery/charge_full"
    private const val CHARGE_FULL_DESIGN = "/sys/class/power_supply/battery/charge_full_design"

    /** SKU from product props, uppercased. Dodge is OnePlus 13. */
    fun productSku(): String {
        val keys = arrayOf(
            "ro.product.odm.device",
            "ro.product.system.device",
            "ro.product.vendor.device",
            "ro.product.device",
            "ro.build.product",
        )
        val values = keys.map { SystemProperties.get(it, "") }.filter { it.isNotEmpty() }
        val sku = values.firstOrNull {
            it.startsWith("OP", ignoreCase = true) ||
                it.startsWith("CPH", ignoreCase = true) ||
                it.startsWith("PJZ", ignoreCase = true)
        } ?: values.firstOrNull() ?: return "--"
        return sku.uppercase(Locale.US)
    }

    fun modelName(sku: String): String = "13"

    fun readVolatile(context: Context): BannerSnapshot {
        val mem = memory()
        val disk = storage()
        val batt = battery(context)
        return BannerSnapshot(
            ramUsedGb = mem?.let { (it[0] - it[1]) / 1048576f } ?: 0f,
            ramTotalGb = mem?.let { it[0] / 1048576f } ?: 0f,
            storageUsedGb = disk?.let { (it[0] - it[1]) / GiB } ?: 0f,
            storageTotalGb = disk?.let { it[0] / GiB } ?: 0f,
            batteryPct = batt.first,
            batteryTemp = batt.second,
        )
    }

    /** Battery SOH. Changes on a charge cycle, not on every expand. */
    fun readHealth(): Int {
        parsePct(firstLine(SOH_UI))?.let { return it }
        parsePct(firstLine(SOH_BATT))?.let { return it }
        val full = parseLong(firstLine(CHARGE_FULL))
        val design = parseLong(firstLine(CHARGE_FULL_DESIGN))
        if (full != null && design != null && design > 0L) {
            return ((100L * full) / design).toInt().coerceIn(0, 100)
        }
        return -1
    }

    private fun battery(context: Context): Pair<Int, Float> {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return -1 to Float.NaN
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        val pct = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temp = if (tenths != Int.MIN_VALUE) tenths / 10f else Float.NaN
        return pct to temp
    }

    private fun memory(): LongArray? {
        var total = 0L
        var avail = 0L
        try {
            BufferedReader(FileReader("/proc/meminfo")).use { br ->
                var line = br.readLine()
                while (line != null && (total == 0L || avail == 0L)) {
                    if (line.startsWith("MemTotal:")) total = memValue(line)
                    else if (line.startsWith("MemAvailable:")) avail = memValue(line)
                    line = br.readLine()
                }
            }
        } catch (_: IOException) {
            return null
        }
        return if (total > 0) longArrayOf(total, avail) else null
    }

    private fun storage(): LongArray? = try {
        val st = StatFs(Environment.getDataDirectory().absolutePath)
        val total = st.blockCountLong * st.blockSizeLong
        val avail = st.availableBlocksLong * st.blockSizeLong
        if (total > 0) longArrayOf(total, avail) else null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun memValue(line: String): Long {
        val p = line.split(Regex("\\s+"))
        return try {
            if (p.size < 2) 0 else p[1].toLong()
        } catch (_: NumberFormatException) {
            0
        }
    }

    private fun parsePct(raw: String?): Int? {
        val v = raw?.trim() ?: return null
        return try {
            v.toFloat().toInt().takeIf { it in 1..100 }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseLong(raw: String?): Long? {
        val v = raw?.trim() ?: return null
        return try {
            v.toLong()
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun firstLine(path: String): String? = try {
        BufferedReader(FileReader(path)).use { it.readLine() }
    } catch (_: IOException) {
        null
    }

    private const val GiB = 1024f * 1024f * 1024f
}
