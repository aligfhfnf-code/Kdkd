package com.carlauncher

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/** Простые настройки: состояние в Compose + запись в SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
    private val m = mutableStateMapOf<String, String>().also { map ->
        sp.all.forEach { (k, v) -> if (v is String) map[k] = v }
    }
    fun get(k: String, d: String) = m[k] ?: d
    fun on(k: String, d: Boolean = true) = get(k, if (d) "1" else "0") == "1"
    fun set(k: String, v: String) { m[k] = v; sp.edit().putString(k, v).apply() }
    fun toggle(k: String, d: Boolean = true) = set(k, if (on(k, d)) "0" else "1")
}
