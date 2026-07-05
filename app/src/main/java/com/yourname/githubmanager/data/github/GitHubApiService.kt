// File: app/src/main/java/com/yourname/githubmanager/data/github/GitHubApiService.kt
package com.yourname.githubmanager.data.github

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Retrofit interface for the GitHub "Git Data" REST API endpoints needed
 * for bulk push (blob -> tree -> commit -> ref update), plus a ref lookup.
 *
 * All calls return Retrofit's [Response] wrapper (not the raw body) so
 * GitHubRepository can inspect the HTTP status code and error body on
 * failure — needed to tell a 401 (bad token) apart from a 403 (rate limit)
 * apart from a plain network failure, per the app's error-handling rules.
 *
 * Every call takes its own [authorization] header rather than relying on
 * a global OkHttp interceptor, since the token can change at runtime
 * (user edits it in Settings) and callers already have it in hand from
 * SecureTokenStore.
 */
interface GitHubApiService {

    /** POST /repos/{owner}/{repo}/git/blobs */
    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String,
        @Body request: BlobRequest
    ): Response<BlobResponse>

    /** POST /repos/{owner}/{repo}/git/trees */
    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String,
        @Body request: TreeRequest
    ): Response<TreeResponse>

    /** POST /repos/{owner}/{repo}/git/commits */
    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String,
        @Body request: CommitRequest
    ): Response<CommitResponse>

    /**
     * GET /repos/{owner}/{repo}/git/refs/heads/{branch}
     * Used before an upload/commit to confirm the branch exists and to
     * fetch its current sha (useful for detecting whether someone else
     * pushed to the branch since our last known commit).
     */
    @GET("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun getRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Authorization") authorization: String
    ): Response<RefResponse>

    /** PATCH /repos/{owner}/{repo}/git/refs/heads/{branch} */
    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Authorization") authorization: String,
        @Body request: RefUpdateRequest
    ): Response<RefResponse>

    companion object {
        private const val BASE_URL = "https://api.github.com/"

        /**
         * Builds a ready-to-use [GitHubApiService]. No token is baked in here —
         * callers pass a fresh "Bearer <token>" string per-call (see interface
         * doc comment), so a single instance can be reused across the app's
         * lifetime even if the user updates their token in Settings.
         */
        fun create(): GitHubApiService {
            val logging = HttpLoggingInterceptor().apply {
                // BODY level would log request/response JSON, which could
                // include the Authorization header in some interceptor
                // configurations — keep this at BASIC or lower in
                // production builds to avoid ever writing the token to logcat.
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GitHubApiService::class.java)
        }
    }
}

