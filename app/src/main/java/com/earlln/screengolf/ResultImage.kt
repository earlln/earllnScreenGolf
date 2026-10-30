package com.earlln.screengolf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** 조편성 결과를 공유하기 좋은 카드 이미지로 그려낸다. */
object ResultImage {

    private const val W = 1080f
    private const val PAD = 56f
    private const val HEADER_H = 288f
    private const val CARD_GAP = 28f
    private const val CARD_HEAD = 156f
    private const val ROW_H = 88f
    private const val CARD_FOOT = 28f
    private const val FOOTER_H = 152f

    // 음식 카드(맨 아래): 머리말 + 이름 줄 + 여백
    private const val FOOD_HEAD = 132f
    private const val FOOD_LINE = 52f
    private const val FOOD_FOOT = 34f

    private const val GREEN_DEEP = 0xFF0A4B2A.toInt()
    private const val GREEN = 0xFF157F45.toInt()
    private const val GREEN_LIGHT = 0xFF3FA86A.toInt()
    private const val BG_TOP = 0xFFF4F8F4.toInt()
    private const val BG_BOTTOM = 0xFFE6EFE8.toInt()
    private const val CARD = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF12261B.toInt()
    private const val INK_SOFT = 0xFF6B7F72.toInt()
    /** 글자 대신 쓰는 표시. 음식 표시는 설정에서 고른 [Food.emoji] 를 쓴다. */
    private const val SLOW_MARK = "\uD83D\uDC22"      // 🐢
    private const val GAME_MARK = "\uD83C\uDFAF"      // 🎯

    /** 이모지 배지 하나가 차지하는 폭(지름 + 간격). */
    private const val MARK_SLOT = 68f

    private const val AMBER = 0xFFF3A712.toInt()
    private const val AMBER_BG = 0xFFFFF2D6.toInt()
    private const val AMBER_INK = 0xFF9A5B00.toInt()
    private const val BLUE = 0xFF2D6CDF.toInt()
    private const val BLUE_BG = 0xFFE4ECFC.toInt()
    private const val BLUE_INK = 0xFF1B4E9E.toInt()
    private const val GAME_BG = 0xFFEDE4FA.toInt()

    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    private fun paint(block: Paint.() -> Unit) = Paint(Paint.ANTI_ALIAS_FLAG).apply(block)

