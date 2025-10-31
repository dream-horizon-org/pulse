package io.opentelemetry.android.demo

class DemoWork {
    fun startWork() {
        error("This is crash as DemoWork.startWork")
    }
}