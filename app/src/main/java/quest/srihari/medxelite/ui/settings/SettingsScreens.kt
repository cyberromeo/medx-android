package quest.srihari.medxelite.ui.settings

import quest.srihari.medxelite.ui.components.subjectIcon
import quest.srihari.medxelite.ui.components.neonSwitchColors
import quest.srihari.medxelite.ui.components.glass
import quest.srihari.medxelite.ui.components.softGlow
import quest.srihari.medxelite.ui.components.GradientText
import quest.srihari.medxelite.ui.components.titleBrush
import quest.srihari.medxelite.ui.theme.Aurora
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Info
import quest.srihari.medxelite.ui.components.GlassSegmented
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import quest.srihari.medxelite.BuildConfig
import quest.srihari.medxelite.Config
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.auth.FirebaseBridge
import quest.srihari.medxelite.auth.GoogleSignIn
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxAccent
import quest.srihari.medxelite.data.MedxAppearance
import quest.srihari.medxelite.data.bool
import quest.srihari.medxelite.data.obj
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.Avatar
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.components.openCustomTab
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx

@Composable
fun SettingsScreen(nav: Navigator) {
    val context = LocalContext.current
    val session by Medx.auth.session.collectAsState()
    val accent by Medx.prefs.accent.collectAsState()
    val appearance by Medx.prefs.appearance.collectAsState()
    val goal by Medx.prefs.dailyGoal.collectAsState()
    val haptics by Medx.prefs.hapticsOn.collectAsState()
    val syncing by Medx.attempts.syncing.collectAsState()
    val stats by Medx.attempts.stats.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pending = session?.uid?.let { Medx.attempts.pending(it).size } ?: 0

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Settings", onBack = nav::back) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), glow = true).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(session, 60.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            GradientText(session?.name ?: "", MaterialTheme.typography.titleLarge, brush = titleBrush(), maxLines = 1)
                            Text(session?.email ?: "", color = Mx.colors.secondaryText)
                            Text(
                                if (session?.provider == "google.com") "Signed in with Google" else "Signed in with email",
                                style = MaterialTheme.typography.labelSmall, color = Mx.colors.tertiaryText,
                            )
                        }
                    }
                }
            }
            item {
                GroupedSection("Accent colour", icon = Icons.Rounded.Palette) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MedxAccent.entries.forEach { a ->
                            val on = a == accent
                            val end = if (a == MedxAccent.TEAL || a == MedxAccent.GREEN) Aurora.violet else Aurora.cyan
                            Box(
                                Modifier.size(40.dp)
                                    .then(if (on) Modifier.softGlow(Color(a.hex), end, 0.55f) else Modifier)
                                    .clip(CircleShape).background(Brush.linearGradient(listOf(Color(a.hex), end)))
                                    .border(if (on) 2.5.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .clickable { Medx.prefs.setAccent(a) },
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(Icons.Rounded.Check, a.label, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
            item {
                GroupedSection("Appearance", icon = Icons.Rounded.Contrast) {
                    GlassSegmented(
                        MedxAppearance.entries.map { it.label }, MedxAppearance.entries.indexOf(appearance),
                        Modifier.padding(12.dp),
                    ) { i -> Medx.prefs.setAppearance(MedxAppearance.entries[i]) }
                }
            }
            item {
                GroupedSection("Study", icon = Icons.Rounded.School) {
                    MedxRow("Daily goal", icon = Icons.Rounded.TrackChanges, iconTint = Hues.orange, trailing = "$goal questions") { editGoal = true }
                    MedxRow("Syllabus checklist", icon = Icons.Rounded.Checklist, iconTint = Hues.green) { nav.go("tracker") }
                    MedxRow(
                        "Haptics", icon = Icons.Rounded.Vibration, iconTint = Hues.pink, showChevron = false, divider = false,
                        trailingContent = { Switch(haptics, { Medx.prefs.setHaptics(it) }, colors = neonSwitchColors()) },
                    )
                }
            }
            item {
                GroupedSection(icon = Icons.Rounded.CloudSync, header = "Sync", footer = "${stats.sittings} sittings on your account" + if (pending > 0) " · $pending waiting to upload" else "") {
                    MedxRow(if (syncing) "Syncing…" else "Sync now", icon = Icons.Rounded.Sync, iconTint = Hues.blue, showChevron = false) {
                        Medx.cache.markAllStale()
                        scope.launch { Medx.attempts.refresh() }
                        scope.launch { Medx.activity.sync() }
                        scope.launch { Medx.custom.refresh() }
                    }
                    MedxRow(
                        if (cleared) "Cache cleared" else "Clear downloaded content",
                        "${Medx.cache.sizeBytes() / 1024} KB of modules, papers and lists kept for offline",
                        icon = Icons.Rounded.CleaningServices, iconTint = Hues.gray, showChevron = false, divider = false,
                    ) {
                        Medx.cache.clear()
                        cleared = true
                    }
                }
            }
            item {
                GroupedSection("About", icon = Icons.Rounded.Info) {
                    MedxRow("MedX on the web", Config.WEB_APP_URL.removePrefix("https://"), icon = Icons.Rounded.Language, iconTint = Hues.teal) {
                        openCustomTab(context, Config.WEB_APP_URL)
                    }
                    MedxRow("Version", icon = Icons.Rounded.Code, iconTint = Hues.indigo, trailing = BuildConfig.VERSION_NAME, showChevron = false)
                    MedxRow(
                        "Backend", "Project ${Config.PROJECT_ID} · ${FirebaseBridge.status} · Google sign-in ${if (GoogleSignIn.isConfigured) "configured" else "not configured"}",
                        icon = Icons.Rounded.Cloud, iconTint = Hues.blue, showChevron = false, divider = false,
                    )
                }
            }
            item {
                GroupedSection {
                    MedxRow("Sign out", icon = Icons.AutoMirrored.Rounded.Logout, iconTint = Mx.colors.wrong, titleColor = Mx.colors.wrong, showChevron = false, divider = false) {
                        confirmSignOut = true
                    }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your sittings, bookmarks and history stay on your account and come back when you sign in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { runCatching { Medx.attempts.flush() } ; Medx.auth.signOut() }
                }) { Text("Sign out", color = Mx.colors.wrong) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    if (editGoal) {
        var text by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("Daily goal") },
            text = {
                Column {
                    Text("Questions to answer each day.", color = Mx.colors.secondaryText)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 50, 100, 150).forEach { v -> TextButton(onClick = { text = v.toString() }) { Text("$v") } }
                    }
                    OutlinedTextField(text, { text = it.filter(Char::isDigit).take(4) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.takeIf { it > 0 }?.let { Medx.prefs.setGoal(it) }
                    editGoal = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Cancel") } },
        )
    }
}

private val trackerFields = listOf("Videos" to "Videos", "R1" to "R1", "R2" to "R2", "PYQs" to "PYQs", "RevisionVideos" to "Rev", "Qbank" to "QBank")

/** Per-subject syllabus checklist in `user_tracker/{uid}` (SyllabusTrackerSheet.swift). */
@Composable
fun SyllabusTrackerScreen(nav: Navigator) {
    val uid = Medx.auth.uid ?: return
    var doc by remember { mutableStateOf<JsonObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val ticks = remember { mutableStateMapOf<String, Boolean>() }
    val banks by Catalog.banks.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        doc = Medx.fs.fetchTracker(uid)
        doc?.obj("subjects")?.forEach { (subject, fields) ->
            (fields as? JsonObject)?.let { f -> trackerFields.forEach { (key, _) -> f.bool(key)?.let { ticks["$subject|$key"] = it } } }
        }
        loaded = true
        Catalog.loadBanks()
    }
    val subjects: List<String> = doc?.obj("subjects")?.keys?.sorted()?.takeIf { it.isNotEmpty() }
        ?: (banks as? Load.Ready)?.value?.filter { it.bank == quest.srihari.medxelite.data.MedxBank.ARISE }?.map { it.name }.orEmpty()
    val done = ticks.count { it.value }
    val total = subjects.size * trackerFields.size

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Syllabus checklist", onBack = nav::back) }) { pad ->
        if (!loaded) { Box(Modifier.padding(pad)) { LoadingState() }; return@Scaffold }
        if (subjects.isEmpty()) { Box(Modifier.padding(pad)) { EmptyState("No subjects yet", "Your checklist appears once the subject list loads.") }; return@Scaffold }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text("$done of $total ticked · ${maxOf(total - done, 0)} left across ${subjects.size} subjects", color = Mx.colors.secondaryText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            subjects.forEach { subject ->
                item(key = subject) {
                    GroupedSection(subject, icon = subjectIcon(subject)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            trackerFields.forEach { (key, label) ->
                                val k = "$subject|$key"
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
                                    Checkbox(ticks[k] == true, { value ->
                                        ticks[k] = value
                                        scope.launch {
                                            runCatching { Medx.fs.updateTrackerCell(uid, subject, key, value) }.onFailure { ticks[k] = !value }
                                        }
                                    })
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
