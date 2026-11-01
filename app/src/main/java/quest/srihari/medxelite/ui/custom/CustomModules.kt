package quest.srihari.medxelite.ui.custom

import androidx.compose.foundation.layout.height
import quest.srihari.medxelite.ui.components.NPill
import quest.srihari.medxelite.ui.components.NSegmented
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.BankChapter
import quest.srihari.medxelite.data.BankSubject
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.CustomModule
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.MedxBank
import quest.srihari.medxelite.data.MedxDates
import quest.srihari.medxelite.data.ModuleSource
import quest.srihari.medxelite.data.Question
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.data.SittingMode
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.LoadingState
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.qbank.StartSessionSheet
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx

/** Gather every source module's questions, tag each with its module, shuffle and cap. */
suspend fun buildCustomPayload(m: CustomModule, mode: SittingMode): RunnerPayload = coroutineScope {
    val parts = m.sources.map { src ->
        async { runCatching { Medx.fs.fetchModule(src.moduleId).questions }.getOrDefault(emptyList()).map { it to src.name } }
    }.awaitAll().flatten()
    var tagged = parts
    // Question ids are only unique within a module; re-key them so answers never collide.
    tagged = tagged.mapIndexed { i, (q, tag) -> q.copy(id = if (tagged.count { it.first.id == q.id } > 1) 800_000_000 + i else q.id) to tag }
    if (m.shuffle) tagged = tagged.shuffled()
    m.limit?.takeIf { it > 0 }?.let { tagged = tagged.take(it) }
    RunnerPayload(
        kind = "custom", id = m.id, name = m.name.ifBlank { "Custom module" }, subject = "Custom module", mode = mode,
        questions = tagged.map { it.first }, questionTags = tagged.map { it.second },
    )
}

