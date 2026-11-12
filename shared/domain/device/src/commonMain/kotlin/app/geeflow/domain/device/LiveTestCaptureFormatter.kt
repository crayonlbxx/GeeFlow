package app.geeflow.domain.device

/** Stable, versioned text exports for offline timing and controller review. */
object LiveTestCaptureFormatter {
    fun jsonl(state: LiveTestRunState): String = buildString {
        state.captureEvents.forEach { event ->
            append("{\"schema_version\":1,\"event\":")
            append(event.type.jsonString())
            append(",\"recorded_at\":")
            append(event.recordedAt.toString().jsonString())
            append(",\"elapsed_ms\":")
            append(event.elapsedMillis?.toString() ?: "null")
            append(",\"fields\":{")
            event.fields.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append(key.jsonString()).append(':').append(value.jsonString())
            }
            append("}}\n")
        }
    }

    fun csv(state: LiveTestRunState): String = buildString {
        appendLine("elapsed_ms,machine_telemetry_at,scale_received_at,machine_sample_age_ms,scale_sample_age_ms,phase_id,pressure_bar,machine_flow_ml_per_s,weight_g,derived_cup_flow_g_per_s,reported_cup_flow_g_per_s,target_pressure_bar,requested_control_value,requested_control_unit,requested_pressure_bar,commanded_pressure_bar,constraints")
        state.samples.forEach { sample ->
            append(sample.elapsedMillis).append(',')
            append(csvValue(sample.machineTelemetryAt?.toString())).append(',')
            append(csvValue(sample.scaleReceivedAt?.toString())).append(',')
            append(sample.machineSampleAgeMillis?.toString().orEmpty()).append(',')
            append(sample.scaleSampleAgeMillis?.toString().orEmpty()).append(',')
            append(csvValue(sample.phaseId))
            append(',').append(sample.pressureBar?.toString().orEmpty())
            append(',').append(sample.machineFlowMlPerSecond?.toString().orEmpty())
            append(',').append(sample.weightGrams?.toString().orEmpty())
            append(',').append(sample.derivedCupFlowGramsPerSecond?.toString().orEmpty())
            append(',').append(sample.reportedCupFlowGramsPerSecond?.toString().orEmpty())
            append(',').append(sample.targetPressureBar?.toString().orEmpty())
            append(',').append(sample.requestedControlValue?.toString().orEmpty())
            append(',').append(csvValue(sample.requestedControlUnit))
            append(',').append(sample.requestedPressureBar?.toString().orEmpty())
            append(',').append(sample.commandedPressureBar?.toString().orEmpty())
            append(',').append(csvValue(sample.constraints))
            appendLine()
        }
    }

    fun verificationReport(state: LiveTestRunState): String {
        val commands = state.captureEvents.filter { it.type == "command_enqueued" }
        val acknowledgements = state.captureEvents.mapNotNull { event ->
            if (event.type == "command_acknowledged") event.fields["acknowledgement_latency_ms"]?.toLongOrNull() else null
        }
        val lastEnd = state.captureEvents.indexOfLast { it.type == "run_ended" }
        val postStopCommands = if (lastEnd < 0) 0 else state.captureEvents.drop(lastEnd + 1).count { it.type == "command_enqueued" }
        return buildString {
            appendLine("Live brew capture verification")
            appendLine("Mode: ${state.mode ?: "unknown"}")
            appendLine("Full local recipe snapshot included: ${state.recipeSnapshot != null}")
            appendLine("Captured samples: ${state.samples.size}")
            appendLine("Command attempts: ${commands.size}")
            appendLine("Acknowledged commands: ${acknowledgements.size}")
            appendLine("Maximum command acknowledgement latency: ${acknowledgements.maxOrNull()?.let { "$it ms" } ?: "not measured"}")
            appendLine("Fallback status: ${state.fallbackStatus ?: "none"}")
            appendLine("Commands after run end: $postStopCommands")
            appendLine("Hydraulic response validated: no, requires an attended physical capture")
            appendLine("Controller settings are marked unvalidated for physical control.")
        }
    }

    private fun String.jsonString(): String = buildString {
        append('"')
        this@jsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    val digits = "0123456789abcdef"
                    for (shift in 12 downTo 0 step 4) append(digits[(char.code shr shift) and 0x0f])
                } else append(char)
            }
        }
        append('"')
    }

    private fun csvValue(value: String?): String {
        val clean = value.orEmpty()
        return "\"${clean.replace("\"", "\"\"")}\""
    }
}
