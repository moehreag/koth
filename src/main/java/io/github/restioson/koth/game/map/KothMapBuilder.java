package io.github.restioson.koth.game.map;

import io.github.restioson.koth.Koth;
import io.github.restioson.koth.game.KothConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;
import net.minecraft.world.biome.BiomeKeys;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateMetadata;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.GameOpenException;

import java.io.IOException;

public class KothMapBuilder {

    private final KothConfig.MapConfig config;

    public KothMapBuilder(KothConfig.MapConfig config) {
        this.config = config;
    }

    public KothMap create(MinecraftServer server) throws GameOpenException {
        try {
            MapTemplate template = MapTemplateSerializer.loadFromResource(server, this.config.id());
            MapTemplateMetadata metadata = template.getMetadata();

            BlockBounds spawn = metadata.getFirstRegionBounds("spawn");
            if (spawn == null) {
                Koth.LOGGER.error("No spawn is defined on the map! The game will not work.");
                throw new GameOpenException(new LiteralText("no spawn defined"));
            }

            BlockBounds throne = metadata.getFirstRegionBounds("throne");

            KothMap map = new KothMap(template, spawn, throne, this.config.spawnAngle());
            template.setBiome(BiomeKeys.PLAINS);

            return map;
        } catch (IOException e) {
            throw new GameOpenException(new LiteralText("Failed to load template"), e);
        }
    }
}
