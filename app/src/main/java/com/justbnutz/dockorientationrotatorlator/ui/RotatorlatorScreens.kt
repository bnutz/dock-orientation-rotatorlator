/*
 * .ui — everything the user sees: the single Activity, the theme, the ViewModel that
 * holds screen state, and the composables below. Depends on .data and .model; nothing
 * depends on it (the Service reaches the repositories directly, not through the UI).
 *
 * The entire UI of the app. Replaces the 2018 View stack (ActivitySplashScreen +
 * ActivityRotatorlator + FragmentRotatorlatorConfigurator + AdapterRotatorlatorConfigs
 * + activity_rotatorlator.xml / fragment_starter_motor.xml / itemrow_*.xml + menu XML).
 *
 *
 * ==========================================================================
 *  READING THIS FILE IF YOU KNOW XML LAYOUTS + VIEWBINDING (i.e. me, 2018)
 * ==========================================================================
 *
 * The mental shift: there is no layout file, no findViewById, no binding object, and
 * no "update the view" code. A @Composable function IS the layout — you call it, and
 * whatever it emits is what's on screen. When the data changes, Compose calls the
 * function again and re-emits. That re-run is called RECOMPOSITION, and it replaces
 * every "myTextView.setText(...)" line the old app had.
 *
 * Rough translation table:
 *
 *   XML / ViewBinding                     Compose (this file)
 *   ------------------------------------  --------------------------------------------
 *   <LinearLayout orientation=vertical>    Column { }
 *   <LinearLayout orientation=horizontal>  Row { }
 *   <FrameLayout>                          Box { }
 *   <TextView android:text="x"/>           Text("x")
 *   <ImageView/>                           Icon(...) / Image(...)
 *   <Switch/>                              Switch(checked =, onCheckedChange =)
 *   <androidx.cardview.widget.CardView>    Card { }
 *   RecyclerView + Adapter + ViewHolder    just... call the item composable in a loop
 *   menu.xml + onOptionsItemSelected       DropdownMenu { DropdownMenuItem(onClick=) }
 *   android:layout_margin="16dp"           Modifier.padding(16.dp)
 *   android:layout_width="match_parent"    Modifier.fillMaxWidth()
 *   android:layout_weight="1"              Modifier.weight(1f)
 *   binding.foo.setOnClickListener { }     onClick = { }   (passed in as a parameter)
 *   binding.foo.text = state               Text(state)     (just read the value)
 *   View.VISIBLE / View.GONE               if (condition) { ... }  — you don't emit it
 *
 * The three ideas that do the heavy lifting here:
 *
 * 1. MODIFIERS replace layout XML attributes. Every composable takes a `modifier`, and
 *    you chain calls onto it: Modifier.fillMaxWidth().padding(16.dp). ORDER MATTERS —
 *    it's applied outside-in, so .padding().background() paints the background inside
 *    the padding, while .background().padding() paints it outside. (Unlike XML, where
 *    the attribute order in the file is irrelevant.)
 *
 * 2. STATE drives everything. `val prefs by viewModel.prefs.collectAsStateWithLifecycle()`
 *    subscribes this composable to a Flow. When the Flow emits, Compose re-runs the
 *    function and the new value is simply read again. There is no observer callback
 *    that manually pokes a View — the UI is a pure function of the state.
 *
 * 3. HOISTING: none of the small composables below own any state. They take the value
 *    to display (e.g. `mode: RotationMode`) and a lambda to call when the user acts
 *    (e.g. `onCycleMode: (PowerStatus) -> Unit`). The state lives up in the ViewModel;
 *    events travel up, data travels down. That's why ConfigCard can be re-used three
 *    times with zero per-instance bookkeeping — the ViewHolder/payload/notifyItemChanged
 *    machinery of the old RecyclerView adapter simply has no equivalent here.
 *
 * Screen structure emitted by this file:
 *
 *   RotatorlatorApp                 ← root; picks ONE of the two screens below
 *    ├── PermissionGateScreen       ← if we lack the WRITE_SETTINGS grant
 *    └── MainScreen                 ← the real screen
 *         └── Scaffold              ← Material 3 page frame (top bar + FAB + content)
 *              ├── TopAppBar        ← title + overflow DropdownMenu
 *              ├── FloatingActionButton  ← leave the app (dash if monitoring, else cross)
 *              └── Column           ← the scrolling content, in order:
 *                   ├── StatusCard      (what the device is doing RIGHT NOW)
 *                   ├── MonitorCard     (the master on/off switch)
 *                   └── ConfigCard × 3  (what to do when unplugged / plugged / wireless)
 *
 * The 7 avd_* AnimatedVectorDrawables carry over unchanged from 2018 — see
 * AvdCycleButton at the bottom for why they're the one place we drop back to a View.
 */

