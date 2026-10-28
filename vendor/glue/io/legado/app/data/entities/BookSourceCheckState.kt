package io.legado.app.data.entities

import java.util.UUID

/** Device-local metadata; deliberately separate from source import/export JSON. */
data class BookSourceCheckState(
    val bookSourceUrl: String,
    val revision: String = UUID.randomUUID().toString(),
    val sourceRevision: String = UUID.randomUUID().toString(),
    val status: String = NEEDS_CHECK,
    val checkedAt: Long = 0,
    val detail: String = "",
) {
    companion object {
        const val NEEDS_CHECK = "NEEDS_CHECK"
        const val PASSED = "PASSED"
        const val FAILED = "FAILED"
    }
}
