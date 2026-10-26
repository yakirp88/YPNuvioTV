package com.nuvio.tv.core.intro

/** No viewing-language preference. Reserve independent-provider capacity and spread unknown labels
 * across languages so a long list of ordinary captions cannot starve a later SDH result. */
internal object IntroSubtitleSearch {
    private fun diverse(items: List<IntroSubtitleOption>, limit: Int): List<IntroSubtitleOption> {
        val result = items.filter { IntroSubtitlePolicy.descriptive(it.label) }.take(limit).toMutableList()
        val groups = items.filterNot { IntroSubtitlePolicy.descriptive(it.label) }
            .groupBy { it.subtitle.lang.lowercase() }.values.map { ArrayDeque(it) }
        while (result.size < limit && groups.any { it.isNotEmpty() }) {
            for (group in groups) if (group.isNotEmpty() && result.size < limit) result += group.removeFirst()
        }
        return result
    }
    fun candidates(independent: List<IntroSubtitleOption>, installed: List<IntroSubtitleOption>): List<IntroSubtitleOption> =
        (diverse(independent.distinctBy { it.subtitle.url }, 24) +
            diverse(installed.distinctBy { it.subtitle.url }, 8)).distinctBy { it.subtitle.url }
            .sortedByDescending { IntroSubtitlePolicy.descriptive(it.label) }
}
