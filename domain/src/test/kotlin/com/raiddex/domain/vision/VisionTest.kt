package com.raiddex.domain.vision

import com.raiddex.domain.Formulas
import com.raiddex.domain.Ivs
import com.raiddex.domain.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VisionTest {
    private val data = TestData.data

    /** Valores verificados contra PC/PS no jogo: arquivo, espécie, PC, PS, barras, nível. */
    private data class Case(val file: String, val species: String, val cp: Int, val hp: Int, val bars: IvBars, val level: Double)

    private val cases = listOf(
        Case("novo-2.jpg", "METAGROSS", 3691, 159, IvBars(13, 15, 13), 39.0),
        Case("novo-1.jpg", "KELDEO", 1565, 115, IvBars(15, 10, 15), 15.0),
        Case("avaliacao-iv-baixo.jpg", "HERACROSS", 2316, 139, IvBars(5, 12, 7), 28.0),
        Case("iv-horrivel.jpg", "CHARMANDER", 11, 11, IvBars(1, 12, 0), 1.0),
    )

    @Test
    fun barsMatchVerifiedValues() {
        for (c in cases) assertEquals(c.bars, BarReader.read(PrintFixtures.load(c.file)), c.file)
    }

    @Test
    fun barsAreConsistentWithCpAndHp() {
        for (c in cases.filter { it.cp > 100 }) {
            val sp = data.species(c.species)
            val cpm = data.cpm(c.level)
            val ivs = Ivs(c.bars.atk, c.bars.def, c.bars.sta)
            assertEquals(c.cp, Formulas.cp(sp, ivs, cpm), c.file)
            assertEquals(c.hp, Formulas.hp(sp, ivs, cpm), c.file)
        }
    }

    @Test
    fun arcLevelMatchesVerifiedValues() {
        for (c in cases) {
            val angle = assertNotNull(ArcReader.dotAngle(PrintFixtures.load(c.file)), c.file)
            assertEquals(c.level, ArcReader.level(data, angle, 39.0), c.file)
        }
    }

    @Test
    fun detailScreenHasNoBarsButHasArc() {
        val detail = PrintFixtures.load("detalhe-topo.png")
        assertNull(BarReader.read(detail))
        assertEquals(ScreenKind.DETAIL, ScreenClassifier.classify(detail))
        assertEquals(39.0, ArcReader.level(data, assertNotNull(ArcReader.dotAngle(detail)), 39.0))
    }

    @Test
    fun classifiesAppraisalAndOther() {
        for (c in cases) assertEquals(ScreenKind.APPRAISAL, ScreenClassifier.classify(PrintFixtures.load(c.file)), c.file)
        assertEquals(ScreenKind.OTHER, ScreenClassifier.classify(PrintFixtures.load("lista-pokemon.png")))
    }

    @Test
    fun transitionFrameIsRejected() {
        // avaliacao-1.png foi capturado no meio da animação (treinador entrando); não pode virar leitura.
        assertNull(BarReader.read(PrintFixtures.load("avaliacao-1.png")))
    }
}
