package com.earlln.screengolf

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.earlln.screengolf.databinding.ActivitySettingsBinding
import com.google.android.material.chip.Chip

/**
 * 설정 화면. 따로 세는 음식(기본 김밥)을 고르고, 결과 이미지 공유 횟수와 구매 상태를 본다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var billing: Billing

    /** 프리셋 칩 id → 음식. '직접 입력' 칩은 여기 없다. */
    private val presetChips = mutableMapOf<Int, Food>()
    private var customChipId = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        fitSystemBars()

        title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.etFoodName.hint = getString(R.string.settings_food_name, Food.MAX_NAME)
        renderFoodChips()
        b.btnFoodApply.setOnClickListener { applyCustom() }

        billing = Billing(this) { renderShare() }
        billing.start()
        b.btnBuy.setOnClickListener { buy() }
        b.btnRestore.setOnClickListener {
            billing.refresh {
                renderShare()
                Toast.makeText(this, R.string.settings_restored, Toast.LENGTH_SHORT).show()
            }
        }

        // 테스트 빌드에서만, 무료 횟수와 가짜 구매를 되돌려 흐름을 다시 시험할 수 있다.
        b.cardTest.visibility = if (BuildConfig.FAKE_BILLING) View.VISIBLE else View.GONE
        b.btnTestReset.setOnClickListener {
            ShareQuota.reset(this)
            renderShare()
        }
        b.btnTestRevoke.setOnClickListener {
            Prefs.saveUnlimited(this, false)
            renderShare()
        }

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        b.tvAbout.text = getString(R.string.about, getString(R.string.app_name), version)

        renderShare()
    }

    override fun onDestroy() {
        billing.stop()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun renderFoodChips() {
        val current = Prefs.loadFood(this)
        b.groupFood.removeAllViews()
        presetChips.clear()

        var checkedId = View.NO_ID
        Food.PRESETS.forEach { food ->
            val chip = foodChip("${food.emoji} ${food.name}")
            b.groupFood.addView(chip)
            presetChips[chip.id] = food
            if (food == current) checkedId = chip.id
        }

        val custom = foodChip(getString(R.string.settings_food_custom))
        b.groupFood.addView(custom)
        customChipId = custom.id

        // 프리셋에 없는 음식이면 직접 입력한 것이다.
        if (checkedId == View.NO_ID) {
            checkedId = customChipId
            b.etFoodName.setText(current.name)
            b.etFoodEmoji.setText(current.emoji)
            b.etFoodUnit.setText(current.unit)
        }
        b.groupFood.check(checkedId)
        b.boxCustom.visibility = if (checkedId == customChipId) View.VISIBLE else View.GONE

        b.groupFood.setOnCheckedStateChangeListener { _, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val preset = presetChips[id]
            if (preset != null) {
                b.boxCustom.visibility = View.GONE
                save(preset)
            } else {
                b.boxCustom.visibility = View.VISIBLE
            }
        }
    }

    private fun foodChip(label: String) = Chip(this).apply {
        id = View.generateViewId()
        text = label
        isCheckable = true
        isCheckedIconVisible = false
        setTextColor(getColorStateList(R.color.chip_text))
        chipBackgroundColor = getColorStateList(R.color.chip_bg)
        chipStrokeColor = getColorStateList(R.color.chip_stroke)
        chipStrokeWidth = resources.displayMetrics.density
    }

    private fun applyCustom() {
        val name = b.etFoodName.text.toString().trim().take(Food.MAX_NAME)
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.settings_food_need_name, Toast.LENGTH_SHORT).show()
            return
        }
        save(
            Food(
                name = name,
                emoji = b.etFoodEmoji.text.toString().trim().ifEmpty { Food.DEFAULT.emoji },
                unit = b.etFoodUnit.text.toString().trim().take(Food.MAX_UNIT)
                    .ifEmpty { Food.DEFAULT.unit }
            )
        )
    }

    private fun save(food: Food) {
        Prefs.saveFood(this, food)
        Toast.makeText(
            this, getString(R.string.settings_food_saved, food.name), Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderShare() {
        val unlimited = ShareQuota.unlimited(this)
        b.tvShareStatus.text =
            if (unlimited) getString(R.string.settings_share_unlimited)
            else getString(
                R.string.settings_share_free, ShareQuota.used(this), ShareQuota.FREE_SHARES
            )

        b.btnBuy.visibility = if (unlimited) View.GONE else View.VISIBLE
        b.btnBuy.text = billing.price()
            ?.let { getString(R.string.settings_buy) + " · " + it }
            ?: getString(R.string.settings_buy)
    }

    private fun buy() {
        billing.buy { outcome ->
            renderShare()
            when (outcome) {
                Billing.Outcome.Bought ->
                    Toast.makeText(this, R.string.purchase_done, Toast.LENGTH_SHORT).show()
                Billing.Outcome.Pending ->
                    Toast.makeText(this, R.string.purchase_pending, Toast.LENGTH_LONG).show()
                Billing.Outcome.Cancelled -> Unit
                is Billing.Outcome.Failed -> Toast.makeText(
                    this, getString(R.string.purchase_failed, outcome.message), Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
