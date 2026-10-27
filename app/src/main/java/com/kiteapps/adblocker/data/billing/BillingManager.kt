package com.kiteapps.adblocker.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.kiteapps.adblocker.core.model.UserTier
import com.kiteapps.adblocker.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class BillingEvent {
    data class PurchaseSuccess(val tier: UserTier) : BillingEvent()
    data object PurchaseCancelled : BillingEvent()
    data class PurchaseError(val message: String, val code: Int) : BillingEvent()
}

class BillingManager private constructor(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _userTier = MutableStateFlow(UserTier.FREE)
    val userTier: StateFlow<UserTier> = _userTier.asStateFlow()

    val isPro: StateFlow<Boolean> = _userTier.map { it.isPro }.stateIn(
        scope,
        SharingStarted.Eagerly,
        false
    )

    private val _billingEvents = MutableSharedFlow<BillingEvent>()
    val billingEvents: SharedFlow<BillingEvent> = _billingEvents.asSharedFlow()

    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(pendingPurchasesParams)
        .build()

    init {
        // Observe persisted user tier from PreferencesManager
        scope.launch {
            preferencesManager.userTier.collect { savedTier ->
                _userTier.value = savedTier
            }
        }
        startConnection()
    }

    fun startConnection() {
        if (_isConnected.value) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    Timber.d("BillingClient: Connected successfully")
                    _isConnected.value = true
                    queryAllProducts()
                    queryActivePurchases()
                } else {
                    Timber.w("BillingClient: Setup failed code=${billingResult.responseCode} msg=${billingResult.debugMessage}")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Timber.w("BillingClient: Service disconnected, will reconnect on next request")
                _isConnected.value = false
            }
        })
    }

    private fun queryAllProducts() {
        if (!_isConnected.value) return

        // 1. Query Subscriptions
        val subProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.PRODUCT_PRO_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.PRODUCT_PRO_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subProducts)
            .build()

        billingClient.queryProductDetailsAsync(subParams) { billingResult, queryProductDetailsList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val current = _productDetails.value.toMutableMap()
                queryProductDetailsList.forEach { details ->
                    current[details.productId] = details
                }
                _productDetails.value = current
                Timber.d("BillingClient: Loaded ${queryProductDetailsList.size} subscription products")
            } else {
                Timber.w("BillingClient: Failed to query subscriptions ${billingResult.debugMessage}")
            }
        }

        // 2. Query Lifetime In-App Product
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.PRODUCT_PRO_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProducts)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, queryProductDetailsList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val current = _productDetails.value.toMutableMap()
                queryProductDetailsList.forEach { details ->
                    current[details.productId] = details
                }
                _productDetails.value = current
                Timber.d("BillingClient: Loaded ${queryProductDetailsList.size} in-app products")
            } else {
                Timber.w("BillingClient: Failed to query in-app products ${billingResult.debugMessage}")
            }
        }
    }

    fun queryActivePurchases() {
        if (!_isConnected.value) return

        // Query Subscription purchases
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                handlePurchasesList(purchases)
            }
        }

        // Query One-time In-App purchases
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(inAppParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                handlePurchasesList(purchases)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                if (purchases != null) {
                    handlePurchasesList(purchases)
                }
            }
            BillingResponseCode.USER_CANCELED -> {
                Timber.d("BillingClient: Purchase cancelled by user")
                scope.launch { _billingEvents.emit(BillingEvent.PurchaseCancelled) }
            }
            else -> {
                Timber.e("BillingClient: Purchase error ${billingResult.debugMessage}")
                scope.launch {
                    _billingEvents.emit(
                        BillingEvent.PurchaseError(
                            billingResult.debugMessage.ifBlank { "Purchase failed with code ${billingResult.responseCode}" },
                            billingResult.responseCode
                        )
                    )
                }
            }
        }
    }

    private fun handlePurchasesList(purchases: List<Purchase>) {
        var highestTier = UserTier.FREE

        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Acknowledge if needed
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }

                for (productId in purchase.products) {
                    val tier = when (productId) {
                        BillingConstants.PRODUCT_PRO_LIFETIME -> UserTier.PRO_LIFETIME
                        BillingConstants.PRODUCT_PRO_ANNUAL -> UserTier.PRO_ANNUAL
                        BillingConstants.PRODUCT_PRO_MONTHLY -> UserTier.PRO_MONTHLY
                        else -> UserTier.FREE
                    }
                    if (tier.ordinal > highestTier.ordinal) {
                        highestTier = tier
                    }
                }
            }
        }

        if (highestTier.isPro) {
            setTier(highestTier)
            scope.launch { _billingEvents.emit(BillingEvent.PurchaseSuccess(highestTier)) }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingResponseCode.OK) {
                Timber.d("BillingClient: Purchase acknowledged ${purchase.orderId}")
            } else {
                Timber.w("BillingClient: Acknowledgment failed ${result.debugMessage}")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String, onLaunchFailed: (String) -> Unit = {}) {
        val details = _productDetails.value[productId]
        if (details == null) {
            Timber.w("BillingClient: ProductDetails not found for $productId")
            onLaunchFailed("Store product details are not available yet. Please check your internet connection.")
            return
        }

        val flowParamsBuilder = BillingFlowParams.newBuilder()

        val productDetailsParamsList = when (details.productType) {
            BillingClient.ProductType.SUBS -> {
                val offerDetails = details.subscriptionOfferDetails?.firstOrNull()
                val offerToken = offerDetails?.offerToken ?: ""
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            }
            BillingClient.ProductType.INAPP -> {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            }
            else -> emptyList()
        }

        if (productDetailsParamsList.isEmpty()) {
            onLaunchFailed("No pricing options available for this product.")
            return
        }

        val flowParams = flowParamsBuilder
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val responseCode = billingClient.launchBillingFlow(activity, flowParams).responseCode
        if (responseCode != BillingResponseCode.OK) {
            onLaunchFailed("Failed to launch billing flow ($responseCode)")
        }
    }

    fun restorePurchases(onComplete: (Boolean, UserTier) -> Unit) {
        if (!_isConnected.value) {
            startConnection()
        }
        queryActivePurchases()
        scope.launch {
            kotlinx.coroutines.delay(1000)
            val currentTier = _userTier.value
            onComplete(currentTier.isPro, currentTier)
        }
    }

    /**
     * Set user tier and persist to PreferencesManager
     */
    fun setTier(tier: UserTier) {
        _userTier.value = tier
        scope.launch {
            preferencesManager.setUserTier(tier)
        }
    }

    /**
     * Developer/Debug utility: allows toggling Free vs Pro instantly in testing environments
     */
    fun setDevTier(tier: UserTier) {
        Timber.i("BillingManager: Dev tier set to ${tier.name}")
        setTier(tier)
    }

    fun getFormattedPrice(productId: String): String {
        val details = _productDetails.value[productId] ?: return when (productId) {
            BillingConstants.PRODUCT_PRO_MONTHLY -> BillingConstants.DEFAULT_PRICE_MONTHLY
            BillingConstants.PRODUCT_PRO_ANNUAL -> BillingConstants.DEFAULT_PRICE_ANNUAL
            BillingConstants.PRODUCT_PRO_LIFETIME -> BillingConstants.DEFAULT_PRICE_LIFETIME
            else -> "--"
        }

        return when (details.productType) {
            BillingClient.ProductType.SUBS -> {
                details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: BillingConstants.DEFAULT_PRICE_ANNUAL
            }
            BillingClient.ProductType.INAPP -> {
                details.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: BillingConstants.DEFAULT_PRICE_LIFETIME
            }
            else -> "--"
        }
    }

    companion object {
        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context, preferencesManager: PreferencesManager): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext, preferencesManager).also {
                    instance = it
                }
            }
        }
    }
}
