package gg.tater.shared

import com.google.gson.JsonParser
import gg.tater.shared.redis.Redis
import me.lucko.helper.Services
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

fun fetchMojangProfile(uuid: UUID): Future<Pair<String, String>> {
    val redis = Services.load(Redis::class.java)

    return redis.profiles()
        .getAsync(uuid)
        .thenApplyAsync { current ->
            if (current != null) return@thenApplyAsync current

            val request = Request.Builder()
                .url("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")
                .get()
                .build()

            Services.load(OkHttpClient::class.java)
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }

                    JsonParser.parseString(response.body?.string())
                        .asJsonObject
                        .let {
                            val properties = it.get("properties").asJsonArray
                            val profile =
                                Pair(it.get("name").asString, properties[0].asJsonObject.get("value").asString)
                            redis.profiles().fastPutAsync(uuid, profile, 1L, TimeUnit.DAYS)
                            profile
                        }
                }
        }.toCompletableFuture()
}