@Composable
fun CustomModulesScreen(nav: Navigator) {
    val modules by Medx.custom.modules.collectAsState()
    val loading by Medx.custom.loading.collectAsState()
    var start by remember { mutableStateOf<CustomModule?>(null) }
    var deleting by remember { mutableStateOf<CustomModule?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Medx.custom.refresh() }

    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = { MedxTopBar("Custom modules", onBack = nav::back) },
        floatingActionButton = {
            NPill("New module", { nav.go("custom/edit") }, icon = Icons.Outlined.Add, modifier = Modifier.height(56.dp))
        },
    ) { pad ->
        when {
            preparing -> Box(Modifier.padding(pad)) { LoadingState("Gathering questions") }
            modules.isEmpty() && loading -> Box(Modifier.padding(pad)) { LoadingState() }
            modules.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState("Build your own paper", "Pick modules from either bank, shuffle them, cap the count, and sit it like any other module.", icon = Icons.Outlined.Tune, actionTitle = "New custom module") { nav.go("custom/edit") }
            }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    GroupedSection(footer = "Saved to your account, so they follow you to every device.") {
                        modules.forEachIndexed { i, m ->
                            MedxRow(
                                title = m.name.ifBlank { "Untitled" },
                                subtitle = "${m.sources.size} modules · ${m.effectiveCount} questions" + (if (m.shuffle) " · shuffled" else "") + (if (m.note.isNotBlank()) " · ${m.note}" else ""),
                                icon = Icons.Outlined.PlayArrow, iconTint = Hues.indigo,
                                showChevron = false,
                                divider = i < modules.size - 1,
                                trailingContent = {
                                    IconButton(onClick = { nav.go("custom/edit?id=${m.id}") }) { Icon(Icons.Outlined.Edit, "Edit", tint = Mx.colors.secondaryText) }
                                    IconButton(onClick = { deleting = m }) { Icon(Icons.Outlined.Delete, "Delete", tint = Mx.colors.secondaryText) }
                                },
                            ) { start = m }
                        }
                    }
                }
            }
        }
    }
    start?.let { m ->
        StartSessionSheet(m.name, "${m.sources.size} modules", m.effectiveCount, Medx.attempts.attempts.value.filter { it.sourceId == m.id }, onDismiss = { start = null }) { mode ->
            start = null
            preparing = true
            scope.launch {
                val payload = buildCustomPayload(m, mode)
                preparing = false
                nav.startSitting(payload)
            }
        }
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${m.name}”?") },
            confirmButton = { TextButton(onClick = { scope.launch { runCatching { Medx.custom.delete(m) } }; deleting = null }) { Text("Delete", color = Mx.colors.wrong) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModuleEditor(nav: Navigator, id: String?) {
    val existing = remember(id) { Medx.custom.modules.value.firstOrNull { it.id == id } }
    val uid = Medx.auth.uid ?: ""
    val base = existing ?: CustomModule(CustomModule.newId(), uid, "", "", emptyList(), true, null, MedxDates.stamp(), MedxDates.stamp())
    var name by remember { mutableStateOf(base.name) }
    var note by remember { mutableStateOf(base.note) }
    var shuffle by remember { mutableStateOf(base.shuffle) }
    var limit by remember { mutableStateOf(base.limit?.toString() ?: "") }
    val sources = remember { mutableStateListOf<ModuleSource>().apply { addAll(base.sources) } }
    var bank by remember { mutableStateOf(MedxBank.ARISE) }
    var openSubject by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val banks by Catalog.banks.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Catalog.loadBanks() }

    fun toggle(src: ModuleSource) {
        val i = sources.indexOfFirst { it.moduleId == src.moduleId }
        if (i >= 0) sources.removeAt(i) else sources.add(src)
    }

    Scaffold(
        containerColor = Mx.colors.groupedBackground,
        topBar = {
            MedxTopBar(if (existing == null) "New custom module" else "Edit module", onBack = nav::back, actions = {
                TextButton(
                    enabled = !saving && sources.isNotEmpty() && name.isNotBlank(),
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                Medx.custom.save(
                                    base.copy(
                                        uid = uid, name = name.trim(), note = note.trim(), sources = sources.toList(), shuffle = shuffle,
                                        limit = limit.toIntOrNull()?.takeIf { it > 0 },
                                    ),
                                )
                                nav.back()
                            } catch (e: Exception) {
                                error = "Saved on this phone, but couldn't reach the server. It will be there next time you refresh."
                                saving = false
                            }
                        }
                    },
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                GroupedSection("Details", error) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Shuffle questions", Modifier.weight(1f))
                            Switch(
                                shuffle, { shuffle = it },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedTrackColor = Mx.colors.accent, checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedBorderColor = Mx.colors.accent,
                                ),
                            )
                        }
                        OutlinedTextField(
                            limit, { limit = it.filter(Char::isDigit) }, label = { Text("Question cap (blank = all)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        val total = sources.sumOf { it.questionCount }
                        val cap = limit.toIntOrNull()?.takeIf { it > 0 }
                        Text("${sources.size} modules · ${cap?.let { minOf(it, total) } ?: total} of $total questions", color = Mx.colors.secondaryText)
                    }
                }
            }
            item {
                NSegmented(MedxBank.entries, bank, { it.label }, { bank = it; openSubject = null }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when (val s = banks) {
                is Load.Loading -> item { LoadingState() }
                is Load.Failed -> item { EmptyState("Couldn't load modules", s.message) }
                is Load.Ready -> {
                    val subjects = s.value.filter { it.bank == bank }
                    subjects.forEach { subject ->
                        item(key = subject.id) {
                            SubjectPicker(subject, openSubject == subject.id, sources.map { it.moduleId }.toSet(), onOpen = {
                                openSubject = if (openSubject == subject.id) null else subject.id
                            }) { chapter, module -> toggle(ModuleSource(module.id, module.name, chapter.name, subject.name, subject.bank, module.questionCount)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPicker(
    subject: BankSubject,
    open: Boolean,
    selected: Set<String>,
    onOpen: () -> Unit,
    onToggle: (BankChapter, quest.srihari.medxelite.data.ModuleSummary) -> Unit,
) {
    val count = subject.modules.count { it.id in selected }
    GroupedSection {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(subject.name, fontWeight = FontWeight.SemiBold)
                Text("${subject.moduleCount} modules" + if (count > 0) " · $count picked" else "", style = MaterialTheme.typography.bodySmall, color = if (count > 0) Mx.colors.accent else Mx.colors.secondaryText)
            }
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (open) {
            subject.chapters.forEach { chapter ->
                Text(chapter.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = Mx.colors.secondaryText, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                chapter.modules.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { onToggle(chapter, m) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m.id in selected, { onToggle(chapter, m) })
                        Text(m.name, Modifier.weight(1f))
                        Text("${m.questionCount}", color = Mx.colors.secondaryText, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    }
}
