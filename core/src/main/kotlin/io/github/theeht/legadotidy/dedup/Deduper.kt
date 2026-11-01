package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEntry
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageResult
import io.github.theeht.legadotidy.probe.ProbeStageStatus
import io.github.theeht.legadotidy.probe.ProbeStatus

/** HTML åŸå‹ä½¿ç”¨çš„ URL å®Œå…¨é‡å¤åˆ†ç»„ã€‚ */
enum class DedupKind(val label: String) {
    EXACT("ç›¸åŒ URL"),
    FINGERPRINT("è§„åˆ™æŒ‡çº¹ç›¸åŒ"),
    SIMILAR("è§„åˆ™é«˜åº¦ç›¸ä¼¼"),
}

/**
 * ä¿ç•™æµæ°´çº¿æ—§é…ç½®å…¥å£çš„å…¼å®¹ç±»å‹ã€‚
 * URL-only å»é‡ä¸ HTML åŸå‹ä¸€è‡´ï¼Œä¸å†ä½¿ç”¨ç›¸ä¼¼åº¦é˜ˆå€¼æˆ– shingle å€™é€‰å‚æ•°ã€‚
 */
data class DedupConfig(
    @Deprecated("å»é‡ç°åœ¨åªæŒ‰è§„èŒƒåŒ– URL åˆ†ç»„") val similarityThreshold: Double = 0.92,
    @Deprecated("å»é‡ç°åœ¨åªæŒ‰è§„èŒƒåŒ– URL åˆ†ç»„") val minSharedShingles: Int = 3,
    @Deprecated("å»é‡ç°åœ¨åªæŒ‰è§„èŒƒåŒ– URL åˆ†ç»„") val maxShingleDocFreq: Int = 60,
)

data class DedupGroup(
    val kind: DedupKind,
    /** ç»„å†…æˆå‘˜åœ¨è¾“å…¥åˆ—è¡¨é‡Œçš„ä¸‹æ ‡ã€‚ */
    val members: List<Int>,
    val keep: Int,
    val similarity: Double,
    /** å¯¹æœ¬ç»„æ¨èä¿ç•™é¡¹çš„å¯è¯»è§£é‡Šã€‚ */
    val recommendationReason: String = "",
) {
    val size: Int get() = members.size
}

data class DedupReport(
    val groups: List<DedupGroup>,
    val total: Int,
) {
    /** æ¯ç»“å»ºè®®ä¿ç•™ä¸€é¡¹ï¼Œå…¶ä½™å‡ä¸ºå€™é€‰åˆ é™¤é¡¹ã€‚ */
    val keepCount: Int = total - groups.sumOf { it.size - 1 }
    val exactGroups: Int get() = groups.count { it.kind == DedupKind.EXACT }
    val fingerprintGroups: Int get() = 0
    val similarGroups: Int get() = 0
}

private data class CandidateQuality(
    val successfulCoreStages: Int,
    val coreStageRanks: List<Int>,
    val failurePenalty: Int,
    val averageSuccessfulStageMs: Long,
    val coreCapabilityCount: Int,
    val auxiliaryCapabilityCount: Int,
    val ruleCompleteness: Int,
    val topLevelFieldCount: Int,
)

