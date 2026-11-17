package com.raiddex.domain

import java.text.Normalizer

object Search {
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** Busca aproximada: prefixo, contém, ou até 2 erros de digitação no prefixo. */
    fun species(all: List<Species>, query: String, limit: Int = 8): List<Species> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return all.mapNotNull { sp ->
            val n = normalize(sp.name)
            val score = when {
                n == q -> 0
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                else -> {
                    val d = distance(q, n.take(q.length))
                    if (d <= 2 && q.length >= 3) 3 + d else null
                }
            }
            score?.let { it to sp }
        }.sortedWith(compareBy({ it.first }, { it.second.mega }, { it.second.dex })).take(limit).map { it.second }
    }
}
