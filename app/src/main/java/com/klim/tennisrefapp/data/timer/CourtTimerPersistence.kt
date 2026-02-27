package com.klim.tennisrefapp.data.timer

import android.content.Context
import org.json.JSONObject

internal data class PersistedCourtTimer(
    val courtId: Long,
    val matchId: Long,
    val courtName: String,
    val timerType: CourtTimerType,
    val endAtMillis: Long,
)

internal object CourtTimerPersistence {
    private const val PREF_NAME = "court_timer_prefs"
    private const val KEY_PREFIX = "court_timer_"
    private const val FIELD_COURT_ID = "courtId"
    private const val FIELD_MATCH_ID = "matchId"
    private const val FIELD_COURT_NAME = "courtName"
    private const val FIELD_TIMER_TYPE = "timerType"
    private const val FIELD_END_AT = "endAt"

    fun save(context: Context, timer: PersistedCourtTimer) {
        val json = JSONObject()
            .put(FIELD_COURT_ID, timer.courtId)
            .put(FIELD_MATCH_ID, timer.matchId)
            .put(FIELD_COURT_NAME, timer.courtName)
            .put(FIELD_TIMER_TYPE, timer.timerType.name)
            .put(FIELD_END_AT, timer.endAtMillis)
            .toString()
        prefs(context).edit().putString(key(timer.courtId), json).apply()
    }

    fun remove(context: Context, courtId: Long) {
        prefs(context).edit().remove(key(courtId)).apply()
    }

    fun load(context: Context, courtId: Long): PersistedCourtTimer? {
        val raw = prefs(context).getString(key(courtId), null) ?: return null
        return parse(raw)
    }

    fun consumeIfMatches(
        context: Context,
        courtId: Long,
        matchId: Long,
        timerType: CourtTimerType,
        expectedEndAtMillis: Long,
        toleranceMillis: Long = 1_500L,
    ): PersistedCourtTimer? = synchronized(this) {
        val store = prefs(context)
        val raw = store.getString(key(courtId), null) ?: return@synchronized null
        val parsed = parse(raw) ?: return@synchronized null
        val matches = parsed.matchId == matchId &&
            parsed.timerType == timerType &&
            (expectedEndAtMillis <= 0L || kotlin.math.abs(parsed.endAtMillis - expectedEndAtMillis) <= toleranceMillis)
        if (!matches) return@synchronized null
        store.edit().remove(key(courtId)).commit()
        return@synchronized parsed
    }

    fun loadAll(context: Context): List<PersistedCourtTimer> {
        return prefs(context).all
            .asSequence()
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { (_, value) ->
                val raw = value as? String ?: return@mapNotNull null
                parse(raw)
            }
            .toList()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun parse(raw: String): PersistedCourtTimer? {
        return runCatching {
            val json = JSONObject(raw)
            PersistedCourtTimer(
                courtId = json.getLong(FIELD_COURT_ID),
                matchId = json.getLong(FIELD_MATCH_ID),
                courtName = json.getString(FIELD_COURT_NAME),
                timerType = CourtTimerType.valueOf(json.getString(FIELD_TIMER_TYPE)),
                endAtMillis = json.getLong(FIELD_END_AT),
            )
        }.getOrNull()
    }

    private fun key(courtId: Long): String = KEY_PREFIX + courtId

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
