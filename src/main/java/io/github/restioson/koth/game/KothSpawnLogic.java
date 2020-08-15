package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class KothSpawnLogic {
    private final GameWorld gameWorld;
    private final KothMap map;

    public KothSpawnLogic(GameWorld gameWorld, KothMap map) {
        this.gameWorld = gameWorld;
        this.map = map;
    }

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.setGameMode(gameMode);

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                20 * 60 * 60,
                1,
                true,
                false
        ));
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        ServerWorld world = this.gameWorld.getWorld();

        BlockBounds bounds = this.map.spawn;
        BlockPos min = bounds.getMin();
        BlockPos max = bounds.getMax();

        double x = MathHelper.nextDouble(player.getRandom(), min.getX(), max.getX());
        double z = MathHelper.nextDouble(player.getRandom(), min.getZ(), max.getZ());
        double y = min.getY() + 1;

        player.teleport(world, x, y, z, this.map.spawnAngle, 0.0F);
    }
}
