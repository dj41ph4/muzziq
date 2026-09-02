package com.muzziq.mobile.providers.spotify

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * `accounts.spotify.com/api/token` — échange PKCE (code → tokens) et refresh.
 * Jamais de `client_secret` ici : Authorization Code + PKCE est un flux "public
 * client" conçu précisément pour ne pas en avoir besoin (le `code_verifier`
 * en tient lieu, généré à chaque tentative — voir SpotifyAuthManager).
 */
interface SpotifyAccountsApi {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun exchangeCode(
        @Field("grant_type") grantType: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
    ): Response<SpotifyTokenResponse>

    @FormUrlEncoded
    @POST("api/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String,
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
    ): Response<SpotifyTokenResponse>
}

/** `api.spotify.com` — Web API documentée officiellement. Lecture seule pour
 * cette V1 (voir SpotifyProvider) : aucune route d'écriture (créer/modifier une
 * playlist Spotify) n'est consommée. */
interface SpotifyWebApi {
    @GET("v1/me")
    suspend fun me(@Header("Authorization") bearer: String): Response<SpotifyMeResponse>

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Header("Authorization") bearer: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 25,
    ): Response<SpotifySearchResponse>

    @GET("v1/me/tracks")
    suspend fun savedTracks(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<SpotifySavedTracksResponse>

    @GET("v1/me/playlists")
    suspend fun myPlaylists(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 50,
    ): Response<SpotifyPlaylistsResponse>

    @GET("v1/playlists/{id}/tracks")
    suspend fun playlistTracks(
        @Path("id") id: String,
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): Response<SpotifyPlaylistTracksResponse>
}

object SpotifyApiClientFactory {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun okHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val accounts: SpotifyAccountsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .client(okHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyAccountsApi::class.java)
    }

    val web: SpotifyWebApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .client(okHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyWebApi::class.java)
    }
}
