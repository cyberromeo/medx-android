package quest.srihari.medxelite.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.coroutineContext

/**
 * Regression test for the post-sign-in crash: Firestore.CacheOnly used to be built with itself
 * as its own key, which is null while the object initialises, so touching it threw
 * ExceptionInInitializerError on the first Firestore read. Plain JVM test, no Android needed.
 */
class CacheOnlyTest {
    @Test fun cacheOnlyElementInitialisesAndIsFoundByItsKey() = runBlocking {
        val key = Firestore.CacheOnlyKey
        assertEquals(null, coroutineContext[key])
        withContext(Firestore.CacheOnly) {
            assertEquals(Firestore.CacheOnly, coroutineContext[key])
        }
        assertEquals(null, coroutineContext[key])
    }
}
