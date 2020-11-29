package io.github.restioson.koth.game;

import jdk.internal.jline.internal.Nullable;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class KothPlayer {
    public long deadTime = -1;
    public int score = 0;
    public int wins = 0;
    public final ServerPlayerEntity player;
    @Nullable
    public AttackRecord lastTimeWasAttacked;

    public KothPlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    public ServerPlayerEntity attacker(long time, ServerWorld world) {
        if (this.lastTimeWasAttacked != null) {
            return this.lastTimeWasAttacked.isValid(time) ? this.lastTimeWasAttacked.player.getEntity(world) : null;
        } else {
            return null;
        }
    }
}
