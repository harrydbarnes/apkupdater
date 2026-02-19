package com.apkupdater.repository

import android.net.Uri
import android.util.Log
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.UptodownSource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup

class UptodownRepository {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        // Uptodown doesn't support batch update checking efficiently.
        // Returning empty list for now.
        emit(emptyList<AppUpdate>())
    }.catch {
        emit(emptyList())
        Log.e("UptodownRepository", "Error getting updates", it)
    }

    suspend fun search(text: String) = flow {
        val baseUrl = "https://en.uptodown.com/android/search"
        val doc = Jsoup.connect("$baseUrl?query=$text")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
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
    }

}
