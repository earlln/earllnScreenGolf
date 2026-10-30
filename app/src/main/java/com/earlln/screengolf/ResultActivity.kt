package com.earlln.screengolf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.earlln.screengolf.databinding.ActivityResultBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    companion object {
        private const val EX_NAMES = "names"
        private const val EX_SLOW = "slow"
        private const val EX_ROOMS = "rooms"
        private const val EX_HANDICAP = "handicap"
        private const val EX_ROOM_NUMBERS = "roomNumbers"
        private const val EX_FOOD = "food"
        private const val EX_GAME = "game"
        private const val EX_ORDER = "order"
        private const val EX_SIZES = "sizes"

        /**
         * [plan] 을 주면 그 편성을 그대로 그린다(추첨 화면에서 공개한 결과).
         * 주지 않으면 이 화면에서 새로 편성한다.
         */
        fun intent(
            ctx: Context,
            members: List<Member>,
            rooms: Int,
            roomNumbers: List<Int>,
            plan: List<List<Member>>? = null
        ): Intent = Intent(ctx, ResultActivity::class.java)
            .putExtra(EX_NAMES, members.map { it.name }.toTypedArray())
            .putExtra(EX_SLOW, members.map { it.slow }.toBooleanArray())
            .putExtra(EX_ROOMS, rooms)
            .putExtra(EX_HANDICAP, members.map { it.handicap }.toIntArray())
            .putExtra(EX_ROOM_NUMBERS, roomNumbers.toIntArray())
            .putExtra(EX_FOOD, members.map { it.food }.toBooleanArray())
                .putExtra(EX_GAME, members.map { it.game }.toBooleanArray())
            .apply {
                if (plan != null) {
                    putExtra(EX_ORDER, plan.flatten().map { it.name }.toTypedArray())
                    putExtra(EX_SIZES, plan.map { it.size }.toIntArray())
                }
            }
    }

    private lateinit var b: ActivityResultBinding
    private var members: List<Member> = emptyList()
    private var rooms = Prefs.DEFAULT_ROOMS
    private var roomNumbers = listOf<Int>()
    private var bitmap: Bitmap? = null
    private lateinit var billing: Billing

    /** 추첨 화면에서 넘어온 확정 편성. 없으면 이 화면에서 새로 편성한다. */
    private var fixed: List<List<Member>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityResultBinding.inflate(layoutInflater)
        setContentView(b.root)
        fitSystemBars()

        title = getString(R.string.result_title)
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
        fixed = readPlan()

        b.btnReshuffle.text = getString(R.string.reshuffle_draw)
        b.btnReshuffle.setOnClickListener {
            // 다시 뽑을 때도 추첨 화면을 거친다. 여럿이 볼 때 결과만 슬쩍 바뀌면 안 되기 때문이다.
            startActivity(DrawActivity.intent(this, members, rooms, roomNumbers))
            finish()
        }
        b.btnSave.setOnClickListener { saveToGallery() }
        b.btnShare.setOnClickListener { share() }

        billing = Billing(this) { updateShareButton() }
        billing.start()
        updateShareButton()

        render()
    }

    override fun onDestroy() {
        billing.stop()
        super.onDestroy()
    }

    /** 공유 버튼에 남은 무료 횟수를 보여준다. */
    private fun updateShareButton() {
        b.btnShare.text =
            if (ShareQuota.unlimited(this)) getString(R.string.share_unlimited)
            else getString(R.string.share_left, ShareQuota.left(this))
    }

    /** 추첨 화면이 넘겨준 편성(방 순서대로 늘어놓은 이름 + 방별 인원)을 복원한다. */
    private fun readPlan(): List<List<Member>>? {
        val order = intent.getStringArrayExtra(EX_ORDER) ?: return null
        val sizes = intent.getIntArrayExtra(EX_SIZES) ?: return null
        if (order.size != sizes.sum()) return null

        val byName = members.associateBy { it.name }
        val plan = mutableListOf<List<Member>>()
        var at = 0
        for (size in sizes) {
            plan.add(order.slice(at until at + size).map { byName[it] ?: Member(it) })
            at += size
        }
        return plan
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render() {
        val grouped = fixed ?: when (
            val result = Grouping.group(
                members, rooms,
                pairs = MemberStore.loadPairs(this),
                priority = Prefs.loadPriority(this)
            )
        ) {
            is Grouping.Result.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                finish()
                return
            }

            is Grouping.Result.Ok -> result.rooms
        }

        val today = SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREA).format(Date())
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        bitmap = ResultImage.render(grouped, today, roomNumbers, Prefs.loadFood(this), version)
        b.ivResult.setImageBitmap(bitmap)
    }

    private fun fileName() =
        "EarllnScreenGolf_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date()) + ".png"

    private fun saveToGallery() {
        val bmp = bitmap ?: return
        runCatching {
            val name = fileName()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + "EarllnScreenGolf"
                    )
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("저장 위치를 만들 수 없습니다")
            contentResolver.openOutputStream(uri).use { out ->
                out ?: error("파일을 열 수 없습니다")
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onSuccess {
            Toast.makeText(this, R.string.saved_to_gallery, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.save_failed, it.message ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun share() {
        if (!ShareQuota.canShare(this)) {
            askToBuy()
            return
        }
        val bmp = bitmap ?: return
        runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val file = File(dir, fileName())
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_image)))
            ShareQuota.record(this)
            updateShareButton()
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.save_failed, it.message ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** 무료 공유를 다 쓰면 '공유 무제한' 구매를 권한다. 사면 바로 공유를 이어간다. */
    private fun askToBuy() {
        val price = billing.price()?.let { getString(R.string.paywall_price, it) }.orEmpty()
        AlertDialog.Builder(this)
            .setTitle(R.string.paywall_title)
            .setMessage(getString(R.string.paywall_message, ShareQuota.FREE_SHARES, price))
            .setPositiveButton(R.string.paywall_buy) { _, _ ->
                billing.buy { outcome -> onBuy(outcome, thenShare = true) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onBuy(outcome: Billing.Outcome, thenShare: Boolean) {
        updateShareButton()
        when (outcome) {
            Billing.Outcome.Bought -> {
                Toast.makeText(this, R.string.purchase_done, Toast.LENGTH_SHORT).show()
                if (thenShare) share()
            }
            Billing.Outcome.Pending ->
                Toast.makeText(this, R.string.purchase_pending, Toast.LENGTH_LONG).show()
            Billing.Outcome.Cancelled -> Unit
            is Billing.Outcome.Failed -> Toast.makeText(
                this, getString(R.string.purchase_failed, outcome.message), Toast.LENGTH_LONG
            ).show()
        }
    }

}