package com.justbnutz.dockorientationrotatorlator.ui

import com.justbnutz.dockorientationrotatorlator.R
import com.justbnutz.dockorientationrotatorlator.model.PowerStatus
import com.justbnutz.dockorientationrotatorlator.model.RotationMode

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.CardDefaults
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
 * The root composable — MainActivity.setContent { } calls this and nothing else.
 *
 * In the 2018 app this decision ("do we have permission? if not, nag; if so, go to the
 * real screen") needed a whole extra Activity (ActivitySplashScreen) plus an Intent to
 * launch the next one. In Compose a "screen" is just a function call, so choosing
 * between two screens is a plain Kotlin `if`. No Activity, no Intent, no back stack.
 *
 * WRITE_SETTINGS is a "special access" grant: it can't be requested with a normal
 * permission dialog, the user has to flip it in a system Settings page. So we can't
 * await a result — we simply RE-CHECK every time the app resumes:
 *
 *   `remember { mutableStateOf(...) }` = a value that survives recomposition AND, when
 *   written to, triggers one. It's the Compose equivalent of a field on the Fragment
 *   plus a manual "now refresh the views" call — except the refresh is automatic.
 *
 *   LifecycleResumeEffect = "run this block on every ON_RESUME" (≈ Fragment.onResume()).
 *   Writing to canWriteSettings there makes this function re-run, which swaps the screen.
 */
@Composable
fun RotatorlatorApp(viewModel: RotatorlatorViewModel, onExit: () -> Unit) {

    val context = LocalContext.current
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }

    LifecycleResumeEffect(Unit) {
        canWriteSettings = Settings.System.canWrite(context)
        onPauseOrDispose { }
    }

    // Emit one screen or the other. Whichever we DON'T call simply doesn't exist —
    // there's no View to hide with View.GONE.
    if (canWriteSettings) {
        MainScreen(viewModel, onExit)

    } else {
        PermissionGateScreen {
            // Send the user to the "Modify System Settings" page for this app. When they
            // come back, LifecycleResumeEffect above re-checks and we flip to MainScreen.
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
 *
 * Anatomy of a basic Compose screen, top to bottom:
 *
 *  - Scaffold: the Material 3 page frame. It hands its content lambda a `padding` value
 *    describing the space taken by the system bars / app bar / FAB. Applying that padding
 *    is how you avoid drawing under the status bar (this app is edge-to-edge, so the
 *    content window really does extend behind the bars — see MainActivity).
 *
 *  - Column: children stacked vertically, in the order written. `horizontalAlignment`
 *    centres them across the width; `verticalArrangement = Center` centres the whole
 *    stack in the leftover vertical space (≈ android:gravity="center" on a LinearLayout).
 *
 *  - Spacer: an empty gap. In XML you'd reach for android:layout_marginTop; in Compose
 *    an explicit Spacer between siblings is often clearer than padding on each child.
 */
@Composable
private fun PermissionGateScreen(onOpenPermissionSettings: () -> Unit) {

    Scaffold { padding ->
        Column(
            // Modifier chain = the layout XML attributes for this Column, applied in order:
            // fill the screen, inset by the Scaffold's system-bar padding, then 32dp of
            // breathing room inside that.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_screen_rotation_black),
                // contentDescription = null marks it decorative, so screen readers skip it
                contentDescription = null,
                // `tint` recolours the whole vector — which is why the app only ships ONE
                // copy of each icon now, instead of the black/grey_400 pairs the 2018 app
                // needed (the View code couldn't retint as cheaply).
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Spacer(Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.permission_explain_title),
                // Typography comes from the theme, not per-TextView textSize/textStyle
                // attributes — MaterialTheme is the successor to styles.xml.
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

            // The click handler was passed IN (hoisting) — this composable knows nothing
            // about Intents or Settings screens, it just reports "the button was pressed".
            Button(onClick = onOpenPermissionSettings) {
                Text(stringResource(R.string.btn_permissions_menu))
            }
        }
    }
}

// endregion

// region ================== MAIN SCREEN ==================
// ====== ================== =========== ==================

/**
 * The real screen. Everything the user sees while the app is open.
 *
 * How data gets in — three Flows from the ViewModel, each subscribed with
 * `collectAsStateWithLifecycle()`. That helper does two jobs:
 *   1. collects the Flow only while the screen is STARTED (so we're not registering
 *      broadcast receivers in the background — it replaces the old onStart/onStop
 *      register/unregister dance), and
 *   2. exposes the latest emission as Compose State, so a new value re-runs this
 *      function automatically.
 *
 * `by` (the delegate) just unwraps State<T> into T, so we can write `prefs` instead of
 * `prefs.value`. Purely cosmetic.
 *
 * Note what ISN'T here: no view binding, no adapter, no notifyItemChanged, no
 * "updateCurrentStatusLabels()" method that hand-writes new values into TextViews. The
 * 2018 fragment needed ~200 lines of that; the layout below simply reads the values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: RotatorlatorViewModel, onExit: () -> Unit) {

    val context = LocalContext.current

    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val powerStatus by viewModel.powerStatus.collectAsStateWithLifecycle(initialValue = null)
    val rotationLock by viewModel.rotationLock.collectAsStateWithLifecycle(initialValue = null)

    // The POST_NOTIFICATIONS runtime request (API 33+), Compose-style: this replaces
    // registerForActivityResult() in a Fragment. The Service runs and switches rotation
    // either way — the grant only decides whether its status notification is visible —
    // so there's nothing to handle in the result callback.
    val notificationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Local UI state: is the overflow menu open? It belongs to the menu alone and nothing
    // else cares, so it lives here rather than in the ViewModel. `remember` keeps it
    // across recompositions (without it, every re-run would reset it to false).
    var menuExpanded by remember { mutableStateOf(false) }

    // Scaffold = the page frame. Its named slots (topBar, floatingActionButton, content)
    // are where the old activity_rotatorlator.xml's Toolbar + FAB + FrameLayout went.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // The overflow "⋮" button and its menu. In 2018 this was menu_options.xml
                    // + onCreateOptionsMenu() + onOptionsItemSelected() with a big when-block.
                    // Here the item and its click handler are declared in the same place.
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
                                // onCheckedChange = null → the Checkbox is display-only; the
                                // click is handled by the menu row it sits in (below), so the
                                // whole row is the tap target rather than just the box.
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
            // Leaves the app. The icon reflects what that MEANS right now, which is the
            // 2018 behaviour restored:
            //   monitoring ON  → a dash. Something of ours is still running in the
            //                    background, so you're minimising, not quitting.
            //   monitoring OFF → a cross. Nothing is left running; you're closing it.
            // (Neither literally minimises or kills the process — this is about matching
            // the user's mental model, not the Activity lifecycle.)
            val monitoring = prefs?.monitorEnabled == true

            FloatingActionButton(onClick = onExit) {
                if (monitoring) {
                    Icon(
                        painter = painterResource(R.drawable.ic_remove_black),
                        contentDescription = stringResource(R.string.btn_minimise)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close)
                    )
                }
            }
        }
    ) { padding ->

        // prefs starts as null for the first frame (DataStore is read asynchronously).
        // Emitting nothing is a perfectly good "loading" state for a screen this small.
        val p = prefs ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)            // clear the app bar + system bars
                .verticalScroll(rememberScrollState())  // ≈ wrapping in a <ScrollView>
                .padding(horizontal = 16.dp, vertical = 8.dp),
            // spacedBy = a uniform gap between children; no per-child layout_margin needed
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 1. What the device is doing RIGHT NOW (power state + rotation lock).
            //    Always on screen: these describe the device, not our monitoring, so
            //    they stay true and useful even with the monitor switched off.
            StatusCard(powerStatus, rotationLock)

            // 2. The master on/off switch.
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

                    // Persists the pref and starts/stops the monitoring Service. Note we
                    // do NOT set any local "checked" state — the Switch is drawn from
                    // p.monitorEnabled, which comes back through the DataStore Flow. The
                    // UI can't drift out of sync with what's actually stored.
                    viewModel.setMonitorEnabled(enabled)
                }
            )

            // 3. One config card per power state. In 2018 these three rows were a
            //    RecyclerView + Adapter + ViewHolder + payload-diffing (~330 lines) purely
            //    so panels could animate in and out. In Compose: call the composable three
            //    times. The list is fixed and tiny, so LazyColumn would be overkill —
            //    a plain Column inside a verticalScroll is the right tool.
            ConfigCard(PowerStatus.DISCONNECTED, p.modeUnplugged, viewModel::cycleRotationMode)
            ConfigCard(PowerStatus.PLUGGED_IN, p.modePlugged, viewModel::cycleRotationMode)

            // AnimatedVisibility = the successor to notifyItemInserted/Removed: wrap the
            // thing, flip the boolean, and it animates in or out on its own.
            AnimatedVisibility(visible = p.showWirelessPanel) {
                ConfigCard(PowerStatus.WIRELESSLY_CHARGING, p.modeWireless, viewModel::cycleRotationMode)
            }
        }
    }
}

/** Open a URL from a string resource (the Source Code / Play Store menu items). */
private fun openLink(context: Context, @StringRes urlRes: Int) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(urlRes)))
    )
}

