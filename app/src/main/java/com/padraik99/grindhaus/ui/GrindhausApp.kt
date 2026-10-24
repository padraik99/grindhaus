package com.padraik99.grindhaus.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.domain.CaffeineModel
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.util.Locale

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    BREW("Brew", R.drawable.ic_nav_brew),
    VAULT("Vault", R.drawable.ic_nav_vault),
    SLEEP("Sleep", R.drawable.ic_nav_sleep),
}

@Composable
fun GrindhausApp(startTab: Tab = Tab.BREW) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "GRINDHAUS",
                color = GrindhausColors.Crema,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.6.sp,
            )
            Text(tab.label, color = GrindhausColors.Foam, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            when (tab) {
                Tab.BREW -> PlaceholderCard("Brew Bar arrives in build step 6.")
                Tab.VAULT -> PlaceholderCard("The Vault arrives in build step 5.")
                Tab.SLEEP -> SleepEnginePreview()
            }
        }
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    Text(
        text,
        color = GrindhausColors.Muted,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(GrindhausColors.Surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
    )
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
        Text("BEDTIME LIMIT BY HALF-LIFE", color = GrindhausColors.Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
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
