package com.example.io

import android.content.Context
import android.net.Uri
import com.example.model.ActionType
import com.example.model.MacroScript
import com.example.model.MacroStep
import com.example.model.TargetResolution
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

/**
 * Manager for importing, exporting, serializing, and deserializing Macro scripts
 * with screen resolution metadata and coordinate auto-scaling.
 */
object MacroJsonManager {

    private const val SCHEMA_VERSION = 1

    /**
     * Serialize MacroScript into a standardized, human-readable JSON string.
     */
    fun toJson(script: MacroScript): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("id", script.id)
        root.put("name", script.name)
        root.put("author", script.author)
        root.put("description", script.description)
        root.put("version", script.version)
        root.put("createdAt", script.createdAt)

        // Target Resolution Metadata
        val resObj = JSONObject()
        resObj.put("width", script.targetResolution.width)
        resObj.put("height", script.targetResolution.height)
        root.put("targetResolution", resObj)

        // Steps array
        val stepsArray = JSONArray()
        script.steps.forEach { step ->
            val stepObj = JSONObject()
            stepObj.put("id", step.id)
            stepObj.put("actionType", step.actionType.name)
            stepObj.put("normalizedX", step.normalizedX.toDouble())
            stepObj.put("normalizedY", step.normalizedY.toDouble())
            stepObj.put("endNormalizedX", step.endNormalizedX.toDouble())
            stepObj.put("endNormalizedY", step.endNormalizedY.toDouble())
            stepObj.put("durationMs", step.durationMs)
            stepObj.put("delayMs", step.delayMs)
            stepObj.put("jitterMs", step.jitterMs)
            stepObj.put("loopCount", step.loopCount)
            stepObj.put("label", step.label)
            stepsArray.put(stepObj)
        }
        root.put("steps", stepsArray)

        return root.toString(2)
    }

    /**
     * Deserialize JSON string into a validated MacroScript instance.
     */
    fun fromJson(jsonStr: String): Result<MacroScript> {
        return runCatching {
            val root = JSONObject(jsonStr)
            val id = root.optString("id", UUID.randomUUID().toString())
            val name = root.getString("name")
            val author = root.optString("author", "Automator")
            val description = root.optString("description", "")
            val version = root.optInt("version", 1)
            val createdAt = root.optLong("createdAt", System.currentTimeMillis())

            val resObj = root.optJSONObject("targetResolution")
            val targetResolution = if (resObj != null) {
                TargetResolution(
                    width = resObj.optInt("width", 1080),
                    height = resObj.optInt("height", 2400)
                )
            } else {
                TargetResolution()
            }

            val stepsArray = root.getJSONArray("steps")
            val stepsList = mutableListOf<MacroStep>()

            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                val actionTypeStr = stepObj.getString("actionType")
                val actionType = runCatching { ActionType.valueOf(actionTypeStr) }.getOrDefault(ActionType.TAP)

                val step = MacroStep(
                    id = stepObj.optString("id", UUID.randomUUID().toString()),
                    actionType = actionType,
                    normalizedX = stepObj.optDouble("normalizedX", 0.5).toFloat(),
                    normalizedY = stepObj.optDouble("normalizedY", 0.5).toFloat(),
                    endNormalizedX = stepObj.optDouble("endNormalizedX", 0.5).toFloat(),
                    endNormalizedY = stepObj.optDouble("endNormalizedY", 0.5).toFloat(),
                    durationMs = stepObj.optLong("durationMs", 100L),
                    delayMs = stepObj.optLong("delayMs", 300L),
                    jitterMs = stepObj.optLong("jitterMs", 20L),
                    loopCount = stepObj.optInt("loopCount", 1),
                    label = stepObj.optString("label", "")
                )
                stepsList.add(step)
            }

            MacroScript(
                id = id,
                name = name,
                author = author,
                description = description,
                version = version,
                createdAt = createdAt,
                targetResolution = targetResolution,
                steps = stepsList
            )
        }
    }

    /**
     * Export script to file URI via Storage Access Framework (SAF).
     */
    fun exportToUri(context: Context, uri: Uri, script: MacroScript): Result<Unit> {
        return runCatching {
            val json = toJson(script)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            } ?: error("Unable to open output stream for URI: $uri")
        }
    }

    /**
     * Import script from file URI via Storage Access Framework (SAF).
     */
    fun importFromUri(context: Context, uri: Uri): Result<MacroScript> {
        return runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: error("Unable to open input stream for URI: $uri")

            fromJson(content).getOrThrow()
        }
    }
}
