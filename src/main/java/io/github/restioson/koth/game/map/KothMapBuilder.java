package io.github.restioson.koth.game.map;

import io.github.restioson.koth.Koth;
import io.github.restioson.koth.game.KothConfig;
import net.minecraft.text.LiteralText;
import net.minecraft.world.biome.BiomeKeys;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.map.template.MapTemplateSerializer;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.util.concurrent.CompletableFuture;

public class KothMapBuilder {

    private final KothConfig.MapConfig config;

    public KothMapBuilder(KothConfig.MapConfig config) {
        this.config = config;
    }

    public CompletableFuture<KothMap> create() throws GameOpenException {
        return MapTemplateSerializer.INSTANCE.load(this.config.id).thenApply(template -> {
            BlockBounds spawn = template.getFirstRegion("spawn");
            if (spawn == null) {
                Koth.LOGGER.error("No spawn is defined on the map! The game will not work.");
                throw new GameOpenException(new LiteralText("no spawn defined"));
            }

            BlockBounds throne = template.getFirstRegion("throne");

            KothMap map = new KothMap(template, spawn, throne, this.config.spawnAngle);
            template.setBiome(BiomeKeys.PLAINS);

            return map;
        });
    }
}