/**
 * å»é‡è§„åˆ™ï¼š
 * 1. ä»…æŒ‰è§„èŒƒåŒ–åçš„ bookSourceUrl åˆ†ç»„ï¼›
 * 2. å…ˆæ¯”è¾ƒæ ¸å¿ƒé˜¶æ®µå®é™…ç»“æœï¼šæœç´¢ã€ä¹¦ç±ä¿¡æ¯ã€ç›®å½•ã€æ­£æ–‡ï¼›
 * 3. å†æ¯”è¾ƒé”™è¯¯æƒ…å†µä¸æˆåŠŸé˜¶æ®µå¹³å‡è€—æ—¶ï¼›
 * 4. å†æ¯”è¾ƒæ ¸å¿ƒ/è¾…åŠ©èƒ½åŠ›é…ç½®å’Œè§„åˆ™å®Œæ•´åº¦ï¼›
 * 5. JSON é¡¶å±‚ä¸šåŠ¡å­—æ®µæ•°é‡åªä½œä¸ºæœ€åä¸€ä¸ªè´¨é‡å› ç´ ï¼›
 * 6. æ‰€æœ‰è´¨é‡å› ç´ å®Œå…¨ç›¸åŒæ—¶ï¼Œç¨³å®šä¿ç•™è¾“å…¥é¡ºåºé å‰çš„å€™é€‰ã€‚
 *
 * æ¯ç»„å§‹ç»ˆäº§ç”Ÿä¸€ä¸ªç¡®å®šçš„æ¨èé¡¹ï¼›æœ¬é˜¶æ®µåªç”Ÿæˆå»ºè®®ï¼Œä¸ä¿®æ”¹åŸå§‹ä¹¦æºã€‚
 */
object Deduper {

    private val URL_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    private val NON_QUALITY_TOP_LEVEL_FIELDS = setOf(
        "bookSourceUrl",
        "bookSourceName",
        "bookSourceGroup",
        "bookSourceType",
        "enabled",
        "enabledExplore",
        "bookSourceEnabled",
        "weight",
        "bookSourceComment",
        "variableComment",
        "customOrder",
        "lastUpdateTime",
        "respondTime",
    )

    private val CORE_STAGE_LABELS = listOf("æœç´¢", "ä¹¦ç±ä¿¡æ¯", "ç›®å½•", "æ­£æ–‡")

    @Suppress("UNUSED_PARAMETER")
    fun run(
        sources: List<BookSourceDoc>,
        config: DedupConfig = DedupConfig(),
        probeResults: Map<Int, ProbeResult> = emptyMap(),
    ): DedupReport {
        if (sources.size <= 1) return DedupReport(emptyList(), sources.size)

        val buckets = LinkedHashMap<String, MutableList<Int>>()
        sources.forEachIndexed { index, source ->
            val key = canonicalSourceUrl(source.url)
            if (key.isNotEmpty()) buckets.getOrPut(key) { ArrayList(2) }.add(index)
        }

        val qualities = sources.indices.associateWith { index ->
            assessQuality(sources[index], probeResults[index])
        }
        val groups = buckets.values
            .asSequence()
            .filter { it.size >= 2 }
            .map { members ->
                val ranked = members.sortedWith { left, right ->
                    compareQuality(qualities.getValue(left), qualities.getValue(right))
                        .takeIf { it != 0 } ?: left.compareTo(right)
                }
                val keep = ranked.first()
                val runnerUp = ranked.getOrNull(1)
                DedupGroup(
                    kind = DedupKind.EXACT,
                    members = members.toList(),
                    keep = keep,
                    similarity = 1.0,
                    recommendationReason = runnerUp?.let {
                        explainRecommendation(
                            winner = qualities.getValue(keep),
                            runnerUp = qualities.getValue(it),
                            winnerProbe = probeResults[keep],
                            runnerUpProbe = probeResults[it],
                        )
                    } ?: "é‡å¤ç»„åªæœ‰ä¸€ä¸ªå€™é€‰ï¼Œæ— éœ€æ¯”è¾ƒã€‚",
                )
            }
            .toList()

        return DedupReport(groups, sources.size)
    }

    /** URL å½’ä¸€åŒ–ä¿æŒç°æœ‰è§„åˆ™ä¸å˜ã€‚ */
    fun canonicalSourceUrl(raw: String): String = raw.trim()
        .lowercase()
        .replace(URL_SCHEME, "")
        .removePrefix("www.")
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')

