/*
 * Overhaul Phase 3, 2026-07: the whole UI as Compose / Material 3. Replaces
 * FragmentRotatorlatorConfigurator + AdapterRotatorlatorConfigs + the XML layouts.
 * The 7 avd_* AnimatedVectorDrawables carry over via AndroidView/ImageView (see
 * AvdCycleButton for why not compose animation-graphics).
 */

package com.justbnutz.dockorientationrotatorlator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Surface
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// region ================== GATE (WRITE_SETTINGS) ==================
// ====== ================== ====================== ==================

/**
 * Root composable: gates the main screen behind the WRITE_SETTINGS special-access grant
 * (re-checked every resume, so returning from the settings screen flows straight in).
 */
@Composable
fun RotatorlatorApp(viewModel: RotatorlatorViewModel, onExit: () -> Unit) {

    val context = LocalContext.current
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }

    LifecycleResumeEffect(Unit) {
        canWriteSettings = Settings.System.canWrite(context)
        onPauseOrDispose { }
    }

    if (canWriteSettings) {
        MainScreen(viewModel, onExit)

    } else {
        PermissionGateScreen {
            // Open the "Modify System Settings" special-access screen for this app
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        }
    }
}

/**
 * Full-screen explainer shown until the WRITE_SETTINGS grant exists.
 */
@Composable
private fun PermissionGateScreen(onOpenPermissionSettings: () -> Unit) {

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_screen_rotation_black),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Spacer(Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.permission_explain_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.size(16.dp))

            Text(
                text = stringResource(R.string.permission_explain_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.size(24.dp))

            Button(onClick = onOpenPermissionSettings) {
                Text(stringResource(R.string.btn_permissions_menu))
            }
        }
    }
}

// endregion

// region ================== MAIN SCREEN ==================
// ====== ================== =========== ==================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: RotatorlatorViewModel, onExit: () -> Unit) {

    val context = LocalContext.current

    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val powerStatus by viewModel.powerStatus.collectAsStateWithLifecycle(initialValue = null)
    val rotationLock by viewModel.rotationLock.collectAsStateWithLifecycle(initialValue = null)

    // POST_NOTIFICATIONS request (API 33+). The Service runs and switches rotation either
    // way; the grant only controls whether its status notification is visible.
    val notificationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mnu_show_wireless_options)) },
                            trailingIcon = {
                                Checkbox(
                                    checked = prefs?.showWirelessPanel ?: true,
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                viewModel.toggleWirelessPanel()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mnu_open_source)) },
                            onClick = {
                                menuExpanded = false
                                openLink(context, R.string.url_open_source)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mnu_play_store)) },
                            onClick = {
                                menuExpanded = false
                                openLink(context, R.string.url_play_store)
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // "Exit" the app (same job as the 2018 minimise FAB)
            FloatingActionButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        }
    ) { padding ->

        val p = prefs ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Master toggle
            MonitorCard(
                monitorEnabled = p.monitorEnabled,
                onToggle = { enabled ->
                    if (enabled
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    // Persists the pref and starts/stops the monitoring Service
                    viewModel.setMonitorEnabled(enabled)
                }
            )

            // Live status readouts, only while monitoring (absorbs the old Test Panel's
            // useful diagnostics: current power state + current rotation-lock setting)
            AnimatedVisibility(visible = p.monitorEnabled) {
                StatusCard(powerStatus, rotationLock)
            }

            // Per-power-state config panels
            ConfigCard(PowerStatus.DISCONNECTED, p.modeUnplugged, viewModel::cycleRotationMode)
            ConfigCard(PowerStatus.PLUGGED_IN, p.modePlugged, viewModel::cycleRotationMode)

            AnimatedVisibility(visible = p.showWirelessPanel) {
                ConfigCard(PowerStatus.WIRELESSLY_CHARGING, p.modeWireless, viewModel::cycleRotationMode)
            }
        }
    }
}

private fun openLink(context: android.content.Context, @StringRes urlRes: Int) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(urlRes)))
    )
}

// endregion

// region ================== CARDS ==================
// ====== ================== ===== ==================

@Composable
private fun MonitorCard(monitorEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    // The whole card is the tap target; the Switch mirrors the same action
    Card(
        onClick = { onToggle(!monitorEnabled) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lbl_monitoring_toggle),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = monitorEnabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun StatusCard(powerStatus: PowerStatus?, rotationLock: RotationLockUi?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusRow(
                label = stringResource(R.string.lbl_current_port_status),
                value = powerStatus?.let { it.statusLabelRes to it.iconRes }
            )
            StatusRow(
                label = stringResource(R.string.lbl_current_orientation_status),
                value = rotationLock?.let { rotationLockDisplay(it) }
            )
        }
    }
}

/**
 * One "label: value + icon" row; value changes animate (the TextSwitcher's successor).
 */
