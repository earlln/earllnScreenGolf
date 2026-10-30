package com.earlln.screengolf

import kotlin.math.abs
import kotlin.random.Random

/**
 * 조편성 로직.
 *
 * 규칙
 *  - 한 방에는 최소 2명, 최대 5명.
 *  - 방 인원은 최대한 고르게 나누되, 인원이 많은 조를 가운데 방에 둔다
 *    (예: 10명 3방 -> 3/4/3).
 *  - 2인 방이 생기면 그 방을 슬로우 골퍼로 먼저 채운다.
 *    두 명만 도는 조는 어차피 빠르므로 슬로우끼리 묶어도 뒤 팀이 밀리지 않는다.
 *    (예: 8명 3방 -> 3 / 3 / 2 이면 2명 방에 슬로우 두 명)
 *  - 그 밖의 슬로우 골퍼는 3인 이상 방에 한 명씩 흩어놓는다.
 *    슬로우 골퍼가 많아 어쩔 수 없이 겹칠 때는,
 *    인원이 한 명 적은(작은) 방부터 겹치게 해서 진행이 덜 밀리도록 한다.
 *  - [MemberPair] 로 "반드시 같은 조" / "반드시 다른 조" 조건을 걸 수 있다.
 *    조건이 걸린 경우에는 조건을 먼저 지키고, 그 안에서 위 슬로우 규칙을
 *    가장 잘 만족하는 배치를 고른다.
 */
object Grouping {

    const val MIN_ROOMS = 1
    const val MAX_ROOMS = 5
    const val MIN_PER_ROOM = 2
    const val MAX_PER_ROOM = 5

    /** 슬로우 분산과 선호도(같은 조 / 다른 조) 중 무엇을 먼저 지킬지. */
    enum class Priority {
        /** 선호도를 절대 조건으로 보고, 그 안에서 슬로우를 흩어놓는다. */
        PREFERENCE,

        /** 슬로우 규칙을 절대 지키고, 그것을 깨지 않는 선에서만 선호도를 맞춘다. */
        SLOW
    }

    sealed class Result {
        /** [unmetPairs] 는 지키지 못한 선호도 조건. 슬로우 우선일 때만 생길 수 있다. */
        data class Ok(
            val rooms: List<List<Member>>,
            val unmetPairs: List<MemberPair> = emptyList()
        ) : Result()

        data class Error(val message: String) : Result()
    }

    /**
     * 전체 인원을 방 수만큼 최대한 고르게 나눈 크기 목록.
     *
     * 인원이 많은 조를 가운데 방에 두고 적은 조를 바깥쪽으로 보낸다.
     * (예: 10명 3방 -> 3 / 4 / 3, 18명 5방 -> 3 / 4 / 4 / 4 / 3)
     */
    fun roomSizes(total: Int, rooms: Int): List<Int> {
        val base = total / rooms
        val rem = total % rooms
        val descending = List(rooms) { i -> if (i < rem) base + 1 else base }

        // 가운데에서 가까운 자리부터 큰 조를 채운다.
        val center = (rooms - 1) / 2.0
        val fromCenter = (0 until rooms).sortedWith(compareBy({ abs(it - center) }, { it }))

        val arranged = MutableList(rooms) { 0 }
        fromCenter.forEachIndexed { rank, position -> arranged[position] = descending[rank] }
        return arranged
    }

    /** 참가 인원 [total] 로 편성 가능한 방 수 목록. */
    fun possibleRooms(total: Int): List<Int> =
        (MIN_ROOMS..MAX_ROOMS).filter { total >= it * MIN_PER_ROOM && total <= it * MAX_PER_ROOM }

    /**
     * [pairs] 는 두 사람 사이의 같은 조 / 다른 조 조건.
     * 비어 있으면 지금까지와 똑같이 동작한다.
     */
    fun group(
        members: List<Member>,
        rooms: Int,
        random: Random = Random.Default,
        pairs: List<MemberPair> = emptyList(),
        priority: Priority = Priority.PREFERENCE
    ): Result {
        if (rooms !in MIN_ROOMS..MAX_ROOMS) {
            return Result.Error("방 수는 ${MIN_ROOMS}~${MAX_ROOMS} 중에서 선택하세요.")
        }
        val total = members.size
        if (total < rooms * MIN_PER_ROOM) {
            return Result.Error(
                "${rooms}방으로 나누려면 최소 ${rooms * MIN_PER_ROOM}명이 필요해요. " +
                    "지금은 ${total}명입니다.${hint(total)}"
            )
        }
        if (total > rooms * MAX_PER_ROOM) {
            return Result.Error(
                "한 방은 최대 ${MAX_PER_ROOM}명이라 ${rooms}방에는 ${rooms * MAX_PER_ROOM}명까지만 들어가요. " +
                    "지금은 ${total}명입니다.${hint(total)}"
            )
        }

        // 참가자끼리 걸린 조건만 본다. 하나라도 있으면 조건을 지키는 쪽으로 푼다.
        val names = members.map { it.name }.toSet()
        val live = pairs.filter { it.a in names && it.b in names && it.a != it.b }
        if (live.isNotEmpty()) {
            return when (priority) {
                Priority.PREFERENCE -> grouped(members, rooms, live, random)
                Priority.SLOW -> slowFirst(members, rooms, live, random)
            }
        }

        return Result.Ok(plain(members, rooms, random).map { it.toList() })
    }

