package com.mrzekai.depoakilli.plan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Faz 3C: keep / delete counts per kind of file, on this phone only (numbers,
 * never a file name or path). Reset from Settings.
 */
class PreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("plan_learned", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PreferenceSnapshot> = _state.asStateFlow()

    @Synchronized
    fun record(key: String, delete: Boolean) {
        val next = _state.value.record(key, delete)
        _state.value = next
        write(next)
    }

    fun reset() {
        _state.value = PreferenceSnapshot()
        prefs.edit().clear().apply()
    }

    private fun write(s: PreferenceSnapshot) {
        val o = JSONObject()
        s.counts.forEach { (k, c) -> o.put(k, JSONArray().put(c.keep).put(c.delete)) }
        prefs.edit().putString(K, o.toString()).apply()
    }

    private fun read(): PreferenceSnapshot = runCatching {
        val o = JSONObject(prefs.getString(K, "{}") ?: "{}")
        PreferenceSnapshot(o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { DecisionCounts(it.getInt(0), it.getInt(1)) } })
    }.getOrDefault(PreferenceSnapshot())

    private companion object {
        const val K = "counts"
    }
}

/** The plan the user started: its goal, where the phone was, and the steps' order. */
data class SavedPlan(val targetBytes: Long, val startAvailableBytes: Long, val keys: List<String>, val startedAt: Long)

class PlanStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("personal_plan", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SavedPlan?> = _state.asStateFlow()

    fun save(plan: SavedPlan) {
        _state.value = plan
        prefs.edit()
            .putLong("target", plan.targetBytes)
            .putLong("start", plan.startAvailableBytes)
            .putString("keys", JSONArray(plan.keys).toString())
            .putLong("at", plan.startedAt)
            .apply()
    }

    fun clear() {
        _state.value = null
        prefs.edit().clear().apply()
    }

    private fun read(): SavedPlan? = runCatching {
        if (!prefs.contains("target")) return@runCatching null
        val keys = JSONArray(prefs.getString("keys", "[]"))
        SavedPlan(prefs.getLong("target", 0L), prefs.getLong("start", 0L), (0 until keys.length()).map(keys::getString), prefs.getLong("at", 0L))
    }.getOrNull()
}
