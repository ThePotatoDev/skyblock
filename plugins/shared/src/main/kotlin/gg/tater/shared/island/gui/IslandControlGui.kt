package gg.tater.shared.island.gui

import gg.tater.shared.ARROW_TEXT
import gg.tater.shared.redis.Redis
import gg.tater.shared.fetchMojangProfile
import gg.tater.shared.island.Island
import gg.tater.shared.island.flag.IslandFlagGui
import gg.tater.shared.island.setting.IslandSettingGui
import me.lucko.helper.Schedulers
import me.lucko.helper.item.ItemStackBuilder
import me.lucko.helper.menu.Gui
import me.lucko.helper.menu.scheme.MenuScheme
import me.lucko.helper.menu.scheme.StandardSchemeMappings
import org.bukkit.Material
import org.bukkit.entity.Player

class IslandControlGui(
    player: Player,
    private val island: Island?,
    private val redis: Redis,
    private val server: String
) :
    Gui(player, 3, "Island Control") {

    companion object {
        val PRESENT_ISLAND_PANE_SCHEME: MenuScheme = MenuScheme(StandardSchemeMappings.STAINED_GLASS)
            .mask("111010111")
            .mask("110101011")
            .mask("111010111")
            .scheme(0, 0, 0, 0, 0, 0, 0)
            .scheme(0, 0, 0, 0, 0, 0)
            .scheme(0, 0, 0, 0, 0, 0, 0)

        val EMPTY_ISLAND_PANE_SCHEME: MenuScheme = MenuScheme(StandardSchemeMappings.STAINED_GLASS)
            .mask("111101111")
            .mask("111111111")
            .mask("111111111")
            .scheme(0, 0, 0, 0, 0, 0, 0, 0)
            .scheme(0, 0, 0, 0, 0, 0, 0, 0, 0)
            .scheme(0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    override fun redraw() {
        if (island == null) {
            EMPTY_ISLAND_PANE_SCHEME.apply(this)
            setItem(4, ItemStackBuilder.of(Material.CLOCK)
                .name("&3&lCreate Your Island")
                .lore(
                    " ",
                    "&7&oGenerate an island to start your adventure!",
                    " ",
                    "$ARROW_TEXT &bClick to create!",
                    " "
                )
                .build {
                    close()
                    player.performCommand("is create")
                })
            return
        }

        PRESENT_ISLAND_PANE_SCHEME.apply(this)

        setItem(
            3, ItemStackBuilder.of(Material.ENCHANTED_BOOK)
                .name("&e&lYour Island")
                .lore(
                    " ",
                    "&7&oBasic information about your island!",
                    " ",
                    "$ARROW_TEXT &6Owner: &f${island.ownerName}",
                    "$ARROW_TEXT &6Member Count: &f${island.allMembers().size}",
                    " "
                )
                .build(null)
        )

        setItem(5, ItemStackBuilder.of(Material.BLUE_BED)
            .name("&b&lIsland Home")
            .lore(
                " ",
                "&7&oTeleport directly to your island.",
                " ",
                "$ARROW_TEXT &6Click to teleport!",
                " "
            )
            .build {
                close()
                player.performCommand("is home")
            })

        setItem(11, ItemStackBuilder.of(Material.ENDER_PEARL)
            .name("&a&lIsland Warps")
            .lore(
                " ",
                "&7&oDisplay your created island warps.",
                " ",
                "$ARROW_TEXT &6Click to view!",
                " "
            )
            .build {
                IslandWarpGui(player, island, redis, server).open()
            })

        setItem(13, ItemStackBuilder.of(Material.ENDER_CHEST)
            .name("&c&lIsland Top")
            .lore(
                " ",
                "&7&oDisplay the top islands on the server.",
                " ",
                "$ARROW_TEXT &6Click to view!",
                " "
            )
            .build {
                close()
            })

        setItem(15, ItemStackBuilder.of(Material.PLAYER_HEAD)
            .name("&2&lIsland Members")
            .lore(
                " ",
                "&7&oDisplay your island members.",
                " ",
                "$ARROW_TEXT &6Click to view!",
                " "
            )
            .build {
                Schedulers.async().supply {
                    island.members.mapValues {
                        fetchMojangProfile(it.key).get()
                    }
                }.thenAcceptSync {
                    IslandMemberGui(player, island, it, redis).open()
                }
            })

        setItem(21, ItemStackBuilder.of(Material.REDSTONE_TORCH)
            .name("&6&lIsland Flags")
            .lore(
                " ",
                "&7&oDisplay your island flags.",
                " ",
                "$ARROW_TEXT &6Click to view!",
                " "
            )
            .build {
                IslandFlagGui(player, island, redis).open()
            })

        setItem(23, ItemStackBuilder.of(Material.LEVER)
            .name("&3&lIsland Settings")
            .lore(
                " ",
                "&7&oDisplay your island settings.",
                " ",
                "$ARROW_TEXT &6Click to view!",
                " "
            )
            .build {
                IslandSettingGui(player, redis, island).open()
            })
    }
}