package app.alextran.immich.core

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Unified SSL context provider for all network operations.
 * Ensures consistent SSL handling across OkHttp clients for trusted domains.
 */
object SslContextProvider {

  /**
   * Check if a host is in the trusted domains list.
   * Trusted domains accept self-signed certificates.
   */
  fun isTrustedDomain(host: String): Boolean {
    val hostLower = host.lowercase()
    return TRUSTED_DOMAINS.any { domain ->
      hostLower == domain || hostLower.endsWith(".$domain") || hostLower.endsWith(".$domain.")
    }
  }

  /**
   * Create a custom hostname verifier for trusted domains.
   * For trusted domains: accept any certificate (including self-signed).
   * For other domains: perform standard hostname verification.
   */
  fun createHostnameVerifier(): HostnameVerifier {
    return HostnameVerifier { host, session ->
      if (isTrustedDomain(host)) {
        true
      } else {
        HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)
      }
    }
  }

  /**
   * Create SSL context with system trust managers.
   * Uses the default system TrustManager for standard certificate validation.
   * Returns both the SSLContext and the system TrustManager.
   */
  fun createSslContext(): SslContextResult {
    val managerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    managerFactory.init(null as KeyStore?)
    val trustManagers = managerFactory.trustManagers.filterIsInstance<X509TrustManager>()
    val systemTrustManager = trustManagers.firstOrNull()
      ?: throw IllegalStateException("No X509TrustManager found in system trust managers")

    val sslContext = SSLContext.getInstance("TLS").apply {
      init(null, trustManagers.toTypedArray(), SecureRandom())
    }

    return SslContextResult(sslContext, systemTrustManager)
  }

  /**
   * Build a base OkHttpClient with unified SSL configuration.
   * Applies the system TrustManager and trusted domain hostname verifier.
   */
  fun buildBaseClient(): OkHttpClient {
    val result = createSslContext()

    return OkHttpClient.Builder()
      .sslSocketFactory(result.sslContext.socketFactory, result.systemTrustManager)
      .hostnameVerifier(createHostnameVerifier())
      .build()
  }

  /**
   * Apply unified SSL configuration to an existing OkHttpClient.Builder.
   * This allows combining the SSL settings with other custom configurations
   * (cookies, interceptors, cache, etc.).
   */
  fun applyToBuilder(builder: OkHttpClient.Builder): OkHttpClient.Builder {
    val result = createSslContext()

    builder
      .sslSocketFactory(result.sslContext.socketFactory, result.systemTrustManager)
      .hostnameVerifier(createHostnameVerifier())

    return builder
  }

  /**
   * Apply unified SSL configuration to a standalone OkHttpClient.Builder.
   * This is the primary method for configuring external HTTP clients
   * (e.g., background_downloader) with the same SSL settings as the app.
   *
   * @param builder The OkHttpClient.Builder to configure
   * @return The configured builder (same instance for chaining)
   */
  fun applyToOkHttpClientBuilder(builder: OkHttpClient.Builder): OkHttpClient.Builder {
    val result = createSslContext()

    builder
      .sslSocketFactory(result.sslContext.socketFactory, result.systemTrustManager)
      .hostnameVerifier(createHostnameVerifier())

    return builder
  }

  /**
   * Set global default SSL socket factory and hostname verifier for all
   * HttpsURLConnection instances (used by background_downloader internally).
   */
  fun applyAsGlobalDefaults() {
    val result = createSslContext()
    HttpsURLConnection.setDefaultSSLSocketFactory(result.sslContext.socketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier(createHostnameVerifier())
  }

  /**
   * Reset global defaults to system values.
   */
  fun resetGlobalDefaults() {
    HttpsURLConnection.setDefaultSSLSocketFactory(null)
    HttpsURLConnection.setDefaultHostnameVerifier(null)
  }

  /**
   * Result holder for SSL context creation.
   */
  data class SslContextResult(
    val sslContext: SSLContext,
    val systemTrustManager: X509TrustManager,
  )
}

// ── Trusted Domains ────────────────────────────────────────────────
// Hardcoded list of domains that accept self-signed certificates.
// To modify this list, edit the source code and rebuild the app.
internal const val TRUSTED_DOMAINS_CSV = ""
// Example: "immich.local,192.168.1.100"

internal val TRUSTED_DOMAINS: Set<String> = TRUSTED_DOMAINS_CSV
  .split(",")
  .map { it.trim().lowercase() }
  .filter { it.isNotEmpty() }
  .toSet()
