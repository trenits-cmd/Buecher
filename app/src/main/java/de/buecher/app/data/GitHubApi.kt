package de.buecher.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

interface GitHubApi {
    @GET("repos/{owner}/{repository}/contents/{path}")
    suspend fun getFile(
        @Path("owner") owner: String,
        @Path("repository") repository: String,
        @Path("path") path: String,
        @Header("Authorization") authorization: String,
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
    ): GitHubFile

    @PUT("repos/{owner}/{repository}/contents/{path}")
    suspend fun putFile(
        @Path("owner") owner: String,
        @Path("repository") repository: String,
        @Path("path") path: String,
        @Header("Authorization") authorization: String,
        @Body request: GitHubPutRequest,
    ): GitHubFile
}

data class GitHubFile(val content: String?, val sha: String?)

data class GitHubPutRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String,
)