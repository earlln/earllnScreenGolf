package com.earlln.screengolf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 회원 명단을 JSON 파일 하나로 관리한다.
 *
 * 앱 내부 저장소의 [FILE_NAME] 이 유일한 원본이고, 내보내기/가져오기도 같은 형식을 쓴다.
 * 그래서 내보낸 파일을 다른 폰에서 가져오면 슬로우·음식 표시와 핸디캡까지 그대로 복원된다.
 *
 * 형식:
 * ```
 * {
 *   "app": "EarllnScreenGolf",
 *   "version": 2,
 *   "savedAt": "2026-08-25 09:10",
 *   "members": [
 *     { "name": "김프로", "slow": false, "handicap": 12, "playing": true, "food": true }
 *   ]
 * }
 * ```
 */
object MemberStore {

    const val FILE_NAME = "members.json"

    /** 쓰다가 죽어도 원본이 상하지 않도록 임시 파일에 쓰고 바꿔 끼운다. */
    private const val TEMP_NAME = "members.json.tmp"

    /** 직전까지 정상이던 사본. 본 파일이 깨졌을 때 여기서 되살린다. */
    private const val BACKUP_NAME = "members.json.bak"

    /**
     * 2 부터 "food", 3 부터 "pairs"(같은 조 / 다른 조 조건),
     * 4 부터 "game"(게임 참여) 이 들어간다.
     * 예전 버전으로 저장된 파일도 그대로 읽힌다(없는 항목은 꺼진 상태 / 빈 목록).
     */
    private const val FORMAT_VERSION = 4

    /** 명단과 조건을 한 묶음으로 다룬다. 파일 하나에 둘 다 들어 있기 때문이다. */
    data class Roster(
        val members: List<Member> = emptyList(),
        val pairs: List<MemberPair> = emptyList()
    )

    fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    private fun temp(ctx: Context) = File(ctx.filesDir, TEMP_NAME)

    private fun backup(ctx: Context) = File(ctx.filesDir, BACKUP_NAME)

    fun loadRoster(ctx: Context): Roster {
        val f = file(ctx)

        // 예전 버전(SharedPreferences)에 저장해 둔 명단이 있으면 파일로 한 번 옮긴다.
        if (!f.exists()) {
            val legacy = Prefs.loadLegacyMembers(ctx)
            if (legacy.isNotEmpty()) {
                val roster = Roster(legacy, emptyList())
                saveRoster(ctx, roster)
                Prefs.clearLegacyMembers(ctx)
                return roster
            }
            return Roster()
        }

        // 본 파일이 깨져 있으면 사본으로 되살린다. 빈 명단으로 조용히 덮어쓰면
        // 다음 저장 때 그 빈 상태가 그대로 굳어버리기 때문이다.
        val fromFile = runCatching { f.readText() }.getOrNull()?.let { parseRoster(it) }
        if (fromFile != null) return fromFile

        val fromBackup = runCatching { backup(ctx).takeIf { it.exists() }?.readText() }
            .getOrNull()
            ?.let { parseRoster(it) }
        return fromBackup ?: Roster()
    }

