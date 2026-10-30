package com.earlln.screengolf

/**
 * 모임 때 따로 세는 음식. 기본은 김밥이고 설정에서 다른 음식으로 바꿀 수 있다.
 *
 * [unit] 은 개수 뒤에 붙는 말이다 (김밥 3개, 커피 3잔).
 * 회원의 [Member.food] 표시는 음식이 바뀌어도 그대로 남는다.
 */
data class Food(
    val name: String,
    val emoji: String,
    val unit: String
) {
    /** "김밥 3개" */
    fun count(n: Int): String = "$name $n$unit"

    /** "🍙 3개" */
    fun markCount(n: Int): String = "$emoji $n$unit"

    companion object {
        val DEFAULT = Food("김밥", "🍙", "개")                 // 🍙

        val PRESETS = listOf(
            DEFAULT,
            Food("햄버거", "🍔", "개"),                         // 🍔
            Food("샌드위치", "🥪", "개"),                       // 🥪
            Food("도시락", "🍱", "개"),                         // 🍱
            Food("짜장면", "🍜", "그릇"),                       // 🍜
            Food("치킨", "🍗", "인분"),                         // 🍗
            Food("커피", "☕", "잔"),                                 // ☕
            Food("맥주", "🍺", "잔")                            // 🍺
        )

        const val MAX_NAME = 8
        const val MAX_UNIT = 3
    }
}
