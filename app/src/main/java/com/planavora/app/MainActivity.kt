package com.planavora.app

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.planavora.app.data.*
import com.planavora.app.domain.*
import com.planavora.app.platform.AmazonPremium
import com.planavora.app.platform.DeadlineWorker
import com.planavora.app.ui.Planavora
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

val Application.settings by preferencesDataStore("settings")

class PlanavoraApplication : Application() {
    val store by lazy { PlanStore(PlanDatabase.open(this)) }
    val billing by lazy { AmazonPremium(this) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PlanavoraApplication
    val store = app.store
    val billing = app.billing
    val selected = MutableStateFlow<String?>(null)
    val events = store.dao.observeEvents().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val event = combine(events, selected) { rows, id ->
        rows?.firstOrNull { it.id == id } ?: rows?.firstOrNull { !it.archived }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val items = event.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null to emptyList())
        else store.dao.observeItems(id).map { rows -> id to rows.map(ItemRow::item) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null to emptyList())
    val settings = app.settings
}

class MainActivity : AppCompatActivity() {
    private val model by viewModels<PlanModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DeadlineWorker.schedule(applicationContext)
        setContent { Planavora(model) }
    }
}
