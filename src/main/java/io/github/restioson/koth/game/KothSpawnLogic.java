package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0f;
        player.setFireTicks(0);

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

        boolean validSpawn = false;

        double x = 0;
        double z = 0;
        double y = min.getY() + 0.5;

        while (!validSpawn) {
            x = MathHelper.nextDouble(player.getRandom(), min.getX(), max.getX());
            z = MathHelper.nextDouble(player.getRandom(), min.getZ(), max.getZ());

            validSpawn = (!world.getBlockState(new BlockPos(x, min.getY(), z)).isAir()) ||
                    (!world.getBlockState(new BlockPos(x, min.getY() - 1, z)).isAir()) ||
                    (!world.getBlockState(new BlockPos(x, min.getY() - 2, z)).isAir());

            System.out.println("Invalid spawn " + x + " " + (min.getY()) + " " + z + " ");
        }


        player.teleport(world, x, y, z, this.map.spawnAngle, 0.0F);
    }
}
