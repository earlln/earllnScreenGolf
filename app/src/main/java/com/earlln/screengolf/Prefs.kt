package com.earlln.screengolf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 방 선택, 우선순위, 음식, 공유 횟수 같은 앱 설정을 저장한다.
 *
 * 회원 명단은 [MemberStore] 가 JSON 파일로 관리한다. 여기 남은 회원 관련 함수는
 * 예전 버전에서 SharedPreferences 에 저장해 둔 명단을 파일로 옮기기 위한 것이다.
 */
object Prefs {

    private const val FILE = "screengolf"
    private const val KEY_MEMBERS = "members"
    private const val KEY_ROOMS = "rooms"
    private const val KEY_ROOM_NUMBERS = "roomNumbers"
    private const val KEY_SELECTED_ROOMS = "selectedRooms"
    private const val KEY_AUTO_ADJACENT = "autoAdjacent"
    private const val KEY_PRIORITY = "priority"
    private const val KEY_FOOD_NAME = "foodName"
    private const val KEY_FOOD_EMOJI = "foodEmoji"
    private const val KEY_FOOD_UNIT = "foodUnit"
    private const val KEY_SHARE_COUNT = "shareCount"
    private const val KEY_UNLIMITED = "unlimitedShare"

    const val DEFAULT_ROOMS = 3

    /** 방 번호는 3번부터 매긴다. 기본 선택은 3 / 4 / 5. */
    const val FIRST_ROOM_NUMBER = 3
    const val MIN_ROOM_NUMBER = 1

    /** 고를 수 있는 방 번호의 끝. 화면에는 1~15 가 격자로 깔린다. */
    const val MAX_ROOM_NUMBER = 15

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadLegacyMembers(ctx: Context): MutableList<Member> {
        val raw = prefs(ctx).getString(KEY_MEMBERS, null) ?: return mutableListOf()
        val list = mutableListOf<Member>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                list.add(
                    Member(
                        name = name,
                        slow = o.optBoolean("slow", false),
                        playing = o.optBoolean("playing", false),
                        handicap = o.optInt("handicap", 0)
                            .coerceIn(Member.MIN_HANDICAP, Member.MAX_HANDICAP)
                    )
                )
            }
        }
        return list
    }

    fun clearLegacyMembers(ctx: Context) {
        prefs(ctx).edit().remove(KEY_MEMBERS).apply()
    }

    fun loadRooms(ctx: Context): Int =
        prefs(ctx).getInt(KEY_ROOMS, DEFAULT_ROOMS).coerceIn(Grouping.MIN_ROOMS, Grouping.MAX_ROOMS)

    fun saveRooms(ctx: Context, rooms: Int) {
        prefs(ctx).edit().putInt(KEY_ROOMS, rooms).apply()
    }

    fun defaultRoomNumbers(count: Int): MutableList<Int> =
        MutableList(count) { FIRST_ROOM_NUMBER + it }

    /** 기본으로 고른 방: 3 / 4 / 5 */
    fun defaultSelectedRooms(): MutableList<Int> = defaultRoomNumbers(DEFAULT_ROOMS)

    /**
     * 고른 방 번호를 오름차순으로 돌려준다.
     * 예전 버전에서 쓰던 방 번호 목록이 있으면 그것을 이어받는다.
     */
    fun loadSelectedRooms(ctx: Context): MutableList<Int> {
        val raw = prefs(ctx).getString(KEY_SELECTED_ROOMS, null)
            ?: prefs(ctx).getString(KEY_ROOM_NUMBERS, null)
            ?: return defaultSelectedRooms()

        val list = raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in MIN_ROOM_NUMBER..MAX_ROOM_NUMBER }
            .distinct()
            .sorted()
            .take(Grouping.MAX_ROOMS)

        return if (list.isEmpty()) defaultSelectedRooms() else list.toMutableList()
    }

    fun saveSelectedRooms(ctx: Context, rooms: List<Int>) {
        prefs(ctx).edit().putString(KEY_SELECTED_ROOMS, rooms.sorted().joinToString(",")).apply()
    }

    /** 기본은 선호도 우선. "무조건 같은 조 / 다른 조" 라는 요청에 맞춘 값이다. */
    fun loadPriority(ctx: Context): Grouping.Priority =
        if (prefs(ctx).getString(KEY_PRIORITY, null) == "slow") {
            Grouping.Priority.SLOW
        } else {
            Grouping.Priority.PREFERENCE
        }

    fun savePriority(ctx: Context, priority: Grouping.Priority) {
        val value = if (priority == Grouping.Priority.SLOW) "slow" else "preference"
        prefs(ctx).edit().putString(KEY_PRIORITY, value).apply()
    }

    fun loadAutoAdjacent(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_ADJACENT, true)

    fun saveAutoAdjacent(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_ADJACENT, on).apply()
    }

    /** 설정에서 고른 음식. 처음엔 김밥. */
    fun loadFood(ctx: Context): Food {
        val p = prefs(ctx)
        val name = p.getString(KEY_FOOD_NAME, null)?.trim().orEmpty()
        if (name.isEmpty()) return Food.DEFAULT
        return Food(
            name = name,
            emoji = p.getString(KEY_FOOD_EMOJI, null)?.trim().orEmpty()
                .ifEmpty { Food.DEFAULT.emoji },
            unit = p.getString(KEY_FOOD_UNIT, null)?.trim().orEmpty()
                .ifEmpty { Food.DEFAULT.unit }
        )
    }

    fun saveFood(ctx: Context, food: Food) {
        prefs(ctx).edit()
            .putString(KEY_FOOD_NAME, food.name)
            .putString(KEY_FOOD_EMOJI, food.emoji)
            .putString(KEY_FOOD_UNIT, food.unit)
            .apply()
    }

    fun loadShareCount(ctx: Context): Int = prefs(ctx).getInt(KEY_SHARE_COUNT, 0)

    fun saveShareCount(ctx: Context, count: Int) {
        prefs(ctx).edit().putInt(KEY_SHARE_COUNT, count).apply()
    }

    /** 마지막으로 확인한 구매 상태. 스토어에 연결되기 전에도 바로 쓰려고 남겨둔다. */
    fun loadUnlimited(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_UNLIMITED, false)

    fun saveUnlimited(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_UNLIMITED, on).apply()
    }

}
