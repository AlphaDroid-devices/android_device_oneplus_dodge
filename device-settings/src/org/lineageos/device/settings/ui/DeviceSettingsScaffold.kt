/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package org.lineageos.device.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.lineageos.device.settings.R
import org.lineageos.device.settings.ui.banner.LiveBanner
import org.lineageos.device.settings.ui.banner.ZONE_BLUR
import org.lineageos.device.settings.ui.banner.ZONE_SCRIM

/** Must match oplus_banner.png / oplus_mask.png pixel size. */
private const val ART_W = 1080f
private const val ART_H = 662f
private const val MASK_W = 1080f
private const val MASK_H = 182f

/** Baked round on the 1080-wide PNGs is ~20px. */
private const val CORNER = 20f / 1080f

/** Original small icon button. 22.dp screen start = 16.dp art gutter + 6.dp extra. */
private val BACK_SIZE = 40.dp
private val BACK_ICON = 24.dp
private val BACK_START = 6.dp
private val BACK_TOP = 10.dp

/** Shared collapsing header: expanded LiveBanner, collapsed oplus_mask stamp. */
@Composable
fun DeviceSettingsScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artWidth = (maxWidth - 32.dp).coerceAtLeast(1.dp)
        val artHeight = artWidth * (ART_H / ART_W)
        val stripHeight = artWidth * (MASK_H / MASK_W)
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val artHeightPx = with(density) { artHeight.toPx() }
        val stripHeightPx = with(density) { stripHeight.toPx() }
        val expandedPx = statusPx + artHeightPx
        val collapsedPx = statusPx + stripHeightPx
        val statusDp = with(density) { statusPx.toDp() }
        val pageColor = MaterialTheme.colorScheme.surfaceContainer
        SideEffect {
            scrollBehavior.state.heightOffsetLimit = collapsedPx - expandedPx
        }
        val barHeight = with(density) {
            (expandedPx + scrollBehavior.state.heightOffset).toDp()
        }
        val expandedAlpha =
            (1f - scrollBehavior.state.collapsedFraction).coerceIn(0f, 1f)
        val collapsedAlpha =
            scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = pageColor,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            ),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(artHeight)
                            .padding(horizontal = 16.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = statusDp)
                            .clip(RoundedCornerShape(artWidth * CORNER)),
                    ) {
                        LiveBanner(
                            active = expandedAlpha > 0.01f,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.TopCenter)
                                .alpha(expandedAlpha),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(stripHeight)
                                .align(Alignment.TopCenter),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.oplus_mask),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(collapsedAlpha),
                            )
                            Image(
                                painter = painterResource(R.drawable.oplus_mask),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(collapsedAlpha)
                                    .clipToCircle(BACK_START, BACK_TOP, BACK_SIZE)
                                    .blur(ZONE_BLUR),
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.oplus_banner),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(expandedAlpha)
                                .clipToCircle(BACK_START, BACK_TOP, BACK_SIZE)
                                .blur(ZONE_BLUR),
                        )
                        Surface(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = BACK_START, top = BACK_TOP)
                                .size(BACK_SIZE),
                            shape = CircleShape,
                            color = ZONE_SCRIM,
                            contentColor = Color.White,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(BACK_ICON),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(statusDp)
                            .align(Alignment.TopCenter)
                            .background(pageColor),
                    )
                }
            },
            content = content,
        )
    }
}

/** Clip after a full-size blur so the circle samples the art 1:1. */
private fun Modifier.clipToCircle(x: Dp, y: Dp, d: Dp): Modifier = drawWithCache {
    val path = Path().apply {
        addOval(Rect(x.toPx(), y.toPx(), (x + d).toPx(), (y + d).toPx()))
    }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}
