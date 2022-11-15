package com.lalilu.lmusic.utils.extension

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/**
 * 将[Flow]转为可手动更新并将更新下传至其他[Flow]的[UpdatableFlow]
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatableFlow<T>(private val flow: Flow<T>) : Flow<T> {
    private val currentTimeFlow = MutableStateFlow(System.currentTimeMillis())
    private val rootFlow = currentTimeFlow.flatMapLatest { flow }

    override suspend fun collect(collector: FlowCollector<T>) {
        rootFlow.collect(collector)
    }

    fun requireUpdate() {
        currentTimeFlow.tryEmit(System.currentTimeMillis())
    }
}

fun <T> Flow<T>.toUpdatableFlow(): UpdatableFlow<T> {
    return UpdatableFlow(this)
}


@OptIn(ExperimentalCoroutinesApi::class)
class CachedFlow<T>(flow: Flow<T>) : Flow<T> {
    @Volatile
    private var cache: T? = null
    private val rootFlow = flow.mapLatest { it.also { cache = it } }

    override suspend fun collect(collector: FlowCollector<T>) {
        rootFlow.collect(collector)
    }

    fun get(): T? = cache
}

fun <T> Flow<T>.toCachedFlow(): CachedFlow<T> {
    return CachedFlow(this)
}

/**
 * 将Flow转换为State
 */
fun <T> Flow<T>.toState(scope: CoroutineScope): State<T?> {
    return mutableStateOf<T?>(null).also { state ->
        this.onEach { state.value = it }.launchIn(scope)
    }
}

/**
 * 将Flow转换为State，附带初始值
 */
fun <T> Flow<T>.toState(defaultValue: T, scope: CoroutineScope): State<T> {
    return mutableStateOf(defaultValue).also { state ->
        this.onEach { state.value = it }.launchIn(scope)
    }
}