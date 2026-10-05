package com.example.autotap.domain.engine

import com.example.autotap.domain.model.MacroAction
import java.util.ArrayDeque

class SubroutineManager(private val maxDepth: Int = 50) : ISubroutineManager {

    private val stack = ArrayDeque<SubroutineFrame>()
    private val stackLock = Any()

    override val currentDepth: Int
        get() = synchronized(stackLock) { stack.size }

    override fun pushFrame(scenarioName: String, actions: List<MacroAction>, returnIndex: Int): Boolean {
        synchronized(stackLock) {
            if (stack.size >= maxDepth) {
                return false
            }
            stack.push(SubroutineFrame(scenarioName, actions, returnIndex))
            return true
        }
    }

    override fun popFrame(): SubroutineFrame? {
        synchronized(stackLock) {
            return if (stack.isNotEmpty()) stack.pop() else null
        }
    }

    override fun clear() {
        synchronized(stackLock) {
            stack.clear()
        }
    }
}
