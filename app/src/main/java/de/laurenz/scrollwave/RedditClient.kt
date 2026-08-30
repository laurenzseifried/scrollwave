package de.laurenz.scrollwave

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

class RedditClient(
    private val tokenStore: TokenStore,
    private val clientId: String,
    private val mediaResolver: MediaResolver,
    private val http: OkHttpClient = OkHttpClient(),
) {
    fun loginUrl(): Uri {
        check(clientId.isNotBlank()) { "REDDIT_CLIENT_ID fehlt" }
        val state = ByteArray(24).also(SecureRandom()::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        tokenStore.saveOAuthState(state)
        return Uri.parse(
            "https://www.reddit.com/api/v1/authorize.compact".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("state", state)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("duration", "permanent")
                .addQueryParameter("scope", "read mysubreddits")
                .build()
                .toString(),
        )
    }

    suspend fun completeLogin(callback: Uri) = withContext(Dispatchers.IO) {
        val expectedState = tokenStore.consumeOAuthState()
        require(callback.getQueryParameter("state") == expectedState && expectedState != null) {
            "Ungültige OAuth-Antwort"
        }
        callback.getQueryParameter("error")?.let { error("Reddit-Login abgebrochen: $it") }
        val code = callback.getQueryParameter("code") ?: error("OAuth-Code fehlt")
        val response = executeTokenRequest(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .build(),
        )
        val refresh = response.optString("refresh_token")
        check(refresh.isNotBlank()) { "Reddit hat keinen dauerhaften Zugriff erteilt" }
        tokenStore.save(response.toTokens(refresh))
    }

    suspend fun loadSources(): List<FeedSource> = withContext(Dispatchers.IO) {
        val multisJson = JSONArray(authorizedGet("$OAUTH_BASE/api/multi/mine?expand_srs=false"))
        val multis = buildList {
            for (index in 0 until multisJson.length()) {
                val data = multisJson.getJSONObject(index).optJSONObject("data") ?: continue
                val path = data.optString("path")
                if (path.isNotBlank()) {
                    add(
                        FeedSource(
                            id = "multi:$path",
                            label = data.optString("display_name", path.substringAfterLast('/')),
                            path = path,
                            kind = SourceKind.CUSTOM_FEED,
                        ),
                    )
                }
            }
        }
        val subreddits = mutableListOf<FeedSource>()
        var after: String? = null
        do {
            val url = "$OAUTH_BASE/subreddits/mine/subscriber".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "100")
                .apply { after?.let { addQueryParameter("after", it) } }
                .build()
            val data = JSONObject(authorizedGet(url.toString())).getJSONObject("data")
            val children = data.getJSONArray("children")
            for (index in 0 until children.length()) {
                val child = children.getJSONObject(index).getJSONObject("data")
                val name = child.optString("display_name")
                if (name.isNotBlank()) {
                    subreddits += FeedSource(
                        id = "sub:$name",
                        label = "r/$name",
                        path = "/r/$name",
                        kind = SourceKind.SUBREDDIT,
                    )
                }
            }
            after = data.optString("after").takeIf { it.isNotBlank() && it != "null" }
        } while (after != null)
        multis.sortedBy { it.label.lowercase() } + subreddits.sortedBy { it.label.lowercase() }
    }

    suspend fun loadListing(
        source: FeedSource,
        sort: FeedSort,
        range: TopRange,
        after: String?,
    ): ListingPage = withContext(Dispatchers.IO) {
        val url = "$OAUTH_BASE${source.path.trimEnd('/')}/${sort.apiName}".toHttpUrl().newBuilder()
            .addQueryParameter("raw_json", "1")
            .addQueryParameter("limit", "100")
            .apply {
                after?.let { addQueryParameter("after", it) }
                if (sort == FeedSort.TOP) addQueryParameter("t", range.apiName)
            }
            .build()
        val data = JSONObject(authorizedGet(url.toString())).getJSONObject("data")
        val children = data.getJSONArray("children")
        val posts = buildList {
            for (index in 0 until children.length()) {
                val post = children.getJSONObject(index).optJSONObject("data") ?: continue
                mediaResolver.resolve(post)?.let(::add)
            }
        }
        ListingPage(
            posts = posts,
            after = data.optString("after").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun authorizedGet(url: String): String {
        var token = validAccessToken()
        var response = executeGet(url, token)
        if (response.first == 401) {
            token = refreshAccessToken()
            response = executeGet(url, token)
        }
        check(response.first in 200..299) { "Reddit antwortet mit HTTP ${response.first}" }
        return response.second
    }

    private fun executeGet(url: String, accessToken: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .build()
        return http.newCall(request).execute().use {
            it.code to (it.body?.string().orEmpty())
        }
    }

    private fun validAccessToken(): String {
        val tokens = tokenStore.load() ?: error("Reddit-Anmeldung fehlt")
        return if (tokens.expiresAtMillis > System.currentTimeMillis() + 60_000) {
            tokens.accessToken
        } else {
            refreshAccessToken()
        }
    }

    private fun refreshAccessToken(): String {
        val old = tokenStore.load() ?: error("Reddit-Anmeldung fehlt")
        val response = executeTokenRequest(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", old.refreshToken)
                .build(),
        )
        val updated = response.toTokens(old.refreshToken)
        tokenStore.save(updated)
        return updated.accessToken
    }

    private fun executeTokenRequest(body: FormBody): JSONObject {
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("Authorization", Credentials.basic(clientId, ""))
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()
        return http.newCall(request).execute().use {
            val text = it.body?.string().orEmpty()
            check(it.isSuccessful) { "OAuth fehlgeschlagen: HTTP ${it.code}" }
            JSONObject(text).also { json ->
                check(!json.has("error")) { "OAuth fehlgeschlagen: ${json.optString("error")}" }
            }
        }
    }

    private fun JSONObject.toTokens(refreshToken: String) = OAuthTokens(
        accessToken = getString("access_token"),
        refreshToken = refreshToken,
        expiresAtMillis = System.currentTimeMillis() + getLong("expires_in") * 1_000,
    )

    companion object {
        const val REDIRECT_URI = "scrollwave://oauth"
        private const val OAUTH_BASE = "https://oauth.reddit.com"
        private const val USER_AGENT = "android:de.laurenz.scrollwave:0.1.0 (personal media reader)"
    }
}
