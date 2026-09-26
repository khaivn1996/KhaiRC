package com.khaiit.kremote.feature.ac

data class DaikinBrcProfile(
    val name: String,
    val frequencyHz: Int,
    val headerMarkUs: Int,
    val headerSpaceUs: Int,
    val bitMarkUs: Int,
    val oneSpaceUs: Int,
    val zeroSpaceUs: Int,
    val interFrameSpaceUs: Int,
    val terminalSpaceUs: Int
) {
    companion object {

        /*
         * Daikin BRC4CXXX protocol.
         *
         * Remote đang sử dụng: BRC4C153
         */
        val BRC4C153 = DaikinBrcProfile(
            name = "Daikin BRC4C153",

            frequencyHz = 38_000,

            headerMarkUs = 5_070,
            headerSpaceUs = 2_140,

            bitMarkUs = 370,
            oneSpaceUs = 1_780,
            zeroSpaceUs = 710,

            interFrameSpaceUs = 29_410,

            // Khoảng OFF sau mark cuối cùng.
            // Giữ một zero-space bình thường cho Android IR pattern.
            terminalSpaceUs = 710
        )
    }
}