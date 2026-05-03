package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.BedGunWars;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ExcludedGunsConfig {

    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("bedgunwars-excluded-guns.txt");

    private static final Set<ResourceLocation> excluded = new HashSet<>();

    public static void load() {
        excluded.clear();
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.writeString(CONFIG_PATH,
                        // default excluded guns, too powerful
                        "# BedGunWars Excluded Guns Config\n" +
                                "# One gun ID per line. Lines starting with # are ignored.\n" +
                                "# Use /game excludegun reload to reload without restarting.\n" +
                                "# Use /game debug listtaczitems to find gun IDs.\n" +
                                "#\n" +
                                "tacz:ai_awp\n" +
                                "tacz:m107\n" +
                                "tacz:minigun\n" +
                                "tacz:deagle_golden\n" +
                                "daffas_arsenal:samula3\n" +
                                "tacz:m95\n"
                );
                BedGunWars.LOGGER.info("[ExcludedGuns] Created default config at {}", CONFIG_PATH);
                // Fall through to load the defaults we just wrote
            }
            for (String line : Files.readAllLines(CONFIG_PATH)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                    try {
                        excluded.add(ResourceLocation.parse(line));
                    BedGunWars.debugLog("[ExcludedGuns] Excluded: {}", line);
                } catch (Exception e) {
                    BedGunWars.LOGGER.warn("[ExcludedGuns] Invalid ID: {}", line);
                }
            }
            BedGunWars.LOGGER.info("[ExcludedGuns] Loaded {} excluded guns.", excluded.size());
        } catch (IOException e) {
            BedGunWars.LOGGER.error("[ExcludedGuns] Failed to load config: {}", e.getMessage());
        }
    }

    public static boolean isExcluded(ResourceLocation id) {
        return excluded.contains(id);
    }

    public static Set<ResourceLocation> getExcluded() {
        return excluded;
    }
}