package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

fun PipelineCoroutineScope(): CoroutineScope {
    return CoroutineScope(Job() + Dispatchers.Unconfined)
}