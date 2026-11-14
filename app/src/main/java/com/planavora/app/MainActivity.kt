package com.planavora.app

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.planavora.app.data.*
import com.planavora.app.domain.*
import com.planavora.app.platform.AmazonPremium
import com.planavora.app.platform.DeadlineWorker
import com.planavora.app.platform.StoreStatus
import com.planavora.app.ui.Planavora
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    var checkoutOrientation: Int? = null
    var waitingForPortrait = false
}

class MainActivity : AppCompatActivity() {
    private val model by viewModels<PlanModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DeadlineWorker.schedule(applicationContext)
        setContent { Planavora(model) }
        lifecycleScope.launch {
            model.billing.state.collect { state ->
                if (!model.waitingForPortrait && state.status !in setOf(StoreStatus.LOADING, StoreStatus.CHECKING)) {
                    restoreCheckoutOrientation()
                }
            }
        }
        if (model.waitingForPortrait) waitForCheckoutRotation()
    }

    fun startPremiumCheckout() {
        if (model.checkoutOrientation != null || !model.billing.canPurchase()) return
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            model.billing.purchase()
            return
        }
        // The Fire Appstore checkout failed to open from landscape on the Fire HD 10.
        model.checkoutOrientation = requestedOrientation
        model.waitingForPortrait = true
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (_: IllegalStateException) {
            restoreCheckoutOrientation()
            model.billing.purchase()
            return
        }
        waitForCheckoutRotation()
    }

    fun loadPremium() {
        if (model.checkoutOrientation == null) model.billing.load()
    }

    private fun waitForCheckoutRotation() {
        lifecycleScope.launch {
            delay(2500)
            if (model.waitingForPortrait) {
                restoreCheckoutOrientation()
                model.billing.purchase()
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (model.waitingForPortrait && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            model.waitingForPortrait = false
            model.billing.purchase()
        }
    }

    private fun restoreCheckoutOrientation() {
        val original = model.checkoutOrientation ?: return
        model.checkoutOrientation = null
        model.waitingForPortrait = false
        requestedOrientation = original
    }
}
