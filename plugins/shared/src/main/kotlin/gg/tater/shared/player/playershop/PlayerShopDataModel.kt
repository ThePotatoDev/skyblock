package gg.tater.shared.player.playershop

import com.google.gson.*
import gg.tater.shared.JSON
import gg.tater.shared.player.position.WrappedPosition
import me.lucko.helper.serialize.Serializers
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Type
import java.util.*

class PlayerShopDataModel(
    var name: String,
    var description: String,
    var icon: ItemStack,
    val islandId: UUID,
    var position: WrappedPosition,
    var visits: Int = 0,
    var open: Boolean = true,
) {

    companion object {
        const val NAME_FIELD = "name"
        const val DESCRIPTION_FIELD = "description"
        const val ISLAND_ID_FIELD = "island_id"
        const val ICON_FIELD = "icon"
        const val POSITION_FIELD = "position"
        const val VISITS_FIELD = "vists"
        const val OPEN_FIELD = "open"
    }

    class Adapter : JsonSerializer<PlayerShopDataModel>, JsonDeserializer<PlayerShopDataModel> {
        override fun serialize(warp: PlayerShopDataModel, type: Type, context: JsonSerializationContext): JsonElement {
            return JsonObject().apply {
                addProperty(NAME_FIELD, warp.name)
                addProperty(DESCRIPTION_FIELD, warp.description)
                addProperty(ISLAND_ID_FIELD, warp.islandId.toString())
                addProperty(VISITS_FIELD, warp.visits)
                addProperty(POSITION_FIELD, JSON.toJson(warp.position))
                addProperty(OPEN_FIELD, warp.open)
                add(ICON_FIELD, Serializers.serializeItemstack(warp.icon))
            }
        }

        override fun deserialize(element: JsonElement, type: Type, context: JsonDeserializationContext): PlayerShopDataModel {
            (element as JsonObject).apply {
                val name = get(NAME_FIELD).asString
                val description = get(DESCRIPTION_FIELD).asString
                val islandId = UUID.fromString(get(ISLAND_ID_FIELD).asString)
                val visits = get(VISITS_FIELD).asInt
                val icon = Serializers.deserializeItemstack(get(ICON_FIELD).asJsonPrimitive)
                val position = JSON.fromJson(get(POSITION_FIELD).asString, WrappedPosition::class.java)
                val open = get(OPEN_FIELD).asBoolean
                return PlayerShopDataModel(name, description, icon, islandId, position, visits, open)
            }
        }
    }
}