    fun render(
        rooms: List<List<Member>>,
        subtitle: String,
        roomNumbers: List<Int> = emptyList(),
        food: Food = Food.DEFAULT,
        version: String = ""
    ): Bitmap {
        val cardHeights = rooms.map { CARD_HEAD + it.size * ROW_H + CARD_FOOT }

        // 음식은 방과 상관없이 전체에서 모아 맨 아래에 따로 적는다.
        val eating = rooms.flatten().filter { it.food }
        val foodLines = wrapNames(eating.map { it.name }, W - PAD * 2 - 92f)
        val foodH =
            if (foodLines.isEmpty()) 0f
            else FOOD_HEAD + foodLines.size * FOOD_LINE + FOOD_FOOT

        val height = HEADER_H + PAD +
            cardHeights.sum() + CARD_GAP * (rooms.size - 1).coerceAtLeast(0) +
            (if (foodH > 0f) CARD_GAP + foodH else 0f) +
            FOOTER_H

        val bmp = Bitmap.createBitmap(W.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 배경
        c.drawRect(
            0f, 0f, W, height,
            paint { shader = LinearGradient(0f, 0f, 0f, height, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP) }
        )

        drawHeader(c, rooms, subtitle, food, version)

        var y = HEADER_H + PAD
        rooms.forEachIndexed { i, room ->
            drawRoomCard(c, roomNumbers.getOrElse(i) { i + 1 }, room, y, cardHeights[i], food)
            y += cardHeights[i] + CARD_GAP
        }
        if (foodH > 0f) drawFoodCard(c, food, eating.size, foodLines, y, foodH)

        // 푸터
        c.drawText(
            "$SLOW_MARK 슬로우 골퍼   ·   ${food.emoji} ${food.name}   ·   $GAME_MARK 게임 참여",
            W / 2f, height - FOOTER_H + 48f,
            paint {
                color = INK_SOFT
                textSize = 30f
                typeface = regular
                textAlign = Paint.Align.CENTER
            }
        )
        c.drawText(
            "EarllnScreenGolf   ·   by Earlln.com",
            W / 2f, height - FOOTER_H + 100f,
            paint {
                color = GREEN
                textSize = 30f
                typeface = medium
                textAlign = Paint.Align.CENTER
            }
        )
        return bmp
    }

    private fun drawHeader(
        c: Canvas,
        rooms: List<List<Member>>,
        subtitle: String,
        food: Food,
        version: String
    ) {
        val r = RectF(0f, 0f, W, HEADER_H)
        c.drawRect(
            r,
            paint { shader = LinearGradient(0f, 0f, W, HEADER_H, GREEN_DEEP, GREEN_LIGHT, Shader.TileMode.CLAMP) }
        )

        // 페어웨이 느낌의 곡선 두 줄
        val stroke = paint {
            color = 0x22FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        for (k in 0..1) {
            val path = Path()
            val base = 96f + k * 70f
            path.moveTo(0f, base)
            path.cubicTo(W * 0.3f, base - 46f, W * 0.7f, base + 46f, W, base - 16f)
            c.drawPath(path, stroke)
        }

        drawFlag(c, W - 168f, 56f)

        val title = "EarllnScreenGolf"
        val badgePaint = paint {
            color = 0xFF3A2600.toInt()
            textSize = 34f
            typeface = bold
            textAlign = Paint.Align.CENTER
        }
        val badgeText = if (version.isEmpty()) "" else "v$version"
        val badgeW = if (badgeText.isEmpty()) 0f else badgePaint.measureText(badgeText) + 44f

        // 타이틀이 길어서, 깃발 앞까지 들어가도록 글자 크기를 줄여 맞춘다.
        val titlePaint = paint {
            color = 0xFFFFFFFF.toInt()
            textSize = 82f
            typeface = bold
            letterSpacing = -0.02f
        }
        val space = W - 200f - PAD - (if (badgeW > 0f) badgeW + 22f else 0f)
        while (titlePaint.textSize > 40f && titlePaint.measureText(title) > space) {
            titlePaint.textSize -= 2f
        }
        c.drawText(title, PAD, 132f, titlePaint)

        // 타이틀 옆 버전 배지
        if (badgeW > 0f) {
            val badgeX = PAD + titlePaint.measureText(title) + 22f
            val badge = RectF(badgeX, 82f, badgeX + badgeW, 138f)
            c.drawRoundRect(badge, 28f, 28f, paint { color = AMBER })
            c.drawText(badgeText, badge.centerX(), badge.centerY() + 13f, badgePaint)
        }
        c.drawText(
            "오늘의 조편성", PAD, 190f,
            paint {
                color = 0xFFD9F2E2.toInt()
                textSize = 40f
                typeface = medium
            }
        )

        val total = rooms.sumOf { it.size }
        val slow = rooms.sumOf { room -> room.count { it.slow } }
        val eating = rooms.sumOf { room -> room.count { it.food } }
        val game = rooms.sumOf { room -> room.count { it.game } }
        val info = "$subtitle   ·   총 ${total}명   ·   ${rooms.size}개 방" +
            (if (slow > 0) "   ·   $SLOW_MARK ${slow}명" else "") +
            (if (eating > 0) "   ·   ${food.markCount(eating)}" else "") +
            (if (game > 0) "   ·   $GAME_MARK ${game}명" else "")
        c.drawText(
            info, PAD, 248f,
            paint {
                color = 0xCCFFFFFF.toInt()
                textSize = 30f
                typeface = regular
            }
        )
    }

    /** 헤더 오른쪽의 깃대 + 홀 아이콘 */
    private fun drawFlag(c: Canvas, x: Float, y: Float) {
        val pole = paint {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(x, y, x, y + 132f, pole)

        val flag = Path().apply {
            moveTo(x, y + 4f)
            lineTo(x + 86f, y + 32f)
            lineTo(x, y + 62f)
            close()
        }
        c.drawPath(flag, paint { color = AMBER })

        // 홀(그린) 타원
        c.drawOval(
            RectF(x - 76f, y + 118f, x + 76f, y + 158f),
            paint { color = 0x33FFFFFF }
        )
        c.drawOval(
            RectF(x - 20f, y + 128f, x + 20f, y + 146f),
            paint { color = GREEN_DEEP }
        )
    }

    private fun drawRoomCard(
        c: Canvas,
        number: Int,
        room: List<Member>,
        top: Float,
        h: Float,
        food: Food
    ) {
        val rect = RectF(PAD, top, W - PAD, top + h)

        // 그림자 + 카드
        c.drawRoundRect(
            rect, 36f, 36f,
            paint {
                color = CARD
                setShadowLayer(18f, 0f, 8f, 0x1A0A4B2A)
            }
        )

        // 왼쪽 초록 띠
        c.save()
        val clip = Path().apply { addRoundRect(rect, 36f, 36f, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawRect(rect.left, rect.top, rect.left + 12f, rect.bottom, paint { color = GREEN })
        c.restore()

        // 방 번호 배지
        val bx = rect.left + 46f
        val by = rect.top + 44f
        c.drawRoundRect(
            RectF(bx, by, bx + 84f, by + 84f), 26f, 26f,
            paint {
                shader = LinearGradient(bx, by, bx + 84f, by + 84f, GREEN, GREEN_LIGHT, Shader.TileMode.CLAMP)
            }
        )
        c.drawText(
            "$number", bx + 42f, by + 60f,
            paint {
                color = 0xFFFFFFFF.toInt()
                textSize = 48f
                typeface = bold
                textAlign = Paint.Align.CENTER
            }
        )

        c.drawText(
            "${number}번 방", bx + 112f, by + 40f,
            paint {
                color = INK
                textSize = 46f
                typeface = bold
            }
        )
        val slowCount = room.count { it.slow }
        val foodCount = room.count { it.food }
        val gameCount = room.count { it.game }
        val sub = "${room.size}명" +
            (if (slowCount > 0) " · $SLOW_MARK ${slowCount}" else "") +
            (if (foodCount > 0) " · ${food.emoji} ${foodCount}" else "") +
            (if (gameCount > 0) " · $GAME_MARK ${gameCount}" else "")
        c.drawText(
            sub, bx + 112f, by + 78f,
            paint {
                color = INK_SOFT
                textSize = 30f
                typeface = regular
            }
        )

        // 구분선
        c.drawLine(
            rect.left + 46f, rect.top + CARD_HEAD - 20f,
            rect.right - 46f, rect.top + CARD_HEAD - 20f,
            paint {
                color = 0xFFE9EFEA.toInt()
                strokeWidth = 2f
            }
        )

        // 멤버 목록
        var y = rect.top + CARD_HEAD + ROW_H / 2f
        room.forEach { m ->
            drawMemberRow(c, m, food, rect.left + 46f, y, rect.right - 46f)
            y += ROW_H
        }
    }

    private fun drawMemberRow(
        c: Canvas,
        m: Member,
        food: Food,
        left: Float,
        centerY: Float,
        right: Float
    ) {
        val avatarR = 27f
        val cx = left + avatarR
        c.drawCircle(
            cx, centerY, avatarR,
            paint { color = if (m.slow) AMBER_BG else 0xFFE8F3EC.toInt() }
        )
        c.drawText(
            m.name.take(1), cx, centerY + 13f,
            paint {
                color = if (m.slow) AMBER_INK else GREEN
                textSize = 32f
                typeface = bold
                textAlign = Paint.Align.CENTER
            }
        )

        val namePaint = paint {
            color = INK
            textSize = 40f
            typeface = medium
        }
        val hcPaint = paint {
            color = GREEN
            textSize = 34f
            typeface = bold
        }
        val hc = m.handicapLabel
        val hcWidth = if (hc.isEmpty()) 0f else hcPaint.measureText(hc) + 18f

        // 오른쪽 표시(슬로우 · 음식)가 차지할 자리를 먼저 빼둔다.
        val tags = (if (m.slow) MARK_SLOT else 0f) +
            (if (m.food) MARK_SLOT else 0f) +
            (if (m.game) MARK_SLOT else 0f)

        val nameX = cx + avatarR + 24f
        val nameRoom = right - nameX - hcWidth - tags
        val name = ellipsize(m.name, namePaint, nameRoom)
        c.drawText(name, nameX, centerY + 14f, namePaint)

        if (hc.isNotEmpty()) {
            c.drawText(hc, nameX + namePaint.measureText(name) + 18f, centerY + 12f, hcPaint)
        }

        // 오른쪽 끝에서부터 슬로우, 그 왼쪽에 음식.
        var edge = right
        if (m.slow) {
            drawMark(c, SLOW_MARK, edge, centerY, AMBER_BG)
            edge -= MARK_SLOT
        }
        if (m.food) {
            drawMark(c, food.emoji, edge, centerY, BLUE_BG)
            edge -= MARK_SLOT
        }
        if (m.game) {
            drawMark(c, GAME_MARK, edge, centerY, GAME_BG)
        }
    }

    /** [right] 를 오른쪽 끝으로 하는 동그란 이모지 배지. */
    private fun drawMark(c: Canvas, mark: String, right: Float, centerY: Float, bg: Int) {
        val radius = 27f
        val cx = right - radius
        c.drawCircle(cx, centerY, radius, paint { color = bg })
        c.drawText(
            mark, cx, centerY + 14f,
            paint {
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
        )
    }

    /**
     * 음식 먹는 사람만 모은 카드. 조편성과 무관하게 몇 개를 시킬지 한눈에 보라고 따로 둔다.
     */
    private fun drawFoodCard(
        c: Canvas,
        food: Food,
        count: Int,
        lines: List<String>,
        top: Float,
        h: Float
    ) {
        val rect = RectF(PAD, top, W - PAD, top + h)

        c.drawRoundRect(
            rect, 36f, 36f,
            paint {
                color = CARD
                setShadowLayer(18f, 0f, 8f, 0x1A0A4B2A)
            }
        )

        // 왼쪽 파란 띠 (방 카드의 초록 띠와 구분된다)
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, 36f, 36f, Path.Direction.CW) })
        c.drawRect(rect.left, rect.top, rect.left + 12f, rect.bottom, paint { color = BLUE })
        c.restore()

        val left = rect.left + 46f
        c.drawText(
            food.markCount(count), left, rect.top + 66f,
            paint {
                color = BLUE_INK
                textSize = 44f
                typeface = bold
            }
        )
        c.drawText(
            "${food.name} 먹는 사람만 따로 모았습니다", left, rect.top + 108f,
            paint {
                color = INK_SOFT
                textSize = 30f
                typeface = regular
            }
        )

        val names = namesPaint()
        var y = rect.top + FOOD_HEAD + 22f
        lines.forEach { line ->
            c.drawText(line, left, y, names)
            y += FOOD_LINE
        }
    }

    private fun namesPaint() = paint {
        color = INK
        textSize = 36f
        typeface = medium
    }

    /** 이름을 " · " 로 이어 붙이되 카드 폭을 넘지 않게 줄을 나눈다. */
    private fun wrapNames(names: List<String>, maxWidth: Float): List<String> {
        if (names.isEmpty()) return emptyList()
        val p = namesPaint()
        val lines = mutableListOf<String>()
        var line = ""
        names.forEach { name ->
            val next = if (line.isEmpty()) name else "$line  ·  $name"
            if (line.isNotEmpty() && p.measureText(next) > maxWidth) {
                lines.add(line)
                line = name
            } else {
                line = next
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || p.measureText(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && p.measureText("$s…") > maxWidth) s = s.dropLast(1)
        return "$s…"
    }
}
