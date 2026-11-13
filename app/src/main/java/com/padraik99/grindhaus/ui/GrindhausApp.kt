package com.padraik99.grindhaus.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.ui.brew.HomeRoute
import com.padraik99.grindhaus.ui.brew.BrewViewModel
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import com.padraik99.grindhaus.ui.sleep.SleepRoute
import com.padraik99.grindhaus.ui.sleep.SleepViewModel
import com.padraik99.grindhaus.ui.vault.VaultRoute
import com.padraik99.grindhaus.ui.vault.VaultViewModel

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
        // consumeWindowInsets: a screen that makes room for the keyboard then doesn't also
        // add back the space the tab bar already took (which left a dead band above the keys).
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.VAULT -> VaultRoute(viewModel(factory = VaultViewModel.factory(graph)))
                Tab.HOME -> HomeRoute(
                    viewModel = viewModel(factory = BrewViewModel.factory(graph)),
                    today = LocalDate.now(graph.clock),
                    onAddBean = { tab = Tab.VAULT },
                )
                Tab.SLEEP -> SleepRoute(viewModel(factory = SleepViewModel.factory(graph)))
            }
        }
    }
}
