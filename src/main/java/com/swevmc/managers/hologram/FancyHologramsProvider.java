package com.swevmc.managers.hologram;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import com.swevmc.utils.ColorUtils;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;

import java.util.List;
import java.util.stream.Collectors;

public class FancyHologramsProvider implements HologramProvider {

    private final scc plugin;

    public FancyHologramsProvider(scc plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHologram(ChunkCollector collector) {
        String hologramName = "collector_" + collector.getUuid().toString();
        Location location = collector.getLocation().clone().add(0.5,
                plugin.getConfigManager().getHologramHeightOffset(), 0.5);

        TextHologramData data = new TextHologramData(hologramName, location);
        data.setText(getFormattedLines(collector));
        data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);

        String backgroundStr = plugin.getConfigManager().getHologramBackground();
        if (backgroundStr != null && !backgroundStr.equalsIgnoreCase("TRANSPARENT")) {
            try {
                org.bukkit.Color color;
                if (backgroundStr.startsWith("#")) {

                    int r = Integer.valueOf(backgroundStr.substring(1, 3), 16);
                    int g = Integer.valueOf(backgroundStr.substring(3, 5), 16);
                    int b = Integer.valueOf(backgroundStr.substring(5, 7), 16);
                    color = org.bukkit.Color.fromRGB(r, g, b);
                } else {

                    java.awt.Color awtColor;
                    try {
                        java.lang.reflect.Field field = java.awt.Color.class.getField(backgroundStr.toLowerCase());
                        awtColor = (java.awt.Color) field.get(null);
                        color = org.bukkit.Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
                    } catch (Exception e) {

                        int r = Integer.valueOf(backgroundStr.substring(1, 3), 16);
                        int g = Integer.valueOf(backgroundStr.substring(3, 5), 16);
                        int b = Integer.valueOf(backgroundStr.substring(5, 7), 16);
                        color = org.bukkit.Color.fromRGB(r, g, b);
                    }
                }
                data.setBackground(color);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid hologram background color: " + backgroundStr);
            }
        }

        Hologram hologram = FancyHologramsPlugin.get().getHologramManager().create(data);
        FancyHologramsPlugin.get().getHologramManager().addHologram(hologram);
    }

    @Override
    public void updateHologram(ChunkCollector collector) {
        String hologramName = "collector_" + collector.getUuid().toString();
        Hologram hologram = FancyHologramsPlugin.get().getHologramManager().getHologram(hologramName).orElse(null);

        if (hologram != null && hologram.getData() instanceof TextHologramData textData) {
            textData.setText(getFormattedLines(collector));
            hologram.updateHologram();
        } else {
            createHologram(collector);
        }
    }

    @Override
    public void removeHologram(ChunkCollector collector) {
        String hologramName = "collector_" + collector.getUuid().toString();
        Hologram hologram = FancyHologramsPlugin.get().getHologramManager().getHologram(hologramName).orElse(null);

        if (hologram != null) {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
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
