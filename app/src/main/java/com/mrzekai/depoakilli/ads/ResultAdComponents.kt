package com.mrzekai.depoakilli.ads

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.mrzekai.depoakilli.BuildConfig

@Composable
fun CleanupResultAdSurface(
    canRequestAds: Boolean,
    onAdPresented: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!canRequestAds) return

    val context = LocalContext.current
    val nativeAdUnitId = BuildConfig.ADMOB_RESULT_NATIVE_ID
    var nativeAd by remember(nativeAdUnitId) { mutableStateOf<NativeAd?>(null) }
    var nativeFailed by remember(nativeAdUnitId) {
        mutableStateOf(nativeAdUnitId.isBlank())
    }

    DisposableEffect(context, canRequestAds, nativeAdUnitId) {
        if (!canRequestAds || nativeAdUnitId.isBlank()) {
            onDispose {
                nativeAd?.destroy()
                nativeAd = null
            }
        } else {
            val loader = AdLoader.Builder(context, nativeAdUnitId)
                .forNativeAd { loaded ->
                    nativeAd?.destroy()
                    nativeAd = loaded
                    nativeFailed = false
                }
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            nativeFailed = true
                        }
                    },
                )
                .build()

            loader.loadAd(AdRequest.Builder().build())

            onDispose {
                nativeAd?.destroy()
                nativeAd = null
            }
        }
    }

    val loadedNative = nativeAd
    when {
        loadedNative != null -> {
            LaunchedEffect(loadedNative) {
                onAdPresented()
            }
            NativeResultAd(
                nativeAd = loadedNative,
                modifier = modifier,
            )
        }

        nativeFailed -> {
            ResultMrecAd(
                canRequestAds = canRequestAds,
                onAdPresented = onAdPresented,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun NativeResultAd(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AndroidView(
        factory = {
            createNativeAdView(context).also { view ->
                bindNativeAd(view, nativeAd)
            }
        },
        update = { view ->
            bindNativeAd(view, nativeAd)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 245.dp, max = 330.dp),
    )
}

@Composable
private fun ResultMrecAd(
    canRequestAds: Boolean,
    onAdPresented: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var presented by remember { mutableStateOf(false) }

    val adView = remember(context, BuildConfig.ADMOB_BANNER_ID) {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.MEDIUM_RECTANGLE)
        }
    }

    DisposableEffect(adView, canRequestAds) {
        if (canRequestAds) {
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (!presented) {
                        presented = true
                        onAdPresented()
                    }
                }
            }
            adView.loadAd(AdRequest.Builder().build())
        }

        onDispose {
            adView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier
                .width(300.dp)
                .height(250.dp),
        )
    }
}

private fun createNativeAdView(context: android.content.Context): NativeAdView {
    val nativeAdView = NativeAdView(context)

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 12))
        background = GradientDrawable().apply {
            setColor(AndroidColor.rgb(10, 24, 50))
            cornerRadius = dp(context, 18).toFloat()
            setStroke(dp(context, 1), AndroidColor.rgb(40, 69, 112))
        }
    }

    val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 10f
        setTextColor(AndroidColor.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3))
        background = GradientDrawable().apply {
            setColor(AndroidColor.rgb(31, 107, 202))
            cornerRadius = dp(context, 8).toFloat()
        }
    }

    val advertiser = TextView(context).apply {
        textSize = 11f
        setTextColor(AndroidColor.rgb(176, 192, 222))
        maxLines = 1
        setPadding(dp(context, 8), 0, 0, 0)
    }

    topRow.addView(
        adBadge,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    topRow.addView(
        advertiser,
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ),
    )
    container.addView(
        topRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )

    val media = MediaView(context).apply {
        setBackgroundColor(AndroidColor.rgb(5, 15, 33))
    }
    container.addView(
        media,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 150),
        ).apply {
            topMargin = dp(context, 8)
        },
    )

    val headline = TextView(context).apply {
        textSize = 16f
        setTextColor(AndroidColor.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 2
    }
    container.addView(
        headline,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(context, 9)
        },
    )

    val body = TextView(context).apply {
        textSize = 11f
        setTextColor(AndroidColor.rgb(184, 197, 220))
        maxLines = 2
    }
    container.addView(
        body,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(context, 4)
        },
    )

    val actionRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val callToAction = Button(context).apply {
        isAllCaps = false
        textSize = 12f
        minHeight = 0
        minWidth = 0
    }

    actionRow.addView(
        icon,
        LinearLayout.LayoutParams(
            dp(context, 36),
            dp(context, 36),
        ),
    )
    actionRow.addView(
        callToAction,
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply {
            marginStart = dp(context, 10)
        },
    )
    container.addView(
        actionRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(context, 7)
        },
    )

    nativeAdView.addView(
        container,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )

    nativeAdView.mediaView = media
    nativeAdView.headlineView = headline
    nativeAdView.bodyView = body
    nativeAdView.advertiserView = advertiser
    nativeAdView.iconView = icon
    nativeAdView.callToActionView = callToAction

    return nativeAdView
}

private fun bindNativeAd(
    view: NativeAdView,
    nativeAd: NativeAd,
) {
    (view.headlineView as? TextView)?.text = nativeAd.headline

    val bodyView = view.bodyView as? TextView
    bodyView?.text = nativeAd.body.orEmpty()
    bodyView?.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE

    val advertiserView = view.advertiserView as? TextView
    advertiserView?.text = nativeAd.advertiser.orEmpty()
    advertiserView?.visibility =
        if (nativeAd.advertiser.isNullOrBlank()) View.INVISIBLE else View.VISIBLE

    val iconView = view.iconView as? ImageView
    val iconDrawable = nativeAd.icon?.drawable
    iconView?.setImageDrawable(iconDrawable)
    iconView?.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE

    val ctaView = view.callToActionView as? Button
    ctaView?.text = nativeAd.callToAction.orEmpty()
    ctaView?.visibility =
        if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

    view.mediaView?.mediaContent = nativeAd.mediaContent
    view.setNativeAd(nativeAd)
}

private fun dp(
    context: android.content.Context,
    value: Int,
): Int = (value * context.resources.displayMetrics.density).toInt()
