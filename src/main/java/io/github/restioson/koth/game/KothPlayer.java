package io.github.restioson.koth.game;

import net.minecraft.server.network.ServerPlayerEntity;

public class KothPlayer {
    public long deadTime = -1;
    public int score = 0;
    public int wins = 0;
    public final ServerPlayerEntity player;

    public KothPlayer(ServerPlayerEntity player) {
        this.player = player;
    }
}
