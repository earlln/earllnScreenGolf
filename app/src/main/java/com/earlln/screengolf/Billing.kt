package com.earlln.screengolf

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * '공유 무제한' 인앱 상품(한 번 사면 끝) 구매를 맡는다.
 *
 * 테스트 빌드(debug, [BuildConfig.FAKE_BILLING])는 Play 스토어 없이도 흐름을 시험할 수 있게
 * 실제 결제 대신 확인 창으로 구매를 흉내 낸다. Play 스토어용 release 빌드만 실제 결제를 쓴다.
 *
 * 구매 상태는 [Prefs.saveUnlimited] 로 남겨, 스토어에 연결되기 전에도 바로 쓴다.
 * 연결될 때마다 구매 내역을 다시 읽어 환불된 구매는 되돌린다.
 */
class Billing(
    private val activity: Activity,
    /** 구매 상태가 바뀌었을 때(구매 완료, 복원, 환불 반영) 화면을 다시 그리라고 부른다. */
    private val onChanged: () -> Unit
) {

    sealed class Outcome {
        object Bought : Outcome()
        object Pending : Outcome()
        object Cancelled : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    companion object {
        /** Play Console 에 만들 인앱 상품 ID 와 같아야 한다. */
        const val PRODUCT_UNLIMITED_SHARE = "unlimited_share"
    }

    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var ready = false
    private var pendingBuy: ((Outcome) -> Unit)? = null

    fun start() {
        if (BuildConfig.FAKE_BILLING) return

        val c = BillingClient.newBuilder(activity)
            .setListener { result, purchases -> onPurchases(result, purchases) }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
        client = c

        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                ready = true
                loadDetails()
                refresh()
            }

            override fun onBillingServiceDisconnected() {
                ready = false
            }
        })
    }

    fun stop() {
        client?.endConnection()
        client = null
        ready = false
    }

    /** 스토어에 표시될 가격 (예: ₩3,300). 아직 모르면 null. */
    fun price(): String? =
        if (BuildConfig.FAKE_BILLING) null
        else details?.oneTimePurchaseOfferDetails?.formattedPrice

    /** 구매 내역을 다시 읽어 무제한 여부를 맞춘다. '구매 복원' 버튼도 이것을 쓴다. */
    fun refresh(done: (() -> Unit)? = null) {
        val c = client
        if (BuildConfig.FAKE_BILLING || c == null || !ready) {
            done?.invoke()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        c.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.filter { it.isUnlimited() }
                owned.forEach { acknowledge(it) }
                setUnlimited(owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED })
            }
            activity.runOnUiThread { done?.invoke() }
        }
    }

    fun buy(done: (Outcome) -> Unit) {
        if (BuildConfig.FAKE_BILLING) {
            fakeBuy(done)
            return
        }
        val c = client
        val d = details
        if (c == null || !ready || d == null) {
            done(Outcome.Failed(activity.getString(R.string.billing_unavailable)))
            // 다음 시도에 쓸 수 있도록 다시 불러둔다.
            if (ready) loadDetails()
            return
        }
        pendingBuy = done
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d)
                        .build()
                )
            )
            .build()
        val result = c.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingBuy = null
            done(failure(result))
        }
    }

    private fun loadDetails() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_UNLIMITED_SHARE)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        c.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = found.productDetailsList.firstOrNull()
                activity.runOnUiThread(onChanged)
            }
        }
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val mine = purchases.orEmpty().filter { it.isUnlimited() }
                when {
                    mine.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> {
                        mine.forEach { acknowledge(it) }
                        setUnlimited(true)
                        Outcome.Bought
                    }
                    mine.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                        Outcome.Pending
                    else -> Outcome.Cancelled
                }
            }

            // 다른 기기에서 이미 산 경우. 구매 내역을 다시 읽어 그대로 적용한다.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                setUnlimited(true)
                refresh()
                Outcome.Bought
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Outcome.Cancelled
            else -> failure(result)
        }

        val done = pendingBuy
        pendingBuy = null
        activity.runOnUiThread { done?.invoke(outcome) }
    }

    /** 3일 안에 확인(acknowledge)하지 않으면 Play 가 자동 환불하므로 반드시 확인한다. */
    private fun acknowledge(purchase: Purchase) {
        val c = client ?: return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        c.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { }
    }

    private fun setUnlimited(on: Boolean) {
        if (Prefs.loadUnlimited(activity) == on) return
        Prefs.saveUnlimited(activity, on)
        activity.runOnUiThread(onChanged)
    }

    private fun Purchase.isUnlimited() = products.contains(PRODUCT_UNLIMITED_SHARE)

    private fun failure(result: BillingResult) =
        Outcome.Failed(result.debugMessage.ifEmpty { "code ${result.responseCode}" })

    private fun fakeBuy(done: (Outcome) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.fake_billing_title)
            .setMessage(R.string.fake_billing_message)
            .setPositiveButton(R.string.fake_billing_ok) { _, _ ->
                Prefs.saveUnlimited(activity, true)
                onChanged()
                done(Outcome.Bought)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> done(Outcome.Cancelled) }
            .setOnCancelListener { done(Outcome.Cancelled) }
            .show()
    }
}
