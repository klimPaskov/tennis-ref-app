package com.klim.tennisrefapp.domain.repository

interface ReminderScheduler {
    fun scheduleNotBeforeReminder(matchId: Long, triggerAtMillis: Long, courtName: String?)
    fun cancelNotBeforeReminder(matchId: Long)
}
