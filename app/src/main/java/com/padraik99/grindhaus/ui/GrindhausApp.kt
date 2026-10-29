package com.padraik99.grindhaus.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.ui.brew.HomeRoute
import com.padraik99.grindhaus.ui.brew.BrewViewModel
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import com.padraik99.grindhaus.domain.CaffeineModel
import com.padraik99.grindhaus.ui.components.BrandHeader
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import com.padraik99.grindhaus.ui.vault.VaultRoute
import com.padraik99.grindhaus.ui.vault.VaultViewModel
import java.util.Locale

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("Home", R.drawable.ic_nav_brew),
    VAULT("Vault", R.drawable.ic_nav_vault),
    SLEEP("Sleep", R.drawable.ic_nav_sleep),
}

@Composable
fun GrindhausApp(
    startTab: Tab = Tab.HOME,
    graph: AppGraph = AppGraph.get(LocalContext.current),
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    Scaffold(
        containerColor = GrindhausColors.Roast,
        bottomBar = {
            NavigationBar(containerColor = GrindhausColors.Roast) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GrindhausColors.Crema,
                            selectedTextColor = GrindhausColors.Crema,
                            indicatorColor = GrindhausColors.SurfaceHigh,
                            unselectedIconColor = GrindhausColors.Muted,
                            unselectedTextColor = GrindhausColors.Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.VAULT -> VaultRoute(viewModel(factory = VaultViewModel.factory(graph)))
                Tab.HOME -> HomeRoute(
                    viewModel = viewModel(factory = BrewViewModel.factory(graph)),
                    today = LocalDate.now(graph.clock),
                    onAddBean = { tab = Tab.VAULT },
                )
                Tab.SLEEP -> PlaceholderTab("Sleep") { SleepEnginePreview() }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader(title)
        content()
    }
}

/** Shows the engine is wired in: the derived bedtime limit at three half-lives. */
@Composable
private fun SleepEnginePreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GrindhausColors.Surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Bedtime limit by half-life")
        listOf(4.0, 5.0, 7.0).forEach { h ->
            val mg = CaffeineModel.derivedThresholdMg(h)
            Text(
                String.format(Locale.US, "%.0f h  →  %.0f mg", h, mg),
                color = GrindhausColors.Foam,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
            )
        }
        Text("The full Sleep Guard arrives in build step 7.", color = GrindhausColors.Muted, fontSize = 13.sp)
    }
}
