package com.khaiit.kremote.feature.ac

interface DaikinAcStateStore {
    fun load(): DaikinAcState
    fun save(state: DaikinAcState)
}
