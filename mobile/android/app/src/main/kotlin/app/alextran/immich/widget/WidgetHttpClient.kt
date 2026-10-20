package app.alextran.immich.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import app.alextran.immich.core.HttpClientManager
import app.alextran.immich.core.SslContextProvider
import app.alextran.immich.widget.model.ServerConfig
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OkHttp-based HTTP client for Widget API.
 * Inherits SSL configuration from SslContextProvider.
 */
class WidgetHttpClient private constructor(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val sessionKey: String,
    private val customHeaders: Map<String, String>,
    private val gson: Gson,
) {
    companion object {
        private const val TAG = "WidgetHttpClient"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
        private const val WRITE_TIMEOUT = 30L

        /**
         * Create a WidgetHttpClient from ServerConfig.
         * Returns null if the server URL is invalid or not HTTPS.
         */
        fun create(context: Context, serverConfig: ServerConfig): WidgetHttpClient? {
            val httpUrl = serverConfig.serverEndpoint.toHttpUrlOrNull()
                ?: run {
                    Log.w(TAG, "Invalid server URL: ${serverConfig.serverEndpoint}")
                    return null
                }

            // Validate scheme - widgets require HTTPS for security
            if (!httpUrl.isHttps) {
                Log.w(TAG, "Widget requires HTTPS: ${serverConfig.serverEndpoint}")
                return null
            }

            // Build OkHttpClient with unified SSL configuration from SslContextProvider
            val httpClient = try {
                HttpClientManager.getClient().newBuilder()
                    .apply {
                        // Apply unified SSL configuration for trusted domains
                        SslContextProvider.applyToBuilder(this)
                    }
                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                    .eventListenerFactory { WidgetCallTracer(serverConfig.serverEndpoint) }
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create OkHttpClient with SSL config", e)
                return null
            }

            return WidgetHttpClient(
                client = httpClient,
                baseUrl = serverConfig.serverEndpoint,
                sessionKey = serverConfig.sessionKey,
                customHeaders = serverConfig.customHeaders,
                gson = Gson(),
            )
        }
    }

    /**
     * Build the full URL with session key parameter.
     */
    private fun buildUrl(endpoint: String, additionalParams: List<Pair<String, String>> = emptyList()): String {
        val base = baseUrl.removeSuffix("/")
        val queryParts = mutableListOf("sessionKey=$sessionKey")
        for ((key, value) in additionalParams) {
            queryParts.add("$key=$value")
        }
        return "$base$endpoint?" + queryParts.joinToString("&")
    }

    /**
     * Execute a GET request and parse the response as JSON.
     */
    suspend fun <T> getJson(
        endpoint: String,
        additionalParams: List<Pair<String, String>> = emptyList(),
        transform: (Response) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = buildUrl(endpoint, additionalParams)

        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                // Add custom headers from server config
                customHeaders.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val response = executeRequest(request)
        try {
            transform(response)
        } finally {
            response.close()
        }
    }

    /**
     * Execute a POST request with JSON body.
     */
    suspend fun <T> postJson(
        endpoint: String,
        additionalParams: List<Pair<String, String>> = emptyList(),
        body: Any,
        transform: (Response) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = buildUrl(endpoint, additionalParams)
        val jsonBody = gson.toJson(body).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(jsonBody)
            .apply {
                // Add custom headers from server config
                customHeaders.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val response = executeRequest(request)
        try {
            transform(response)
        } finally {
            response.close()
        }
    }

    /**
     * Fetch an image from the server.
     */
    suspend fun getImage(
        endpoint: String,
        additionalParams: List<Pair<String, String>> = emptyList(),
    ): Bitmap = withContext(Dispatchers.IO) {
        val url = buildUrl(endpoint, additionalParams)

        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                // Add custom headers from server config
                customHeaders.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val response = executeRequest(request)
        try {
            val body = response.body
                ?: throw IOException("Empty response body for image: $endpoint")

            val data = body.bytes()
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IOException("Failed to decode image from ${response.request.url}")

            bitmap
        } finally {
            response.close()
        }
    }

    /**
     * Execute an HTTP request with error handling.
     */
    private fun executeRequest(request: Request): Response {
        val call = client.newCall(request)
        return call.execute().also { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Widget API request failed: ${response.code} ${response.message} for ${request.url}"
                )
            }
        }
    }
}

/**
 * EventListener for tracing widget HTTP calls.
 * Helps debug connection issues and performance.
 */
private class WidgetCallTracer(
    private val baseUrl: String,
) : EventListener() {
    companion object {
        private const val TAG = "WidgetCallTracer"
    }

    private var requestUrl: String? = null

    override fun callStart(call: Call) {
        requestUrl = call.request().url.toString()
        Log.d(TAG, "Widget HTTP request: ${call.request().url.host}${call.request().url.encodedPath}")
    }

    override fun callFailed(call: Call, exception: IOException) {
        Log.e(TAG, "Widget HTTP failed: ${requestUrl ?: "unknown"} - ${exception.message}", exception)
    }
}
