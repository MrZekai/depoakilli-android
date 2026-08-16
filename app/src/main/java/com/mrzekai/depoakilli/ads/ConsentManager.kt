package com.mrzekai.depoakilli.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConsentManager(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val initialized = AtomicBoolean(false)
    private val _canRequestAds = MutableStateFlow(false)

    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()
    val privacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(activity: Activity) {
        val parameters = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateAdsState(activity.applicationContext)
                }
                updateAdsState(activity.applicationContext)
            },
            {
                updateAdsState(activity.applicationContext)
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            updateAdsState(activity.applicationContext)
        }
    }

    private fun updateAdsState(context: Context) {
        val allowed = consentInformation.canRequestAds()
        _canRequestAds.value = allowed
        if (allowed && initialized.compareAndSet(false, true)) {
            Thread {
                MobileAds.initialize(context) { }
            }.start()
        }
    }
}
