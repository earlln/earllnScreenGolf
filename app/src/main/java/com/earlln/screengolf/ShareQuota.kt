package com.earlln.screengolf

import android.content.Context

/**
 * 결과 이미지 공유 횟수.
 *
 * [FREE_SHARES] 번까지는 무료, 그 뒤로는 '공유 무제한'(한 번 사는 인앱 상품)을 사야 공유할 수 있다.
 * 횟수는 이 폰에만 저장되므로 앱을 지웠다 깔면 다시 0 부터 센다.
 * 이미지 저장(갤러리)은 세지 않는다.
 */
object ShareQuota {

    const val FREE_SHARES = 5

    fun used(ctx: Context): Int = Prefs.loadShareCount(ctx)

    fun left(ctx: Context): Int = (FREE_SHARES - used(ctx)).coerceAtLeast(0)

    fun unlimited(ctx: Context): Boolean = Prefs.loadUnlimited(ctx)

    fun canShare(ctx: Context): Boolean = unlimited(ctx) || used(ctx) < FREE_SHARES

    /** 공유 창을 띄울 때 한 번 센다. 무제한이면 세지 않는다. */
    fun record(ctx: Context) {
        if (unlimited(ctx)) return
        Prefs.saveShareCount(ctx, (used(ctx) + 1).coerceAtMost(FREE_SHARES))
    }

    /** 테스트 빌드에서만 쓴다. */
    fun reset(ctx: Context) {
        Prefs.saveShareCount(ctx, 0)
    }
}
