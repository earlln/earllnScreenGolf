package com.earlln.screengolf

/**
 * 두 사람 사이의 조편성 조건.
 *
 * [together] 가 true 면 반드시 같은 조, false 면 반드시 다른 조다.
 * 사람은 이름으로 가리키므로 회원을 지우면 그 조건도 함께 버린다.
 */
data class MemberPair(
    val a: String,
    val b: String,
    val together: Boolean
) {
    /** 순서를 무시하고 같은 짝인지 보려고 쓰는 열쇠. */
    val key: String get() = listOf(a, b).sorted().joinToString("\u0000")

    fun has(name: String) = a == name || b == name

    val label: String get() = "$a ↔ $b"
}
