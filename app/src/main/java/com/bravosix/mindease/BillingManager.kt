package com.bravosix.mindease

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Capa fina sobre Google Play Billing v6.2.
 *
 * Productos (INAPP):
 *  - LIFETIME_ID = "mindease_lifetime" : pago único ~4,99 € → tokens ilimitados para siempre
 *  - TOKENS_50   = "tokens_50"         : pack consumible 0,99 € → +50 tokens de chat
 *  - TOKENS_200  = "tokens_200"        : pack consumible 2,99 € → +200 tokens de chat
 *  - TIP_ID      = "tip_099"           : consumible 0,99 € → donación al desarrollador
 */
class BillingManager private constructor(private val app: Application) : PurchasesUpdatedListener {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _lifetimeDetails = MutableStateFlow<ProductDetails?>(null)
    val lifetimeDetails: StateFlow<ProductDetails?> = _lifetimeDetails

    private val _tokens50Details = MutableStateFlow<ProductDetails?>(null)
    val tokens50Details: StateFlow<ProductDetails?> = _tokens50Details

    private val _tokens200Details = MutableStateFlow<ProductDetails?>(null)
    val tokens200Details: StateFlow<ProductDetails?> = _tokens200Details

    private val _tipDetails = MutableStateFlow<ProductDetails?>(null)
    val tipDetails: StateFlow<ProductDetails?> = _tipDetails

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun start() {
        if (client.isReady) { queryProducts(); queryOwned(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts(); queryOwned()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(LIFETIME_ID).setProductType(BillingClient.ProductType.INAPP).build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(TOKENS_50).setProductType(BillingClient.ProductType.INAPP).build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(TOKENS_200).setProductType(BillingClient.ProductType.INAPP).build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(TIP_ID).setProductType(BillingClient.ProductType.INAPP).build(),
            )).build()
        client.queryProductDetailsAsync(params) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                _lifetimeDetails.value  = list.firstOrNull { it.productId == LIFETIME_ID }
                _tokens50Details.value  = list.firstOrNull { it.productId == TOKENS_50 }
                _tokens200Details.value = list.firstOrNull { it.productId == TOKENS_200 }
                _tipDetails.value       = list.firstOrNull { it.productId == TIP_ID }
            }
        }
    }

    private fun queryOwned() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()
        ) { _, purchases -> handle(purchases) }
    }

    fun launchLifetime(activity: Activity)  { _lifetimeDetails.value?.let { launch(activity, it) } }
    fun launchTokens50(activity: Activity)  { _tokens50Details.value?.let { launch(activity, it) } }
    fun launchTokens200(activity: Activity) { _tokens200Details.value?.let { launch(activity, it) } }
    fun launchTip(activity: Activity)       { _tipDetails.value?.let { launch(activity, it) } }

    private fun launch(activity: Activity, pd: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(pd).build()
            )).build()
        client.launchBillingFlow(activity, params)
    }

    fun lifetimePrice(): String? = _lifetimeDetails.value?.oneTimePurchaseOfferDetails?.formattedPrice
    fun tokens50Price(): String? = _tokens50Details.value?.oneTimePurchaseOfferDetails?.formattedPrice
    fun tokens200Price(): String? = _tokens200Details.value?.oneTimePurchaseOfferDetails?.formattedPrice
    fun tipPrice(): String?       = _tipDetails.value?.oneTimePurchaseOfferDetails?.formattedPrice

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handle(purchases)
        }
    }

    private fun handle(purchases: List<Purchase>) {
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (LIFETIME_ID in p.products) {
                _isPremium.value = true
                Prefs.setPremium(app, true)
                if (!p.isAcknowledged) acknowledge(p)
            }
            if (TOKENS_50 in p.products && !p.isAcknowledged) {
                Prefs.addTokens(app, 50)
                consume(p)
            }
            if (TOKENS_200 in p.products && !p.isAcknowledged) {
                Prefs.addTokens(app, 200)
                consume(p)
            }
            if (TIP_ID in p.products && !p.isAcknowledged) {
                consume(p)
            }
        }
    }

    private fun acknowledge(p: Purchase) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) { /* no-op */ }
    }

    private fun consume(p: Purchase) {
        client.consumeAsync(
            ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) { _, _ -> /* no-op */ }
    }

    companion object {
        const val LIFETIME_ID = "mindease_lifetime"
        const val TOKENS_50   = "tokens_50"
        const val TOKENS_200  = "tokens_200"
        const val TIP_ID      = "tip_099"

        @Volatile private var INSTANCE: BillingManager? = null

        fun init(app: Application): BillingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(app).also { INSTANCE = it; it.start() }
            }

        fun get(): BillingManager = checkNotNull(INSTANCE) { "BillingManager not initialized" }
    }
}
