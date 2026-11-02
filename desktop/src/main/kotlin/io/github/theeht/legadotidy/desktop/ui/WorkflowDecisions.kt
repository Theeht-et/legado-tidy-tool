package io.github.theeht.legadotidy.desktop.ui

import com.google.gson.JsonObject
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceField
import io.github.theeht.legadotidy.organize.OrganizeRule
import io.github.theeht.legadotidy.probe.ProbeStatus
import java.io.File
import java.nio.file.Path

/**
 * Pure decision result for committing a stage's delete marks.
 * It deliberately works on stable-ID entries rather than list indexes.
 */
internal data class DeletionPlan<T>(
    val canAdvance: Boolean,
    val retained: List<T>,
    val removedCount: Int,
    val allItemsMarked: Boolean,
)

internal fun <T> planMarkedDeletions(
    items: List<T>,
    isMarked: (T) -> Boolean,
): DeletionPlan<T> {
    val markedCount = items.count(isMarked)
    val allMarked = items.isNotEmpty() && markedCount == items.size
    return DeletionPlan(
        // An empty retained set is a valid outcome: never rescue a marked source merely
        // to keep one item in a duplicate group or to make the export non-empty.
        canAdvance = true,
        retained = items.filterNot(isMarked),
        removedCount = markedCount,
        allItemsMarked = allMarked,
    )
}

/**
 * A later-stage back-navigation must not leak deduplication/organization marks into
 * validation. Previously committed validation removals are already absent from the pool,
 * so even an old failed probe must not silently recreate a validation delete mark.
 */
internal data class ValidationReviewReset(
    val deleteMarked: Boolean,
    val userDecision: Boolean,
    val suggestion: String,
)

internal fun validationReviewReset(status: ProbeStatus?): ValidationReviewReset =
    ValidationReviewReset(
        deleteMarked = false,
        userDecision = false,
        suggestion = when (status) {
            null -> "待重新校验"
            ProbeStatus.OK, ProbeStatus.UNCHECKABLE -> "建议保留"
            ProbeStatus.SKIPPED -> "跳过校验"
            else -> "建议复核"
        },
    )

/** Probe outcomes that should enter the pending-delete bucket by default. */
internal fun shouldMarkForDeletionByDefault(status: ProbeStatus): Boolean =
    status != ProbeStatus.OK && status != ProbeStatus.UNCHECKABLE

/**
 * The dialog may display a suggested candidate, but that suggestion is not a user
 * decision. Never initialize the selection to a candidate already marked for deletion.
 */
internal fun initialDuplicateKeepId(
    candidates: List<Pair<Int, Boolean>>,
    suggestedKeepId: Int,
): Int? =
    candidates.firstOrNull { (id, marked) -> id == suggestedKeepId && !marked }?.first
        ?: candidates.firstOrNull { (_, marked) -> !marked }?.first

internal fun canSaveDuplicateChoice(
    selectionMade: Boolean,
    keepAll: Boolean,
    keepId: Int?,
    candidateIds: List<Int>,
): Boolean =
    keepAll || (selectionMade && keepId != null && keepId in candidateIds)

/** A manual decision must survive recalculating duplicate groups and other automatic suggestions. */
internal fun resolveDeleteMark(
    defaultMarked: Boolean,
    currentMarked: Boolean,
    hasUserDecision: Boolean,
): Boolean = if (hasUserDecision) currentMarked else defaultMarked

/**
 * Keep the exception/review bucket mutually exclusive with pending deletion.
 * A manually restored delete mark makes the outcome visible for review again.
 */
internal fun shouldShowInExceptionReview(status: ProbeStatus?, deleteMarked: Boolean): Boolean =
    !deleteMarked && status != null &&
        status != ProbeStatus.OK && status != ProbeStatus.UNCHECKABLE && status != ProbeStatus.SKIPPED

/**
 * Single source of truth for the category filters shown above the source list.
 * Both row visibility and the displayed counts use this predicate.
 */
