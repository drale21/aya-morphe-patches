package app.xiondigital.patches.tandem.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/*
 * R8 relocates Tandem's own classes into the default package, which is why the community-ad
 * composable below has a one-letter descriptor (`Ljhp;`) while the bundled SDKs keep their package
 * names (their consumer ProGuard rules preserve the API surface). Both kinds of anchor are exact
 * signatures pulled from classes.dex/classes4.dex of the stock 6.38.0 base.apk.
 *
 * Rewarded formats are deliberately absent: those are only requested when the user asks for one
 * (to earn in-app rewards), so blocking them would remove a feature instead of an annoyance.
 */

/**
 * `Ljhp;->a(Lex4;Lf79;Lpw4;Leh;Lbg5;I)V` — Tandem's community ad composable, in classes4.dex.
 *
 * The view model behind it emits a `CommunityAdConfig(adUnitId, adSize)` built from the AdMob unit
 * `ca-app-pub-4507927355231659/3667237836`, and this composable is the only consumer: it reads the
 * config, draws the ad, then a divider. The `"communityAd"` test tag it assigns to that slot pins
 * the method, and no other method in the class carries it.
 *
 * Note that the ad unit is an AdMob one but the AdMob *view* classes are not present in the dex —
 * Tandem renders the slot itself — so this composable, not an SDK call, is the choke point.
 */
internal object CommunityAdComposableFingerprint : Fingerprint(
    definingClass = "Ljhp;",
    name = "a",
    isStatic = true,
    returnType = "V",
    parameters = listOf(
        "Lex4;",
        "Lf79;",
        "Lpw4;",
        "Leh;",
        "Lbg5;",
        "I",
    ),
    filters = listOf(
        string("communityAd"),
    ),
)

/** `com.applovin.mediation.ads.MaxAppOpenAd.loadAd()` — the app-open ad Tandem requests at launch. */
internal object MaxAppOpenAdLoadFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/ads/MaxAppOpenAd;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/** `com.applovin.mediation.ads.MaxInterstitialAd.loadAd()`. */
internal object MaxInterstitialAdLoadFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/ads/MaxInterstitialAd;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/** `com.applovin.mediation.ads.MaxAdView.loadAd()` — the banner request. */
internal object MaxAdViewLoadFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/ads/MaxAdView;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/**
 * `com.applovin.mediation.ads.MaxAdView.startAutoRefresh()`.
 *
 * `loadAd()` issues a single request; this is what schedules the repeating banner refresh, so both
 * have to go or an empty banner container keeps polling.
 */
internal object MaxAdViewStartAutoRefreshFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/ads/MaxAdView;",
    name = "startAutoRefresh",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/** `com.facebook.ads.InterstitialAd.loadAd()`. */
internal object FacebookInterstitialLoadFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/InterstitialAd;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/** `com.facebook.ads.InterstitialAd.loadAd(InterstitialAd$InterstitialLoadAdConfig)`. */
internal object FacebookInterstitialLoadConfigFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/InterstitialAd;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Lcom/facebook/ads/InterstitialAd\$InterstitialLoadAdConfig;"),
)

/** `com.facebook.ads.AdView.loadAd()`. */
internal object FacebookAdViewLoadFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/AdView;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/** `com.facebook.ads.AdView.loadAd(AdView$AdViewLoadConfig)`. */
internal object FacebookAdViewLoadConfigFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/AdView;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Lcom/facebook/ads/AdView\$AdViewLoadConfig;"),
)

/** `com.facebook.ads.NativeAdBase.loadAd()` — `NativeAd` and `NativeBannerAd` inherit this one. */
internal object FacebookNativeAdLoadFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/NativeAdBase;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/** `com.facebook.ads.NativeAdBase.loadAd(NativeAdBase$NativeLoadAdConfig)`. */
internal object FacebookNativeAdLoadConfigFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/ads/NativeAdBase;",
    name = "loadAd",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Lcom/facebook/ads/NativeAdBase\$NativeLoadAdConfig;"),
)
