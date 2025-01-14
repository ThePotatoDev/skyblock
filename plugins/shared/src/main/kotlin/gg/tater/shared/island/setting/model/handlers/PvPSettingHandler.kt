package gg.tater.shared.island.setting.model.handlers

import gg.tater.shared.island.IslandService
import gg.tater.shared.island.setting.model.IslandSettingHandler
import gg.tater.shared.island.setting.model.IslandSettingType
import me.lucko.helper.Events
import me.lucko.helper.event.filter.EventFilters
import me.lucko.helper.terminable.TerminableConsumer
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent

class PvPSettingHandler(service: IslandService) : IslandSettingHandler(service) {

    override fun setup(consumer: TerminableConsumer) {
        Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.HIGHEST)
            .filter(EventFilters.ignoreCancelled())
            .filter { it.entity is Player && it.damager is Player }
            .handler {
                val entity = it.entity
                val world = entity.location.world
                val island = service.getIsland(world) ?: return@handler

                val enabled = island.isSettingEnabled(IslandSettingType.PVP)
                if (enabled) return@handler

                it.isCancelled = true
            }
            .bindWith(consumer)

        Events.subscribe(ProjectileHitEvent::class.java, EventPriority.HIGHEST)
            .handler {
                val hitEntity = it.hitEntity
                if (hitEntity !is Player) return@handler

                val world = hitEntity.world
                val island = service.getIsland(world) ?: return@handler

                val enabled = island.isSettingEnabled(IslandSettingType.PVP)
                if (enabled) return@handler

                it.isCancelled = true
            }
            .bindWith(consumer)
    }
}