    /** 조건을 보지 않고 슬로우 규칙만으로 짠다. 조건이 없을 때의 기본 경로. */
    private fun plain(
        members: List<Member>,
        rooms: Int,
        random: Random
    ): List<MutableList<Member>> {
        val sizes = roomSizes(members.size, rooms)
        val buckets = List(rooms) { mutableListOf<Member>() }

        val slow = members.filter { it.slow }.shuffled(random)
        val normal = members.filter { !it.slow }.shuffled(random)

        var placed = 0

        // 1) 2인 방이 있으면 슬로우 골퍼로 먼저 채운다.
        //    두 명만 도는 조는 어차피 진행이 빠르므로, 슬로우 골퍼끼리 묶어도 뒤 팀이 밀리지 않는다.
        //    (예: 8명 3방 -> 3 / 3 / 2 이면 2명 방에 슬로우 두 명을 몰아넣는다)
        for (i in buckets.indices.filter { sizes[it] == MIN_PER_ROOM }.shuffled(random)) {
            while (buckets[i].size < sizes[i] && placed < slow.size) {
                buckets[i].add(slow[placed])
                placed++
            }
        }

        // 2) 남은 슬로우 골퍼는 3인 이상 방에 한 명씩 흩어놓는다. (방 순서는 매번 섞는다)
        for (i in buckets.indices.filter { sizes[it] > MIN_PER_ROOM }.shuffled(random)) {
            if (placed >= slow.size) break
            if (buckets[i].size < sizes[i]) {
                buckets[i].add(slow[placed])
                placed++
            }
        }

        // 3) 그래도 남으면 (슬로우가 적은 방 → 인원이 적은 방) 순서로 겹쳐 넣는다.
        while (placed < slow.size) {
            val target = buckets.indices
                .filter { buckets[it].size < sizes[it] }
                .minByOrNull { i -> buckets[i].count { it.slow } * 100 + sizes[i] }
                ?: break
            buckets[target].add(slow[placed])
            placed++
        }

        // 4) 나머지 자리는 일반 참가자로 채운다.
        var n = 0
        for (i in buckets.indices) {
            while (buckets[i].size < sizes[i] && n < normal.size) {
                buckets[i].add(normal[n])
                n++
            }
        }

        // 5) 혹시 남은 사람이 있으면(방어 코드) 자리가 있는 방에 넣는다.
        val leftovers = (slow.drop(placed) + normal.drop(n))
        for (m in leftovers) {
            val target = buckets.indices.minByOrNull { buckets[it].size } ?: break
            buckets[target].add(m)
        }

        return buckets
    }

    // ──────────────────────────── 조건이 걸린 경우 ────────────────────────────

    private const val ATTEMPTS = 60
    private const val NODE_BUDGET = 6000

    /**
     * 같은 조 조건으로 묶인 사람들을 한 덩어리로 보고, 덩어리를 방에 넣는다.
     * 다른 조 조건을 깨지 않는 배치를 여러 번 찾아보고 슬로우 규칙 점수가
     * 가장 좋은 것을 고른다.
     */
    private fun grouped(
        members: List<Member>,
        rooms: Int,
        pairs: List<MemberPair>,
        random: Random
    ): Result {
        val index = members.withIndex().associate { (i, m) -> m.name to i }
        val sizes = roomSizes(members.size, rooms)

        // 1) 같은 조 조건으로 덩어리를 만든다.
        val parent = IntArray(members.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) {
                parent[r] = parent[parent[r]]
                r = parent[r]
            }
            return r
        }
        pairs.filter { it.together }.forEach { p ->
            val ra = find(index.getValue(p.a))
            val rb = find(index.getValue(p.b))
            if (ra != rb) parent[ra] = rb
        }
        val blocks = members.indices.groupBy { find(it) }.values.toList()

