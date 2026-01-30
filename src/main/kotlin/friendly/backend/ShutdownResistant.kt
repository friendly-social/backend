package friendly.backend

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext.Element
import kotlin.coroutines.CoroutineContext.Key
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

data class ShutdownResistantOperationsContext(
    val currentRunningJobs: MutableStateFlow<Int>,
    var isGracefullyFinishing: Boolean,
) : Element {
    override val key = ShutdownResistantOperationsContext
    companion object : Key<ShutdownResistantOperationsContext> {}
}

suspend fun <T> withShutdownResistantOperations(
    gracefulPeriod: Duration? = null,
    block: suspend () -> T,
): T {
    val context = ShutdownResistantOperationsContext(
        currentRunningJobs = MutableStateFlow(0),
        isGracefullyFinishing = false,
    )
    val thread = thread(start = false) {
        runBlocking {
            context.isGracefullyFinishing = true
            var job: Job? = null
            job = launch {
                if (gracefulPeriod != null) {
                    launch {
                        delay(gracefulPeriod)
                        job?.cancel()
                    }
                }
                context.currentRunningJobs.first { it == 0 }
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(thread)
    return try {
        withContext(context) {
            block()
        }
    } finally {
        Runtime.getRuntime().removeShutdownHook(thread)
    }
}

/**
 * All the jobs that are currently running are guaranteed to be finished before
 * the application exit. All the jobs that will try to start execution when
 * already finishing, are guaranteed not to start.
 */
suspend fun <T> shutdownResistant(block: suspend () -> T): T {
    val context = coroutineContext[ShutdownResistantOperationsContext]
        ?: error("use withAtomicOperations() somewhere at the root level")
    if (context.isGracefullyFinishing) {
        throw InterruptedException("JVM is finishing right now")
    }
    context.currentRunningJobs.update { it + 1 }
    if (context.isGracefullyFinishing) {
        context.currentRunningJobs.update { it - 1 }
        throw InterruptedException("JVM is finishing right now")
    }
    return try {
        block()
    } finally {
        context.currentRunningJobs.update { it - 1 }
    }
}
