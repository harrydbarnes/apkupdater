package com.apkupdater.repository

import android.net.Uri
import android.util.Log
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.UptodownSource
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import org.jsoup.Jsoup

class UptodownRepository {

    @OptIn(FlowPreview::class)
    suspend fun updates(apps: List<AppInstalled>) = flow {
        val updates = apps.asFlow()
            .flatMapMerge(concurrency = 3) { app ->
                checkAppUpdate(app)
            }
            .toList()
        emit(updates)
    }.catch {
        emit(emptyList())
        Log.e("UptodownRepository", "Error getting updates", it)
    }.flowOn(Dispatchers.IO)

    private fun checkAppUpdate(app: AppInstalled): Flow<AppUpdate> = flow {
        try {
            val searchUrl = "https://en.uptodown.com/android/search"
            val doc = Jsoup.connect("$searchUrl?query=${app.packageName}")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get()

            val firstItem = doc.selectFirst("div.item") ?: return@flow
            val linkElement = firstItem.selectFirst("div.name > a") ?: return@flow
            val appUrl = linkElement.attr("href")

            if (appUrl.isEmpty() || !appUrl.contains("uptodown.com")) return@flow

            val appDoc = Jsoup.connect(appUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get()

            val versionText = appDoc.selectFirst("div.version")?.text() ?: return@flow

            if (Version(versionText) > Version(app.version)) {
                 val iconSrc = firstItem.selectFirst("figure > img")?.attr("src") ?: ""
                 emit(AppUpdate(
                    name = app.label,
                    packageName = app.packageName,
                    version = versionText,
                    oldVersion = app.version,
                    versionCode = 0L,
                    oldVersionCode = app.versionCode,
                    source = UptodownSource,
                    link = Link.Url(appUrl),
                    iconUri = if (iconSrc.isNotEmpty()) Uri.parse(iconSrc) else Uri.EMPTY
                ))
            }
        } catch (e: Exception) {
            // Log.e("UptodownRepository", "Error checking ${app.packageName}", e)
        }
    }

    suspend fun search(text: String) = flow {
        val baseUrl = "https://en.uptodown.com/android/search"
        val doc = Jsoup.connect("$baseUrl?query=$text")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .timeout(10000)
            .get()

        val items = doc.select("div.item")
        val result = items.mapNotNull { item ->
            try {
                val nameElement = item.selectFirst("div.name > a")
                val name = nameElement?.text() ?: return@mapNotNull null
                val link = nameElement.attr("href")
                if (link.isEmpty()) return@mapNotNull null
                val iconSrc = item.selectFirst("figure > img")?.attr("src") ?: ""

                // Use the slug from the URL as a temporary package name
                // URL format: https://{slug}.en.uptodown.com/android
                val slug = link.substringAfter("https://").substringBefore(".en.uptodown.com")
                val packageName = if (slug.isNotEmpty()) slug else name // Fallback to name if slug parsing fails

                AppUpdate(
                    name = name,
                    packageName = packageName,
                    version = "?",
                    oldVersion = "?",
                    versionCode = 0L,
                    oldVersionCode = 0L,
                    source = UptodownSource,
                    link = Link.Url(link),
                    iconUri = if (iconSrc.isNotEmpty()) Uri.parse(iconSrc) else Uri.EMPTY
                )
            } catch (e: Exception) {
                Log.e("UptodownRepository", "Error parsing item", e)
                null
            }
        }
        emit(Result.success(result))
    }.catch {
        emit(Result.failure(it))
        Log.e("UptodownRepository", "Error searching.", it)
    }.flowOn(Dispatchers.IO)

}
