package com.vitrack.app.data.weight

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitrack.app.data.weight.db.VitrackDatabase
import com.vitrack.app.data.weight.db.WeightDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the editor's save and delete transactions against a real in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class WeightDaoTest {

    private lateinit var db: VitrackDatabase
    private lateinit var dao: WeightDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            VitrackDatabase::class.java,
        ).build()
        dao = db.weightDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveTwiceOnOneDayUpdatesTheEntry() = runTest {
        dao.save(null, 100, 80.0, 1, emptyList(), emptyList(), listOf("a.jpg"))
        dao.save(null, 100, 79.5, 2, emptyList(), emptyList(), listOf("b.jpg"))

        val weighIns = dao.observeWeighIns().first()
        assertEquals(1, weighIns.size)
        assertEquals(79.5, weighIns.single().weightKg, 0.0)
        // Photos from both saves stay on the day.
        assertEquals(listOf("a.jpg", "b.jpg"), dao.photosForDay(100).map { it.fileName })
    }

    @Test
    fun movingAnEntryCarriesKeptPhotosAndDropsRemovedOnes() = runTest {
        dao.save(null, 100, 80.0, 1, emptyList(), emptyList(), listOf("keep.jpg", "drop.jpg"))
        val photos = dao.photosForDay(100)
        val keep = photos.first { it.fileName == "keep.jpg" }.id
        val drop = photos.first { it.fileName == "drop.jpg" }.id

        val removed = dao.save(100, 101, 79.0, 2, listOf(keep), listOf(drop), emptyList())

        assertEquals(listOf("drop.jpg"), removed)
        assertEquals(listOf(101L), dao.observeWeighIns().first().map { it.epochDay })
        assertEquals(listOf("keep.jpg"), dao.photosForDay(101).map { it.fileName })
        assertTrue(dao.photosForDay(100).isEmpty())
    }

    @Test
    fun deleteRemovesTheEntryAndReturnsItsPhotoFiles() = runTest {
        dao.save(null, 100, 80.0, 1, emptyList(), emptyList(), listOf("a.jpg"))
        dao.save(null, 99, 81.0, 1, emptyList(), emptyList(), listOf("other.jpg"))

        val removed = dao.delete(100)

        assertEquals(listOf("a.jpg"), removed)
        assertEquals(listOf(99L), dao.observeWeighIns().first().map { it.epochDay })
        assertEquals(listOf("other.jpg"), dao.observePhotos().first().map { it.fileName })
    }
}
