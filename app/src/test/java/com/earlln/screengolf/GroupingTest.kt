package com.earlln.screengolf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.random.Random

class GroupingTest {

    private fun members(total: Int, slow: Int) =
        List(total) { Member(name = "p$it", slow = it < slow, playing = true) }

    private fun ok(result: Grouping.Result) = (result as Grouping.Result.Ok).rooms

    @Test
    fun `방 인원은 최대한 고르게 나뉜다`() {
        assertEquals(listOf(4, 4, 3), Grouping.roomSizes(11, 3))
        assertEquals(listOf(4, 4, 4), Grouping.roomSizes(12, 3))
        assertEquals(listOf(3, 3, 3), Grouping.roomSizes(9, 3))
        assertEquals(listOf(2, 2), Grouping.roomSizes(4, 2))
    }

    @Test
    fun `인원이 많은 조가 가운데 방에 온다`() {
        assertEquals(listOf(3, 4, 3), Grouping.roomSizes(10, 3))
        assertEquals(listOf(3, 4, 4, 3), Grouping.roomSizes(14, 4))
        assertEquals(listOf(3, 4, 4, 4, 3), Grouping.roomSizes(18, 5))
        assertEquals(listOf(2, 3, 2), Grouping.roomSizes(7, 3))

        for (rooms in 3..Grouping.MAX_ROOMS) {
            for (total in rooms * Grouping.MIN_PER_ROOM..rooms * Grouping.MAX_PER_ROOM) {
                val sizes = Grouping.roomSizes(total, rooms)
                val where = "rooms=$rooms total=$total sizes=$sizes"

                // 가운데 방이 양 끝 방보다 작지 않다.
                assertTrue(where, sizes[rooms / 2] >= sizes.first())
                assertTrue(where, sizes[rooms / 2] >= sizes.last())

                // 바깥에서 가운데로 갈수록 커지고, 가운데를 지나면 다시 작아진다.
                for (i in 0 until (rooms - 1) / 2) assertTrue(where, sizes[i] <= sizes[i + 1])
                for (i in (rooms - 1) / 2 until rooms - 1) assertTrue(where, sizes[i] >= sizes[i + 1])
            }
        }
    }

    @Test
    fun `인원이 모자라거나 넘치면 안내 메시지를 준다`() {
        assertTrue(Grouping.group(members(5, 0), 3) is Grouping.Result.Error)
        assertTrue(Grouping.group(members(16, 0), 3) is Grouping.Result.Error)
        assertTrue(Grouping.group(members(6, 0), 3) is Grouping.Result.Ok)
        assertTrue(Grouping.group(members(12, 0), 3) is Grouping.Result.Ok)
    }

    @Test
    fun `모든 방은 2명 이상 5명 이하이고 전원이 정확히 한 번씩 배정된다`() {
        val random = Random(42)
        for (rooms in Grouping.MIN_ROOMS..Grouping.MAX_ROOMS) {
            for (total in rooms * Grouping.MIN_PER_ROOM..rooms * Grouping.MAX_PER_ROOM) {
                for (slow in 0..total) {
                    val people = members(total, slow)
                    val result = ok(Grouping.group(people, rooms, random))

                    assertEquals(rooms, result.size)
                    assertTrue(result.all { it.size in Grouping.MIN_PER_ROOM..Grouping.MAX_PER_ROOM })
                    assertEquals(
                        people.map { it.name }.sorted(),
                        result.flatten().map { it.name }.sorted()
                    )
                    assertEquals(
                        Grouping.roomSizes(total, rooms).sorted(),
                        result.map { it.size }.sorted()
                    )
                }
            }
        }
    }

    @Test
    fun `2인 방이 있으면 슬로우 골퍼로 먼저 채운다`() {
        val random = Random(11)
        for (rooms in Grouping.MIN_ROOMS..Grouping.MAX_ROOMS) {
            for (total in rooms * Grouping.MIN_PER_ROOM..rooms * Grouping.MAX_PER_ROOM) {
                for (slow in 0..total) {
                    val sizes = Grouping.roomSizes(total, rooms)
                    val twoSeaters = sizes.count { it == Grouping.MIN_PER_ROOM }
                    if (twoSeaters == 0) continue

                    val result = ok(Grouping.group(members(total, slow), rooms, random))
                    val inTwoSeaters = result
                        .filter { it.size == Grouping.MIN_PER_ROOM }
                        .sumOf { room -> room.count { it.slow } }

                    assertEquals(
                        "rooms=$rooms total=$total slow=$slow",
                        minOf(slow, twoSeaters * Grouping.MIN_PER_ROOM).toLong(),
                        inTwoSeaters.toLong()
                    )
                }
            }
        }
    }