// endregion

// region ================== CARDS ==================
// ====== ================== ===== ==================

/*
 * Each card below is a small, stateless composable: it receives the values to display
 * and a lambda to call when tapped. That's the whole contract. Because they hold no
 * state, they can be called anywhere, in any order, as many times as needed — and the
 * only way the screen can change is if the ViewModel's state changes.
 */

/**
 * Row 1 — the live device status: what the power/dock state is, and what the system
 * rotation-lock setting currently is.
 *
 * Given a distinct container colour (`colors = CardDefaults.cardColors(...)`) so it reads
 * as a readout rather than another control. Colour comes from the Material You scheme,
 * so it adapts to wallpaper and dark mode automatically — never a hardcoded hex.
 */
@Composable
private fun StatusCard(powerStatus: PowerStatus?, rotationLock: RotationLockUi?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // `?.let { }` → null (still loading) shows the "---" placeholder inside StatusRow
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
 * One "label ......... value + icon" line inside the status card.
 *
 * `value` is a (stringRes, drawableRes) pair, or null while we're still loading.
 *
 * AnimatedContent is the successor to the 2018 TextSwitcher (+ textswitcher_in/out.xml):
 * when `targetState` changes, it cross-fades the old content out and the new one in.
 * The lambda receives the target value — always read `target` inside it, never the outer
 * `value`, or the outgoing copy would render the incoming text mid-animation.
 */
@Composable
private fun StatusRow(label: String, value: Pair<Int, Int>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            // weight(1f) = "take all the leftover width", pushing the value to the right
            // edge (≈ layout_weight="1" on the label in a horizontal LinearLayout)
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
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Row 2 — the master monitoring toggle.
 *
 * Card(onClick = ...) makes the WHOLE card the tap target, with a proper ripple, rather
 * than only the little Switch. The Switch keeps its own onCheckedChange so dragging it
 * still works; Compose gives the inner control priority when you hit it directly, so
 * there's no double-fire.
 */
@Composable
private fun MonitorCard(monitorEnabled: Boolean, onToggle: (Boolean) -> Unit) {
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

/**
 * Rows 3-5 — one per power state: "when [unplugged / plugged in / wirelessly charging],
 * do [rotation mode]". Tapping anywhere on the card cycles to the next mode.
 *
 * Called three times with different arguments; that's the entire "adapter" now. The
 * `powerStatus` enum carries its own icon and label through the extension properties at
 * the bottom of this file, so no per-card branching is needed here.
 */
@Composable
private fun ConfigCard(
    powerStatus: PowerStatus,
    mode: RotationMode,
    onCycleMode: (PowerStatus) -> Unit
) {
    Card(
        onClick = { onCycleMode(powerStatus) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: which power state this card configures
            Icon(
                painter = painterResource(powerStatus.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(16.dp))

            // Middle: the state's name, and under it the currently chosen mode.
            // weight(1f) makes this column absorb the leftover width, so the AVD button
            // is pinned to the right edge.
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

            // Right: the animated icon button (the whole reason this app exists)
            AvdCycleButton(mode = mode, onClick = { onCycleMode(powerStatus) })
        }
    }
}

/**
 * The rotation-mode button, animated by the original 2018 AnimatedVectorDrawables.
 *
 * THE ONE PLACE THIS APP DROPS BACK TO A VIEW — and worth understanding, because it's
 * the escape hatch you'll want whenever Compose can't do something the View system can:
 *
 *   AndroidView(factory = { ... }, update = { ... }) embeds any classic View inside the
 *   Compose tree. `factory` runs ONCE to create it (≈ inflating a layout); `update` runs
 *   on every recomposition to push new values into it (≈ the code you'd write after
 *   findViewById). It is the bridge between the two worlds, in both directions.
 *
 * Why we need it here: Compose's own AnimatedImageVector (animation-graphics) only
 * supports a SUBSET of the AVD format, and it mangled these particular Shape Shifter
 * clips — animations stuck on their start frame (so the icon showed the previous mode)
 * and clipped mid-transition. The platform's AnimatedVectorDrawable renderer, driven
 * through a plain ImageView, plays them exactly as the 2018 View app did.
 *
 * The `tag` check matters: `update` fires on EVERY recomposition (theme change, a
 * neighbouring card's state changing, anything). Without the guard we'd restart the
 * animation constantly. Stashing the current mode in the View's tag makes the swap
 * happen only when the mode genuinely changed.
 */
@Composable
private fun AvdCycleButton(mode: RotationMode, onClick: () -> Unit) {

    // Read the theme colour HERE, in the composable — inside `update` below we're in
    // plain View-land, where MaterialTheme isn't available.
    val tint = MaterialTheme.colorScheme.primary

    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        AndroidView(
            factory = { context -> ImageView(context) },
            update = { imageView ->
                imageView.setColorFilter(tint.toArgb())
                imageView.contentDescription = imageView.context.getString(mode.labelRes)

                if (imageView.tag != mode) {
                    imageView.tag = mode
                    imageView.setImageResource(mode.avdRes)

                    // Kick off the transition animation (the AVD's own start→end clip)
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

/*
 * Enum → resource lookups, as Kotlin extension properties.
 *
 * The domain enums (PowerStatus, RotationMode) deliberately know nothing about R.string
 * or R.drawable — they're pure data, shared with the Service and the repositories. These
 * extensions live in the UI layer and bolt the presentation on from the outside, which is
 * why they're `private` to this file.
 */

private val PowerStatus.iconRes: Int
    @DrawableRes get() = when (this) {
        PowerStatus.DISCONNECTED -> R.drawable.ic_dock_black
        PowerStatus.PLUGGED_IN -> R.drawable.ic_power_black
        PowerStatus.WIRELESSLY_CHARGING -> R.drawable.ic_tap_and_play_black
    }

/** "Unplugged" / "Plugged-in" / "Wirelessly charging" — the live status readout */
private val PowerStatus.statusLabelRes: Int
    @StringRes get() = when (this) {
        PowerStatus.DISCONNECTED -> R.string.lbl_status_unplugged
        PowerStatus.PLUGGED_IN -> R.string.lbl_status_plugged
        PowerStatus.WIRELESSLY_CHARGING -> R.string.lbl_status_wireless
    }

/** "When Unplugged:" / "When Plugged-in:" / "Wirelessly Charging:" — the config card titles */
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

/**
 * The AVD played when ARRIVING at this mode. Each file is a "from → to" clip (the names
 * are the 2018 originals), so the animation shown is chosen by the destination mode —
 * same mapping the old adapter used.
 */
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
 * Turn the raw system rotation-lock settings into a (label, icon) pair for the status card.
 *
 * The awkward bit (unchanged since 2018): USER_ROTATION is relative to the device's
 * NATURAL orientation. ROTATION_0 means portrait on a phone but landscape on a
 * naturally-landscape tablet — so every value has to be interpreted against the natural
 * orientation before it can be labelled. Returns null when that baseline is unknown.
 */
private fun rotationLockDisplay(info: RotationLockUi): Pair<Int, Int>? {

    if (info.isAutoRotate) {
        return R.string.lbl_status_auto_rotate to R.drawable.ic_screen_rotation_black
    }

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
