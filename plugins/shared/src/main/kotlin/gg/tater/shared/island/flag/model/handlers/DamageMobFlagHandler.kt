package gg.tater.shared.island.flag.model.handlers

import gg.tater.shared.island.flag.model.FlagType
import gg.tater.shared.island.flag.model.IslandFlagHandler
import gg.tater.shared.island.IslandService
import me.lucko.helper.Events
import me.lucko.helper.event.filter.EventFilters
import me.lucko.helper.terminable.TerminableConsumer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent

class DamageMobFlagHandler(private val service: IslandService) : IslandFlagHandler {

    override fun type(): FlagType {
        return FlagType.DAMAGE_MOBS
    }

    override fun setup(consumer: TerminableConsumer) {
        Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.HIGHEST)
            .filter(EventFilters.ignoreCancelled())
            .filter { it.entity is Mob && it.damager is Player }
            .handler {
                val entity = it.entity
                val damager = it.damager
                val world = entity.world
                val island = service.getIsland(world) ?: return@handler

                if (island.canInteract(damager.uniqueId, FlagType.DAMAGE_MOBS)) return@handler

                it.isCancelled = true
                damager.sendMessage(Component.text("You cannot do that on this island!", NamedTextColor.RED))
            }
            .bindWith(consumer)
    }
}