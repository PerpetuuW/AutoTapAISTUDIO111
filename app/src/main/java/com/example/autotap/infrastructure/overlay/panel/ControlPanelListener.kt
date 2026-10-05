package com.example.autotap.infrastructure.overlay.panel

interface ControlPanelListener {
    fun onPlayClicked()
    fun onPlayLongClicked()
    fun onAddClicked()
    fun onAddActionSelected(type: com.example.autotap.domain.model.ActionType) { onAddClicked() }
    fun onAddActionSelected(type: com.example.autotap.domain.model.ActionType, isNeural: Boolean) { onAddActionSelected(type) }
    fun onRecordClicked()
    fun onSmartRecordClicked() { onRecordClicked() }
    fun onCaptureClicked()
    fun onScriptsClicked()
    fun onSettingsClicked()
    fun onJoystickClicked()
    fun onScreenLockClicked()
    fun onClearAllClicked()
    fun onToggleNumbersClicked()
    fun onHelpClicked()
    fun onLogsClicked() { onHelpClicked() }
    fun onCloseClicked()
    fun onGraphClicked()
}