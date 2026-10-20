package app.alextran.immich.widget

import android.content.Context
import android.graphics.Bitmap
import app.alextran.immich.widget.model.*
import com.google.gson.reflect.TypeToken
import es.antonborri.home_widget.HomeWidgetPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immich API client for Android widgets.
 * Uses WidgetHttpClient (OkHttp-based) for all network operations.
 */
class ImmichAPI(
    private val context: Context,
    cfg: ServerConfig,
) {
    private val serverConfig = cfg
    private val httpClient: WidgetHttpClient?

    init {
        httpClient = WidgetHttpClient.create(context, serverConfig)
        if (httpClient == null) {
            android.util.Log.w("ImmichAPI", "Failed to create WidgetHttpClient for ${serverConfig.serverEndpoint}")
        }
    }

    companion object {
        private const val TAG = "ImmichAPI"

        fun getServerConfig(context: Context): ServerConfig? {
            val prefs = HomeWidgetPlugin.getData(context)

            val serverURL = prefs.getString("widget_server_url", "") ?: ""
            val sessionKey = prefs.getString("widget_auth_token", "") ?: ""
            val customHeadersJSON = prefs.getString("widget_custom_headers", "") ?: ""

            if (serverURL.isBlank() || sessionKey.isBlank()) {
                return null
            }

            var customHeaders: Map<String, String> = HashMap<String, String>()

            if (customHeadersJSON.isNotBlank()) {
                val stringMapType = object : TypeToken<Map<String, String>>() {}.type
                customHeaders = com.google.gson.Gson().fromJson(customHeadersJSON, stringMapType)
            }

            return ServerConfig(
                serverURL,
                sessionKey,
                customHeaders,
            )
        }
    }

    /**
     * Fetch random search results (assets) from the server.
     */
    suspend fun fetchSearchResults(filters: SearchFilters): List<Asset> = withContext(Dispatchers.IO) {
        val client = httpClient
        if (client == null) {
            android.util.Log.w(TAG, "HttpClient not initialized for fetchSearchResults")
            return@withContext emptyList()
        }

        val endpoint = "/search/random"
        val params = emptyList<Pair<String, String>>()

        try {
            client.postJson<List<Asset>>(endpoint, params, filters) { response ->
                val type = object : TypeToken<List<Asset>>() {}.type
                com.google.gson.Gson().fromJson(response.peekBody(1024 * 1024).string(), type)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching search results", e)
            emptyList()
        }
    }

    /**
     * Fetch memories for a specific date.
     */
    suspend fun fetchMemory(date: java.time.LocalDate): List<MemoryResult> = withContext(Dispatchers.IO) {
        val client = httpClient
        if (client == null) {
            android.util.Log.w(TAG, "HttpClient not initialized for fetchMemory")
            return@withContext emptyList()
        }

        val iso8601 = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val endpoint = "/memories"
        val params = listOf("for" to iso8601)

        try {
            client.getJson<List<MemoryResult>>(endpoint, params) { response ->
                val type = object : TypeToken<List<MemoryResult>>() {}.type
                com.google.gson.Gson().fromJson(response.peekBody(1024 * 1024).string(), type)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching memories", e)
            emptyList()
        }
    }

    /**
     * Fetch a thumbnail image for an asset.
     */
    suspend fun fetchImage(asset: Asset): Bitmap = withContext(Dispatchers.IO) {
        val client = httpClient
            ?: throw IllegalStateException("HttpClient not initialized for fetchImage")

        val endpoint = "/assets/${asset.id}/thumbnail"
        val params = listOf("size" to "preview", "edited" to "true")

        try {
            client.getImage(endpoint, params)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching image for asset ${asset.id}", e)
            throw e
        }
    }

    /**
     * Fetch all albums.
     */
    suspend fun fetchAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val client = httpClient
        if (client == null) {
            android.util.Log.w(TAG, "HttpClient not initialized for fetchAlbums")
            return@withContext emptyList()
        }

        val endpoint = "/albums"
        val params = emptyList<Pair<String, String>>()

        try {
            client.getJson<List<Album>>(endpoint, params) { response ->
                val type = object : TypeToken<List<Album>>() {}.type
                com.google.gson.Gson().fromJson(response.peekBody(1024 * 1024).string(), type)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error fetching albums", e)
            emptyList()
        }
    }
}
