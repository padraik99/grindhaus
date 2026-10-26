package com.padraik99.grindhaus.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.testing.TestGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarcodeLookupTest {

    private var asked = 0
    private val graph: AppGraph = TestGraph.create(
        TestGraph.clockAt("2026-10-26T15:30"),
        products = ProductLookup {
            asked++
            ProductResult.Found(BagDetails(title = "From the internet"))
        },
    )

    @After
    fun tearDown() = graph.db.close()

    @Test
    fun aBagYouHadBeforeComesFromYourVaultWithoutGoingOnline() = runBlocking {
        val dao = graph.db.beanDao()
        val id = dao.insert(Bean(title = "Colombian Anei", roastLevel = RoastLevel.MEDIUM, barcode = "0123456789012", buyAgain = BuyAgain.YES))
        dao.archive(id)

        val outcome = graph.barcodeLookup.lookup("0123456789012")
        assertTrue(outcome is LookupOutcome.FromVault)
        assertEquals("Colombian Anei", (outcome as LookupOutcome.FromVault).bean.title)
        assertEquals(0, asked)
    }

    @Test
    fun theNewestMatchingBagWins() = runBlocking {
        val dao = graph.db.beanDao()
        dao.insert(Bean(title = "First bag", roastLevel = RoastLevel.MEDIUM, barcode = "111"))
        dao.insert(Bean(title = "Second bag", roastLevel = RoastLevel.DARK, barcode = "111"))
        assertEquals("Second bag", (graph.barcodeLookup.lookup("111") as LookupOutcome.FromVault).bean.title)
    }

    @Test
    fun aNewBarcodeIsLookedUpOnline() = runBlocking {
        val outcome = graph.barcodeLookup.lookup("8000070042025")
        assertEquals(LookupOutcome.Found("8000070042025", BagDetails(title = "From the internet")), outcome)
        assertEquals(1, asked)
    }
}
