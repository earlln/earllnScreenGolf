package com.earlln.screengolf

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.earlln.screengolf.databinding.ActivityPairsBinding
import com.earlln.screengolf.databinding.DialogPairBinding
import com.earlln.screengolf.databinding.ItemPairBinding

/**
 * 두 사람을 반드시 같은 조로 묶거나 반드시 떨어뜨리는 조건을 관리한다.
 *
 * 조건은 회원 명단과 같은 members.json 에 들어가므로 내보내기/가져오기에도 함께 실린다.
 */
class PairsActivity : AppCompatActivity() {

    private lateinit var b: ActivityPairsBinding
    private var members = listOf<Member>()
    private var pairs = mutableListOf<MemberPair>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPairsBinding.inflate(layoutInflater)
        setContentView(b.root)
        fitSystemBars()

        title = getString(R.string.pairs_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 우선순위 라디오는 onResume 에서 저장값을 맞춘 뒤 리스너를 단다.
        b.btnAddPair.setOnClickListener { askPair() }
    }

    override fun onResume() {
        super.onResume()
        val roster = MemberStore.loadRoster(this)
        members = roster.members
        pairs = roster.pairs.toMutableList()

        // 체크 상태를 먼저 맞추고 리스너를 다시 달아야 저장이 되돌려 쓰이지 않는다.
        val priority = Prefs.loadPriority(this)
        b.rgPriority.setOnCheckedChangeListener(null)
        b.rgPriority.check(
            if (priority == Grouping.Priority.SLOW) R.id.rbSlow else R.id.rbPreference
        )
        b.rgPriority.setOnCheckedChangeListener { _, checked ->
            Prefs.savePriority(
                this,
                if (checked == R.id.rbSlow) Grouping.Priority.SLOW
                else Grouping.Priority.PREFERENCE
            )
        }

        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render() {
        b.listPairs.removeAllViews()

        if (pairs.isEmpty()) {
            b.listPairs.addView(
                android.widget.TextView(this).apply {
                    text = getString(R.string.pairs_empty)
                    setTextColor(getColor(R.color.ink_faint))
                }
            )
            return
        }

        pairs.forEachIndexed { index, pair ->
            val row = ItemPairBinding.inflate(layoutInflater, b.listPairs, false)
            row.tvKind.text = getString(
                if (pair.together) R.string.pairs_kind_together else R.string.pairs_kind_apart
            )
            row.tvPair.text = pair.label
            row.btnDelete.setOnClickListener {
                if (index in pairs.indices) {
                    pairs.removeAt(index)
                    save()
                    render()
                }
            }
            b.listPairs.addView(row.root)
        }
    }

    private fun askPair() {
        if (members.size < 2) {
            Toast.makeText(this, R.string.pairs_need_two, Toast.LENGTH_SHORT).show()
            return
        }

        val names = members.map { it.name }
        val view = DialogPairBinding.inflate(layoutInflater)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        view.spA.adapter = adapter
        view.spB.adapter = adapter
        view.spB.setSelection(1)

        AlertDialog.Builder(this)
            .setTitle(R.string.pairs_add)
            .setView(view.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val a = names.getOrNull(view.spA.selectedItemPosition)
                val bName = names.getOrNull(view.spB.selectedItemPosition)
                if (a == null || bName == null || a == bName) {
                    Toast.makeText(this, R.string.pairs_same_person, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                add(MemberPair(a, bName, view.rbTogether.isChecked))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun add(pair: MemberPair) {
        val at = pairs.indexOfFirst { it.key == pair.key }
        if (at >= 0) {
            pairs[at] = pair
            Toast.makeText(this, R.string.pairs_replaced, Toast.LENGTH_SHORT).show()
        } else {
            pairs.add(pair)
        }
        save()
        render()
    }

    private fun save() = MemberStore.savePairs(this, pairs)
}
