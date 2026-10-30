package com.earlln.screengolf

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.earlln.screengolf.databinding.ActivityMainBinding
import com.earlln.screengolf.databinding.ItemMemberBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var members = mutableListOf<Member>()
    /** 오늘 쓸 방 번호들. 오름차순으로 들고 있는다. */
    private var selectedRooms = Prefs.defaultSelectedRooms()

    /** 켜져 있으면 방을 누를 때 앞뒤 한 칸까지 같이 고른다(보통 연결된 방을 쓰므로). */
    private var autoAdjacent = true

    /** 설정에서 고른, 따로 세는 음식. */
    private var food = Food.DEFAULT

    private val rooms: Int get() = selectedRooms.size

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        fitSystemBars()

        // 어느 버전이 깔려 있는지 바로 알 수 있게 타이틀 옆에 표시한다.
        versionName()?.let { b.tvVersion.text = "v$it" }

        b.btnMembers.setOnClickListener {
            startActivity(Intent(this, MembersActivity::class.java))
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.btnSelectAll.setOnClickListener { setAllPlaying(true) }
        b.btnClearAll.setOnClickListener { setAllPlaying(false) }

        b.btnFoodClear.setOnClickListener { clearFood() }
        b.btnGameClear.setOnClickListener { clearGame() }

        b.btnResetRooms.setOnClickListener {
            selectedRooms = Prefs.defaultSelectedRooms()
            Prefs.saveSelectedRooms(this, selectedRooms)
            renderRooms()
            updateSummary()
        }

        b.btnGroup.setOnClickListener { makeGroups() }
    }

    override fun onResume() {
        super.onResume()
        members = MemberStore.load(this)
        food = Prefs.loadFood(this)
        selectedRooms = Prefs.loadSelectedRooms(this)
        autoAdjacent = Prefs.loadAutoAdjacent(this)

        b.swAutoAdjacent.setOnCheckedChangeListener(null)
        b.swAutoAdjacent.isChecked = autoAdjacent
        b.swAutoAdjacent.setOnCheckedChangeListener { _, checked ->
            autoAdjacent = checked
            Prefs.saveAutoAdjacent(this, checked)
        }

        renderRooms()
        renderMembers()
        updateSummary()
        updateFood()
        updateGame()
    }

    private fun setAllPlaying(playing: Boolean) {
        members = members.map { it.copy(playing = playing) }.toMutableList()
        MemberStore.save(this, members)
        renderMembers()
        updateSummary()
        updateFood()
        updateGame()
    }

    /** 게임 참여 표시를 한 번에 끈다. */
    private fun clearGame() {
        members = members.map { it.copy(game = false) }.toMutableList()
        MemberStore.save(this, members)
        renderMembers()
        updateGame()
    }

    /** 음식 표시를 한 번에 끈다. 지난 모임 표시가 남아 있을 때 쓴다. */
    private fun clearFood() {
        members = members.map { it.copy(food = false) }.toMutableList()
        MemberStore.save(this, members)
        renderMembers()
        updateFood()
        updateGame()
    }

    /** 1~15번 방을 격자로 깔고, 고른 방은 초록으로 채운다. */
    private fun renderRooms() {
        b.gridRooms.removeAllViews()

        val perRow = 5
        var number = Prefs.MIN_ROOM_NUMBER
        while (number <= Prefs.MAX_ROOM_NUMBER) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            }
            repeat(perRow) {
                if (number <= Prefs.MAX_ROOM_NUMBER) {
                    row.addView(roomCell(number))
                    number++
                }
            }
            b.gridRooms.addView(row)
        }
    }

    private fun roomCell(number: Int): TextView {
        val picked = selectedRooms.contains(number)
        return TextView(this).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            setBackgroundResource(if (picked) R.drawable.bg_room_on else R.drawable.bg_room_off)
            setTextColor(getColor(if (picked) R.color.card else R.color.ink_soft))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(
                typeface,
                if (picked) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { toggleRoom(number) }
        }
    }

    private fun toggleRoom(number: Int) {
        when {
            // 해제는 언제나 누른 방 하나만. 연결이 끊긴 조합도 이렇게 만들 수 있다.
            selectedRooms.contains(number) -> selectedRooms.remove(number)

            // 보통 연결된 방을 쓰므로, 누른 방을 가운데 두고 앞뒤 한 칸씩 함께 고른다.
            autoAdjacent -> selectedRooms = adjacentBlock(number)

            selectedRooms.size >= Grouping.MAX_ROOMS -> {
                Toast.makeText(
                    this, getString(R.string.rooms_max, Grouping.MAX_ROOMS), Toast.LENGTH_SHORT
                ).show()
                return
            }

            else -> selectedRooms.add(number)
        }
        selectedRooms.sort()
        Prefs.saveSelectedRooms(this, selectedRooms)
        renderRooms()
        updateSummary()
    }

    /** 누른 방을 가운데로 둔 연속 세 칸. 끝쪽이면 안으로 밀어 세 칸을 유지한다. */
    private fun adjacentBlock(number: Int): MutableList<Int> {
        val size = Prefs.DEFAULT_ROOMS
        val first = (number - 1).coerceIn(
            Prefs.MIN_ROOM_NUMBER, Prefs.MAX_ROOM_NUMBER - size + 1
        )
        return MutableList(size) { first + it }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    private fun renderMembers() {
        b.listMembers.removeAllViews()
        b.tvEmpty.visibility = if (members.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        members.forEachIndexed { index, member ->
            val row = ItemMemberBinding.inflate(layoutInflater, b.listMembers, false)
            row.chkPlaying.text =
                member.name + if (member.handicap != 0) "  ${member.handicapLabel}" else ""
            row.chkPlaying.isChecked = member.playing
            row.chipSlow.isChecked = member.slow
            row.chipFood.text = food.name
            row.chipFood.isChecked = member.food
            row.chipGame.isChecked = member.game

            row.chkPlaying.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(playing = checked) }
            }
            row.chipSlow.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(slow = checked) }
            }
            row.chipFood.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(food = checked) }
            }
            row.chipGame.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(game = checked) }
            }
            b.listMembers.addView(row.root)
        }
    }

    /**
     * 체크 하나를 바꿀 때는 목록을 통째로 덮어쓰지 않고 그 사람만 고친다.
     * 화면이 들고 있던 목록이 낡아 있어도 다른 변경이 날아가지 않는다.
     */
    private fun update(index: Int, block: (Member) -> Member) {
        val name = members.getOrNull(index)?.name ?: return
        members = MemberStore.updateMember(this, name, block).toMutableList()
        updateSummary()
        updateFood()
        updateGame()
    }

    private fun versionName(): String? =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

    private fun playing(): List<Member> = members.filter { it.playing }

    private fun updateSummary() {
        val list = playing()
        val slow = list.count { it.slow }
        val picked =
            if (selectedRooms.isEmpty()) "방 미선택"
            else "${rooms}개 방 (" + selectedRooms.joinToString("·") + "번)"
        val eating = list.count { it.food }
        val marks = listOfNotNull(
            if (slow > 0) "슬로우 ${slow}명" else null,
            if (eating > 0) food.count(eating) else null
        )
        val head = "참가 ${list.size}명" +
            (if (marks.isNotEmpty()) " (" + marks.joinToString(", ") + ")" else "") +
            " · " + picked

        val detail = when {
            selectedRooms.isEmpty() -> getString(R.string.need_rooms)
            list.isEmpty() -> "참가할 회원을 체크하세요."
            list.size < rooms * Grouping.MIN_PER_ROOM ->
                "⚠ ${rooms}방이면 최소 ${rooms * Grouping.MIN_PER_ROOM}명이 필요해요."
            list.size > rooms * Grouping.MAX_PER_ROOM ->
                "⚠ ${rooms}방이면 최대 ${rooms * Grouping.MAX_PER_ROOM}명까지예요."
            else -> Grouping.roomSizes(list.size, rooms)
                .mapIndexed { i, size -> "${selectedRooms.getOrElse(i) { i + 1 }}번 ${size}명" }
                .joinToString("  ·  ")
        }

        b.tvSummary.text = "$head\n$detail"
        b.tvMembersTitle.text = getString(R.string.section_members) +
            if (members.isNotEmpty()) "  (${list.size}/${members.size})" else ""
    }

    /**
     * 음식은 조편성과 상관없이, 오늘 몇 개 시킬지만 따로 센다.
     * 표시는 해뒀지만 오늘 안 오는 사람은 개수에서 빼고 아래에 따로 알려준다.
     */
    private fun updateFood() {
        val eating = playing().filter { it.food }
        val absent = members.filter { it.food && !it.playing }

        b.tvFoodTitle.text =
            if (eating.isEmpty()) "${food.emoji} ${food.name}"
            else "${food.emoji} " + food.count(eating.size)

        b.btnFoodClear.visibility =
            if (members.any { it.food }) android.view.View.VISIBLE else android.view.View.GONE

        val lines = mutableListOf<String>()
        if (eating.isNotEmpty()) {
            lines.add(eating.joinToString("  ·  ") { it.name })
        }
        if (absent.isNotEmpty()) {
            lines.add(getString(R.string.food_absent, absent.joinToString(", ") { it.name }))
        }
        b.tvFoodNames.text =
            if (lines.isEmpty()) getString(R.string.food_none, food.name, food.unit)
            else lines.joinToString("\n")
    }

    /** 음식 카드와 같은 방식으로, 오늘 참가하는 사람 기준으로 게임 인원을 센다. */
    private fun updateGame() {
        val playingGame = playing().filter { it.game }
        val absent = members.filter { it.game && !it.playing }

        b.tvGameTitle.text =
            if (playingGame.isEmpty()) getString(R.string.section_game)
            else getString(R.string.game_count, playingGame.size)

        b.btnGameClear.visibility =
            if (members.any { it.game }) android.view.View.VISIBLE else android.view.View.GONE

        val lines = mutableListOf<String>()
        if (playingGame.isNotEmpty()) {
            lines.add(playingGame.joinToString("  ·  ") { it.name })
        }
        if (absent.isNotEmpty()) {
            lines.add(getString(R.string.game_absent, absent.joinToString(", ") { it.name }))
        }
        b.tvGameNames.text =
            if (lines.isEmpty()) getString(R.string.game_none) else lines.joinToString("\n")
    }

    private fun makeGroups() {
        val list = playing()
        if (members.isEmpty()) {
            alert(getString(R.string.need_members))
            return
        }
        if (list.isEmpty()) {
            alert(getString(R.string.need_playing))
            return
        }
        if (selectedRooms.isEmpty()) {
            alert(getString(R.string.need_rooms))
            return
        }
        when (val result = Grouping.group(
                list, rooms,
                pairs = MemberStore.loadPairs(this),
                priority = Prefs.loadPriority(this)
            )) {
            is Grouping.Result.Error -> alert(result.message)

            // 편성이 가능한지만 확인하고, 실제 공개는 추첨 화면에서 한 명씩 한다.
            is Grouping.Result.Ok ->
                startActivity(DrawActivity.intent(this, list, rooms, selectedRooms))
        }
    }

    private fun alert(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cannot_group)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
