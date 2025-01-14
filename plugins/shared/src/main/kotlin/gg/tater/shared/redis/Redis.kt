package gg.tater.shared.redis

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.tater.shared.JSON
import gg.tater.shared.island.Island
import gg.tater.shared.network.model.ServerDataModel
import gg.tater.shared.network.model.ServerState
import gg.tater.shared.network.model.ServerType
import gg.tater.shared.player.PlayerDataModel
import gg.tater.shared.player.auction.AuctionHouseItem
import gg.tater.shared.player.economy.PlayerEconomyModel
import gg.tater.shared.player.kit.KitPlayerDataModel
import gg.tater.shared.player.playershop.PlayerShopDataModel
import gg.tater.shared.player.progression.PlayerProgressDataModel
import gg.tater.shared.player.vault.VaultDataModel
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.redisson.Redisson
import org.redisson.api.*
import org.redisson.client.codec.BaseCodec
import org.redisson.client.handler.State
import org.redisson.client.protocol.Decoder
import org.redisson.client.protocol.Encoder
import org.redisson.config.Config
import org.reflections.Reflections
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.reflect.KClass

class Redis(credential: Credential) {

    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class ReqRes(val channel: String)

    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Mapping(val id: String)

    data class Credential(val username: String, val password: String, val address: String, val port: Int)

    internal class Codec : BaseCodec() {
        companion object {
            const val MAPPING_FIELD = "mapping"
            const val DATA_FIELD = "data"
        }

        private val mappings: BiMap<KClass<*>, String> = HashBiMap.create()

        /**
         * Initialize the mappings for the codec
         * Mappings are ID references to POJO's that are serialized and deserialized
         * ID's should not change once a mapping is created and actively used in production.
         * If a mapping is changed, the Redis map should be cleared to prevent deserialization errors.
         *
         * The overarching goal of the mapping system is to allow POJO classes
         * to be relocated or renamed without breaking the serialization/deserialization process.
         *
         * If a mapping does not exist for an object, the class name will be used as the mapping. (This is not recommended but required for classes such as string, int, etc.)
         */
        init {
            val reflections = Reflections("gg.tater.shared")
            for (clazz in reflections.getTypesAnnotatedWith(Mapping::class.java)) {
                val mapping = clazz.getAnnotation(Mapping::class.java)
                mappings[clazz.kotlin] = mapping.id
            }
        }

