package io.github.restioson.koth.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;

public class KothConfig {
    public static final Codec<KothConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.playerConfig),
            MapConfig.CODEC.fieldOf("map").forGetter(config -> config.map),
            Codec.INT.fieldOf("time_limit_secs").forGetter(config -> config.timeLimitSecs),
            Codec.BOOL.fieldOf("winner_takes_all").forGetter(config -> config.winnerTakesAll)
    ).apply(instance, KothConfig::new));

    public final PlayerConfig playerConfig;
    public final MapConfig map;
    public final int timeLimitSecs;
    public final boolean winnerTakesAll;

    public KothConfig(PlayerConfig players, MapConfig map, int timeLimitSecs, boolean winnerTakesAll) {
        this.playerConfig = players;
        this.map = map;
        this.timeLimitSecs = timeLimitSecs;
        this.winnerTakesAll = winnerTakesAll;
    }

    public static class MapConfig {
        public static final Codec<MapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(config -> config.id),
                Codec.INT.fieldOf("spawn_angle").forGetter(config -> config.spawnAngle)
        ).apply(instance, MapConfig::new));

        public final Identifier id;
        public final int spawnAngle;

        public MapConfig(Identifier id, int spawnAngle) {
            this.id = id;
            this.spawnAngle = spawnAngle;
        }
    }
}
