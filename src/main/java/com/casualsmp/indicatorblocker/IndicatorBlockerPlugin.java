
package com.casualsmp.indicatorblocker;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class IndicatorBlockerPlugin extends JavaPlugin {

    private IndicatorPacketListener packetListener;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("PacketEvents not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        packetListener = new IndicatorPacketListener(this);

        // IMPORTANT: Use the one-argument registration method.
        PacketEvents.getAPI().getEventManager()
                .registerListener(packetListener);

        getLogger().info("IndicatorBlocker enabled!");
    }

    @Override
    public void onDisable() {

        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager()
                    .unregisterListener(packetListener);
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!command.getName().equalsIgnoreCase("indicatorblocker")) {
            return false;
        }

        if (!sender.hasPermission("indicatorblocker.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "IndicatorBlocker reloaded!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /indicatorblocker reload");
        return true;
    }
}