    @Test
    fun `3인 이상 방끼리는 슬로우 골퍼가 최대한 흩어진다`() {
        val random = Random(7)
        for (rooms in Grouping.MIN_ROOMS..Grouping.MAX_ROOMS) {
            for (total in rooms * Grouping.MIN_PER_ROOM..rooms * Grouping.MAX_PER_ROOM) {
                for (slow in 0..total) {
                    val sizes = Grouping.roomSizes(total, rooms)
                    val twoSeaters = sizes.count { it == Grouping.MIN_PER_ROOM }
                    val bigRooms = rooms - twoSeaters
                    if (bigRooms == 0) continue

                    val result = ok(Grouping.group(members(total, slow), rooms, random))
                    // 2인 방이 먼저 흡수하고 남은 슬로우 골퍼만 큰 방으로 퍼진다.
                    val spilled = slow - minOf(slow, twoSeaters * Grouping.MIN_PER_ROOM)
                    val worst = result
                        .filter { it.size > Grouping.MIN_PER_ROOM }
                        .maxOf { room -> room.count { it.slow } }

                    assertTrue(
                        "rooms=$rooms total=$total slow=$slow worst=$worst",
                        worst <= ceil(spilled.toDouble() / bigRooms).toInt()
                    )
                }
            }
        }
    }

    @Test
    fun `3-3-2 로 나뉘면 2명 방에 슬로우 두 명을 몰아넣는다`() {
        // 8명 3방 -> 3 / 3 / 2. 슬로우 2명이면 둘 다 2명 방으로 간다.
        val random = Random(5)
        repeat(200) {
            val result = ok(Grouping.group(members(8, 2), 3, random))
            val twoSeater = result.first { it.size == 2 }
            assertEquals(2, twoSeater.count { it.slow })
            assertTrue(result.filter { it.size == 3 }.all { room -> room.none { it.slow } })
        }
    }

    @Test
    fun `음식 표시는 조편성에 영향을 주지 않고 그대로 따라간다`() {
        // 음식은 몇 개 시킬지 세기 위한 표시일 뿐이라, 슬로우 규칙만 지켜지면 된다.
        val random = Random(11)
        val people = List(8) {
            Member(name = "p$it", slow = it < 2, playing = true, food = it % 2 == 0)
        }
        repeat(200) {
            val result = ok(Grouping.group(people, 3, random))

            // 슬로우 규칙(3/3/2 이면 2명 방에 슬로우 두 명)은 그대로다.
            assertEquals(2, result.first { it.size == 2 }.count { it.slow })

            // 음식 표시는 사람을 따라다니고, 전체 개수도 그대로다.
            val flat = result.flatten()
            assertEquals(4, flat.count { it.food })
            assertEquals(
                people.filter { it.food }.map { it.name }.toSet(),
                flat.filter { it.food }.map { it.name }.toSet()
            )
        }
    }

    @Test
    fun `게임 참여 표시도 조편성에 영향을 주지 않고 그대로 따라간다`() {
        // 게임 참여는 누가 끼는지 세기 위한 표시일 뿐이다. 조편성 규칙은 건드리지 않는다.
        val random = Random(41)
        val people = List(8) {
            Member(
                name = "p$it",
                slow = it < 2,
                playing = true,
                food = it % 2 == 0,
                game = it % 3 == 0
            )
        }
        repeat(200) {
            val result = ok(Grouping.group(people, 3, random))

            // 슬로우 규칙(3/3/2 이면 2명 방에 슬로우 두 명)은 그대로다.
            assertEquals(2, result.first { it.size == 2 }.count { it.slow })

            // 게임 / 음식 표시는 사람을 따라다니고 전체 수도 그대로다.
            val flat = result.flatten()
            assertEquals(people.count { it.game }, flat.count { it.game })
            assertEquals(
                people.filter { it.game }.map { it.name }.toSet(),
                flat.filter { it.game }.map { it.name }.toSet()
            )
            assertEquals(people.count { it.food }, flat.count { it.food })
        }
    }

