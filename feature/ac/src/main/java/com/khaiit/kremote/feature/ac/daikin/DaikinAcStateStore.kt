package com.khaiit.kremote.feature.ac.daikin

interface DaikinAcStateStore {
    fun load(): DaikinAcState
    fun save(state: DaikinAcState)
}
