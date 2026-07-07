// File: app/src/main/java/com/yourname/githubmanager/data/github/GitHubApiService.kt
package com.yourname.githubmanager.data.github

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
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
 * Retrofit interface for the GitHub "Git Data" REST API.
 *
 * Uses the DTOs already defined in GitHubModels.kt — no duplicate models
 * are created here. Every call requires the caller to pass a ready-made
 * Authorization header value (see [bearerToken]).
 *
 * NOTE ON [getCommit]: the original spec for this file only listed
 * createBlob / createTree / createCommit / getRef / updateRef. While
 * building GitHubRepository it became clear that a correct, NON-destructive
 * "Upload" (one that doesn't wipe out a README or other files already in
 * the repo) needs the *tree sha* of whatever commit a branch currently
 * points to — and GitHub's ref object only exposes the *commit* sha, not
 * its tree sha. The only way to resolve commit-sha -> tree-sha with the
 * Git Data API is GET /repos/{owner}/{repo}/git/commits/{sha}, so that
 * endpoint (and its two tiny supporting DTOs below) was added here. It is
 * additive only — GitHubModels.kt was not touched.
 */
interface GitHubApiService {

    // ── Blobs ────────────────────────────────────────────────────────────
    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Header("Authorization") authToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: BlobRequest
    ): Response<BlobResponse>

    // ── Trees ────────────────────────────────────────────────────────────
    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Header("Authorization") authToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: TreeRequest
    ): Response<TreeResponse>

    // ── Commits ──────────────────────────────────────────────────────────
    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Header("Authorization") authToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CommitRequest
    ): Response<CommitResponse>

    /**
     * Extra endpoint (see class doc) — only used to read a commit's tree
     * sha so an "Upload" can be layered on top of a repo's existing
     * content instead of replacing it.
     */
    @GET("repos/{owner}/{repo}/git/commits/{commit_sha}")
    suspend fun getCommit(
        @Header("Authorization") authToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("commit_sha") commitSha: String
    ): Response<GetCommitResponse>

    // ── Refs ─────────────────────────────────────────────────────────────
    @GET("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun getRef(
        @Header("Authorization") authToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<RefResponse>

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Header("Authorization") authToken: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body request: RefUpdateRequest
    ): Response<RefResponse>

    companion object {
        const val BASE_URL = "https://api.github.com/"

        /** GitHub's classic PAT auth header format: "token <the_pat>". */
        fun bearerToken(rawToken: String): String = "token $rawToken"
    }
}

/**
 * Minimal shape of GitHub's "get a commit" response — only the two fields
 * GitHubRepository actually needs (see class doc on [GitHubApiService]).
 * Deliberately NOT added to GitHubModels.kt since that file is treated as
 * frozen/working; this lives next to the interface that uses it instead.
 */
data class CommitTreeRef(
    @SerializedName("sha") val sha: String
)

data class GetCommitResponse(
    @SerializedName("sha") val sha: String,
    @SerializedName("tree") val tree: CommitTreeRef
)

/**
 * Tiny, DI-free Retrofit factory — consistent with the rest of the app's
 * "plain singleton object" style (AppPreferences, SecureTokenStore, etc.).
 * GitHubRepository is the only intended caller.
 */
object GitHubApiClient {

    // ✅ यह है वो एक लाइन जो बदली है:
    private val gson: Gson by lazy { GsonBuilder().serializeNulls().create() }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // BODY logs request/response JSON — fine for debugging, but this
            // never includes the PAT itself (it's a header, not a body key)
            // and Authorization headers are not printed in BASIC/BODY level
            // by default besides their presence. Still, keep this at NONE in
            // release builds if you swap this to a BuildConfig check.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // file blobs can be sizeable
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GitHubApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun create(): GitHubApiService = retrofit.create(GitHubApiService::class.java)
}