        private val encoder = Encoder { obj ->
            try {
                val mapping =
                    mappings[obj::class]
                        ?: obj::class.java.name // If the class mapping is not registered, use the simple name

                Unpooled.wrappedBuffer(JsonObject().apply {
                    addProperty(MAPPING_FIELD, mapping)
                    addProperty(DATA_FIELD, JSON.toJson(obj))
                }.toString().toByteArray(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                throw IOException("Error encoding object to JSON", e)
            }
        }

        private val decoder = Decoder { buf: ByteBuf, _: State? ->
            try {
                val json = JsonParser.parseString(buf.toString(StandardCharsets.UTF_8)).asJsonObject
                val mapping = json.get(MAPPING_FIELD).asString
                val data = json.get(DATA_FIELD).asString
                val clazz =
                    mappings.inverse()[mapping] ?: Class.forName(mapping).kotlin
                JSON.fromJson(data, clazz.java)
            } catch (e: Exception) {
                throw IOException("Error decoding JSON to object", e)
            }
        }

        override fun getMapValueDecoder() = decoder
        override fun getMapValueEncoder() = encoder
        override fun getMapKeyDecoder() = decoder
        override fun getMapKeyEncoder() = encoder
        override fun getValueDecoder() = decoder
        override fun getValueEncoder() = encoder
    }

    companion object {
        const val PLAYER_MAP_NAME = "players"
        const val SERVER_MAP_NAME = "servers"
        const val ECONOMY_MAP_NAME = "economy"
        const val MESSAGE_TARGET_MAP_NAME = "message_targets"
        const val ISLAND_MAP_NAME = "islands"
        const val INVITES_FOR_MAP_NAME = "invites_for"
        const val PROGRESSIONS_DATA_MODEL = "progressions"
        const val KIT_PLAYER_DATA_MODEL = "kit_players"
        const val AUCTIONS_SET_NAME = "auctions"
        const val EXPIRED_AUCTIONS_SET_NAME = "expired_auctions"
        const val VAULT_MAP_NAME = "vaults"
        const val PROFILES_MAP_NAME = "profiles"
        const val PLAYER_SHOP_MAP_NAME = "player_shops"
    }

    val client: RedissonClient
    private val codec: Codec

    init {
        val config = Config()
        val instance = Codec()
        config.useSingleServer().address =
            "redis://${credential.username}:${credential.password}@${credential.address}:${credential.port}"
        config.codec = instance

        this.codec = instance
        this.client = Redisson.create(config)
    }

    fun playerShops(): RMap<UUID, PlayerShopDataModel> {
        return client.getMap(PLAYER_SHOP_MAP_NAME)
    }

    fun profiles(): RMapCache<UUID, Pair<String, String>> {
        return client.getMapCache(PROFILES_MAP_NAME)
    }

    fun vaults(): RMap<UUID, VaultDataModel> {
        return client.getMap(VAULT_MAP_NAME)
    }

    fun economy(): RMap<UUID, PlayerEconomyModel> {
        return client.getMap(ECONOMY_MAP_NAME)
    }

    fun auctions(): RMapCache<UUID, AuctionHouseItem> {
        return client.getMapCache(AUCTIONS_SET_NAME)
    }

    fun expiredAuctions(): RListMultimap<UUID, AuctionHouseItem> {
        return client.getListMultimap(EXPIRED_AUCTIONS_SET_NAME)
    }

    fun kits(): RMap<UUID, KitPlayerDataModel> {
        return client.getMap(KIT_PLAYER_DATA_MODEL)
    }

    fun invites(): RListMultimapCache<UUID, UUID> {
        return client.getListMultimapCache(INVITES_FOR_MAP_NAME)
    }

    fun progressions(): RMap<UUID, PlayerProgressDataModel> {
        return client.getMap(PROGRESSIONS_DATA_MODEL)
    }

    fun players(): RMap<UUID, PlayerDataModel> {
        return client.getMap(PLAYER_MAP_NAME)
    }

    fun servers(): RMap<String, ServerDataModel> {
        return client.getMap(SERVER_MAP_NAME)
    }

    fun targets(): RMapCache<UUID, UUID> {
        return client.getMapCache(MESSAGE_TARGET_MAP_NAME)
    }

    fun islands(): RMap<UUID, Island> {
        return client.getMap(ISLAND_MAP_NAME)
    }

    fun deleteIsland(island: Island): RFuture<Island> {
        // Remove the island for all the members
        return islands().removeAsync(island.id)
    }

    fun getServer(id: String): RFuture<ServerDataModel?> {
        return servers().getAsync(id)
    }

    // Primary function to get a server of a specific type that's already allocated or ready to be allocated
    fun getServer(type: ServerType): ServerDataModel? {
        return query(type)
    }

    fun getReadyServer(type: ServerType): ServerDataModel {
        return servers().values.filter { it.type == type && it.state == ServerState.READY }
            .minByOrNull { it.usedMemory }
            ?: throw IllegalStateException("No servers available for type $type")
    }

    inline fun <reified T : Any> listen(noinline consumer: (T) -> Unit) {
        val meta = T::class.annotations.find { it is ReqRes } as? ReqRes
            ?: throw IllegalArgumentException("Class ${T::class} must have @ReqRes annotation")

        client.getTopic(meta.channel).addListenerAsync(T::class.java) { _, message ->
            consumer(message)
        }
    }

    inline fun <reified T : Any> publish(message: T) {
        val meta = T::class.annotations.find { it is ReqRes } as? ReqRes
            ?: throw IllegalArgumentException("Class ${T::class} must have @ReqRes annotation")

        client.getTopic(meta.channel).publishAsync(message)
    }

    private fun query(type: ServerType): ServerDataModel? {
        var allocated = servers().values.filter { it.type == type && it.state == ServerState.ALLOCATED }
            .minByOrNull { it.usedMemory }

        // If there's no servers that are allocated, find a regular ready server
        if (allocated == null) {
            return servers().values.firstOrNull { it.type == type && it.state == ServerState.READY }
        }

        val usedMemory = allocated.usedMemory

        // If the server memory will be maxed out or near maxed out, we want to find a server that is ready
        if (usedMemory + ServerDataModel.SERVER_MEMORY_PER_WORLD > ServerDataModel.MAX_SERVER_MEMORY ||
            usedMemory + ServerDataModel.SERVER_MEMORY_PER_WORLD >= ServerDataModel.MAX_SERVER_MEMORY - 50
        ) {
            allocated = servers().values.filter { it.type == type && it.state == ServerState.READY }
                .minByOrNull { it.usedMemory }
        }

        return allocated
    }
}