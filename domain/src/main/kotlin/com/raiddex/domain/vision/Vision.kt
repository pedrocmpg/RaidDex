package com.raiddex.domain.vision

import com.raiddex.domain.GameData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Fonte de pixels independente de Android (Bitmap no app, ImageIO nos testes). Cor em 0xRRGGBB. */
interface PixelSource {
    val width: Int
    val height: Int
    fun rgb(x: Int, y: Int): Int
}

/** Coordenadas calibradas em 1080 × 2340 (Galaxy A34); tudo é escalado por percentual da tela. */
class Layout(private val src: PixelSource) {
    private val sx = src.width / REF_W
    private val sy = src.height / REF_H

    fun x(v: Double) = (v * sx).roundToInt().coerceIn(0, src.width - 1)
    fun y(v: Double) = (v * sy).roundToInt().coerceIn(0, src.height - 1)

    companion object {
        const val REF_W = 1080.0
        const val REF_H = 2340.0
    }
}

private fun r(c: Int) = (c shr 16) and 0xFF
private fun g(c: Int) = (c shr 8) and 0xFF
private fun b(c: Int) = c and 0xFF

/** Barra preenchida (laranja, ou rosa quando cheia); o limite baixo aceita o pixel suavizado na ponta. */
private fun isFill(c: Int) = r(c) > 190 && r(c) - b(c) > 25

/** Trilho vazio da barra: cinza claro. */
private fun isTrack(c: Int) = abs(r(c) - g(c)) < 12 && abs(g(c) - b(c)) < 12 && r(c) in 205..245

data class IvBars(val atk: Int, val def: Int, val sta: Int) {
    val percent: Int get() = ((atk + def + sta) / 45.0 * 100).roundToInt()
}

/** Lê as três barras da tela de avaliação. Devolve null se a tela não for o cartão de barras. */
object BarReader {
    private val rows = doubleArrayOf(1754.0, 1853.0, 1953.0)
    private val segments = listOf(128.0 to 249.0, 255.0 to 374.0, 380.0 to 502.0)

    fun read(src: PixelSource): IvBars? {
        val l = Layout(src)
        val values = IntArray(3)
        for ((i, row) in rows.withIndex()) {
            val y = l.y(row)
            var lastFill = -1
            // Cada pixel do trilho tem de ser preenchimento ou cinza; senão não é o cartão de barras.
            for ((a, bEnd) in segments) {
                for (x in l.x(a + 3)..l.x(bEnd - 3)) {
                    val c = src.rgb(x, y)
                    when {
                        isFill(c) -> lastFill = x
                        isTrack(c) -> Unit
                        else -> return null
                    }
                }
            }
            // O preenchimento é contíguo a partir da esquerda: um preenchido depois de um vazio indica animação ou ruído.
            if (!contiguous(src, l, y)) return null
            values[i] = valueFor(l, lastFill)
        }
        return IvBars(values[0], values[1], values[2])
    }

    private fun contiguous(src: PixelSource, l: Layout, y: Int): Boolean {
        var seenTrack = false
        for ((a, bEnd) in segments) {
            for (x in l.x(a + 3)..l.x(bEnd - 3)) {
                val c = src.rgb(x, y)
                if (isTrack(c)) seenTrack = true else if (isFill(c) && seenTrack) return false
            }
        }
        return true
    }

    private fun valueFor(l: Layout, lastFillX: Int): Int {
        if (lastFillX < 0) return 0
        var v = 0.0
        for ((a, bEnd) in segments) {
            val ax = l.x(a)
            val bx = l.x(bEnd)
            when {
                lastFillX >= bx - 1 -> v += 5.0
                lastFillX > ax -> { v += 5.0 * (lastFillX - ax) / (bx - ax); return v.roundToInt().coerceIn(0, 15) }
                else -> return v.roundToInt().coerceIn(0, 15)
            }
        }
        return v.roundToInt().coerceIn(0, 15)
    }
}

/**
 * Nível pelo ponto branco do arco atrás do Pokémon. A posição é proporcional ao CPM,
 * indo do nível 1 (esquerda) até o nível máximo (nível de treinador + 2, direita).
 */
object ArcReader {
    private const val CX = 540.0
    private const val CY = 690.0
    private const val R = 439.0
    private const val ANGLE_LEVEL_1 = -88.3
    private const val ANGLE_LEVEL_MAX = 82.3
    private const val DOT_RADIUS = 13
    private const val MIN_DOT_PIXELS = 200

    /** Ângulo do ponto (graus a partir da vertical, positivo à direita) ou null se não houver ponto. */
    fun dotAngle(src: PixelSource): Double? {
        val l = Layout(src)
        var best = 0
        var bestAngle = 0.0
        var i = -900
        while (i <= 900) {
            val th = i / 10.0 * PI / 180.0
            val px = l.x(CX + R * sin(th))
            val py = l.y(CY - R * cos(th))
            var n = 0
            for (dx in -DOT_RADIUS..DOT_RADIUS) for (dy in -DOT_RADIUS..DOT_RADIUS) {
                if (dx * dx + dy * dy > DOT_RADIUS * DOT_RADIUS) continue
                val x = px + dx
                val y = py + dy
                if (x < 0 || y < 0 || x >= src.width || y >= src.height) continue
                val c = src.rgb(x, y)
                if (minOf(r(c), g(c), b(c)) > 225) n++
            }
            if (n > best) { best = n; bestAngle = i / 10.0 }
            i += 2
        }
        return if (best >= MIN_DOT_PIXELS) bestAngle else null
    }

    /** Nível (de meio em meio) mais próximo do ângulo medido, dado o nível máximo do arco. */
    fun level(data: GameData, angle: Double, maxLevel: Double): Double {
        val f = (angle - ANGLE_LEVEL_1) / (ANGLE_LEVEL_MAX - ANGLE_LEVEL_1)
        val lo = data.cpm(1.0)
        val hi = data.cpm(maxLevel)
        var bestLevel = 1.0
        var bestDiff = Double.MAX_VALUE
        var level = 1.0
        while (level <= maxLevel) {
            val diff = abs((data.cpm(level) - lo) / (hi - lo) - f)
            if (diff < bestDiff) { bestDiff = diff; bestLevel = level }
            level += 0.5
        }
        return bestLevel
    }
}

enum class ScreenKind { APPRAISAL, DETAIL, OTHER }

object ScreenClassifier {
    /** Cartão branco do detalhe/avaliação começa por volta de y = 780 e cobre quase toda a largura. */
    private fun hasWhiteCard(src: PixelSource): Boolean {
        val l = Layout(src)
        val y = l.y(860.0)
        return listOf(120.0, 540.0, 900.0).all { xx ->
            val c = src.rgb(l.x(xx), y)
            r(c) > 235 && g(c) > 235 && b(c) > 235
        }
    }

    /** Acima do cartão há a cena do Pokémon (nunca toda branca), como não ocorre numa lista clara. */
    private fun hasScene(src: PixelSource): Boolean {
        val l = Layout(src)
        val y = l.y(740.0)
        return listOf(60.0, 540.0, 1000.0).any { xx ->
            val c = src.rgb(l.x(xx), y)
            minOf(r(c), g(c), b(c)) < 215
        }
    }

    fun classify(src: PixelSource): ScreenKind {
        if (!hasWhiteCard(src) || !hasScene(src)) return ScreenKind.OTHER
        if (BarReader.read(src) != null) return ScreenKind.APPRAISAL
        return if (ArcReader.dotAngle(src) != null) ScreenKind.DETAIL else ScreenKind.OTHER
    }
}
