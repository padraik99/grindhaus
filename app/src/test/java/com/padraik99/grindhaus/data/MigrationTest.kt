package com.padraik99.grindhaus.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The phone already holds a version 1 database with real beans in it.
 *
 * This builds a version 1 database from the layout saved in app/schemas (exactly what
 * step 5 created on the phone), puts a bean in it, then opens it with the current app.
 * Room runs the upgrade and refuses to open if the result doesn't match version 2's
 * expected layout, so a passing test means the upgrade is correct and the bean survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createVersion1(file: File) {
        val schema = JSONObject(File("schemas/com.padraik99.grindhaus.data.GrindhausDatabase/1.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                """INSERT INTO beans (id, title, roaster, origin, species, robusta_share, roast_level, roast_date,
                   best_by_date, purchase_date, opened_date, bag_grams, pre_ground, label_photo_path, tasting_tags,
                   notes, archived)
                   VALUES (1, 'Colombia Huila', 'Roaster', 'Colombia', 'NOT_SURE', NULL, 'MEDIUM', NULL,
                   20660, 20380, NULL, 340.0, 1, NULL, 'Cocoa, Caramel', 'Good in the moka', 0)""",
            )
            db.version = 1
        }
    }

    @Test
    fun version1BeansSurviveTheUpgradeToVersion2() = runBlocking {
        val file = context.getDatabasePath("upgrade-test.db").apply { parentFile?.mkdirs(); delete() }
        createVersion1(file)

        val db = Room.databaseBuilder(context, GrindhausDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
        try {
            val beans = db.beanDao().active()
            assertEquals(1, beans.size)
            val bean = beans.single()
            assertEquals("Colombia Huila", bean.title)
            assertEquals(340.0, bean.bagGrams!!, 0.0)
            assertEquals(listOf("Cocoa", "Caramel"), bean.tags)
            assertEquals(true, bean.preGround)
            assertEquals(null, bean.buyAgain)
            assertEquals(1500.0, bean.rankScore, 0.0)
            assertEquals(2, db.openHelper.readableDatabase.version)
            assertEquals(0, db.brewDao().observeIntakeSince(0).first().size)
        } finally {
            db.close()
        }
    }
}
