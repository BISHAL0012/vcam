package com.wangyiheng.vcamsx.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Vcam key system (client side).
 *
 * Server = two plain files in the public GitHub repo BISHAL0012/vcam:
 *   keys.json   {"keys":[{"android_id_sha256":"<hex>","key":"VCAM-XXXX-XXXX",
 *                         "name":"<customer>","contact":"<telegram/imo>",
 *                         "expires_at":"2026-12-06","active":true}]}
 *   config.json {"min_version_code":1,
 *                "telegram_url":"https://t.me/username",
 *                "imo_url":"https://imo.im/xxxx"}
 *
 * No secret lives in the app: keys are random strings and the server list
 * is the source of truth. The app only ever sends the SHA-256 hash of the
 * Android ID — the raw Android ID is never stored or transmitted.
 *
 * NOTE (v1 limits, see CHANGES.md): no signature pinning — a revoked key
 * is enforced by the server list, but a modified APK could skip this
 * check entirely. Signature pinning + Play-style integrity is a v2
 * hardening item.
 */
object KeyManager {

    private const val KEYS_URL =
        "https://raw.githubusercontent.com/BISHAL0012/vcam/main/keys.json"
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/BISHAL0012/vcam/main/config.json"

    private const val PREFS_NAME = "vcam_key"
    private const val PREF_KEY = "key"
    private const val PREF_EXPIRES = "expires_at"

    enum class State {
        CHECKING,
        VALID,
        NEED_KEY,
        NO_INTERNET,
        UPDATE_REQUIRED
    }

    data class CheckResult(
        val state: State,
        val daysLeft: Int = -1,
        val expiresAt: String? = null,
        val telegramUrl: String = "https://t.me/",
        val imoUrl: String = "https://imo.im/",
        val message: String? = null
    )

    /** SHA-256 hex of Settings.Secure.ANDROID_ID (the device id). */
    fun androidIdHash(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** The raw Android ID, only for display (tap-to-copy in the dialog). */
    fun androidId(context: Context): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun storedKey(context: Context): String? = prefs(context).getString(PREF_KEY, null)

    private fun storeKey(context: Context, key: String, expiresAt: String) {
        prefs(context).edit()
            .putString(PREF_KEY, key)
            .putString(PREF_EXPIRES, expiresAt)
            .apply()
    }

    fun clearKey(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun appVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Vcam")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP $code for $url")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** End of the expiry day (exclusive): "2026-12-06" stays valid through that day. */
    private fun expiryEndMs(expiresAt: String): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = fmt.parse(expiresAt) ?: return 0L
        return date.time + 24L * 60L * 60L * 1000L
    }

    private fun daysLeft(expiresAt: String): Int {
        val left = expiryEndMs(expiresAt) - System.currentTimeMillis()
        return (left / (24L * 60L * 60L * 1000L)).toInt()
    }

    private data class ServerData(
        val keys: List<JSONObject>,
        val minVersionCode: Int,
        val telegramUrl: String,
        val imoUrl: String
    )

    private fun fetchServer(): ServerData {
        val configJson = JSONObject(fetchText(CONFIG_URL))
        val keysJson = JSONObject(fetchText(KEYS_URL))
        val keys = mutableListOf<JSONObject>()
        val arr = keysJson.optJSONArray("keys")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                keys.add(arr.getJSONObject(i))
            }
        }
        return ServerData(
            keys = keys,
            minVersionCode = configJson.optInt("min_version_code", 1),
            telegramUrl = configJson.optString("telegram_url", "https://t.me/"),
            imoUrl = configJson.optString("imo_url", "https://imo.im/")
        )
    }

    /** Entry match: same key (case-insensitive), same device hash, active, not expired. */
    private fun findEntry(
        server: ServerData,
        idHash: String,
        key: String
    ): JSONObject? {
        val wanted = key.trim().uppercase(Locale.US)
        for (entry in server.keys) {
            if (!entry.optBoolean("active", false)) continue
            if (entry.optString("key", "").trim().uppercase(Locale.US) != wanted) continue
            if (entry.optString("android_id_sha256", "") != idHash) continue
            val expiresAt = entry.optString("expires_at", "")
            if (expiresAt.isBlank()) continue
            if (daysLeft(expiresAt) < 0) continue
            return entry
        }
        return null
    }

    /** Full check on app start: internet -> version -> stored key. */
    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val server = try {
            fetchServer()
        } catch (e: Exception) {
            return@withContext CheckResult(
                State.NO_INTERNET,
                message = "Internet required. Check your connection and retry."
            )
        }
        if (appVersionCode(context) < server.minVersionCode) {
            return@withContext CheckResult(
                State.UPDATE_REQUIRED,
                telegramUrl = server.telegramUrl,
                imoUrl = server.imoUrl,
                message = "Please update Vcam to the latest version."
            )
        }
        val stored = storedKey(context)
        if (stored.isNullOrBlank()) {
            return@withContext CheckResult(
                State.NEED_KEY,
                telegramUrl = server.telegramUrl,
                imoUrl = server.imoUrl
            )
        }
        val entry = findEntry(server, androidIdHash(context), stored)
        if (entry == null) {
            clearKey(context)
            return@withContext CheckResult(
                State.NEED_KEY,
                telegramUrl = server.telegramUrl,
                imoUrl = server.imoUrl,
                message = "Your key is no longer valid. Please enter a new key."
            )
        }
        val expiresAt = entry.optString("expires_at", "")
        CheckResult(
            State.VALID,
            daysLeft = daysLeft(expiresAt),
            expiresAt = expiresAt,
            telegramUrl = server.telegramUrl,
            imoUrl = server.imoUrl
        )
    }

    /** Validate a typed key against a FRESH fetch; store it on success. */
    suspend fun activate(context: Context, enteredKey: String): CheckResult =
        withContext(Dispatchers.IO) {
            val server = try {
                fetchServer()
            } catch (e: Exception) {
                return@withContext CheckResult(
                    State.NO_INTERNET,
                    telegramUrl = "",
                    imoUrl = "",
                    message = "Internet required. Check your connection and retry."
                )
            }
            if (appVersionCode(context) < server.minVersionCode) {
                return@withContext CheckResult(
                    State.UPDATE_REQUIRED,
                    telegramUrl = server.telegramUrl,
                    imoUrl = server.imoUrl,
                    message = "Please update Vcam to the latest version."
                )
            }
            val entry = findEntry(server, androidIdHash(context), enteredKey)
            if (entry == null) {
                return@withContext CheckResult(
                    State.NEED_KEY,
                    telegramUrl = server.telegramUrl,
                    imoUrl = server.imoUrl,
                    message = "Invalid or expired key."
                )
            }
            // Store the canonical key from the server (exact case) + expiry.
            val key = entry.optString("key", "")
            val expiresAt = entry.optString("expires_at", "")
            storeKey(context, key, expiresAt)
            CheckResult(
                State.VALID,
                daysLeft = daysLeft(expiresAt),
                expiresAt = expiresAt,
                telegramUrl = server.telegramUrl,
                imoUrl = server.imoUrl
            )
        }
}
