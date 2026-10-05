package com.example.autotap.domain.model

sealed interface ExecutionState {
    object Idle : ExecutionState

    data class Running(
        val currentStepIndex: Int,
        val totalSteps: Int,
        val currentRepeat: Int,
        val totalRepeats: Int,
        val isDebugPaused: Boolean = false,
        val isSearching: Boolean = false,
        val searchProgress: Float = 0f,
        val searchTitle: String = "",
        val stepType: ActionType = ActionType.CLICK,
        val stepDescription: String = "",
        val stepProgress: Float = 0f,
        val searchStartTime: Long = 0L,
        val searchTimeoutMs: Long = 5000L,
        val isInfiniteSearch: Boolean = false,
        val targetTagOrName: String = "",
        val activeNodeId: String = "",
        val activeNodeTitle: String = ""
    ) : ExecutionState

    data class Paused(val reason: PauseReason) : ExecutionState

    data class Completed(
        val totalExecutedSteps: Int,
        val durationMs: Long
    ) : ExecutionState

    data class Error(
        val throwable: Throwable,
        val message: String
    ) : ExecutionState
}

enum class PauseReason {
    USER_MANUAL,
    STEP_BY_STEP_DEBUG,
    THERMAL_THROTTLING,
    LOW_BATTERY
}