        val tooBig = blocks.firstOrNull { it.size > MAX_PER_ROOM }
        if (tooBig != null) {
            return Result.Error(
                "같은 조로 묶인 ${tooBig.size}명(" +
                    tooBig.joinToString(", ") { members[it].name } +
                    ")은 한 방 최대 ${MAX_PER_ROOM}명을 넘습니다. 조건을 줄여주세요."
            )
        }

        // 2) 다른 조 조건이 같은 덩어리 안에 있으면 서로 모순이다.
        val apart = HashSet<Long>()
        pairs.filterNot { it.together }.forEach { p ->
            val a = index.getValue(p.a)
            val b = index.getValue(p.b)
            apart.add(pairKey(a, b))
        }
        for (block in blocks) {
            for (i in block.indices) {
                for (j in i + 1 until block.size) {
                    if (apart.contains(pairKey(block[i], block[j]))) {
                        return Result.Error(
                            "${members[block[i]].name} 와 ${members[block[j]].name} 는 " +
                                "같은 조이면서 다른 조일 수 없습니다. 조건을 확인해주세요."
                        )
                    }
                }
            }
        }

        // 3) 큰 덩어리부터 넣어본다. 방 순서와 같은 크기끼리의 순서는 매번 섞는다.
        val slowOf = BooleanArray(members.size) { members[it].slow }
        val bySize = blocks.indices.sortedByDescending { blocks[it].size }
        var best: IntArray? = null
        var bestScore = Int.MAX_VALUE

        repeat(ATTEMPTS) {
            val order = shuffleWithinEqualSizes(bySize, blocks, random)
            val assigned = IntArray(blocks.size) { -1 }
            val used = IntArray(rooms)
            val inRoom = List(rooms) { mutableListOf<Int>() }
            val nodes = intArrayOf(0)

            if (place(0, order, blocks, sizes, apart, assigned, used, inRoom, nodes, random, slowOf)) {
                val candidate = slowScore(materialize(assigned, blocks, members, rooms), sizes)
                if (candidate < bestScore) {
                    bestScore = candidate
                    best = assigned.copyOf()
                }
            }
        }

        val answer = best ?: return Result.Error(
            "걸어 둔 조건을 모두 만족하는 편성을 찾지 못했어요. " +
                "조건을 줄이거나 방 수를 바꿔보세요."
        )

