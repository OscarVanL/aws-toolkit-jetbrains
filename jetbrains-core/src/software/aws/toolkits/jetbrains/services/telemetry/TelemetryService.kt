// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import software.amazon.awssdk.services.toolkittelemetry.model.Unit
import software.aws.toolkits.core.telemetry.DefaultMetricEvent
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_NA
import software.aws.toolkits.core.telemetry.DefaultMetricEvent.Companion.METADATA_NOT_SET
import software.aws.toolkits.core.telemetry.MetricEvent
import software.aws.toolkits.core.telemetry.TelemetryBatcher
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.activeAwsAccount
import software.aws.toolkits.jetbrains.core.credentials.activeRegion
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

interface TelemetryService : Disposable {
    data class MetricEventMetadata(
        val awsAccount: String = METADATA_NA,
        val awsRegion: String = METADATA_NA
    )

    // TODO consider using DataProvider for the metricEventMetadata.
    fun record(namespace: String, metricEventMetadata: MetricEventMetadata, buildEvent: MetricEvent.Builder.() -> kotlin.Unit = {}): MetricEvent

    fun record(project: Project?, namespace: String, buildEvent: MetricEvent.Builder.() -> kotlin.Unit = {}): CompletableFuture<MetricEvent> {
        val metricEvent = CompletableFuture<MetricEvent>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val metricEventMetadata = if (project == null) MetricEventMetadata() else MetricEventMetadata(
                    awsAccount = project.activeAwsAccount() ?: METADATA_NOT_SET,
                    awsRegion = project.activeRegion().id
                )
                metricEvent.complete(record(namespace, metricEventMetadata, buildEvent))
            } catch (e: Exception) {
                metricEvent.completeExceptionally(e)
            }
        }
        return metricEvent
    }

    companion object {
        @JvmStatic
        fun getInstance(): TelemetryService = ServiceManager.getService(TelemetryService::class.java)
    }
}

class DefaultTelemetryService(
    messageBusService: MessageBusService,
    settings: AwsSettings,
    private val batcher: TelemetryBatcher = DefaultToolkitTelemetryBatcher()
) : TelemetryService {
    private val isDisposing: AtomicBoolean = AtomicBoolean(false)
    private val startTime: Instant

    constructor(messageBusService: MessageBusService, settings: AwsSettings) : this(messageBusService, settings, DefaultToolkitTelemetryBatcher())

    init {
        messageBusService.messageBus.connect().subscribe(
            messageBusService.telemetryEnabledTopic,
            object : TelemetryEnabledChangedNotifier {
                override fun notify(isTelemetryEnabled: Boolean) {
                    batcher.onTelemetryEnabledChanged(isTelemetryEnabled)
                }
            }
        )
        messageBusService.messageBus.syncPublisher(messageBusService.telemetryEnabledTopic)
            .notify(settings.isTelemetryEnabled)

        record("ToolkitStart").also {
            startTime = it.createTime
        }
    }

    override fun dispose() {
        if (!isDisposing.compareAndSet(false, true)) {
            return
        }

        val endTime = Instant.now()
        record("ToolkitEnd") {
            createTime(endTime)
            datum("duration") {
                value(Duration.between(startTime, endTime).toMillis().toDouble())
                unit(Unit.MILLISECONDS)
            }
        }

        batcher.shutdown()
    }

    override fun record(namespace: String, metricEventMetadata: TelemetryService.MetricEventMetadata, buildEvent: MetricEvent.Builder.() -> kotlin.Unit): MetricEvent {
        val builder = DefaultMetricEvent.builder(namespace)
        buildEvent(builder)
        builder.awsAccount(metricEventMetadata.awsAccount)
        builder.awsRegion(metricEventMetadata.awsRegion)
        val event = builder.build()
        batcher.enqueue(event)
        return event
    }

    private fun record(namespace: String, buildEvent: MetricEvent.Builder.() -> kotlin.Unit = {}): MetricEvent =
        record(namespace, TelemetryService.MetricEventMetadata(), buildEvent)

    companion object {
        private val LOG = getLogger<TelemetryService>()
    }
}