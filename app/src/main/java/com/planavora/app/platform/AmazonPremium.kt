package com.planavora.app.platform

import android.app.Application
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.planavora.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class StoreStatus { IDLE, LOADING, UNAVAILABLE, CHECKING, RESTORED, FAILED, CANCELLED }
data class PremiumState(val active: Boolean, val price: String? = null, val status: StoreStatus = StoreStatus.IDLE)

class AmazonPremium(private val app: Application) : PurchasingListener {
    private val prefs = app.getSharedPreferences("premium", 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val state = MutableStateFlow(PremiumState(prefs.getBoolean("verified", false)))
    private var registered = false
    private var checking: Job? = null
    private var timeout: Job? = null
    private val sku = "planavora.premium"

    private fun begin(timeoutMillis: Long) {
        state.value = state.value.copy(status = StoreStatus.LOADING)
        timeout?.cancel()
        timeout = scope.launch {
            delay(timeoutMillis)
            if (state.value.status == StoreStatus.LOADING) status(StoreStatus.UNAVAILABLE)
        }
    }

    fun load() {
        if (state.value.status == StoreStatus.LOADING || state.value.status == StoreStatus.CHECKING) return
        request {
            if (!registered) {
                PurchasingService.registerListener(app, this)
                registered = true
            }
            PurchasingService.getProductData(setOf(sku))
        }
    }

    fun canPurchase(): Boolean = !state.value.active && state.value.price != null &&
        state.value.status !in setOf(StoreStatus.LOADING, StoreStatus.CHECKING) &&
        BuildConfig.RECEIPT_ENDPOINT.isNotBlank()

    fun purchase() {
        if (canPurchase()) {
            request(timeoutMillis = 180_000) { PurchasingService.purchase(sku) }
        }
    }

    fun restore() = request {
        if (registered) PurchasingService.getPurchaseUpdates(true)
        else {
            PurchasingService.registerListener(app, this)
            registered = true
            PurchasingService.getPurchaseUpdates(true)
        }
    }

    private fun request(timeoutMillis: Long = 15_000, action: () -> Unit) {
        if (BuildConfig.RECEIPT_ENDPOINT.isBlank()) {
            status(StoreStatus.UNAVAILABLE)
            return
        }
        begin(timeoutMillis)
        runCatching(action).onFailure { status(StoreStatus.UNAVAILABLE) }
    }

    override fun onUserDataResponse(response: UserDataResponse) = Unit
    override fun onProductDataResponse(response: ProductDataResponse) {
        val price = response.productData?.get(sku)?.price
        state.value = state.value.copy(price = price, status = if (price == null) StoreStatus.UNAVAILABLE else StoreStatus.IDLE)
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        when (response.requestStatus.name) {
            "SUCCESSFUL" -> verify(response.userData.userId, response.receipt)
            "ALREADY_PURCHASED" -> restore()
            "PENDING" -> status(StoreStatus.CHECKING)
            "INVALID_SKU", "NOT_SUPPORTED" -> status(StoreStatus.UNAVAILABLE)
            "CANCELED", "CANCELLED" -> status(StoreStatus.CANCELLED)
            else -> status(StoreStatus.FAILED)
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
            status(StoreStatus.FAILED)
            return
        }
        val receipts = response.receipts.filter { it.sku == sku }
        val active = receipts.lastOrNull { !it.isCanceled }
        if (active != null) verify(response.userData.userId, active)
        else {
            if (receipts.any { it.receiptId == prefs.getString("receipt", null) && it.isCanceled }) {
                prefs.edit().clear().apply()
                state.value = state.value.copy(active = false)
            }
            if (!response.hasMore()) status(StoreStatus.RESTORED)
        }
        if (response.hasMore()) PurchasingService.getPurchaseUpdates(false)
    }

    private fun verify(user: String, receipt: Receipt) {
        if (receipt.sku != sku || receipt.productType != ProductType.ENTITLED || receipt.isCanceled) {
            status(StoreStatus.FAILED)
            return
        }
        status(StoreStatus.CHECKING)
        checking?.cancel()
        checking = scope.launch {
            val verified = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(BuildConfig.RECEIPT_ENDPOINT).openConnection() as HttpsURLConnection
                    try {
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 10_000
                        connection.readTimeout = 10_000
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        val body = JSONObject().put("userId", user).put("receiptId", receipt.receiptId)
                        connection.outputStream.use { it.write(body.toString().toByteArray()) }
                        connection.responseCode == 200 && connection.inputStream.bufferedReader().use {
                            val reply = JSONObject(it.readText())
                            reply.optBoolean("valid") && reply.optString("sku") == sku
                        }
                    } finally { connection.disconnect() }
                }.getOrDefault(false)
            }
            if (verified) {
                prefs.edit().putBoolean("verified", true).putString("receipt", receipt.receiptId).apply()
                state.value = state.value.copy(active = true, status = StoreStatus.RESTORED)
                PurchasingService.notifyFulfillment(receipt.receiptId, FulfillmentResult.FULFILLED)
            } else status(StoreStatus.FAILED)
        }
    }

    private fun status(status: StoreStatus) { state.value = state.value.copy(status = status) }
}
