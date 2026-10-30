package com.earlln.screengolf

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.earlln.screengolf.databinding.ActivityDrawBinding
import com.earlln.screengolf.databinding.ItemDrawRoomBinding

/**
 * 조 추첨을 여럿이 보는 앞에서 진행하기 위한 화면.
 *
 * 결과는 화면에 들어오는 순간 한 번에 정해두고(그래야 규칙을 지킬 수 있다),
 * 그 결과를 한 명씩 공개한다. 이름이 남은 사람들 사이에서 빠르게 돌다가 멈추고,
 * 뽑힌 사람은 "남은 사람" 목록에서 사라진다. 보는 사람이 남은 후보가 줄어드는 걸
 * 눈으로 따라갈 수 있어야 추첨이 투명해 보이기 때문이다.
 */
class DrawActivity : AppCompatActivity() {

    companion object {
        private const val EX_NAMES = "names"
        private const val EX_SLOW = "slow"
        private const val EX_ROOMS = "rooms"
        private const val EX_HANDICAP = "handicap"
        private const val EX_ROOM_NUMBERS = "roomNumbers"
        private const val EX_FOOD = "food"
        private const val EX_GAME = "game"

        /** 한 명을 뽑는 데 걸리는 시간 ≒ TICKS * (TICK_MS + TICKS * TICK_STEP / 2) */
        private const val TICKS = 14
        private const val TICK_MS = 30L
        private const val TICK_STEP = 10L
        private const val PAUSE_MS = 420L

        fun intent(
            ctx: Context,
            members: List<Member>,
            rooms: Int,
            roomNumbers: List<Int>
        ): Intent = Intent(ctx, DrawActivity::class.java)
                .putExtra(EX_NAMES, members.map { it.name }.toTypedArray())
                .putExtra(EX_SLOW, members.map { it.slow }.toBooleanArray())
                .putExtra(EX_ROOMS, rooms)
                .putExtra(EX_HANDICAP, members.map { it.handicap }.toIntArray())
                .putExtra(EX_ROOM_NUMBERS, roomNumbers.toIntArray())
                .putExtra(EX_FOOD, members.map { it.food }.toBooleanArray())
                .putExtra(EX_GAME, members.map { it.game }.toBooleanArray())
    }

    private lateinit var b: ActivityDrawBinding
    private val handler = Handler(Looper.getMainLooper())

    private var members: List<Member> = emptyList()
    private var rooms = Prefs.DEFAULT_ROOMS
    private var roomNumbers = listOf<Int>()
    private val food by lazy { Prefs.loadFood(this) }

    /** 미리 뽑아둔 결과 */
    private var plan: List<List<Member>> = emptyList()

    /** 공개 순서: (방 번호, 뽑힌 사람) */
    private var order: List<Pair<Int, Member>> = emptyList()
    private var step = 0

    private val roomViews = mutableListOf<ItemDrawRoomBinding>()
    private var revealed = listOf<MutableList<Member>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDrawBinding.inflate(layoutInflater)
        setContentView(b.root)
        fitSystemBars()