    private fun assessQuality(source: BookSourceDoc, probe: ProbeResult?): CandidateQuality {
        val entryStage = if (probe?.entry == ProbeEntry.EXPLORE) ProbeStage.EXPLORE else ProbeStage.SEARCH
        val expectedStages = when (probe?.depth ?: ProbeDepth.FULL_CHAIN) {
            ProbeDepth.SEARCH -> listOf(entryStage)
            ProbeDepth.WITH_TOC -> listOf(entryStage, ProbeStage.BOOK_INFO ProbeStage.TOC)
            ProbeDepth.FULL_CHAIN -> listOf(entryStage, ProbeStage.BOOK_INFO ProbeStage.TOC ProbeStage.CONTENT)
        }
        val actualStages = probe?.stageResults.orEmpty().associateBy { it.stage }

        fun fallbackEntryRank(): Int = when (probe?.status) {
            ProbeStatus.OK -> 4
            ProbeStatus.PARTIAL -> 0
            ProbeStatus.PARSE_EMPTY -> 2
            ProbeStatus.NETWORK, ProbeStatus.TIMEOUT, ProbeStatus.HTTP_ERROR -> 1
            ProbeStatus.SKIPPED -> -1
            null -> -2
            else -> 0
        }

        fun rank(stage: ProbeStage): Int {
            if (probe == null) return -2
            val actual = actualStages[stage]
            if (actual != null) return stageRank(actual)
            if (probe.stageResults.isEmpty() && stage == entryStage) return fallbackEntryRank()
            if (probe.stageResults.isEmpty() && probe.status == ProbeStatus.OK && stage in expectedStages) return 4
            return -2
        }

        val stageRanks = expectedStages.map(::rank)
        val successfulCount = if (probe == null) {
            0
        } else if (probe.stageResults.isEmpty() && probe.status == ProbeStatus.OK) {
            expectedStages.size
        } else if (probe.stageResults.isEmpty() && probe.status == ProbeStatus.PARTIAL) {
            1
        } else {
            expectedStages.count { actualStages[it]?.status == ProbeStageStatus.SUCCESS }
        }

        val relevantResults = probe?.stageResults.orEmpty().filter { it.stage in expectedStages }
        val failurePenalty = if (probe == null || relevantResults.isEmpty()) {
            fallbackFailurePenalty(probe?.status)
        } else {
            relevantResults.sumOf { stage ->
                when (stage.status) {
                    ProbeStageStatus.SUCCESS, ProbeStageStatus.NOT_RUN -> 0
                    ProbeStageStatus.EMPTY -> 2
                    ProbeStageStatus.FAILED -> when (stage.failureStatus) {
                        ProbeStatus.NETWORK, ProbeStatus.TIMEOUT -> 1
                        ProbeStatus.HTTP_ERROR -> 2
                        else -> 3
                    }
                }
            }
        }

        val successfulTimes = relevantResults
            .filter { it.status == ProbeStageStatus.SUCCESS && it.elapsedMs > 0 }
            .map { it.elapsedMs }
        val averageMs = when {
            successfulTimes.isNotEmpty() -> successfulTimes.average().toLong()
            probe != null && probe.stageResults.isEmpty() && probe.status == ProbeStatus.OK && probe.elapsedMs > 0 ->
                probe.elapsedMs / expectedStages.size.coerceAtLeast(1)
            else -> Long.MAX_VALUE
        }

        return CandidateQuality(
            successfulCoreStages = successfulCount,
            coreStageRanks = stageRanks,
            failurePenalty = failurePenalty,
            averageSuccessfulStageMs = averageMs,
            coreCapabilityCount = coreCapabilityCount(source),
            auxiliaryCapabilityCount = auxiliaryCapabilityCount(source),
            ruleCompleteness = source.ruleCompleteness,
            topLevelFieldCount = qualityTopLevelFieldCount(source),
        )
    }

    private fun stageRank(result: ProbeStageResult): Int = when (result.status) {
        ProbeStageStatus.SUCCESS -> 4
        ProbeStageStatus.EMPTY -> 2
        ProbeStageStatus.FAILED -> when (result.failureStatus) {
            ProbeStatus.NETWORK, ProbeStatus.TIMEOUT, ProbeStatus.HTTP_ERROR -> 1
            else -> 0
        }
        ProbeStageStatus.NOT_RUN -> -1
    }

