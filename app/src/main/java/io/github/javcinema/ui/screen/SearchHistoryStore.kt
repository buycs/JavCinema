package io.github.javcinema.ui.screen

import android.content.Context
import com.google.gson.Gson

internal fun searchHistoryKey(scope: SearchScope): String = when (scope) {
    SearchScope.MOVIES -> "history_movies"
    SearchScope.ACTRESSES -> "history_actresses"
    SearchScope.FAVORITES -> "history_favorites"
}

internal fun prependSearchHistory(existing: List<String>, query: String, max: Int = 25): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return existing
    return (listOf(q) + existing.filter { it != q }).take(max)
}

object SearchHistoryStore {

    private const val PREFS = "javcinema_search_history"
    private const val LEGACY_KEY = "history"
    private const val MAX = 25
    private val gson = Gson()

    fun load(context: Context, scope: SearchScope): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scoped = readList(prefs.getString(searchHistoryKey(scope), null))
        if (scoped.isNotEmpty() || scope != SearchScope.MOVIES) return scoped
        return readList(prefs.getString(LEGACY_KEY, null))
    }

    fun add(context: Context, scope: SearchScope, query: String): List<String> {
        val updated = prependSearchHistory(load(context, scope), query, MAX)
        save(context, scope, updated)
        return updated
    }

    fun remove(context: Context, scope: SearchScope, query: String): List<String> {
        val updated = load(context, scope).filter { it != query }
        save(context, scope, updated)
        return updated
    }

    fun clear(context: Context, scope: SearchScope) {
        save(context, scope, emptyList())
    }

    private fun save(context: Context, scope: SearchScope, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(searchHistoryKey(scope), gson.toJson(list))
            .apply()
    }

    private fun readList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
