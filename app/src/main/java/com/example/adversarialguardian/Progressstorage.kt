package com.example.adversarialguardian

import android.content.Context

/**
 * Oyuncunun ilerlemesini (geçtiği en yüksek seviye) cihazda saklar.
 *
 * Anahtarın aksine bu HASSAS DEĞİL — o yüzden şifreli depo gerekmez,
 * düz SharedPreferences yeterli. "Doğru araç doğru iş" örneği:
 * anahtar → EncryptedSharedPreferences, ilerleme → düz SharedPreferences.
 *
 * Model: maxCompletedLevel = geçilen en yüksek seviye (başlangıç 0).
 *   - level <= maxCompletedLevel        → geçildi (✓)
 *   - level == maxCompletedLevel + 1     → açık (oynanabilir)
 *   - level >  maxCompletedLevel + 1     → kilitli (🔒)
 */
object ProgressStorage {
    private const val PREFS = "progress"
    private const val KEY_MAX = "max_level"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadMaxLevel(context: Context): Int =
        prefs(context).getInt(KEY_MAX, 0)

    /** Yalnızca daha yüksek bir seviye geçildiyse günceller. */
    fun saveMaxLevel(context: Context, level: Int) {
        val current = prefs(context).getInt(KEY_MAX, 0)
        if (level > current) {
            prefs(context).edit().putInt(KEY_MAX, level).apply()
        }
    }

    // --- En iyi (en az) tur skoru, seviye başına. 0 = henüz kayıt yok. ---

    fun saveBestTurns(context: Context, level: Int, turns: Int) {
        val current = prefs(context).getInt("best_turns_$level", 0)
        if (current == 0 || turns < current) {
            prefs(context).edit().putInt("best_turns_$level", turns).apply()
        }
    }

    /** Tüm seviyelerin en iyi skorlarını {seviye: tur} olarak döndürür. */
    fun loadAllBestTurns(context: Context): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        for (level in 1..5) {
            val t = prefs(context).getInt("best_turns_$level", 0)
            if (t > 0) result[level] = t
        }
        return result
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}