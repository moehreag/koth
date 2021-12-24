package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Random;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;

public class KothSpawnLogic {
    private final ServerWorld world;
    private final KothMap map;
    private final LongList spawnPositions;

    public KothSpawnLogic(ServerWorld world, KothMap map) {
        this.world = world;
        this.map = map;
        this.spawnPositions = collectSpawnPositions(world, map);
    }

    public PlayerOfferResult acceptPlayer(PlayerOffer offer, GameMode gameMode, @Nullable KothStageManager stageManager) {
        var player = offer.player();
        return offer.accept(this.world, this.findSpawnFor(player))
                .and(() -> {
                    player.setYaw(this.map.spawnAngle);
                    this.resetPlayer(player, gameMode, stageManager);
                });
    }

    public void resetAndRespawn(ServerPlayerEntity player, GameMode gameMode, @Nullable KothStageManager stageManager) {
        Vec3d spawn = this.findSpawnFor(player);
        player.teleport(this.world, spawn.x, spawn.y, spawn.z, this.map.spawnAngle, 0.0F);

        this.resetPlayer(player, gameMode, stageManager);
    }

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode, @Nullable KothStageManager stageManager) {
        player.changeGameMode(gameMode);
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

        player.networkHandler.syncWithPlayerPosition();

        if (stageManager != null) {
            KothStageManager.FrozenPlayer state = stageManager.frozen.computeIfAbsent(player, p -> new KothStageManager.FrozenPlayer());
            state.lastPos = player.getPos();
        }
    }

    public Vec3d findSpawnFor(ServerPlayerEntity player) {
        Random random = player.getRandom();

        long packedPos = this.spawnPositions.getLong(random.nextInt(this.spawnPositions.size()));
        BlockPos min = map.spawn.min();

        int x = BlockPos.unpackLongX(packedPos);
        int z = BlockPos.unpackLongZ(packedPos);

        return new Vec3d(x + random.nextDouble(), min.getY(), z + random.nextDouble());
    }

    private static LongList collectSpawnPositions(ServerWorld world, KothMap map) {
        LongList spawnPositions = new LongArrayList(64);

        BlockPos min = map.spawn.min();
        BlockPos max = map.spawn.max();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = min.getX(); x < max.getX(); x++) {
            for (int z = min.getZ(); z < max.getZ(); z++) {
                for (int y = min.getY(); y > min.getY() - 3; y--) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir()) {
                        spawnPositions.add(pos.asLong());
                        continue;
                    }
                }
            }
        }

        if (spawnPositions.isEmpty()) {
            BlockPos centerBottom = new BlockPos(map.spawn.centerBottom());
            spawnPositions.add(centerBottom.asLong());
        }

        return spawnPositions;
    }
}