internal fun isSourceIncludedInFilter(
    filter: String,
    hasDuplicateGroup: Boolean,
    needsProbe: Boolean,
    probeStatus: ProbeStatus?,
    deleteMarked: Boolean,
): Boolean = when (filter) {
    "全部" -> true
    "重复候选" -> hasDuplicateGroup
    "待校验" -> needsProbe
    "校验通过" -> probeStatus == ProbeStatus.OK || probeStatus == ProbeStatus.UNCHECKABLE
    "异常 / 复核" -> shouldShowInExceptionReview(probeStatus, deleteMarked)
    "待删除" -> deleteMarked
    else -> true
}

internal fun <T> resolveSelectedId(
    selectedId: Int?,
    items: List<T>,
    idOf: (T) -> Int,
): Int? {
    if (selectedId != null && items.any { idOf(it) == selectedId }) return selectedId
    return items.firstOrNull()?.let(idOf)
}

/** Map engine/core result indexes to stable working-set IDs before deleting or reordering anything. */
internal fun <T> stableIdsAtIndexes(
    items: List<T>,
    indexes: List<Int>,
    idOf: (T) -> Int,
): List<Int> = indexes.mapNotNull { index -> items.getOrNull(index)?.let(idOf) }.distinct()

/** Resolve a nonblank user-provided directory without silently using the process working directory. */
internal fun resolveExportDirectory(directory: String): Result<Path> = runCatching {
    require(directory.isNotBlank()) { "导出目录不能为空" }
    Path.of(directory).toAbsolutePath().normalize()
}

/**
 * Pick a fresh output name, never overwriting the imported input or an existing export.
 * The base name normally comes from [io.github.theeht.legadotidy.export.Exporter.defaultFileName].
 */
internal fun nextSafeExportFileName(
    inputFile: File?,
    exportDir: File,
    baseName: String,
): String {
    val inputPath = inputFile?.absoluteFile?.toPath()?.normalize()
    var suffix = 0
    while (true) {
        val candidate = when (suffix) {
            0 -> baseName
            1 -> baseName.removeSuffix(".json") + "-cleaned.json"
            else -> baseName.removeSuffix(".json") + "-cleaned-${suffix}.json"
        }
        val outputFile = File(exportDir, candidate)
        val outputPath = outputFile.absoluteFile.toPath().normalize()
        if (outputPath != inputPath && !outputFile.exists()) return candidate
        suffix++
    }
}


/**
 * Organizer groups rows by type for display, but that grouping alone must not alter
 * the JSON array order. Only a checked explicit ordering rule may do that.
 */
internal fun usesOrganizerOutputOrder(rules: Set<OrganizeRule>): Boolean =
    OrganizeRule.SORT_BY_RESPONSE_TIME in rules

/** Preserving an existing group is a no-op; only regrouping or manual edits need a JSON write. */
internal fun shouldApplyGroups(rules: Set<OrganizeRule>, hasManualGroupEdits: Boolean): Boolean =
    OrganizeRule.DROP_ORIGINAL_GROUPS in rules ||
        OrganizeRule.REGROUP_BY_TYPE in rules ||
        OrganizeRule.REGROUP_BY_QUALITY in rules ||
        hasManualGroupEdits

/**
 * A previous probe is reusable only while every input that can change the executed
 * upstream request/rule chain is unchanged. Display metadata and enable/list state
 * remain deliberately excluded by BookSourceDoc.behaviorFields().
 */
internal fun probeInputsChanged(before: BookSourceDoc, after: BookSourceDoc): Boolean =
    before.url != after.url ||
        before.type != after.type ||
        before.behaviorFields() != after.behaviorFields()

private const val LEGACY_ENABLED_FIELD = "bookSourceEnabled"

/** Effective enabled state across the modern and legacy JSON field names. */
internal fun isDocumentEnabled(doc: BookSourceDoc): Boolean {
    val legacyEnabled = doc.raw.get(LEGACY_ENABLED_FIELD)?.let { value ->
        if (value.isJsonNull) null else runCatching { value.asBoolean }.getOrNull()
    }
    return doc.enabled && legacyEnabled != false
}

/**
 * Write both names when the legacy field exists. Keeping a stale legacy false value
 * would otherwise make the UI continue to display a source as disabled after editing.
 */
internal fun setDocumentEnabled(raw: JsonObject, enabled: Boolean) {
    raw.addProperty(SourceField.ENABLED, enabled)
    if (raw.has(LEGACY_ENABLED_FIELD)) raw.addProperty(LEGACY_ENABLED_FIELD, enabled)
}
