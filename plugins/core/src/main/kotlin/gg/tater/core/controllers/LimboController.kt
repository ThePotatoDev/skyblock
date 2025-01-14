package gg.tater.core.controllers

import gg.tater.shared.redis.Redis
import gg.tater.shared.island.message.placement.IslandPlacementRequest
import gg.tater.shared.network.model.ServerType
import gg.tater.shared.player.PlayerDataModel
import gg.tater.shared.player.PlayerRedirectRequest
import gg.tater.shared.player.position.PlayerPositionResolver
import io.papermc.paper.event.player.AsyncChatEvent
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.event.filter.EventFilters
import me.lucko.helper.terminable.TerminableConsumer
import me.lucko.helper.terminable.module.TerminableModule
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.EventPriority
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.util.*
import java.util.concurrent.TimeUnit

class LimboController(private val redis: Redis) : TerminableModule {

    private val retries: MutableSet<UUID> = Collections.newSetFromMap(
        ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.CREATED)
            .expiration(2L, TimeUnit.SECONDS)
            .build()
    )

    override fun setup(consumer: TerminableConsumer) {
        val spawn = ServerType.LIMBO.spawn
        val location = Location(Bukkit.getWorld("world")!!, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)

        Events.subscribe(PlayerJoinEvent::class.java)
            .handler {
                it.joinMessage(null)
            }
            .bindWith(consumer)

        Events.subscribe(AsyncChatEvent::class.java, EventPriority.LOWEST)
            .filter(EventFilters.ignoreCancelled())
            .handler { it.isCancelled = true }
            .bindWith(consumer)

        Events.subscribe(AsyncPlayerPreLoginEvent::class.java, EventPriority.HIGHEST)
            .filter(EventFilters.ignoreDisallowedPreLogin())
            .handler {
                val uuid = it.uniqueId
                val name = it.name
                val data = redis.players().computeIfAbsent(uuid) {
                    PlayerDataModel(
                        uuid,
                        name,
                        ServerType.SPAWN
                    ).setPositionResolver(PlayerPositionResolver.Type.TELEPORT_SPAWN)
                }

                data.name = name
                redis.players().fastPut(uuid, data)
            }
            .bindWith(consumer)

        Schedulers.sync().runRepeating(Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                if (retries.contains(player.uniqueId)) continue
                retries.add(player.uniqueId)

                val uuid = player.uniqueId
                val name = player.name

                player.teleportAsync(location)

                redis.players().getAsync(uuid).thenAcceptAsync { data ->
                    val lastServerType = data.lastServerType
                    val lastServer = redis.getServer(lastServerType) ?: return@thenAcceptAsync
                    val island = data.islandId?.let { redis.islands()[it] }

                    if (lastServerType == ServerType.SERVER && island != null) {
                        redis.players()
                            .fastPut(uuid, data.setPositionResolver(PlayerPositionResolver.Type.TELEPORT_ISLAND_HOME))
                        redis.publish(IslandPlacementRequest(lastServer.id, uuid, island.id, name, true))
                    } else {
                        redis.publish(PlayerRedirectRequest(uuid, ServerType.SPAWN))
                    }
                }
            }
        }, 20L, 5L).bindWith(consumer)
    }
}