        return Result.Ok(materialize(answer, blocks, members, rooms).map { it.shuffled(random) })
    }

    private fun materialize(
        assigned: IntArray,
        blocks: List<List<Int>>,
        members: List<Member>,
        rooms: Int
    ): List<MutableList<Member>> {
        val buckets = List(rooms) { mutableListOf<Member>() }
        blocks.forEachIndexed { bi, block ->
            block.forEach { buckets[assigned[bi]].add(members[it]) }
        }
        return buckets
    }

    private fun pairKey(a: Int, b: Int): Long =
        minOf(a, b).toLong() * 1000L + maxOf(a, b).toLong()

    /** 큰 덩어리 순서는 지키고, 크기가 같은 것들끼리만 섞는다. */
    private fun shuffleWithinEqualSizes(
        bySize: List<Int>,
        blocks: List<List<Int>>,
        random: Random
    ): List<Int> {
        val out = mutableListOf<Int>()
        var i = 0
        while (i < bySize.size) {
            var j = i
            while (j < bySize.size && blocks[bySize[j]].size == blocks[bySize[i]].size) j++
            out.addAll(bySize.subList(i, j).shuffled(random))
            i = j
        }
        return out
    }

    private fun place(
        at: Int,
        order: List<Int>,
        blocks: List<List<Int>>,
        sizes: List<Int>,
        apart: Set<Long>,
        assigned: IntArray,
        used: IntArray,
        inRoom: List<MutableList<Int>>,
        nodes: IntArray,
        random: Random,
        slowOf: BooleanArray
    ): Boolean {
        if (nodes[0] > NODE_BUDGET) return false
        nodes[0]++
        if (at == order.size) return true

        val bi = order[at]
        val block = blocks[bi]
        val blockSlow = block.count { slowOf[it] }

        // 아무 방이나 넣고 운에 맡기면 슬로우 규칙을 놓치기 쉽다.
        // 슬로우가 든 덩어리는 2인 방부터(그 다음 슬로우가 적은 방),
        // 슬로우가 없는 덩어리는 2인 방을 비워두는 쪽부터 넣어본다.
        // 같은 우선순위끼리는 섞인 순서를 그대로 둬서 매번 다른 결과가 나온다.
        val roomOrder = sizes.indices.shuffled(random).sortedBy { room ->
            if (blockSlow > 0) {
                if (sizes[room] == MIN_PER_ROOM) 0
                else 1 + inRoom[room].count { slowOf[it] }
            } else {
                if (sizes[room] == MIN_PER_ROOM) 1 else 0
            }
        }

        for (room in roomOrder) {
            if (used[room] + block.size > sizes[room]) continue
            val clashes = block.any { m -> inRoom[room].any { apart.contains(pairKey(m, it)) } }
            if (clashes) continue

            used[room] += block.size
            inRoom[room].addAll(block)
            assigned[bi] = room

            if (place(
                    at + 1, order, blocks, sizes, apart,
                    assigned, used, inRoom, nodes, random, slowOf
                )
            ) {
                return true
            }

            used[room] -= block.size
            repeat(block.size) { inRoom[room].removeAt(inRoom[room].size - 1) }
            assigned[bi] = -1
        }
        return false
    }

    /**
     * 작을수록 좋은 점수. 조건 없을 때의 규칙을 그대로 옮긴 것이다.
     * 2인 방에 슬로우를 몰아넣는 것이 가장 중요하고, 그 다음이 3인 이상 방의
     * 고른 분산, 마지막이 "겹칠 땐 작은 방부터" 다.
     */
    private fun slowScore(buckets: List<List<Member>>, sizes: List<Int>): Int {
        var score = 0
        buckets.forEachIndexed { room, people ->
            val slow = people.count { it.slow }
            if (sizes[room] == MIN_PER_ROOM) {
                score -= 1000 * slow
            } else {
                score += 10 * slow * slow
                if (slow >= 2) score += sizes[room]
            }
        }
        return score
    }

    /** 지켜지지 않은 선호도 조건. */
    private fun unmet(buckets: List<List<Member>>, pairs: List<MemberPair>): List<MemberPair> {
        val roomOf = HashMap<String, Int>()
        buckets.forEachIndexed { room, people -> people.forEach { roomOf[it.name] = room } }
        return pairs.filter {
            val a = roomOf[it.a] ?: return@filter false
            val b = roomOf[it.b] ?: return@filter false
            if (it.together) a != b else a == b
        }
    }

    // ──────────────────────── 슬로우 분산을 먼저 지키는 경우 ────────────────────────

    private const val SWAP_PASSES = 12

    /**
     * 먼저 조건을 무시하고 슬로우 규칙대로 짠 다음,
     * 슬로우 점수를 떨어뜨리지 않는 교환만으로 선호도를 맞춰 간다.
     *
     * 그래서 슬로우 규칙은 절대 깨지지 않고, 선호도는 "가급적" 지켜진다.
     * 못 지킨 조건은 [Result.Ok.unmetPairs] 로 돌려준다.
     */
    private fun slowFirst(
        members: List<Member>,
        rooms: Int,
        pairs: List<MemberPair>,
        random: Random
    ): Result {
        val sizes = roomSizes(members.size, rooms)
        val buckets = plain(members, rooms, random)

        var score = slowScore(buckets, sizes)
        var left = unmet(buckets, pairs)

        val spots = buckets.indices
            .flatMap { room -> buckets[room].indices.map { room to it } }

        for (pass in 0 until SWAP_PASSES) {
            if (left.isEmpty()) break
            var improved = false

            val shuffled = spots.shuffled(random)
            for (i in shuffled.indices) {
                for (j in i + 1 until shuffled.size) {
                    val (r1, i1) = shuffled[i]
                    val (r2, i2) = shuffled[j]
                    if (r1 == r2) continue

                    swap(buckets, r1, i1, r2, i2)
                    val nextScore = slowScore(buckets, sizes)
                    val nextLeft = unmet(buckets, pairs)

                    // 슬로우 점수가 나빠지지 않으면서 못 지킨 조건이 줄어들 때만 받아들인다.
                    if (nextScore <= score && nextLeft.size < left.size) {
                        score = nextScore
                        left = nextLeft
                        improved = true
                    } else {
                        swap(buckets, r1, i1, r2, i2)
                    }
                }
            }
            if (!improved) break
        }

        return Result.Ok(buckets.map { it.toList() }, left)
    }

    private fun swap(
        buckets: List<MutableList<Member>>,
        r1: Int,
        i1: Int,
        r2: Int,
        i2: Int
    ) {
        val tmp = buckets[r1][i1]
        buckets[r1][i1] = buckets[r2][i2]
        buckets[r2][i2] = tmp
    }

    private fun hint(total: Int): String {
        val ok = possibleRooms(total)
        return if (ok.isEmpty()) {
            " (${MIN_PER_ROOM}~${MAX_ROOMS * MAX_PER_ROOM}명까지 편성할 수 있어요)"
        } else {
            " (${total}명이면 ${ok.joinToString("방, ")}방 가능)"
        }
    }
}
