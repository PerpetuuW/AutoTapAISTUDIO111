package com.example.autotap.domain.gateway

interface IOverlayGateway {
    fun setOverlaysVisible(visible: Boolean)
    fun setTargetsTouchable(touchable: Boolean)
    fun requestRedraw()
    fun hideAll()
}
