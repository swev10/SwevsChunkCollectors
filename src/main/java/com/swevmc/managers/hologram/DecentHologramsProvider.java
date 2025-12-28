package com.swevmc.managers.hologram;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import com.swevmc.utils.ColorUtils;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;
import java.util.stream.Collectors;

public class DecentHologramsProvider implements HologramProvider {

    private final scc plugin;

    public DecentHologramsProvider(scc plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHologram(ChunkCollector collector) {
        String hologramName = "collector_" + collector.getUuid().toString();
        Location location = collector.getLocation().clone().add(0.5,
                plugin.getConfigManager().getHologramHeightOffset(), 0.5);

        if (DHAPI.getHologram(hologramName) != null) {
            DHAPI.removeHologram(hologramName);
        }

        DHAPI.createHologram(hologramName, location, getFormattedLines(collector));
    }

    @Override
    public void updateHologram(ChunkCollector collector) {
        String hologramName = "collector_" + collector.getUuid().toString();
        Hologram hologram = DHAPI.getHologram(hologramName);

        if (hologram != null) {
            DHAPI.setHologramLines(hologram, getFormattedLines(collector));
        } else {
            createHologram(collector);
        }
    }

    @Override
    public void removeHologram(ChunkCollector collector) {
        String hologramName = "collector_" + collector.getUuid().toString();
        if (DHAPI.getHologram(hologramName) != null) {
            DHAPI.removeHologram(hologramName);
        }
    }

    @Override
    public void removeHologram(Location location) {

    }

    private List<String> getFormattedLines(ChunkCollector collector) {
        List<String> lines = plugin.getConfigManager().getHologramLines();
        return lines.stream().map(line -> {
            line = line.replace("${amount}", String.format("%.2f", collector.getTotalMoneyEarned()));
            line = line.replace("${owner}", collector.getOwnerName());
            line = line.replace("${time}", formatTime(collector.getTimeRemaining()));
            line = line.replace("${battery}", collector.getBatteryBars());
            return ColorUtils.translateColors(line);
        }).collect(Collectors.toList());
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