    /**
     * 임시 파일에 먼저 쓰고 이름을 바꿔 끼운다.
     * 쓰는 중에 앱이 죽어도 본 파일은 직전 상태로 남는다.
     *
     * 이미 저장된 명단이 있는데 빈 명단을 쓰려고 하면 막는다.
     * 어딘가에서 잘못 읽어온 빈 목록이 기존 명단을 날리는 일을 방지한다.
     */
    fun saveRoster(ctx: Context, roster: Roster) {
        val cleaned = Roster(roster.members, prune(roster.members, roster.pairs))

        if (cleaned.members.isEmpty() && file(ctx).exists()) {
            val stored = loadRoster(ctx)
            if (stored.members.isNotEmpty()) return
        }

        runCatching {
            val target = file(ctx)
            val tmp = temp(ctx)
            tmp.writeText(toJson(cleaned))

            // 바꿔 끼우기 전에 지금 파일을 사본으로 남긴다.
            if (target.exists()) {
                backup(ctx).delete()
                target.copyTo(backup(ctx), overwrite = true)
            }
            if (!tmp.renameTo(target)) {
                // 이름 바꾸기가 안 되는 환경이면 직접 쓴다.
                target.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    /**
     * 파일을 다시 읽어 [name] 한 사람만 고쳐 쓴다.
     *
     * 화면이 들고 있던 목록을 통째로 덮어쓰면, 그 목록이 만들어진 뒤 다른 화면에서
     * 바뀐 내용이 사라진다. 체크 하나를 바꿀 때는 이 함수를 쓴다.
     */
    fun updateMember(ctx: Context, name: String, change: (Member) -> Member): List<Member> {
        val roster = loadRoster(ctx)
        val at = roster.members.indexOfFirst { it.name == name }
        if (at < 0) return roster.members

        val members = roster.members.toMutableList()
        members[at] = change(members[at])
        saveRoster(ctx, Roster(members, roster.pairs))
        return members
    }

    fun load(ctx: Context): MutableList<Member> = loadRoster(ctx).members.toMutableList()

    fun loadPairs(ctx: Context): MutableList<MemberPair> = loadRoster(ctx).pairs.toMutableList()

    /** 명단만 저장한다. 같은 파일에 들어 있는 조건은 그대로 둔다. */
    fun save(ctx: Context, members: List<Member>) {
        saveRoster(ctx, Roster(members, loadRoster(ctx).pairs))
    }

    /** 조건만 저장한다. 명단은 그대로 둔다. */
    fun savePairs(ctx: Context, pairs: List<MemberPair>) {
        saveRoster(ctx, Roster(loadRoster(ctx).members, pairs))
    }

    /** 명단에 없는 사람을 가리키는 조건은 버린다. 같은 짝은 하나만 남긴다. */
    fun prune(members: List<Member>, pairs: List<MemberPair>): List<MemberPair> {
        val names = members.map { it.name }.toSet()
        val seen = mutableSetOf<String>()
        return pairs.filter {
            it.a != it.b &&
                it.a in names && it.b in names &&
                seen.add(it.key)
        }
    }

    /** 사람이 읽을 수 있게 들여쓴 JSON. 내보내기 파일도 같은 내용이다. */
    fun toJson(roster: Roster): String {
        val members = roster.members
        val array = JSONArray()
        members.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("slow", it.slow)
                    .put("handicap", it.handicap)
                    .put("playing", it.playing)
                    .put("food", it.food)
                    .put("game", it.game)
            )
        }
        val pairArray = JSONArray()
        prune(members, roster.pairs).forEach {
            pairArray.put(
                JSONObject()
                    .put("a", it.a)
                    .put("b", it.b)
                    .put("together", it.together)
            )
        }

        return JSONObject()
            .put("app", "EarllnScreenGolf")
            .put("version", FORMAT_VERSION)
            .put("savedAt", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date()))
            .put("members", array)
            .put("pairs", pairArray)
            .toString(2)
    }

    /**
     * 위 형식 또는 회원 배열만 들어 있는 JSON 을 읽는다.
     * 형식이 아니면 null (가져오기 실패를 구분하기 위해 빈 목록과 다르게 돌려준다).
     */
    /** 명단과 조건을 함께 읽는다. 형식이 아니면 null. */
    fun parseRoster(text: String): Roster? {
        val members = parse(text) ?: return null

        val pairs = runCatching {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) return@runCatching emptyList()

            val array = JSONObject(trimmed).optJSONArray("pairs")
                ?: return@runCatching emptyList()

            val list = mutableListOf<MemberPair>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val a = o.optString("a").trim()
                val b = o.optString("b").trim()
                if (a.isEmpty() || b.isEmpty() || a == b) continue
                list.add(MemberPair(a, b, o.optBoolean("together", false)))
            }
            list.toList()
        }.getOrNull() ?: emptyList()

        return Roster(members, prune(members, pairs))
    }

    fun parse(text: String): List<Member>? = runCatching {
        val trimmed = text.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("members") ?: return@runCatching null
        }

        val list = mutableListOf<Member>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            list.add(
                Member(
                    name = name,
                    slow = o.optBoolean("slow", false),
                    playing = o.optBoolean("playing", false),
                    handicap = o.optInt("handicap", 0)
                        .coerceIn(Member.MIN_HANDICAP, Member.MAX_HANDICAP),
                    // GolfZo 에서 내보낸 파일은 "kimbap" 으로 들어 있다.
                    food = o.optBoolean("food", o.optBoolean("kimbap", false)),
                    game = o.optBoolean("game", false)
                )
            )
        }
        list
    }.getOrNull()

    /** 같은 짝은 가져온 쪽 조건으로 덮고, 없던 짝은 뒤에 붙인다. */
    fun mergePairs(current: List<MemberPair>, incoming: List<MemberPair>): MutableList<MemberPair> {
        val merged = current.toMutableList()
        incoming.forEach { new ->
            val at = merged.indexOfFirst { it.key == new.key }
            if (at >= 0) merged[at] = new else merged.add(new)
        }
        return merged
    }

    /** 이름이 같으면 가져온 쪽 값으로 덮고, 없던 사람은 뒤에 붙인다. */
    fun merge(current: List<Member>, incoming: List<Member>): MutableList<Member> {
        val merged = current.toMutableList()
        incoming.forEach { new ->
            val at = merged.indexOfFirst { it.name == new.name }
            if (at >= 0) {
                merged[at] = merged[at].copy(
                    slow = new.slow,
                    handicap = new.handicap,
                    food = new.food,
                    game = new.game
                )
            } else {
                merged.add(new)
            }
        }
        return merged
    }

    fun exportFileName(): String =
        "EarllnScreenGolf_회원명단_" + SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date()) + ".json"
}
