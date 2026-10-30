package com.earlln.screengolf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import com.earlln.screengolf.databinding.ActivityMembersBinding
import com.earlln.screengolf.databinding.ItemMemberEditBinding

/**
 * 회원 명단(옵션) 화면. 이름을 줄 단위로 입력하고, 회원별로 슬로우 골퍼와 음식을 표시한다.
 * 음식은 조편성에는 쓰이지 않고, 몇 개를 시킬지 따로 세는 데만 쓴다.
 */
class MembersActivity : AppCompatActivity() {

    private lateinit var b: ActivityMembersBinding
    private var members = mutableListOf<Member>()
    private var food = Food.DEFAULT

    /** 파일 선택기. 확장자를 못 알아보는 파일 관리자가 있어 형식을 넓게 연다. */
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMembersBinding.inflate(layoutInflater)
        setContentView(b.root)
        fitSystemBars()

        title = getString(R.string.members_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.btnApply.setOnClickListener { applyBulk() }
        b.btnPairs.setOnClickListener {
            startActivity(Intent(this, PairsActivity::class.java))
        }
        b.btnExport.setOnClickListener { export() }
        b.btnImport.setOnClickListener {
            pickFile.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 바뀐 내용 위에 낡은 목록을 덮어쓰지 않도록 매번 다시 읽는다.
        members = MemberStore.load(this)
        food = Prefs.loadFood(this)
        b.tvMembersDesc.text = getString(R.string.members_desc, food.name)
        b.etBulk.setText(members.joinToString("\n") { it.name })
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** 입력창의 이름 목록을 명단에 반영한다. 기존 회원의 슬로우/참가 표시는 그대로 유지. */
    private fun applyBulk() {
        val names = b.etBulk.text.toString()
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val old = MemberStore.loadRoster(this).members.associateBy { it.name }
        members = names.map { name ->
            old[name] ?: Member(name = name, slow = false, playing = true)
        }.toMutableList()

        save()
        render()
        Toast.makeText(this, getString(R.string.members_saved, members.size), Toast.LENGTH_SHORT).show()
    }

    private fun render() {
        b.listMembers.removeAllViews()
        if (members.isEmpty()) {
            b.listMembers.addView(
                android.widget.TextView(this).apply {
                    text = getString(R.string.no_members)
                    setTextColor(0xFF7A8B80.toInt())
                }
            )
            return
        }
        members.forEachIndexed { index, member ->
            val row = ItemMemberEditBinding.inflate(layoutInflater, b.listMembers, false)
            row.tvName.text = member.name
            row.tvHandicap.text = handicapText(member.handicap)

            row.chipSlow.isChecked = member.slow
            row.chipSlow.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(slow = checked) }
            }

            row.chipFood.text = food.name
            row.chipFood.isChecked = member.food
            row.chipGame.isChecked = member.game
            row.chipFood.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(food = checked) }
            }
            row.chipGame.setOnCheckedChangeListener { _, checked ->
                update(index) { it.copy(game = checked) }
            }

            row.btnMinus.setOnClickListener { bump(index, -1, row.tvHandicap) }
            row.btnPlus.setOnClickListener { bump(index, +1, row.tvHandicap) }
            row.tvHandicap.setOnClickListener { askHandicap(index, row.tvHandicap) }

            row.btnDelete.setOnClickListener {
                if (index in members.indices) {
                    members.removeAt(index)
                    save()
                    b.etBulk.setText(members.joinToString("\n") { it.name })
                    render()
                }
            }
            b.listMembers.addView(row.root)
        }
    }

    /** 편집 화면에서는 0 도 그대로 보여준다. 다른 화면에서는 0 이면 표시하지 않는다. */
    private fun handicapText(value: Int) = if (value == 0) "0" else Member.label(value)

    private fun update(index: Int, block: (Member) -> Member) {
        val name = members.getOrNull(index)?.name ?: return
        members = MemberStore.updateMember(this, name, block).toMutableList()
    }

    private fun bump(index: Int, delta: Int, label: TextView) {
        if (index !in members.indices) return
        val next = (members[index].handicap + delta)
            .coerceIn(Member.MIN_HANDICAP, Member.MAX_HANDICAP)
        update(index) { it.copy(handicap = next) }
        label.text = handicapText(next)
    }

    /** 숫자를 누르면 직접 입력. 큰 값을 한 번에 넣을 때 쓴다. */
    private fun askHandicap(index: Int, label: TextView) {
        if (index !in members.indices) return
        val member = members[index]

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(if (member.handicap == 0) "" else member.handicap.toString())
            hint = getString(R.string.handicap_hint, Member.MIN_HANDICAP, Member.MAX_HANDICAP)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.handicap_of, member.name))
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val next = input.text.toString().trim().toIntOrNull()
                    ?.coerceIn(Member.MIN_HANDICAP, Member.MAX_HANDICAP)
                    ?: 0
                update(index) { it.copy(handicap = next) }
                label.text = handicapText(next)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun save() = MemberStore.save(this, members)

    /** 명단을 JSON 파일로 만들어 다른 앱으로 보낸다(카톡, 드라이브, 파일로 저장 등). */
    private fun export() {
        if (members.isEmpty()) {
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val out = File(dir, MemberStore.exportFileName())
            out.writeText(MemberStore.toJson(MemberStore.Roster(members, MemberStore.loadPairs(this))))

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, out.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.export_title)))
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.save_failed, it.message ?: it.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun importFrom(uri: Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

        val roster = text?.let { MemberStore.parseRoster(it) }
        val incoming = roster?.members
        if (roster == null || incoming.isNullOrEmpty()) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.import_title)
            .setMessage(getString(R.string.import_found, incoming.size, members.size))
            .setPositiveButton(R.string.import_replace) { _, _ ->
                applyImport(incoming.toMutableList(), roster.pairs)
            }
            .setNeutralButton(R.string.import_merge) { _, _ ->
                applyImport(
                    MemberStore.merge(members, incoming),
                    MemberStore.mergePairs(MemberStore.loadPairs(this), roster.pairs)
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyImport(next: MutableList<Member>, pairs: List<MemberPair>) {
        members = next
        MemberStore.saveRoster(this, MemberStore.Roster(members, pairs))
        b.etBulk.setText(members.joinToString("\n") { it.name })
        render()
        Toast.makeText(this, getString(R.string.import_done, members.size), Toast.LENGTH_SHORT).show()
    }
}
