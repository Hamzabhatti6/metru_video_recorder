package com.hamza.metru.utils

import com.hamza.metru.enums.PlayerMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecordingTimer( val millisInFuture: Long,
                      val countDownInterval: Long = 1000,
                      runAtStart: Boolean = false,
                      val onFinish: (() -> Unit)? = null,
                      val onTick: ((Long) -> Unit)? = null) {

    private var job: Job = Job()
     val _tick = MutableStateFlow(0L)
    val tick = _tick.asStateFlow()
    private val _playerMode = MutableStateFlow(PlayerMode.STOPPED)
    val playerMode = _playerMode.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        if (runAtStart) start()
    }

    fun start() {
        if (_tick.value == 0L) _tick.value = millisInFuture
        job.cancel()
        job = scope.launch(Dispatchers.IO) {
            _playerMode.value = PlayerMode.PLAYING
            while (isActive) {
                if (_tick.value <= 0) {
                    job.cancel()
                    onFinish?.invoke()
                    _playerMode.value = PlayerMode.STOPPED
                    return@launch
                }
                delay(timeMillis = countDownInterval)
                _tick.value -= countDownInterval
                onTick?.invoke(this@RecordingTimer._tick.value)
            }
        }
    }
    fun stop() {
        job.cancel()
        _tick.value = 0
        _playerMode.value = PlayerMode.STOPPED
    }
}