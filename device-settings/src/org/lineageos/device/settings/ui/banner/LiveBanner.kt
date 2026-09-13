/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui.banner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.lineageos.device.settings.R
import java.util.Locale

/**
 * Expanded inventory on a bottom-right frosted plate. The 1+ in the art is a
 * watermark, not a hero. Frost a full-size banner copy, then clip — do not
 * scale a crop. See device-settings/README.md.
 */

private val COL_LABEL = Color(0xFF8A97A6)
private val COL_VALUE = Color(0xFFF2F5F8)

/** Bottom-right plate. Left/top stay fractional; right/bottom are 12.dp. */
private const val PANEL_START = 0.345f
private const val PANEL_TOP = 0.20f
private val PANEL_END = 12.dp
private val PANEL_BOTTOM = 12.dp
private const val LABEL_SIZE = 0.055f
private const val VALUE_SIZE = 0.065f
private const val ICON_SIZE = 0.085f
private const val ZONE_CORNER = 0.035f
internal val ZONE_SCRIM = Color.Black.copy(alpha = 0.2f)
private val ZONE_INSET = 10.dp
internal val ZONE_BLUR = 16.dp

@Composable
fun LiveBanner(
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sku = remember { BannerFacts.productSku() }
    val model = remember(sku) { BannerFacts.modelName(sku) }
    var snap by remember { mutableStateOf(BannerSnapshot()) }
    var health by remember { mutableIntStateOf(-1) }
    DisposableEffect(active) {
        if (active) {
            snap = BannerFacts.readVolatile(context)
            if (health < 0) {
                health = BannerFacts.readHealth()
            }
        }
        onDispose { }
    }
    BoxWithConstraints(modifier) {
        val bannerW = maxWidth
        val bannerH = maxHeight
        val labelSp = with(density) { (bannerH * LABEL_SIZE).toSp() }
        val valueSp = with(density) { (bannerH * VALUE_SIZE).toSp() }
        val icon = bannerH * ICON_SIZE
        val padStart = bannerW * PANEL_START
        val padEnd = PANEL_END
        val padTop = bannerH * PANEL_TOP
        val padBottom = PANEL_BOTTOM
        val zoneCorner = bannerW * ZONE_CORNER
        val bannerPainter = painterResource(R.drawable.oplus_banner)
        Image(
            painter = bannerPainter,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = bannerPainter,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clipToPanel(padStart, padEnd, padTop, padBottom, zoneCorner)
                .blur(ZONE_BLUR),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padStart,
                    end = padEnd,
                    top = padTop,
                    bottom = padBottom,
                ),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(zoneCorner))
                    .background(ZONE_SCRIM),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ZONE_INSET),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
            BoardRow(
                icon = Icons.Outlined.Smartphone,
                label = "Device",
                value = model,
                extra = sku.takeIf { it != "--" },
                iconSize = icon,
                labelSize = labelSp,
                valueSize = valueSp,
            )
            BoardRow(
                icon = Icons.Outlined.Memory,
                label = "RAM",
                value = gbPair(snap.ramUsedGb, snap.ramTotalGb, usedDecimals = 1),
                iconSize = icon,
                labelSize = labelSp,
                valueSize = valueSp,
            )
            BoardRow(
                icon = Icons.Outlined.Storage,
                label = "Storage",
                value = gbPair(snap.storageUsedGb, snap.storageTotalGb, usedDecimals = 0),
                iconSize = icon,
                labelSize = labelSp,
                valueSize = valueSp,
            )
            BoardRow(
                icon = Icons.Outlined.BatteryStd,
                label = "Battery",
                value = if (snap.batteryPct < 0) "--" else "${snap.batteryPct}%",
                extra = if (snap.batteryTemp.isNaN()) null
                else String.format(Locale.US, "%.1f°C", snap.batteryTemp),
                iconSize = icon,
                labelSize = labelSp,
                valueSize = valueSp,
            )
            BoardRow(
                icon = Icons.Outlined.FavoriteBorder,
                label = "Health",
                value = if (health < 0) "--" else "$health%",
                iconSize = icon,
                labelSize = labelSp,
                valueSize = valueSp,
            )
            }
        }
    }
}

@Composable
private fun BoardRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconSize: Dp,
    labelSize: TextUnit,
    valueSize: TextUnit,
    extra: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = COL_LABEL,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = label,
            color = COL_LABEL,
            fontSize = labelSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (extra != null) {
            Text(
                text = extra,
                color = COL_LABEL,
                fontSize = labelSize,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = value,
            color = COL_VALUE,
            fontSize = valueSize,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Clip after a full-size blur so the plate samples the banner 1:1, not a scaled crop. */
private fun Modifier.clipToPanel(
    padStart: Dp,
    padEnd: Dp,
    padTop: Dp,
    padBottom: Dp,
    corner: Dp,
): Modifier = drawWithCache {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = padStart.toPx(),
                top = padTop.toPx(),
                right = size.width - padEnd.toPx(),
                bottom = size.height - padBottom.toPx(),
                cornerRadius = CornerRadius(corner.toPx()),
            ),
        )
    }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

private fun gbPair(used: Float, total: Float, usedDecimals: Int): String {
    if (total <= 0f) return "--"
    return if (usedDecimals <= 0) {
        String.format(Locale.US, "%.0f/%.0fG", used, total)
    } else {
        String.format(Locale.US, "%.1f/%.0fG", used, total)
    }
}
