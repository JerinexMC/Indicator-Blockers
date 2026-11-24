package com.casualsmp.indicatorblocker;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;  public class IndicatorPacketListener extends PacketListenerAbstract { {

    // 1.21.x LivingEntity SynchedEntityData indices:
    // 9 = DATA_HEALTH_ID (Float)
    // 10 = DATA_EFFECT_PARTICLES (List<ParticleOptions>)
    // 17 = Player DATA_PLAYER_ABSORPTION_ID (Float)
    private static final int INDEX_LIVING_HEALTH = 9;
    private static final int INDEX_EFFECT_PARTICLES = 10;
    private static final int INDEX_PLAYER_ABSORPTION = 17;

    private final IndicatorBlockerPlugin plugin;

    public IndicatorPacketListener(IndicatorBlockerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player receiver)) {
            return;
        }
        if (receiver.hasPermission("indicatorblocker.bypass")) {
            return;
        }

        int receiverEntityId = receiver.getEntityId();

        // 1. Block potion status effect packets sent about OTHER entities/players
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EFFECT) {
            if (!plugin.getConfig().getBoolean("hide-other-player-potions.enabled", true)
                    || !plugin.getConfig().getBoolean("hide-other-player-potions.block-effect-packets", true)) {
                return;
            }
            WrapperPlayServerEntityEffect wrapper = new WrapperPlayServerEntityEffect(event);
            if (wrapper.getEntityId() != receiverEntityId) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
            if (!plugin.getConfig().getBoolean("hide-other-player-potions.enabled", true)
                    || !plugin.getConfig().getBoolean("hide-other-player-potions.block-effect-packets", true)) {
                return;
            }
            WrapperPlayServerRemoveEntityEffect wrapper = new WrapperPlayServerRemoveEntityEffect(event);
            if (wrapper.getEntityId() != receiverEntityId) {
                event.setCancelled(true);
            }
            return;
        }

        // 2. Spoof Health, Absorption, and Potion Particle Metadata on OTHER entities
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            int targetId = wrapper.getEntityId();

            // Never touch the player's own metadata so their own health/hearts/effects stay 100% real
            if (targetId == receiverEntityId) {
                return;
            }

            Entity targetEntity = findEntityById(receiver, targetId);
            if (!(targetEntity instanceof LivingEntity)) {
                return;
            }

            boolean isPlayerTarget = targetEntity instanceof Player;
            boolean spoofHealthEnabled = plugin.getConfig().getBoolean("spoof-health.enabled", true);
            boolean playersOnly = plugin.getConfig().getBoolean("spoof-health.players-only", true);
            float spoofedHealthValue = (float) plugin.getConfig().getDouble("spoof-health.spoofed-health", 20.0);
            boolean spoofAbsorption = plugin.getConfig().getBoolean("spoof-health.spoof-absorption", true);

            boolean hidePotionParticles = plugin.getConfig().getBoolean("hide-other-player-potions.enabled", true)
                    && plugin.getConfig().getBoolean("hide-other-player-potions.hide-potion-particles", true);

            List<EntityData<?>> metadataList = wrapper.getEntityMetadata();
            boolean modified = false;

            for (int i = 0; i < metadataList.size(); i++) {
                EntityData<?> data = metadataList.get(i);
                int index = data.getIndex();

                // Spoof Health (Index 9)
                if (spoofHealthEnabled && (!playersOnly || isPlayerTarget) && index == INDEX_LIVING_HEALTH) {
                    if (data.getValue() instanceof Float currentHp && currentHp > 0.0f) {
                        metadataList.set(i, new EntityData<>(INDEX_LIVING_HEALTH, EntityDataTypes.FLOAT, spoofedHealthValue));
                        modified = true;
                    }
                }

                // Spoof Absorption / Golden Hearts (Index 17 on Player)
                if (spoofHealthEnabled && spoofAbsorption && isPlayerTarget && index == INDEX_PLAYER_ABSORPTION) {
                    if (data.getValue() instanceof Float currentAbs && currentAbs > 0.0f) {
                        metadataList.set(i, new EntityData<>(INDEX_PLAYER_ABSORPTION, EntityDataTypes.FLOAT, 0.0f));
                        modified = true;
                    }
                }

                // Clear Potion Particle Swirls (Index 10)
                if (hidePotionParticles && isPlayerTarget && index == INDEX_EFFECT_PARTICLES) {
                    if (data.getValue() instanceof List<?> particles && !particles.isEmpty()) {
                        metadataList.set(i, new EntityData<>(INDEX_EFFECT_PARTICLES, EntityDataTypes.PARTICLES, new ArrayList<>()));
                        modified = true;
                    }
                }
            }

            if (modified) {
                wrapper.setEntityMetadata(metadataList);
                event.markForReEncode(true);
            }
            return;
        }

        // 3. Strip Durability Damage Component from OTHER players' equipped items
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            if (!plugin.getConfig().getBoolean("spoof-equipment-durability.enabled", true)
                    || !plugin.getConfig().getBoolean("spoof-equipment-durability.strip-damage-component", true)) {
                return;
            }

            WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
            if (wrapper.getEntityId() == receiverEntityId) {
                return;
            }

            boolean hideEnchants = plugin.getConfig().getBoolean("spoof-equipment-durability.hide-enchantments", false);
            List<Equipment> equipmentList = wrapper.getEquipment();
            boolean modified = false;

            for (Equipment eq : equipmentList) {
                ItemStack item = eq.getItem();
                if (item == null || item.isEmpty()) {
                    continue;
                }

                ItemStack copy = item.copy();
                boolean itemChanged = false;

                if (copy.hasComponent(ComponentTypes.DAMAGE)) {
                    copy.unsetComponent(ComponentTypes.DAMAGE);
                    itemChanged = true;
                }

                if (hideEnchants && copy.hasComponent(ComponentTypes.ENCHANTMENTS)) {
                    copy.unsetComponent(ComponentTypes.ENCHANTMENTS);
                    itemChanged = true;
                }

                if (itemChanged) {
                    eq.setItem(copy);
                    modified = true;
                }
            }

            if (modified) {
                wrapper.setEquipment(equipmentList);
                event.markForReEncode(true);
            }
        }
    }

    private Entity findEntityById(Player observer, int entityId) {
        for (Entity entity : observer.getWorld().getEntities()) {
            if (entity.getEntityId() == entityId) {
                return entity;
            }
        }
        return null;
    }
}