@Composable
private fun StatusRow(label: String, value: Pair<Int, Int>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        AnimatedContent(targetState = value, label = "statusValue") { target ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (target != null) stringResource(target.first) else stringResource(R.string.lbl_status_blank),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (target != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(target.second),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    powerStatus: PowerStatus,
    mode: RotationMode,
    onCycleMode: (PowerStatus) -> Unit
) {
    // Tapping anywhere on the card cycles the mode (the AVD button is the visual
    // affordance, but the whole row is the tap target)
    Card(
        onClick = { onCycleMode(powerStatus) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(powerStatus.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(powerStatus.settingLabelRes),
                    style = MaterialTheme.typography.titleMedium
                )
                AnimatedContent(targetState = mode, label = "modeLabel") { m ->
                    Text(
                        text = stringResource(m.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AvdCycleButton(mode = mode, onClick = { onCycleMode(powerStatus) })
        }
    }
}

/**
 * The rotation-mode cycle button, still animated by the original 2018 AVDs —
 * "the whole point of the app".
 *
 * Deliberately rendered via a classic ImageView (AndroidView): compose
 * animation-graphics' AnimatedImageVector parser supports only a subset of AVD
 * features and mangles these Shape Shifter clips (stuck on the start frame →
 * out-of-sync icons, clipped mid-animation states). The platform
 * AnimatedVectorDrawable renderer plays them exactly as the 2018 View app did.
 */
@Composable
private fun AvdCycleButton(mode: RotationMode, onClick: () -> Unit) {

    val tint = MaterialTheme.colorScheme.primary

    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        AndroidView(
            factory = { context -> ImageView(context) },
            update = { imageView ->
                imageView.setColorFilter(tint.toArgb())
                imageView.contentDescription = imageView.context.getString(mode.labelRes)

                // update() runs on every recomposition — only swap the drawable and
                // replay the transition when the mode actually changed
                if (imageView.tag != mode) {
                    imageView.tag = mode
                    imageView.setImageResource(mode.avdRes)
                    (imageView.drawable as? Animatable)?.start()
                }
            },
            modifier = Modifier.size(40.dp)
        )
    }
}

// endregion

// region ================== DISPLAY MAPPINGS ==================
// ====== ================== ================ ==================

private val PowerStatus.iconRes: Int
    @DrawableRes get() = when (this) {
        PowerStatus.DISCONNECTED -> R.drawable.ic_dock_black
        PowerStatus.PLUGGED_IN -> R.drawable.ic_power_black
        PowerStatus.WIRELESSLY_CHARGING -> R.drawable.ic_tap_and_play_black
    }

private val PowerStatus.statusLabelRes: Int
    @StringRes get() = when (this) {
        PowerStatus.DISCONNECTED -> R.string.lbl_status_unplugged
        PowerStatus.PLUGGED_IN -> R.string.lbl_status_plugged
        PowerStatus.WIRELESSLY_CHARGING -> R.string.lbl_status_wireless
    }

private val PowerStatus.settingLabelRes: Int
    @StringRes get() = when (this) {
        PowerStatus.DISCONNECTED -> R.string.lbl_setting_unplugged
        PowerStatus.PLUGGED_IN -> R.string.lbl_setting_plugged
        PowerStatus.WIRELESSLY_CHARGING -> R.string.lbl_setting_wireless
    }

private val RotationMode.labelRes: Int
    @StringRes get() = when (this) {
        RotationMode.NO_CHANGE -> R.string.lbl_status_no_change
        RotationMode.PORTRAIT -> R.string.lbl_status_portrait
        RotationMode.PORTRAIT_INVERTED -> R.string.lbl_status_portrait_inverted
        RotationMode.LANDSCAPE -> R.string.lbl_status_landscape
        RotationMode.LANDSCAPE_INVERTED -> R.string.lbl_status_landscape_inverted
        RotationMode.AUTO_ROTATE -> R.string.lbl_status_auto_rotate
    }

/** The transition AVD shown when arriving at this mode (same mapping as the 2018 adapter) */
private val RotationMode.avdRes: Int
    @DrawableRes get() = when (this) {
        RotationMode.NO_CHANGE -> R.drawable.avd_rotate_to_no_change
        RotationMode.PORTRAIT -> R.drawable.avd_no_change_to_portrait
        RotationMode.PORTRAIT_INVERTED -> R.drawable.avd_portrait_to_portrait_inverted
        RotationMode.LANDSCAPE -> R.drawable.avd_portrait_to_landscape
        RotationMode.LANDSCAPE_INVERTED -> R.drawable.avd_landscape_to_landscape_inverted
        RotationMode.AUTO_ROTATE -> R.drawable.avd_landscape_to_rotate
    }

/**
 * Map the current rotation-lock system settings to a (label, icon) pair, relative to the
 * device's natural orientation — same matrix as the 2018 original.
 */
private fun rotationLockDisplay(info: RotationLockUi): Pair<Int, Int>? {

    if (info.isAutoRotate) {
        return R.string.lbl_status_auto_rotate to R.drawable.ic_screen_rotation_black
    }

    // Only resolvable if we have a baseline orientation to check against
    if (info.naturalOrientation == Configuration.ORIENTATION_UNDEFINED) return null

    val naturallyPortrait = info.naturalOrientation == Configuration.ORIENTATION_PORTRAIT

    return when (info.userRotation) {
        Surface.ROTATION_0 ->
            if (naturallyPortrait) R.string.lbl_status_portrait to R.drawable.ic_stay_primary_portrait_black
            else R.string.lbl_status_landscape to R.drawable.ic_stay_primary_landscape_black

        Surface.ROTATION_90 ->
            if (naturallyPortrait) R.string.lbl_status_landscape to R.drawable.ic_stay_primary_landscape_black
            else R.string.lbl_status_portrait to R.drawable.ic_stay_primary_portrait_black

        Surface.ROTATION_180 ->
            if (naturallyPortrait) R.string.lbl_status_portrait_inverted to R.drawable.ic_stay_primary_portrait_black
            else R.string.lbl_status_landscape_inverted to R.drawable.ic_stay_primary_landscape_black

        Surface.ROTATION_270 ->
            if (naturallyPortrait) R.string.lbl_status_landscape_inverted to R.drawable.ic_stay_primary_landscape_black
            else R.string.lbl_status_portrait_inverted to R.drawable.ic_stay_primary_portrait_black

        else -> null
    }
}

// endregion
