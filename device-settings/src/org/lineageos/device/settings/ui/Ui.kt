/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.axion.compose.preferences.ClickablePreference
import org.lineageos.device.settings.ui.picker.RadioChoiceDialog
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager

internal fun defaultPrefs(context: Context): SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(context)

@Composable
internal fun PrefIcon(@DrawableRes id: Int) {
    val context = LocalContext.current
    val uiMode = context.resources.configuration.uiMode
    val drawable = remember(id, uiMode) {
        ContextCompat.getDrawable(context, id)?.mutate()
    } ?: return
    Icon(
        painter = rememberDrawablePainter(drawable),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Kit [com.android.axion.compose.preferences.ListPreference] has no icon slot. */
@Composable
internal fun IconListPreference(
    title: String,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
    @DrawableRes icon: Int,
    enabled: Boolean = true,
) {
    var show by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second
    ClickablePreference(
        title = title,
        summary = selectedLabel,
        enabled = enabled,
        customIcon = { PrefIcon(icon) },
        onClick = { show = true },
    )
    if (show && enabled) {
        RadioChoiceDialog(
            title = title,
            options = options,
            selectedKey = value,
            onSelect = {
                onValueChange(it)
                show = false
            },
            onDismiss = { show = false },
        )
    }
}

@Composable
internal fun rememberAppPainter(packageName: String): Painter? {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    } ?: return null
    return rememberDrawablePainter(drawable)
}

@Composable
internal fun rememberDrawablePainter(drawable: Drawable): Painter {
    val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
    return BitmapPainter(bitmap)
}

@Composable
internal fun AppIcon(packageName: String) {
    val painter = rememberAppPainter(packageName)
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
internal fun SettingsScroll(
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

internal fun arrayOptions(
    context: Context,
    entriesId: Int,
    valuesId: Int,
): List<Pair<String, String>> {
    val entries = context.resources.getStringArray(entriesId)
    val values = context.resources.getStringArray(valuesId)
    return values.zip(entries.toList())
}

internal fun appLabel(context: Context, packageName: String): String {
    if (packageName.isEmpty()) return ""
    val pm = context.packageManager
    return try {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
}
