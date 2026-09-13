/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings.ui.picker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lineageos.device.settings.R
import org.lineageos.device.settings.ui.rememberDrawablePainter

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

private val PACKAGE_WHITELIST = arrayOf(
    "android",
    "com.android.systemui",
    "com.android.providers.downloads",
)

suspend fun loadLauncherApps(
    context: Context,
    excluded: Set<String>,
): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val byPackage = LinkedHashMap<String, InstalledApp>()
    val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    for (info in pm.queryIntentActivities(main, 0)) {
        val app = info.activityInfo.applicationInfo
        if (app.packageName in excluded) continue
        val existing = byPackage[app.packageName]
        if (existing == null) {
            byPackage[app.packageName] = InstalledApp(
                packageName = app.packageName,
                label = app.loadLabel(pm).toString(),
                icon = app.loadIcon(pm),
            )
        }
    }
    for (packageName in PACKAGE_WHITELIST) {
        if (packageName in excluded || byPackage.containsKey(packageName)) continue
        try {
            val app = pm.getApplicationInfo(packageName, 0)
            byPackage[packageName] = InstalledApp(
                packageName = app.packageName,
                label = app.loadLabel(pm).toString(),
                icon = app.loadIcon(pm),
            )
        } catch (_: PackageManager.NameNotFoundException) {
        }
    }
    byPackage.values.sortedBy { it.label.lowercase() }
}

@Composable
fun AppPickerDialog(
    title: String,
    excluded: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val apps by produceState(initialValue = emptyList<InstalledApp>(), excluded) {
        value = loadLauncherApps(context, excluded)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (apps.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.packageName) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Image(
                                painter = rememberDrawablePainter(app.icon),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.notification_slider_app_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

@Composable
fun RadioChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedKey == key,
                            onClick = { onSelect(key) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun HbmWarningDialog(
    onEnable: (dontShowAgain: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val checked = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.hbm_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.hbm_warning_summary))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked.value,
                        onCheckedChange = { checked.value = it },
                    )
                    Text(stringResource(R.string.hbm_warning_dont_show_again))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEnable(checked.value) }) {
                Text(stringResource(R.string.hbm_btn_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}
