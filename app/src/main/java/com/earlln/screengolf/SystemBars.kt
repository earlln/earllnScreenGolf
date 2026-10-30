package com.earlln.screengolf

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15 부터는 targetSdk 35 이상 앱의 화면이 상태바·내비게이션바 밑까지 깔린다.
 * 화면을 하나하나 고치지 않고, 액션바까지 담긴 창의 최상위 뷰를 시스템 바와 키보드만큼 밀어 넣는다.
 * 밀려난 자리는 짙은 초록으로 칠해 예전 상태바 색과 맞춘다.
 *
 * setContentView 다음에 부른다.
 */
fun AppCompatActivity.fitSystemBars() {
    // 버전마다 다르게 굴지 않도록 예전 폰에서도 같은 방식(바 밑까지 깔고 직접 밀기)으로 맞춘다.
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val root = (window.decorView as ViewGroup).getChildAt(0) ?: return
    root.setBackgroundColor(getColor(R.color.green_deep))

    // 초록 바탕 위라 상태바 / 내비게이션바 아이콘은 밝게 둔다.
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }

    ViewCompat.setOnApplyWindowInsetsListener(root) { v: View, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
        // 아래쪽 뷰(액션바 등)가 같은 여백을 또 넣지 않도록 여기서 소비한다.
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(root)
}
