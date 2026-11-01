package quest.srihari.medxelite.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import quest.srihari.medxelite.Medx

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/**
 * In-memory copies of the read-only catalogues, shared by every screen that draws them
 * (the QBank tabs, a subject page, the custom-module builder and search all want the same tree).
 */
object Catalog {
    val banks = MutableStateFlow<Load<List<BankSubject>>>(Load.Loading)
    val series = MutableStateFlow<Load<List<SeriesPaper>>>(Load.Loading)
    val tests = MutableStateFlow<Load<List<BatchTest>>>(Load.Loading)
    val videos = MutableStateFlow<Load<List<RecordedVideo>>>(Load.Loading)
    val flashcards = MutableStateFlow<Load<List<FlashcardSubject>>>(Load.Loading)

    private val mutex = Mutex()

    private suspend fun <T> load(
        flow: MutableStateFlow<Load<T>>,
        force: Boolean,
        failure: String,
        block: suspend () -> T,
    ) {
        mutex.withLock {
            if (!force && flow.value is Load.Ready) return
        }
        // First paint from any cached copy, then the real read.
        if (flow.value !is Load.Ready) {
            runCatching { withContext(Firestore.CacheOnly) { block() } }
                .onSuccess { if (it !is List<*> || it.isNotEmpty()) flow.value = Load.Ready(it) }
        }
        try {
            flow.value = Load.Ready(block())
        } catch (e: Exception) {
            if (flow.value !is Load.Ready) flow.value = Load.Failed(failure)
        }
    }

    suspend fun loadBanks(force: Boolean = false) =
        load(banks, force, "The question bank could not be loaded. Check your connection and try again.") { Medx.fs.fetchBanks() }

    suspend fun loadSeries(force: Boolean = false) =
        load(series, force, "The Marrow series could not be read. Check your connection and try again.") { Medx.fs.fetchSeries() }

    suspend fun loadTests(force: Boolean = false) =
        load(tests, force, "Batch papers could not be loaded.") { Medx.fs.fetchTests() }

    suspend fun loadVideos(force: Boolean = false) =
        load(videos, force, "Classes could not be loaded.") { Medx.fs.fetchVideos() }

    suspend fun loadFlashcards(force: Boolean = false) =
        load(flashcards, force, "Flashcards could not be loaded.") { Medx.fs.fetchFlashcardSubjects() }

    fun subject(id: String): BankSubject? = (banks.value as? Load.Ready)?.value?.firstOrNull { it.id == id }
}