    private fun fallbackFailurePenalty(status: ProbeStatus?): Int = when (status) {
        ProbeStatus.NETWORK, ProbeStatus.TIMEOUT -> 1
        ProbeStatus.HTTP_ERROR -> 2
        ProbeStatus.PARSE_EMPTY -> 2
        ProbeStatus.SKIPPED, null, ProbeStatus.OK -> 0
        else -> 3
    }

    /** Negative means left is preferred; JSON field count and input order are deliberately last. */
    private fun compareQuality(left: CandidateQuality, right: CandidateQuality): Int {
        fun descending(a: Int, b: Int): Int = b.compareTo(a)
        descending(left.successfulCoreStages, right.successfulCoreStages).takeIf { it != 0 }?.let { return it }
        for (index in 0 until minOf(left.coreStageRanks.size, right.coreStageRanks.size)) {
            descending(left.coreStageRanks[index], right.coreStageRanks[index]).takeIf { it != 0 }?.let { return it }
        }
        left.failurePenalty.compareTo(right.failurePenalty).takeIf { it != 0 }?.let { return it }
        left.averageSuccessfulStageMs.compareTo(right.averageSuccessfulStageMs).takeIf { it != 0 }?.let { return it }
        descendini¨¡±•™Ğ¹½É•…Á…‰¥±¥Ñå½Õ¹Ğ°É¥¡Ğ¹½É•…Á…‰¥±¥Ñå½Õ¹Ğ¤¹Ñ…­•%˜ì¥Ğ€„ô€Àôü¹±•ĞìÉ•ÑÕÉ¸¥Ğô(€€€€€€€‘•Í•¹‘¥¹œ¡±•™Ğ¹…Õá¥±¥…Éå…Á…‰¥±¥Ñå½Õ¹Ğ°É¥¡Ğ¹…Õá¥±¥…Éå…Á…‰¥±¥Ñå½Õ¹Ğ¤¹Ñ…­•%˜ì¥Ğ€„ô€Àôü¹±•ĞìÉ•ÑÕÉ¸¥Ğô(€€€€€€€‘•Í•¹‘¥¹œ¡±•™Ğ¹ÉÕ±•½µÁ±•Ñ•¹•ÍÌ°É¥¡Ğ¹ÉÕ±•½µÁ±•Ñ•¹•ÍÌ¤¹Ñ…­•%˜ì¥Ğ€„ô€Àôü¹±•ĞìÉ•ÑÕÉ¸¥Ğô(€€€€€€€‘•Í•¹‘¥¹œ¡±•™Ğ¹Ñ½Á1•Ù•±¥•±‘½Õ¹Ğ°É¥¡Ğ¹Ñ½Á1•Ù•±¥•±‘½Õ¹Ğ¤¹Ñ…­•%˜ì¥Ğ€„ô€Àôü¹±•ĞìÉ•ÑÕÉ¸¥Ğô(€€€€€€€É•ÑÕÉ¸€À(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸•áÁ±…¥¹I•½µµ•¹‘…Ñ¥½¸ (€€€€€€€İ¥¹¹•Èè…¹‘¥‘…Ñ•EÕ…±¥Ñä°(€€€€€€€ÉÕ¹¹•ÉUÀè…¹‘¥‘…Ñ•EÕ…±¥Ñä°(€€€€€€€İ¥¹¹•ÉAÉ½‰”èAÉ½‰•I•ÍÕ±Ğü°(€€€€€€€ÉÕ¹¹•ÉUÁAÉ½‰”èAÉ½‰•I•ÍÕ±Ğü°(€€€€¤èMÑÉ¥¹œì(€€€€€€€¥˜€¡İ¥¹¹•È¹ÍÕ•ÍÍ™Õ±½É•MÑ…•Ì€„ôÉÕ¹¹•ÉUÀ¹ÍÕ•ÍÍ™Õ±½É•MÑ…•Ì¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹š‚ã–ş–*¢÷š"C–*¦bÛšº×šnÓ–’k¾ò ‘íİ¥¹¹•È¹ÍÕ•ÍÍ™Õ±½É•MÑ…•Íô¼Ó¾ò3–¾çš¾P€‘íÉÕ¹¹•ÉUÀ¹ÍÕ•ÍÍ™Õ±½É•MÑ…•Íô¼Ó¾ò'ˆ(€€€€€€€ô(€€€€€€€™½È€¡¥¹‘•à¥¸€ÀÕ¹Ñ¥°µ¥¹=˜¡İ¥¹¹•È¹½É•MÑ…•I…¹­Ì¹Í¥é”°ÉÕ¹¹•ÉUÀ¹½É•MÑ…•I…¹­Ì¹Í¥é”¤¤ì(€€€€€€€€€€€¥˜€¡İ¥¹¹•È¹½É•MÑ…•I…¹­Ím¥¹‘•át€„ôÉÕ¹¹•ÉUÀ¹½É•MÑ…•I…¹­Ím¥¹‘•át¤ì(€€€€€€€€€€€€€€€Ù…°±…‰•°€ô¥˜€¡İ¥¹¹•ÉAÉ½‰”ü¹•¹ÑÉä€ôôAÉ½‰•¹ÑÉä¹aA1=I€˜˜¥¹‘•à€ôô€À¤ì(€€€€€€€€€€€€€€€€€€€€‹–>G:Àˆ(€€€€€€€€€€€€€€€ô•±Í”ì(€€€€€€€€€€€€€€€€€€€=I}MQ}1	1L¹•Ñ=É±Í”¡¥¹‘•à¤ì€‹š‚ã–ş–*¢ôˆô(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€€€€€É•ÑÕÉ¸€ˆ‘±…‰•³¦bÛšº×j–º{¦fš‚‡¦ª3îOšzsšnÓ––÷ˆ(€€€€€€€€€€€ô(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•È¹™…¥±ÕÉ•A•¹…±Ñä€„ôÉÕ¹¹•ÉUÀ¹™…¥±ÕÉ•A•¹…±Ñä¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹šr³š²‡š‚‡¦ª3’â·j¦Rg¢¾¿–J3–’Ç¢Ò—š–×¢ú¢öïˆ(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•È¹…Ù•É…•MÕ•ÍÍ™Õ±MÑ…•5Ì€„ôÉÕ¹¹•ÉUÀ¹…Ù•É…•MÕ•ÍÍ™Õ±MÑ…•5Ì¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹š‚ã–ş¦bÛšº×–æÏ–v–N7–êSšnÓ–ş¯¾ò ‘í±…Ñ•¹å1…‰•°¡İ¥¹¹•È¹…Ù•É…•MÕ•ÍÍ™Õ±MÑ…•5Ì¥÷¾ò3–¾çš¾P€‘í±…Ñ•¹å1…‰•°¡ÉÕ¹¹•ÉUÀ¹…Ù•É…•MÕ•ÍÍ™Õ±MÑ…•5Ì¥÷¾ò'ˆ(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•È¹½É•…Á…‰¥±¥Ñå½Õ¹Ğ€„ôÉÕ¹¹•ÉUÀ¹½É•…Á…‰¥±¥Ñå½Õ¹Ğ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹š‚ã–ş–*¢÷¦7ö»šnÓ–º3šVÓˆ(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•È¹…Õá¥±¥…Éå…Á…‰¥±¥Ñå½Õ¹Ğ€„ôÉÕ¹¹•ÉUÀ¹…Õá¥±¥…Éå…Á…‰¥±¥Ñå½Õ¹Ğ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹–>G:Ãšº×¢¾¶'¢ú–*§¢÷–*o¦7ö»šnÓ–º3šVÓˆ(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•È¹ÉÕ±•½µÁ±•Ñ•¹•ÍÌ€„ôÉÕ¹¹•ÉUÀ¹ÉÕ±•½µÁ±•Ñ•¹•ÍÌ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹¦v{¦ë¢†3’âë¦7ö»¦†çšnÓ–’kˆ(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•È¹Ñ½Á1•Ù•±¥•±‘½Õ¹Ğ€„ôÉÕ¹¹•ÉUÀ¹Ñ½Á1•Ù•±¥•±‘½Õ¹Ğ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹–&7¢şÃ–nƒÒƒnã–B3¾ò1)M=8ƒ¦†Û–Æ’âk–*‡–¶_šº×šVÃ¦?šnÓ–’k¾ò#šr–B;–s–êW–nƒÒƒ¾ò'ˆ(€€€€€€€ô(€€€€€€€¥˜€¡İ¥¹¹•ÉAÉ½‰”ü¹ÍÑ…ÑÕÌ€„ôÉÕ¹¹•ÉUÁAÉ½‰”ü¹ÍÑ…ÑÕÌ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€‹¦bÛšº×îOšzsnã–B3¾ò3šVÓ’öOš‚‡¦ª3îO¢ºëšnÓ––÷ˆ(€€€€€€€ô(€€€€€€€É•ÑÕÉ¸€‹–B¦†ç¢Ò£¦?–nƒÒƒnã–B3¾ò3š2'–:–/–¾ó–—¦†ë–ê?¢Ï–ºk’şwVgˆ(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸±…Ñ•¹å1…‰•°¡Ù…±Õ”è1½¹œ¤èMÑÉ¥¹œ€ô(€€€€€€€¥˜€¡Ù…±Õ”€ôô1½¹œ¹5a}Y1U¤€‹š^ƒ–>¿š¾S¢ú¢_š^Øˆ•±Í”€ˆ‘íÙ…±Õ•õµÌˆ((€€€ÁÉ¥Ù…Ñ”™Õ¸½É•…Á…‰¥±¥Ñå½Õ¹Ğ¡Í½ÕÉ”è	½½­M½ÕÉ•½Œ¤è%¹Ğ€ô±¥ÍÑ=˜ (€€€€€€€Í½ÕÉ”¹…¹M•…É °(€€€€€€€€…Í½ÕÉ”¹ÉÕ±•	½½­%¹™¼¹¥ÍµÁÑä ¤°(€€€€€€€€…Í½ÕÉ”¹ÉÕ±•Q½Œ¹¥ÍµÁÑä ¤°(€€€€€€€€…Í½ÕÉ”¹ÉÕ±•½¹Ñ•¹Ğ¹¥ÍµÁÑä ¤°(€€€€¤¹½Õ¹Ğì¥Ğô((€€€ÁÉ¥Ù…Ñ”™Õ¸…Õá¥±¥…Éå…Á…‰¥±¥Ñå½Õ¹Ğ¡Í½ÕÉ”è	½½­M½ÕÉ•½Œ¤è%¹Ğ€ô±¥ÍÑ=˜ (€€€€€€€Í½ÕÉ”¹•áÁ±½É•UÉ°¹¥Í9½Ñ	±…¹¬ ¤ñğ€…Í½ÕÉ”¹ÉÕ±•áÁ±½É”¹¥ÍµÁÑä ¤°(€€€€€€€€…Í½ÕÉ”¹ÉÕ±•I•Ù¥•Ü¹¥ÍµÁÑä ¤°(€€€€¤¹½Õ¹Ğì¥Ğô((€€€€¼¨¨ƒR£š"ß–?––÷–Ë’ë–šVÃš6»–J3î¢º‡–¶_šº×’â7–>’â;šr–B;j)M=8ƒ–¶_šº×šVÃ¦?š¾S¢ú€¨¼(€€€ÁÉ¥Ù…Ñ”™Õ¸ÅÕ…±¥ÑåQ½Á1•Ù•±¥•±‘½Õ¹Ğ¡Í½ÕÉ”è	½½­M½ÕÉ•½Œ¤è%¹Ğ€ô(€€€€€€€Í½ÕÉ”¹É…Ü¹•¹ÑÉåM•Ğ ¤¹½Õ¹Ğì€¡­•ä°Ù…±Õ”¤€´ø(€€€€€€€€€€€­•ä€…¥¸9=9}EU1%Qe}Q=A}1Y1}%1L€˜˜€…Ù…±Õ”¹¥Í)Í½¹9Õ±°(€€€€€€€ô)ô(