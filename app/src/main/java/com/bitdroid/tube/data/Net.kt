package com.bitdroid.tube.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** The HTTP client NewPipeExtractor talks to YouTube through. */
object Net : Downloader() {
    const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), data?.toRequestBody())
            .header("User-Agent", BROWSER_UA)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            val body = r.body?.string()
            return Response(r.code, r.message, r.headers.toMultimap(), body, r.request.url.toString())
        }
    }

    /** Plain GET for small JSON calls (dislikes, update.json). */
    fun getText(url: String): String {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "bitTube").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }
}
