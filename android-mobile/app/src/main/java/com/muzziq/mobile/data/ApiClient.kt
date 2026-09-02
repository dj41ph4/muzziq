package com.muzziq.mobile.data

import com.muzziq.mobile.data.model.AddLibraryItemRequest
import com.muzziq.mobile.data.model.AndroidUpdateInfo
import com.muzziq.mobile.data.model.HealthResponse
import com.muzziq.mobile.data.model.LibraryItemsResponse
import com.muzziq.mobile.data.model.LoginRequest
import com.muzziq.mobile.data.model.LoginResponse
import com.muzziq.mobile.data.model.MeResponse
import com.muzziq.mobile.data.model.PlayableSource
import com.muzziq.mobile.data.model.PlaylistsResponse
import com.muzziq.mobile.data.model.ResolvedPlayback
import com.muzziq.mobile.data.model.SearchResult
import com.muzziq.mobile.data.model.ServerCapabilitiesResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Client HTTP vers un serveur MuzziQ — mode Lié uniquement (§56.4). Ne parle
 * jamais à un provider tiers directement (règle absolue du serveur MuzziQ) :
 * uniquement les routes sous /api/ déjà construites et vérifiées côté serveur.
 */
interface MuzziqApi {
    @GET("/api/health")
    suspend fun health(): Response<HealthResponse>

    @GET("/api/auth/me")
    suspend fun me(@Header("Cookie") cookie: String?): Response<MeResponse>

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("/api/search")
    suspend fun search(@Query("q") query: String, @Header("Cookie") cookie: String?): Response<SearchResult>

    @GET("/api/library/items")
    suspend fun libraryItems(@Header("Cookie") cookie: String?): Response<LibraryItemsResponse>

    @POST("/api/library/items")
    suspend fun addLibraryItem(
        @Body body: AddLibraryItemRequest,
        @Header("Cookie") cookie: String?,
    ): Response<Unit>

    @GET("/api/playlists")
    suspend fun playlists(@Header("Cookie") cookie: String?): Response<PlaylistsResponse>

    @GET("/api/recordings/{id}/resolve")
    suspend fun resolveRecording(@Path("id") recordingId: String): Response<ResolvedPlayback>

    @GET("/api/play/{trackId}")
    suspend fun resolvePlayback(@Path("trackId") trackId: String): Response<PlayableSource>

    @GET("/api/updates/android")
    suspend fun androidUpdate(): Response<AndroidUpdateInfo>

    @GET("/api/capabilities")
    suspend fun capabilities(): Response<ServerCapabilitiesResponse>
}

object ApiClientFactory {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    /** Un client par base URL — le serveur est configurable par l'utilisateur, jamais figé. */
    fun create(baseUrl: String): MuzziqApi {
        val normalized = baseUrl.trim().trimEnd('/')
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl("$normalized/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MuzziqApi::class.java)
    }

    /** URL de flux directe pour un fichier local servi par le serveur (/api/stream/{fileId}) —
     * pas de JSON, ExoPlayer lit directement ce endpoint en HTTP range. */
    fun streamUrl(baseUrl: String, fileId: String): String =
        "${baseUrl.trim().trimEnd('/')}/api/stream/$fileId"
}
