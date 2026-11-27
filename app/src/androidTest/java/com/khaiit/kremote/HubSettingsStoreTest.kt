package com.khaiit.kremote

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.security.NetworkSecurityPolicy
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HubSettingsStoreTest {
    private val endpoint = HubEndpoint("192.168.10.20")
    private val dummyKey = "integration-test-key-".padEnd(64, 'x')
    private val config get() = HubConfig(endpoint, dummyKey)

    @Test
    fun cleartextIsBlockedForLanHub() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertFalse(policy.isCleartextTrafficPermitted)
        assertFalse(policy.isCleartextTrafficPermitted(endpoint.ipv4))
    }

    @Test
    fun encryptedSettingsRoundTripAndCorruptionPreserveStoredData() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val testPrefsName = "hub_integration_" + UUID.randomUUID().toString()
        // Isolate the blob from production preferences; never read a user's Hub or Tuya values.
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(testPrefsName, mode)
        }
        val prefs = isolated.getSharedPreferences("hub_secure_v1", Context.MODE_PRIVATE)
        try {
            val store = HubSettingsStore(isolated)
            assertNull(store.load())
            val settings = HubSettings(config, HubTransport.HUB, HubTransport.PHONE)
            store.save(settings)
            val firstBlob = prefs.getString("config", null)
            assertNotNull(firstBlob)
            assertFalse(firstBlob!!.contains(dummyKey))
            assertFalse(String(Base64.decode(firstBlob, Base64.NO_WRAP), Charsets.UTF_8).contains(dummyKey))
            assertFalse(settings.toString().contains(dummyKey))
            assertFalse(config.toString().contains(dummyKey))
            val loaded = store.load()!!
            assertEquals(endpoint, loaded.config.endpoint)
            assertEquals(dummyKey, loaded.config.apiKey)
            assertEquals(HubTransport.HUB, loaded.fanTransport)
            assertEquals(HubTransport.PHONE, loaded.fixTransport)
            store.save(settings)
            assertNotEquals("Each save needs a fresh GCM IV", firstBlob, prefs.getString("config", null))

            val bytes = Base64.decode(prefs.getString("config", null), Base64.NO_WRAP)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            val corrupted = Base64.encodeToString(bytes, Base64.NO_WRAP)
            assertTrue(prefs.edit().putString("config", corrupted).commit())
            val error = runCatching { store.load() }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertNull("No parser/crypto cause should expose decrypted input", error!!.cause)
            assertFalse(error.message.orEmpty().contains(dummyKey))
            assertEquals("Loading corrupt data must not silently erase it", corrupted, prefs.getString("config", null))
            store.save(settings)
            assertEquals(dummyKey, store.load()!!.config.apiKey)
        } finally {
            base.deleteSharedPreferences(testPrefsName)
        }
    }
}
