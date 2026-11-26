package com.mrzekai.depoakilli.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A purchase as the app needs it; built from Play's Purchase (pure, unit-tested). */
data class OwnedPurchase(
    val products: List<String>,
    val state: State,
    val acknowledged: Boolean,
    val token: String,
) {
    enum class State { PURCHASED, PENDING, UNSPECIFIED }
}

/** Pure rules behind the Pro entitlement. */
object EntitlementRules {
    /** Pro = an active `pro` subscription or `pro_lifetime`, in the PURCHASED state. PENDING never counts. */
    fun isPro(purchases: List<OwnedPurchase>): Boolean = purchases.any { purchase ->
        purchase.state == OwnedPurchase.State.PURCHASED &&
            purchase.products.any(BillingProducts.PRO_PRODUCTS::contains)
    }

    /** A Pro purchase is still waiting for payment (shown to the user, grants nothing). */
    fun hasPending(purchases: List<OwnedPurchase>): Boolean = purchases.any { purchase ->
        purchase.state == OwnedPurchase.State.PENDING &&
            purchase.products.any(BillingProducts.PRO_PRODUCTS::contains)
    }

    /** Purchases Play still needs acknowledged (within 3 days, or it refunds them). */
    fun needingAcknowledge(purchases: List<OwnedPurchase>): List<OwnedPurchase> =
        purchases.filter { it.state == OwnedPurchase.State.PURCHASED && !it.acknowledged }
}

/**
 * The Pro entitlement. The last known value is kept on the device so the app
 * opens correctly offline; Play's answer corrects it as soon as it arrives.
 * There is no server: ownership is checked on the device only.
 */
class Entitlement(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Applies a complete answer from Play (both product types). */
    fun update(purchases: List<OwnedPurchase>) {
        val pro = EntitlementRules.isPro(purchases)
        if (pro != _isPro.value || !prefs.contains(KEY_IS_PRO)) {
            prefs.edit().putBoolean(KEY_IS_PRO, pro).apply()
        }
        _isPro.value = pro
    }

    private companion object {
        const val PREFS = "pro_entitlement"
        const val KEY_IS_PRO = "last_known_is_pro"
    }
}
