package com.khaiit.kremote.feature.tuya

import org.junit.Assert.assertEquals
import org.junit.Test

class TuyaRequestSignerTest {
    private val clientId = "1KAD46OrT9HafiKdsXeg"
    private val secret = "4OHBOnWOqaEC1mWXOpVL3yV50s0qGSRC"
    private val timestamp = "1588925778000"
    private val nonce = "5138cc3a9033d69856923fd07b491173"
    private val testHeaders = "area_id:29a33e8796834b1efa6\ncall_id:8afdb70ab2ed11eb85290242ac130003\n"

    // Official Tuya Cloud Authorization examples, updated Jan 2026.
    @Test fun tokenSignatureMatchesOfficialVector() {
        val signed = TuyaRequestSigner.sign(
            clientId, secret, "GET", "/v1.0/token?grant_type=1",
            byteArrayOf(), timestamp, nonce, optionalSignatureKey = testHeaders
        )
        assertEquals("9E48A3E93B302EEECC803C7241985D0A34EB944F40FB573C7B5C2A82158AF13E", signed)
    }

    @Test fun generalApiSignatureMatchesOfficialVector() {
        val signed = TuyaRequestSigner.sign(
            clientId, secret, "GET", "/v2.0/apps/schema/users?page_no=1&page_size=50",
            byteArrayOf(), timestamp, nonce,
            accessToken = "3f4eda2bdec17232f67c0b188af3eec1",
            optionalSignatureKey = testHeaders
        )
        assertEquals("AE4481C692AA80B25F3A7E12C3A5FD9BBF6251539DD78E565A1A72A508A88784", signed)
    }

    @Test fun sha256EmptyBody() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            TuyaRequestSigner.bodySha256(byteArrayOf())
        )
    }
}
