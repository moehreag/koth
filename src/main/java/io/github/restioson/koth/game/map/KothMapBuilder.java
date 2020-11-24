package io.github.restioson.koth.game.map;

import io.github.restioson.koth.Koth;
import io.github.restioson.koth.game.KothConfig;
import net.minecraft.text.LiteralText;
import net.minecraft.world.biome.BiomeKeys;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.MapTemplateMetadata;
import xyz.nucleoid.plasmid.map.template.MapTemplateSerializer;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.io.IOException;

public class KothMapBuilder {

    private final KothConfig.MapConfig config;

    public KothMapBuilder(KothConfig.MapConfig config) {
        this.config = config;
    }

    public KothMap create() throws GameOpenException {
        try {
            MapTemplate template = MapTemplateSerializer.INSTANCE.loadFromResource(this.config.id);
            MapTemplateMetadata metadata = template.getMetadata();

            BlockBounds spawn = metadata.getFirstRegionBounds("spawn");
            if (spawn == null) {
                Koth.LOGGER.error("No spawn is defined on the map! The game will not work.");
                throw new GameOpenException(new LiteralText("no spawn defined"));
            }

            BlockBounds throne = metadata.getFirstRegionBounds("throne");

            KothMap map = new KothMap(template, spawn, throne, this.config.spawnAngle);
            template.setBiome(BiomeKeys.PLAINS);

            return map;
        } catch (IOException e) {
            throw new GameOpenException(new LiteralText("Failed to load template"), e);
        }
    }
}
