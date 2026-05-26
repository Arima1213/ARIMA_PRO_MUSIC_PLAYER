package com.arima.pro.core.audio

sealed class DacState {
    object NotDetected : DacState()
    data class Detected(val dacInfo: DacInfo) : DacState()
}
