package io.github.devhovai.gameyfin.storeextras

/** Scores how well a store title matches the title Gameyfin is asking for (0..100). */
object TitleMatcher {
    const val MIN_SCORE = 80
    private const val SEQUEL_CAP = 70

    private val roman = Regex("^(ii|iii|iv|v|vi|vii|viii|ix|x)$")

    fun normalize(title: String): String =
        title.lowercase()
            .replace(Regex("[™©®]"), "")
            .replace("&", " and ")
            .replace("'", "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    fun score(query: String, candidate: String): Int {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0
        if (q == c) return 100

        // "Anno 1503" vs "Anno 1503 History Edition": extra words are fine, unless they look like a sequel number.
        val (shorter, longer) = if (q.length <= c.length) q to c else c to q
        if (longer.startsWith("$shorter ")) {
            val extra = longer.removePrefix("$shorter ").split(' ')
            val looksLikeSequel = extra.any { word -> word.any(Char::isDigit) || roman.matches(word) }
            return if (looksLikeSequel) minOf(SEQUEL_CAP, similarity(q, c)) else maxOf(0, 92 - 4 * extra.size)
        }
        return similarity(q, c)
    }

    private fun similarity(a: String, b: String): Int =
        (100.0 * (1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length))).toInt()

    fun <T> best(query: String, candidates: List<T>, limit: Int, title: (T) -> String): List<T> =
        candidates.map { it to score(query, title(it)) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
