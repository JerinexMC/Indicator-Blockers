
package com.casualsmp.indicatorblocker;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;

import org.bukkit.entity.Player;

import java.util.List;

public final class IndicatorPacketListener extends PacketListenerAbstract {

    private final IndicatorBlockerPlugin plugin;

    // Minecraft 1.21.11 LivingEntity metadata indexes.
    private static final int POTION_COLOR_INDEX = 10;
    private static final int POTION_AMBIENT_INDEX = 11;

    public IndicatorPacketListener(IndicatorBlockerPlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {

        if (!(event.getPlayer() instanceof Player receiver)) {
            return;
        }

        // Players with bypass permission see everything normally.
        if (receiver.hasPermission("indicatorblocker.bypass")) {
            return;
        }

        int receiverId = receiver.getEntityId();

        // 1. Hide other players' potion effect packets.
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EFFECT) {

            if (!plugin.getConfig().getBoolean(
                    "hide-other-player-potions.block-effect-packets", true)) {
                return;
            }

            WrapperPlayServerEntityEffect wrapper =
                    new WrapperPlayServerEntityEffect(event);

            if (wrapper.getEntityId() != receiverId) {
                event.setCancelled(true);
            }

            return;
        }

        // 2. Hide other players' potion effect removal packets.
        if (event.getPacketType() == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {

            if (!plugin.getConfig().getBoolean(
                    "hide-other-player-potions.block-effect-packets", true)) {
                return;
            }

            WrapperPlayServerRemoveEntityEffect wrapper =
                    new WrapperPlayServerRemoveEntityEffect(event);

            if (wrapper.getEntityId() != receiverId) {
                event.setCancelled(true);
            }

            return;
        }

        // 3. Hide other players' potion color and ambient metadata.
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {

            if (!plugin.getConfig().getBoolean(
                    "hide-other-player-potions.hide-potion-particles", true)) {
                return;
            }

            WrapperPlayServerEntityMetadata wrapper =
                    new WrapperPlayServerEntityMetadata(event);

            // Do not modify the receiver's own metadata.
            if (wrapper.getEntityId() == receiverId) {
                return;
            }

            List<EntityData<?>> metadata = wrapper.getEntityMetadata();
            boolean modified = false;

            for (int i = 0; i < metadata.size(); i++) {

                EntityData<?> data = metadata.get(i);

                // Potion color.
                if (data.getIndex() == POTION_COLOR_INDEX
                        && data.getValue() instanceof Integer color
                        && color != 0) {

                    metadata.set(i, new EntityData<>(
                            POTION_COLOR_INDEX,
                            EntityDataTypes.INT,
                            0
                    ));

                    modified = true;
                }

                // Potion ambient flag.
                if (data.getIndex() == POTION_AMBIENT_INDEX
                        && data.getValue() instanceof Boolean ambient
                        && ambient) {

                    metadata.set(i, new EntityData<>(
                            POTION_AMBIENT_INDEX,
                            EntityDataTypes.BOOLEAN,
                            false
                    ));

                    modified = true;
                }
            }

            if (modified) {
                wrapper.setEntityMetadata(metadata);
                event.markForReEncode(true);
            }

            return;
        }

        // 4. Make other players' armor appear undamaged.
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {

            if (!plugin.getConfig().getBoolean(
                    "spoof-equipment-durability.enabled", true)) {
                return;
            }

            WrapperPlayServerEntityEquipment wrapper =
                    new WrapperPlayServerEntityEquipment(event);

            // Do not modify the receiver's own equipment.
            if (wrapper.getEntityId() == receiverId) {
                return;
            }

            List<Equipment> equipment = wrapper.getEquipment();
            boolean modified = false;

            for (Equipment eq : equipment) {

                ItemStack item = eq.getItem();

                if (item == null || item.isEmpty()) {
                    continue;
                }

                ItemStack copy = item.copy();

                if (copy.hasComponent(ComponentTypes.DAMAGE)) {
                    copy.unsetComponent(ComponentTypes.DAMAGE);

                    eq.setItem(copy);
                    modified = true;
                }
            }

            if (modified) {
                wrapper.setEquipment(equipment);
                event.markForReEncode(true);
            }
        }
    }
}
