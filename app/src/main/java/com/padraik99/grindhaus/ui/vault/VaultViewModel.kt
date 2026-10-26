package com.padraik99.grindhaus.ui.vault

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BeanDao
import com.padraik99.grindhaus.data.GrindhausDatabase
import com.padraik99.grindhaus.domain.Freshness
import com.padraik99.grindhaus.domain.FreshnessRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BeanCard(val bean: Bean, val freshness: Freshness)

fun Bean.freshness(today: LocalDate): Freshness = FreshnessRules.of(
    today = today,
    roastDate = roastDate?.let(LocalDate::ofEpochDay),
    bestByDate = bestByDate?.let(LocalDate::ofEpochDay),
    openedDate = openedDate?.let(LocalDate::ofEpochDay),
    preGround = preGround,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val dao: BeanDao,
    val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val query = MutableStateFlow("")
    val showArchived = MutableStateFlow(false)

    val cards: StateFlow<List<BeanCard>> =
        combine(query, showArchived) { q, archived -> q.trim() to archived }
            .flatMapLatest { (q, archived) -> dao.observeList(archived, q) }
            .map { beans -> beans.map { BeanCard(it, it.freshness(today())) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCount: StateFlow<Int> =
        dao.observeCount(false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val archivedCount: StateFlow<Int> =
        dao.observeCount(true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun bean(id: Long): Flow<Bean?> = dao.observe(id)

    fun save(bean: Bean, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (bean.id == 0L) dao.insert(bean) else bean.id.also { dao.update(bean) }
            onSaved(id)
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { dao.setArchived(id, archived) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                VaultViewModel(GrindhausDatabase.get(app).beanDao())
            }
        }

        fun factory(dao: BeanDao, today: () -> LocalDate): ViewModelProvider.Factory = viewModelFactory {
            initializer { VaultViewModel(dao, today) }
        }
    }
}