        title = getString(R.string.draw_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val names = intent.getStringArrayExtra(EX_NAMES) ?: emptyArray()
        val slow = intent.getBooleanArrayExtra(EX_SLOW) ?: BooleanArray(names.size)
        rooms = intent.getIntExtra(EX_ROOMS, Prefs.DEFAULT_ROOMS)
        roomNumbers = intent.getIntArrayExtra(EX_ROOM_NUMBERS)?.toList()
            ?: Prefs.defaultRoomNumbers(rooms)
        val handicap = intent.getIntArrayExtra(EX_HANDICAP) ?: IntArray(names.size)
        val food = intent.getBooleanArrayExtra(EX_FOOD) ?: BooleanArray(names.size)
        val game = intent.getBooleanArrayExtra(EX_GAME) ?: BooleanArray(names.size)
        members = names.mapIndexed { i, n ->
            Member(
                name = n,
                slow = slow.getOrElse(i) { false },
                playing = true,
                handicap = handicap.getOrElse(i) { 0 },
                food = food.getOrElse(i) { false },
                game = game.getOrElse(i) { false }
            )
        }

        when (val result = Grouping.group(
            members, rooms,
            pairs = MemberStore.loadPairs(this),
            priority = Prefs.loadPriority(this)
        )) {
            is Grouping.Result.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                finish()
                return
            }

            is Grouping.Result.Ok -> {
                plan = result.rooms

                // 슬로우 우선이면 조건을 못 지킬 수 있다. 그냥 넘기지 않고 알려준다.
                if (result.unmetPairs.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.unmet_pairs,
                            result.unmetPairs.size,
                            result.unmetPairs.joinToString(", ") { it.label }
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val sequence = mutableListOf<Pair<Int, Member>>()
        plan.forEachIndexed { room, people -> people.forEach { sequence.add(room to it) } }
        order = sequence
        revealed = List(rooms) { mutableListOf<Member>() }

        buildRoomViews()
        b.tvStage.text = "${members.size}명 · ${rooms}개 방"
        updatePool()

        b.btnStart.setOnClickListener { onStartPressed() }
        b.btnSkip.setOnClickListener { skipToEnd() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun roomNumber(index: Int) = roomNumbers.getOrElse(index) { index + 1 }

    private fun buildRoomViews() {
        b.listRooms.removeAllViews()
        roomViews.clear()
        plan.forEachIndexed { i, people ->
            val row = ItemDrawRoomBinding.inflate(layoutInflater, b.listRooms, false)
            row.tvRoomTitle.text = "${roomNumber(i)}번 방 (${people.size}명)"
            row.tvRoomMembers.text = "—"
            b.listRooms.addView(row.root)
            roomViews.add(row)
        }
    }

    private fun onStartPressed() {
        if (step >= order.size) {
            // 추첨이 끝난 뒤에는 결과 이미지로 넘어간다.
            startActivity(ResultActivity.intent(this, members, rooms, roomNumbers, plan))
            finish()
            return
        }
        b.btnStart.isEnabled = false
        drawNext()
    }

    private fun drawNext() {
        if (step >= order.size) {
            finishDraw()
            return
        }
        val (room, member) = order[step]
        b.tvStage.text = getString(
            R.string.draw_stage, roomNumber(room), revealed[room].size + 1, plan[room].size
        )
        b.tvRollerTag.visibility = View.INVISIBLE

        roll(member) {
            lock(room, member)
            step++
            handler.postDelayed({ drawNext() }, PAUSE_MS)
        }
    }

    /** 아직 안 뽑힌 사람들 사이에서 이름을 빠르게 돌리다가 점점 느려진다. */
    private fun roll(target: Member, onDone: () -> Unit) {
        val pool = order.drop(step).map { it.second.name }
        if (pool.isEmpty()) {
            onDone()
            return
        }
        var tick = 0
        val spin = object : Runnable {
            override fun run() {
                if (tick >= TICKS) {
                    onDone()
                    return
                }
                b.tvRoller.text = pool.random()
                tick++
                handler.postDelayed(this, TICK_MS + tick * TICK_STEP)
            }
        }
        handler.post(spin)
    }

    private fun lock(room: Int, member: Member) {
        revealed[room].add(member)

        b.tvRoller.text = member.name
        b.tvRollerTag.visibility = if (member.slow) View.VISIBLE else View.INVISIBLE

        b.tvRoller.scaleX = 1.25f
        b.tvRoller.scaleY = 1.25f
        b.tvRoller.animate().scaleX(1f).scaleY(1f).setDuration(220).start()

        buzz()
        refreshRoom(room)
        updatePool()
    }

    private fun refreshRoom(room: Int) {
        roomViews.getOrNull(room)?.tvRoomMembers?.text =
            revealed[room].joinToString("  ·  ") { m ->
                buildString {
                    append(m.name)
                    if (m.handicap != 0) append(" ").append(m.handicapLabel)
                    val tags = listOfNotNull(
                        if (m.slow) getString(R.string.slow) else null,
                        if (m.food) food.name else null,
                        if (m.game) getString(R.string.game) else null
                    )
                    if (tags.isNotEmpty()) append(tags.joinToString(", ", " (", ")"))
                }
            }
    }

    private fun updatePool() {
        val left = order.drop(step).map { it.second }
        b.tvPoolTitle.text = getString(R.string.draw_remaining, left.size)
        b.tvPool.text = if (left.isEmpty()) "—" else left.joinToString("   ") { it.name }
    }

    private fun skipToEnd() {
        handler.removeCallbacksAndMessages(null)
        while (step < order.size) {
            val (room, member) = order[step]
            revealed[room].add(member)
            step++
        }
        plan.indices.forEach { refreshRoom(it) }
        updatePool()
        b.tvRoller.text = getString(R.string.draw_done)
        b.tvRollerTag.visibility = View.INVISIBLE
        finishDraw()
    }

    private fun finishDraw() {
        b.tvStage.text = getString(R.string.draw_done)
        b.btnSkip.visibility = View.GONE
        b.btnStart.isEnabled = true
        b.btnStart.text = getString(R.string.draw_show_result)
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
