package com.earlln.screengolf

/**
 * 회원 한 명.
 *
 * [slow] 가 true 면 슬로우 골퍼로 표시되고 조편성 때 서로 흩어놓는다.
 * [playing] 은 "오늘 참가" 체크 상태로, 앱을 껐다 켜도 유지된다.
 * [handicap] 은 부호 있는 값이라 마이너스 핸디도 넣을 수 있다. 0 은 표시하지 않는다.
 * [food] 은 음식을 먹는 사람 표시. 조편성과는 무관하고 몇 개를 시킬지 세는 데만 쓴다.
 * [game] 은 그날 게임에 참여하는 사람 표시. 음식처럼 조편성에는 영향을 주지 않는다.
 */
data class Member(
    val name: String,
    val slow: Boolean = false,
    val playing: Boolean = false,
    val handicap: Int = 0,
    val food: Boolean = false,
    val game: Boolean = false
) {
    companion object {
        const val MIN_HANDICAP = -30
        const val MAX_HANDICAP = 54

        /** 화면과 결과 이미지에 쓰는 표기. 0(아직 안 정함)이면 빈 문자열. */
        fun label(handicap: Int): String = when {
            handicap > 0 -> "+$handicap"
            handicap < 0 -> "$handicap"
            else -> ""
        }
    }

    val handicapLabel: String get() = label(handicap)
}
