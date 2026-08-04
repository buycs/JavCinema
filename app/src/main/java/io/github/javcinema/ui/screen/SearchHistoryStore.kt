package io.github.javcinema.ui.screen

import android.content.Context
import com.google.gson.Gson

object SearchHistoryStore {

    private const val PREFS = "javcinema_search_history"
    private const val KEY = "history"
    private const val MAX = 25

    fun load(context: Context): List<String> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Gson().fromJson(json, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return load(context)
        val updated = (listOf(q) + load(context).filter { it != q }).take(MAX)
        save(context, updated)
        return updated
    }

    fun remove(context: Context, query: String): List<String> {
        val updated = load(context).filter { it != query }
        save(context, updated)
        return updated
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, Gson().toJson(list))
            .apply()
    }
}