    @Test
    fun `같은 조 조건은 반드시 한 방에 모인다`() {
        val random = Random(21)
        val people = members(11, 2)
        val pairs = listOf(
            MemberPair("p0", "p5", together = true),
            MemberPair("p7", "p8", together = true)
        )
        repeat(50) {
            val result = ok(Grouping.group(people, 3, random, pairs))
            assertEquals(listOf(4, 4, 3).sorted(), result.map { it.size }.sorted())
            pairs.forEach { pair ->
                val room = result.indexOfFirst { room -> room.any { it.name == pair.a } }
                assertTrue(
                    "${pair.label} 가 흩어졌다",
                    result[room].any { it.name == pair.b }
                )
            }
        }
    }

    @Test
    fun `다른 조 조건은 반드시 흩어진다`() {
        val random = Random(22)
        val people = members(12, 3)
        val pairs = listOf(
            MemberPair("p0", "p1", together = false),
            MemberPair("p1", "p2", together = false),
            MemberPair("p3", "p4", together = false)
        )
        repeat(50) {
            val result = ok(Grouping.group(people, 3, random, pairs))
            pairs.forEach { pair ->
                val a = result.indexOfFirst { room -> room.any { it.name == pair.a } }
                val b = result.indexOfFirst { room -> room.any { it.name == pair.b } }
                assertTrue("${pair.label} 가 같은 방에 있다", a != b)
            }
        }
    }

    @Test
    fun `조건이 섞여 있어도 전원이 정확히 한 번씩 배정된다`() {
        val random = Random(23)
        val people = members(10, 4)
        val pairs = listOf(
            MemberPair("p0", "p9", together = true),
            MemberPair("p1", "p2", together = false)
        )
        repeat(50) {
            val result = ok(Grouping.group(people, 3, random, pairs))
            assertEquals(
                people.map { it.name }.sorted(),
                result.flatten().map { it.name }.sorted()
            )
            assertTrue(result.all { it.size in Grouping.MIN_PER_ROOM..Grouping.MAX_PER_ROOM })
        }
    }

    @Test
    fun `지킬 수 없는 조건은 안내 메시지를 준다`() {
        val random = Random(24)

        // 같은 조로 6명을 묶으면 한 방(최대 5명)에 못 들어간다.
        val tooBig = listOf(
            MemberPair("p0", "p1", together = true),
            MemberPair("p1", "p2", together = true),
            MemberPair("p2", "p3", together = true),
            MemberPair("p3", "p4", together = true),
            MemberPair("p4", "p5", together = true)
        )
        assertTrue(Grouping.group(members(12, 0), 3, random, tooBig) is Grouping.Result.Error)

        // 같은 조이면서 다른 조일 수는 없다.
        val contradiction = listOf(
            MemberPair("p0", "p1", together = true),
            MemberPair("p0", "p1", together = false)
        )
        assertTrue(
            Grouping.group(members(12, 0), 3, random, contradiction) is Grouping.Result.Error
        )

        // 세 명이 서로 다른 조여야 하는데 방이 둘뿐이면 불가능하다.
        val threeApart = listOf(
            MemberPair("p0", "p1", together = false),
            MemberPair("p1", "p2", together = false),
            MemberPair("p0", "p2", together = false)
        )
        assertTrue(Grouping.group(members(8, 0), 2, random, threeApart) is Grouping.Result.Error)
    }

    @Test
    fun `조건이 걸려도 2인 방은 슬로우 골퍼로 채운다`() {
        // 8명 3방 -> 3 / 3 / 2. 슬로우 2명(p0, p1)과 무관한 같은 조 조건 하나.
        val random = Random(25)
        val pairs = listOf(MemberPair("p2", "p3", together = true))
        repeat(100) {
            val result = ok(Grouping.group(members(8, 2), 3, random, pairs))
            val twoSeater = result.first { it.size == 2 }
            assertEquals(2, twoSeater.count { it.slow })
        }
    }

    @Test
    fun `슬로우 분산 우선이면 조건보다 슬로우 규칙을 먼저 지킨다`() {
        // 8명 3방 -> 3 / 3 / 2. 슬로우는 p0, p1.
        // p0(슬로우)와 p2(일반)를 같은 조로 묶으면 2인 방에 슬로우 둘을 넣을 수 없다.
        val random = Random(31)
        val pairs = listOf(MemberPair("p0", "p2", together = true))

        repeat(100) {
            val result = Grouping.group(
                members(8, 2), 3, random, pairs, Grouping.Priority.SLOW
            ) as Grouping.Result.Ok

            // 슬로우 규칙은 깨지지 않는다.
            val twoSeater = result.rooms.first { it.size == 2 }
            assertEquals(2, twoSeater.count { it.slow })

            // 대신 그 조건은 못 지켰다고 알려준다.
            assertEquals(1, result.unmetPairs.size)
        }
    }

