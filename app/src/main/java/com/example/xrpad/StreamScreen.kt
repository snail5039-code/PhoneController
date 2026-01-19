package com.example.xrpad

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@Composable
fun rememberMjpegFrames(url: String): State<ByteArray?> {
    val client = remember {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    return produceState<ByteArray?>(initialValue = null, url) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            delay(500)
                            return@use
                        }
                        val body = resp.body ?: run {
                            delay(500); return@use
                        }
                        val stream = BufferedInputStream(body.byteStream())

                        val buf = ByteArray(4096)
                        var inJpeg = false
                        var prev = -1
                        val out = ByteArrayOutputStream(256 * 1024)

                        while (isActive) {
                            val n = stream.read(buf)
                            if (n <= 0) break

                            for (i in 0 until n) {
                                val b = buf[i].toInt() and 0xFF

                                if (!inJpeg) {
                                    if (prev == 0xFF && b == 0xD8) {
                                        inJpeg = true
                                        out.reset()
                                        out.write(0xFF)
                                        out.write(0xD8)
                                        prev = -1
                                        continue
                                    }
                                    prev = b
                                } else {
                                    out.write(b)
                                    if (prev == 0xFF && b == 0xD9) {
                                        value = out.toByteArray()
                                        inJpeg = false
                                        prev = -1
                                        continue
                                    }
                                    prev = b
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    delay(500)
                }
            }
        }
    }
}
