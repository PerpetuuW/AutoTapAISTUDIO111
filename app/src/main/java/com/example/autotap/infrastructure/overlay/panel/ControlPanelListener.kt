package com.example.autotap.infrastructure.overlay.panel

interface ControlPanelListener {
    fun onPlayClicked()
    fun onPlayLongClicked()
    fun onAddClicked()
    fun onAddActionSelected(type: com.example.autotap.domain.model.ActionType)
    fun onAddActionSelected(type: com.example.autotap.domain.model.ActionType, isNeural: Boolean)
    fun onRecordClicked()
    fun onSmartRecordClicked()
    fun onCaptureClicked()
    fun onSaveClicked()
    fun onScriptsClicked()
    fun onSettingsClicked()
    fun onJoystickClicked()
    fun onScreenLockClicked()
    fun onClearAllClicked()
    fun onToggleNumbersClicked()
    fun onHelpClicked()
    fun onLogsClicked()
    fun onCloseClicked()
    fun onGraphClicked()
}