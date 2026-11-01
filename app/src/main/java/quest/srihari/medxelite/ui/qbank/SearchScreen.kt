package quest.srihari.medxelite.ui.qbank

import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import quest.srihari.medxelite.Medx
import quest.srihari.medxelite.data.Catalog
import quest.srihari.medxelite.data.Load
import quest.srihari.medxelite.data.ModuleSummary
import quest.srihari.medxelite.data.RunnerPayload
import quest.srihari.medxelite.ui.Navigator
import quest.srihari.medxelite.ui.components.EmptyState
import quest.srihari.medxelite.ui.components.GroupedSection
import quest.srihari.medxelite.ui.components.MedxRow
import quest.srihari.medxelite.ui.components.MedxTopBar
import quest.srihari.medxelite.ui.theme.Hues
import quest.srihari.medxelite.ui.theme.Mx

/**
 * Search across both banks' subjects, chapters and modules, plus the text of bookmarked questions.
 * (The iOS full-text index over all ~30k question bodies is not ported yet.)
 */
@Composable
fun SearchScreen(nav: Navigator) {
    val banks by Catalog.banks.collectAsState()
    val bookmarks by Medx.activity.bookmarks.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var pick by remember { mutableStateOf<Pair<ModuleSummary, String>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { Catalog.loadBanks(); runCatching { focus.requestFocus() } }

    Scaffold(containerColor = Mx.colors.groupedBackground, topBar = { MedxTopBar("Search", onBack = nav::back) }) { pad ->
        val q = query.trim()
        val subjects = (banks as? Load.Ready)?.value.orEmpty()
        data class Hit(val module: ModuleSummary, val subject: String, val chapter: String, val bank: String)
        val hits = if (q.length < 2) emptyList() else subjects.flatMap { s ->
            s.chapters.flatMap { c ->
                c.modules.filter { m ->
                    m.name.contains(q, true) || c.name.contains(q, true) || s.name.contains(q, true)
                }.map { Hit(it, s.name, c.name, s.bank.label) }
            }
        }.take(80)
        val bookmarkHits = if (q.length < 2) emptyList() else bookmarks.filter {
            it.previewText.contains(q, true) || it.question.options.any { o -> o.text.contains(q, true) }
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Subjects, chapters, modules…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                )
            }
            if (q.length >= 2 && hits.isEmpty() && bookmarkHits.isEmpty()) {
                item { EmptyState("No results", "Nothing matches “$q”.", icon = Icons.Rounded.Search) }
            }
            if (hits.isNotEmpty()) {
                item {
                    GroupedSection("Modules (${hits.size})", icon = Icons.Rounded.ViewModule) {
                        hits.forEachIndexed { i, h ->
                            MedxRow(h.module.name, "${h.bank} · ${h.subject} · ${h.chapter} · ${h.module.questionCount} q", divider = i < hits.size - 1) {
                                pick = h.module to h.subject
                            }
                        }
                    }
                }
            }
            if (bookmarkHits.isNotEmpty()) {
                item {
                    GroupedSection("Bookmarked questions (${bookmarkHits.size})", icon = Icons.Rounded.Bookmark) {
                        bookmarkHits.forEachIndexed { i, b ->
                            MedxRow(b.previewText, b.sourceName, icon = Icons.Rounded.Bookmark, iconTint = Hues.orange, divider = i < bookmarkHits.size - 1) {
                                nav.go("bookmarks")
                            }
                        }
                    }
                }
            }
            if (q.length < 2) {
                item { Box(Modifier.padding(24.dp)) { Text("Type at least two letters.", color = Mx.colors.secondaryText) } }
            }
        }
    }
    pick?.let { (m, subject) ->
        StartSessionSheet(m.name, subject, m.questionCount, Medx.attempts.attempts.value.filter { it.sourceId == m.id }, onDismiss = { pick = null }) { mode ->
            pick = null
            nav.startSitting(RunnerPayload("qbank", m.id, m.name, subject, mode))
        }
    }
}
