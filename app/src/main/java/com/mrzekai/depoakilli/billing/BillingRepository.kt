package com.mrzekai.depoakilli.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One-off results the UI turns into a short message. */
enum class BillingEvent { PRO_ACTIVATED, PENDING, RESTORED, NOTHING_TO_RESTORE, RESTORE_FAILED, FAILED, UNAVAILABLE }

/**
 * Google Play Billing for Smart Cleaner Pro (2.0 Faz 1).
 *
 * Talks only to the Play Store app on the device (no network call from this
 * app, no server). It connects with retries, reads products and prices from
 * Play, starts purchases, re-reads owned purchases on every app start and
 * resume, acknowledges every unacknowledged purchase and keeps [Entitlement]
 * current. A PENDING purchase never unlocks Pro.
 */
class BillingRepository(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()

    val entitlement = Entitlement(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ProScreenState(isPro = entitlement.isPro.value))
    val state: StateFlow<ProScreenState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private var productDetails: Map<String, ProductDetails> = emptyMap()
    private var launchedKind: ProPlanKind? = null
    private var launchedTrialDays: Int? = null

    /** S3: end of a trial bought on this device (Play does not give it otherwise); null without one. */
    val trialEndMillis: Long? get() = prefs.getLong(KEY_TRIAL_END, 0L).takeIf { it > 0L }

    /** S3: the subscription still renews (a cancelled trial must not be reminded). */
    val autoRenewing: Boolean get() = state.value.ownedPlan == OwnedPlan.YEARLY

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        scope.launch {
            entitlement.isPro.collect { pro -> _state.update { it.copy(isPro = pro) } }
        }
    }

    /**
     * Re-reads what the user owns (app start, every resume, "Restore purchases").
     * The entitlement changes only on a complete answer for both product types,
     * so an offline start keeps the last known state.
     */
    fun refreshPurchases(userRequested: Boolean = false) {
        scope.launch {
            if (!ensureConnected()) {
                if (userRequested) _events.tryEmit(BillingEvent.UNAVAILABLE)
                return@launch
            }
            val subs = queryPurchases(BillingClient.ProductType.SUBS)
            val oneTime = queryPurchases(BillingClient.ProductType.INAPP)
            if (subs == null || oneTime == null) {
                if (userRequested) _events.tryEmit(BillingEvent.RESTORE_FAILED)
                return@launch
            }
            val owned = (subs + oneTime).map(::toOwned)
            acknowledge(owned)
            entitlement.update(owned)
            val lastWeekPass = prefs.getString(KEY_LAST_KIND, null) == ProPlanKind.WEEK_PASS.name
            _state.update {
                it.copy(
                    pending = EntitlementRules.hasPending(owned),
                    ownedPlan = EntitlementRules.ownedPlan(owned, lastWeekPass),
                )
            }
            if (userRequested) {
                _events.tryEmit(
                    if (EntitlementRules.isPro(owned)) BillingEvent.RESTORED else BillingEvent.NOTHING_TO_RESTORE,
                )
            }
        }
    }

    /** Loads the plans and their prices from Play for the Pro page. */
    fun loadProducts() {
        scope.launch {
            _state.update { it.copy(loading = true) }
            if (!ensureConnected()) {
                _state.update { it.copy(loading = false, plans = emptyList()) }
                return@launch
            }
            val subscription = queryProduct(BillingClient.ProductType.SUBS, BillingProducts.SUBSCRIPTION)
            val lifetime = queryProduct(BillingClient.ProductType.INAPP, BillingProducts.LIFETIME)
            productDetails = listOfNotNull(subscription, lifetime).associateBy(ProductDetails::getProductId)
            val offers = subscription?.subscriptionOfferDetails.orEmpty().map { offer ->
                SubscriptionOfferInfo(
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    offerToken = offer.offerToken,
                    phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                        PricingPhaseInfo(
                            phase.priceAmountMicros,
                            phase.formattedPrice,
                            phase.billingPeriod,
                            phase.priceCurrencyCode,
                        )
                    },
                )
            }
            val oneTime = lifetime?.oneTimePurchaseOfferDetails
            val plans = ProOffers.build(
                offers,
                oneTime?.formattedPrice,
                oneTime?.priceAmountMicros ?: 0L,
                oneTime?.priceCurrencyCode.orEmpty(),
            )
            _state.update { it.copy(loading = false, plans = plans, upgrade = ProOffers.upgrade(offers)) }
        }
    }

    /**
     * Opens Play's purchase sheet for [plan]. The result arrives in [onPurchasesUpdated].
     * A week-pass top-up ("Add time") is launched exactly like the first purchase;
     * Play extends the pass and the top-up is acknowledged like any purchase.
     */
    fun launchPurchase(activity: Activity, plan: ProPlanOption) {
        val productId = if (plan.kind == ProPlanKind.LIFETIME) BillingProducts.LIFETIME else BillingProducts.SUBSCRIPTION
        val details = productDetails[productId]
        if (details == null || !client.isReady) {
            _events.tryEmit(BillingEvent.UNAVAILABLE)
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { plan.offerToken?.let(::setOfferToken) }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        launchedKind = plan.kind
        launchedTrialDays = plan.trialDays
        val result = client.launchBillingFlow(activity, flowParams)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases(userRequested = true)
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> _events.tryEmit(BillingEvent.UNAVAILABLE)
            else -> _events.tryEmit(BillingEvent.FAILED)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val owned = purchases.orEmpty().map(::toOwned)
                val kind = launchedKind
                // S3: a trial started now ends after its days; the reminder comes a day before.
                val trialDays = launchedTrialDays
                val trialPurchase = purchases.orEmpty().firstOrNull {
                    BillingProducts.SUBSCRIPTION in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (kind == ProPlanKind.YEARLY && trialDays != null && trialPurchase != null) {
                    val end = com.mrzekai.depoakilli.pro.SalesRules.trialEnd(trialPurchase.purchaseTime, trialDays)
                    prefs.edit().putLong(KEY_TRIAL_END, end).apply()
                    TrialReminder.schedule(appContext, end)
                }
                if (EntitlementRules.isPro(owned) && kind != null && kind != ProPlanKind.LIFETIME) {
                    // Remembered so Y5 can name a pass (Play does not say which base plan a purchase is).
                    prefs.edit().putString(KEY_LAST_KIND, kind.name).apply()
                }
                when {
                    EntitlementRules.isPro(owned) -> _events.tryEmit(BillingEvent.PRO_ACTIVATED)
                    EntitlementRules.hasPending(owned) -> _events.tryEmit(BillingEvent.PENDING)
                }
                // Acknowledges and recomputes Pro from everything the user owns.
                refreshPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases(userRequested = true)
            else -> _events.tryEmit(BillingEvent.FAILED)
        }
    }

    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        // Faz 2: every attempt is time-limited (BillingConnect), so the lock is always released.
        BillingConnect.connect {
            when (connectOnce().responseCode) {
                BillingClient.BillingResponseCode.OK -> BillingConnect.Outcome.OK
                // No Play Store / billing not supported on this device: retrying will not help.
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> BillingConnect.Outcome.GIVE_UP
                else -> BillingConnect.Outcome.RETRY
            }
        }
    }

    private suspend fun connectOnce(): BillingResult = suspendCancellableCoroutine { continuation ->
        val answered = AtomicBoolean(false)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (answered.compareAndSet(false, true) && continuation.isActive) continuation.resume(billingResult)
            }

            override fun onBillingServiceDisconnected() {
                // Automatic service reconnection handles later drops.
            }
        })
    }

    private suspend fun queryPurchases(type: String): List<Purchase>? = suspendCancellableCoroutine { continuation ->
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (continuation.isActive) {
                continuation.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases else null,
                )
            }
        }
    }

    private suspend fun queryProduct(type: String, productId: String): ProductDetails? =
        suspendCancellableCoroutine { continuation ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(type)
                .build()
            val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
            client.queryProductDetailsAsync(params) { result, details ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            details.productDetailsList.firstOrNull { it.productId == productId }
                        } else {
                            null
                        },
                    )
                }
            }
        }

    /** Play refunds a purchase that is not acknowledged within 3 days; never consumed (Pro is permanent or time-bound). */
    private fun acknowledge(owned: List<OwnedPurchase>) {
        EntitlementRules.needingAcknowledge(owned).forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()
            client.acknowledgePurchase(params) { /* re-checked on the next refresh */ }
        }
    }

    private fun toOwned(purchase: Purchase) = OwnedPurchase(
        products = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> OwnedPurchase.State.PURCHASED
            Purchase.PurchaseState.PENDING -> OwnedPurchase.State.PENDING
            else -> OwnedPurchase.State.UNSPECIFIED
        },
        acknowledged = purchase.isAcknowledged,
        token = purchase.purchaseToken,
        autoRenewing = purchase.isAutoRenewing,
    )

    private companion object {
        const val PREFS = "pro_billing"
        const val KEY_LAST_KIND = "last_bought_kind"
        const val KEY_TRIAL_END = "trial_end_millis"
    }
}
