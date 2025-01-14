package gg.tater.core.controllers

import gg.tater.shared.redis.Redis
import gg.tater.shared.network.Agones
import gg.tater.shared.network.model.ServerState
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.TerminableConsumer
import me.lucko.helper.terminable.module.TerminableModule
import org.bukkit.Bukkit

class ServerStatusController(private val id: String, private val actions: Agones, private val redis: Redis) :
    TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        Schedulers.sync().runRepeating(Runnable {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                actions.ready()
            }

            val server = redis.servers()[id] ?: return@Runnable
            actions.health()

            if (Bukkit.getOnlinePlayers().isEmpty()) {
                server.state = ServerState.READY
            } else {
                actions.allocate()
                server.state = ServerState.ALLOCATED
            }

            server.usedMemory = getUsedMemory()
            server.players = Bukkit.getOnlinePlayers().size
            redis.servers()[id] = server
        }, 20L, 20L).bindWith(consumer)

        consumer.bind(AutoCloseable {
            actions.shutdown()
        })
    }

    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024 * 1024) // Convert bytes to MB
    }
}