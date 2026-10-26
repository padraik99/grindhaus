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
 * The phone already holds an older database with real beans and brews in it.
 *
 * Each test builds an old version from the layout saved in app/schemas (exactly what that
 * release created on the phone), puts data in it, then opens it with the current app.
 * Room runs every upgrade in turn and refuses to open if the result doesn't match the
 * current layout, so a passing test means the upgrades are correct and the data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createVersion(version: Int, file: File, seed: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("schemas/com.padraik99.grindhaus.data.GrindhausDatabase/$version.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.seed()
            db.version = version
        }
    }

    private fun open(file: File) = Room.databaseBuilder(context, GrindhausDatabase::class.java, file.absolutePath)
        .allowMainThreadQueries()
        .build()

    private fun freshFile(name: String) = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }

    @Test
    fun version1BeansSurviveTheUpgradeToTheCurrentVersion() = runBlocking {
        val file = freshFile("upgrade-v1.db")
        createVersion(1, file) {
            execSQL(
                """INSERT INTO beans (id, title, roaster, origin, species, robusta_share, roast_level, roast_date,
                   best_by_date, purchase_date, opened_date, bag_grams, pre_ground, label_photo_path, tasting_tags,
                   notes, archived)
                   VALUES (1, 'Colombia Huila', 'Roaster', 'Colombia', 'NOT_SURE', NULL, 'MEDIUM', NULL,
                   20660, 20380, NULL, 340.0, 1, NULL, 'Cocoa, Caramel', 'Good in the moka', 0)""",
            )
        }

        val db = open(file)
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
            assertEquals(3, db.openHelper.readableDatabase.version)
            assertEquals(0, db.brewDao().observeIntakeSince(0).first().size)
        } finally {
            db.close()
        }
    }

    /** Version 2 is what 0.1.5 put on the phone: beans, the grinder, brews and their caffeine. */
    @Test
    fun version2BrewsSurviveAndCountAsWeighed() = runBlocking {
        val file = freshFile("upgrade-v2.db")
        createVersion(2, file) {
            execSQL(
                """INSERT INTO beans (id, title, roaster, origin, species, robusta_share, roast_level, roast_date,
                   best_by_date, purchase_date, opened_date, bag_grams, pre_ground, label_photo_path, tasting_tags,
                   notes, archived, buy_again, rank_score)
                   VALUES (1, 'Colombian Anei', 'Roaster', 'Colombia', 'ARABICA', NULL, 'MEDIUM', 20380,
                   NULL, 20385, 20386, 340.0, 0, NULL, '', '', 0, 'YES', 1512.0)""",
            )
            execSQL(
                """INSERT INTO grinders (id, name, min_setting, max_setting, step, finer_is_lower, range_labels, archived)
                   VALUES (1, 'Maestri House', 0.0, 30.0, 1.0, 1, '5:Espresso', 0)""",
            )
            execSQL(
                """INSERT INTO brew_logs (id, bean_id, method, grinder_id, filter, grind_setting, dose_g, out_g,
                   shot_time_s, rating, taste, notes, caffeine_mg, brewed_at)
                   VALUES (1, 1, 'ESPRESSO', 1, 'SINGLE_WALL', 3.0, 18.0, 40.0, 27, 4, 'BALANCED', 'First one', 170.0,
                   1793000000000)""",
            )
            execSQL(
                """INSERT INTO intake_events (id, taken_at, mg, source, brew_log_id, label)
                   VALUES (1, 1793000000000, 170.0, 'BREW', 1, 'Espresso')""",
            )
        }

        val db = open(file)
        try {
            val log = db.brewDao().log(1)!!
            assertEquals(true, log.weighed)
            assertEquals(40.0, log.outG, 0.0)
            assertEquals(27, log.shotTimeS)
            assertEquals("First one", log.notes)
            assertEquals(170.0, db.brewDao().observeIntakeSince(0).first().single().mg, 0.0)
            assertEquals(1512.0, db.beanDao().get(1)!!.rankScore, 0.0)
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
