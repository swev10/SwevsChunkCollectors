package com.swevmc.managers.hologram;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import com.swevmc.utils.ColorUtils;
import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HolographicDisplaysProvider implements HologramProvider {

    private final scc plugin;
    private final HolographicDisplaysAPI api;
    private final Map<UUID, Hologram> holograms = new HashMap<>();

    public HolographicDisplaysProvider(scc plugin) {
        this.plugin = plugin;
        this.api = HolographicDisplaysAPI.get(plugin);
    }

    @Override
    public void createHologram(ChunkCollector collector) {
        removeHologram(collector);

        Location location = collector.getLocation().clone().add(0.5,
                plugin.getConfigManager().getHologramHeightOffset() + 2, 0.5);
        Hologram hologram = api.createHologram(location);

        updateHologramLines(hologram, collector);

        holograms.put(collector.getUuid(), hologram);
    }

    @Override
    public void updateHologram(ChunkCollector collector) {
        Hologram hologram = holograms.get(collector.getUuid());
        if (hologram == null || hologram.isDeleted()) {
            createHologram(collector);
            return;
        }

        updateHologramLines(hologram, collector);
    }

    @Override
    public void removeHologram(ChunkCollector collector) {
        Hologram hologram = holograms.remove(collector.getUuid());
        if (hologram != null) {
            hologram.delete();
        }
    }

    @Override
    public void removeHologram(Location location) {

    }

    private void updateHologramLines(Hologram hologram, ChunkCollector collector) {
        hologram.getLines().clear();
        List<String> lines = plugin.getConfigManager().getHologramLines();

        for (String line : lines) {
            line = line.replace("${amount}", String.format("%.2f", collector.getTotalMoneyEarned()));
            line = line.replace("${owner}", collector.getOwnerName());
            line = line.replace("${time}", formatTime(collector.getTimeRemaining()));
            line = line.replace("${battery}", collector.getBatteryBars());
            hologram.getLines().appendText(ColorUtils.translateColors(line));
        }
    }

    private String formatTime(long timeRemaining) {
        if (timeRemaining <= 0) {
            return "&cNo Charge";
        }

        long hours = timeRemaining / 3600;
        long minutes = (timeRemaining % 3600) / 60;
        long seconds = timeRemaining % 60;

        if (hours > 0) {
            return String.format("&e%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("&e%dm %ds", minutes, seconds);
        } else {
            return String.format("&e%ds", seconds);
        }
    }
}
