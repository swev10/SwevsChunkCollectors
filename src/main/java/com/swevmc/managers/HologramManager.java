package com.swevmc.managers;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;

import org.bukkit.Location;
import com.swevmc.managers.hologram.DecentHologramsProvider;
import com.swevmc.managers.hologram.FancyHologramsProvider;
import com.swevmc.managers.hologram.HologramProvider;
import com.swevmc.managers.hologram.HolographicDisplaysProvider;
import org.bukkit.Bukkit;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class HologramManager {

    private final scc plugin;
    private HologramProvider provider;
    private BukkitTask updateTask;

    public HologramManager(scc plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        if (initializeProvider()) {
            startUpdateTask();
            return true;
        }
        return false;
    }

    private boolean initializeProvider() {
        String providerName = plugin.getConfigManager().getHologramProvider();

        if (providerName.equalsIgnoreCase("AUTO")) {
            if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
                provider = new FancyHologramsProvider(plugin);
                plugin.getLogger().info("Hooked into FancyHolograms!");
                return true;
            } else if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                provider = new DecentHologramsProvider(plugin);
                plugin.getLogger().info("Hooked into DecentHolograms!");
                return true;
            } else if (Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays")) {
                provider = new HolographicDisplaysProvider(plugin);
                plugin.getLogger().info("Hooked into HolographicDisplays!");
                return true;
            }
        } else if (providerName.equalsIgnoreCase("FANCY")) {
            if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
                provider = new FancyHologramsProvider(plugin);
                return true;
            }
        } else if (providerName.equalsIgnoreCase("DECENT")) {
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                provider = new DecentHologramsProvider(plugin);
                return true;
            }
        } else if (providerName.equalsIgnoreCase("HD")) {
            if (Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays")) {
                provider = new HolographicDisplaysProvider(plugin);
                return true;
            }
        }

        return false;
    }

    private void startUpdateTask() {

        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (provider == null)
                return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (ChunkCollector collector : plugin.getChunkCollectorManager().getCollectors()) {
                    if (collector.isActive()) {
                        provider.updateHologram(collector);
                    }
                }
            });
        }, 60L, 60L);
    }

    public void createHologram(ChunkCollector collector) {
        if (provider != null) {
            provider.createHologram(collector);
        }
    }

    public void updateHologram(ChunkCollector collector) {
        if (provider != null) {
            provider.updateHologram(collector);
        }
    }

    public void removeHologram(ChunkCollector collector) {
        if (provider != null) {
            provider.removeHologram(collector);
        }
    }

    public void removeHologram(UUID collectorUuid) {

    }

    public void removeHologramByLocation(Location location) {
        if (provider != null) {
            provider.removeHologram(location);
        }
    }

    public void shutdown() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
    }
}