    @Test
    fun `선호도 우선이면 같은 조건에서 조건을 지킨다`() {
        // 같은 입력을 선호도 우선으로 돌리면 조건이 지켜지고, 대신 2인 방 슬로우가 줄어든다.
        val random = Random(32)
        val pairs = listOf(MemberPair("p0", "p2", together = true))

        repeat(50) {
            val result = Grouping.group(
                members(8, 2), 3, random, pairs, Grouping.Priority.PREFERENCE
            ) as Grouping.Result.Ok

            val roomOf = { name: String ->
                result.rooms.indexOfFirst { room -> room.any { it.name == name } }
            }
            assertEquals(roomOf("p0"), roomOf("p2"))
            assertTrue(result.unmetPairs.isEmpty())
        }
    }

    @Test
    fun `슬로우 분산 우선이면 지킬 수 없는 조건도 편성은 된다`() {
        // 같은 조 6명은 선호도 우선에서는 안내하고 멈추지만, 슬로우 우선에서는
        // 지킬 수 있는 만큼만 맞추고 편성을 끝낸다.
        val random = Random(33)
        val tooBig = listOf(
            MemberPair("p0", "p1", together = true),
            MemberPair("p1", "p2", together = true),
            MemberPair("p2", "p3", together = true),
            MemberPair("p3", "p4", together = true),
            MemberPair("p4", "p5", together = true)
        )

        assertTrue(
            Grouping.group(members(12, 0), 3, random, tooBig, Grouping.Priority.PREFERENCE)
                is Grouping.Result.Error
        )

        val result = Grouping.group(
            members(12, 0), 3, random, tooBig, Grouping.Priority.SLOW
        ) as Grouping.Result.Ok
        assertEquals(
            members(12, 0).map { it.name }.sorted(),
            result.rooms.flatten().map { it.name }.sorted()
        )
        assertTrue(result.rooms.all { it.size in Grouping.MIN_PER_ROOM..Grouping.MAX_PER_ROOM })
    }

    @Test
    fun `슬로우 분산 우선은 슬로우 점수를 절대 떨어뜨리지 않는다`() {
        val random = Random(34)
        for (rooms in 2..Grouping.MAX_ROOMS) {
            for (total in rooms * Grouping.MIN_PER_ROOM..rooms * Grouping.MAX_PER_ROOM) {
                for (slow in 0..minOf(total, 6)) {
                    val people = members(total, slow)
                    val pairs = listOf(
                        MemberPair("p0", "p${total - 1}", together = true),
                        MemberPair("p0", "p1", together = false)
                    ).filter { it.a != it.b }

                    val plain = ok(Grouping.group(people, rooms, random)) 
                    val withPairs = (
                        Grouping.group(people, rooms, random, pairs, Grouping.Priority.SLOW)
                            as Grouping.Result.Ok
                        ).rooms

                    // 2인 방에 들어간 슬로우 수는 조건이 있어도 줄어들지 않는다.
                    val twoSeats = { out: List<List<Member>> ->
                        out.filter { it.size == Grouping.MIN_PER_ROOM }
                            .sumOf { room -> room.count { it.slow } }
                    }
                    assertTrue(
                        "rooms=$rooms total=$total slow=$slow",
                        twoSeats(withPairs) >= twoSeats(plain)
                    )
                }
            }
        }
    }

    @Test
    fun `참가자와 무관한 조건은 무시한다`() {
        // 조건에 적힌 사람이 오늘 안 왔으면 조건이 없는 것과 같아야 한다.
        val random = Random(26)
        val pairs = listOf(MemberPair("없는사람A", "없는사람B", together = false))
        val result = ok(Grouping.group(members(11, 4), 3, random, pairs))

        val doubled = result.filter { room -> room.count { it.slow } >= 2 }
        assertEquals(1, doubled.size)
        assertEquals(3, doubled[0].size)
    }

    @Test
    fun `겹칠 수밖에 없을 때는 인원이 적은 방에서 겹친다`() {
        // 11명 3방 -> 4 4 3, 슬로우 4명이면 한 방에는 두 명이 들어갈 수밖에 없다.
        val random = Random(3)
        repeat(200) {
            val result = ok(Grouping.group(members(11, 4), 3, random))
            val doubled = result.filter { room -> room.count { it.slow } >= 2 }
            assertEquals(1, doubled.size)
            assertEquals(3, doubled[0].size)
        }
    }
}
