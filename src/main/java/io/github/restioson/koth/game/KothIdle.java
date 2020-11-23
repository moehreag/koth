package io.github.restioson.koth.game;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KothIdle {
    private final KothConfig config;
    private long closeTime = -1;
    public long finishTime = -1;
    private long startTime = -1;
    private final Object2ObjectMap<ServerPlayerEntity, FrozenPlayer> frozen;
    private SpectatorSetState spectatorSetState = SpectatorSetState.BEFORE_WIN_CALCULATION;

    public KothIdle(KothConfig config) {
        this.config = config;
        this.frozen = new Object2ObjectOpenHashMap<>();
    }

    public void onOpen(long time, KothConfig config, GameWorld world) {
        this.startTime = time - (time % 20) + (4 * 20) + 19;
        this.finishTime = this.startTime + (config.timeLimitSecs * 20);
        String line2;

        if (config.deathmatch) {
            line2 = "Deathmatch! Stay on the platform";
        } else if (!config.winnerTakesAll) {
            line2 = "Score points by staying on top of the hill. Whoever reigns longest wins!";
        } else {
            line2 = "Whoever is highest when the game ends wins!";
        }

        List<String> lines = new ArrayList<>();
        Collections.addAll(lines, "King of the Hill - get to the top of the hill and knock off others to win!", line2);

        if (this.config.hasFeather) {
            lines.add("Right-click with your feather to leap forwards.");
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            for (String line : lines) {
                Text text = new LiteralText(line).formatted(Formatting.GOLD);
                player.sendMessage(text, false);
            }

            player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    public IdleTickResult tick(long time, GameWorld world, boolean overtime) {
        // Game has finished. Wait a few seconds before finally closing the game.
        if (this.closeTime > 0) {
            if (time >= this.closeTime) {
                return IdleTickResult.GAME_CLOSED;
            }
            return IdleTickResult.TICK_FINISHED;
        }

        // Game hasn't started yet. Display a countdown before it begins.
        if (this.startTime > time) {
            this.tickStartWaiting(time, world);
            return IdleTickResult.TICK_FINISHED;
        }

        boolean noPlayers = world.getPlayerCount() == 0;
        if (this.config.deathmatch) {
            int remainingPlayers = 0;
            for (ServerPlayerEntity player : world.getPlayerSet()) {
                if (!player.isSpectator()) {
                    remainingPlayers++;
                }
            }

            if (remainingPlayers <= 1) {
                noPlayers = true;
            }
        }

        // Game has just finished. Transition to the waiting-before-close state.
        if (time > this.finishTime || noPlayers) {
            if (!overtime) {
                if (this.spectatorSetState == SpectatorSetState.BEFORE_WIN_CALCULATION) {
                    this.spectatorSetState = SpectatorSetState.NOT_YET_SET; // Give time to calculate win result
                } else if (this.spectatorSetState == SpectatorSetState.NOT_YET_SET) {
                    this.spectatorSetState = SpectatorSetState.SET;
                    for (ServerPlayerEntity player : world.getPlayers()) {
                        player.setGameMode(GameMode.SPECTATOR);
                    }
                }

                this.closeTime = time + (5 * 20);

                return IdleTickResult.GAME_FINISHED;
            } else {
                return IdleTickResult.OVERTIME;
            }
        }

        return IdleTickResult.CONTINUE_TICK;
    }

    private void tickStartWaiting(long time, GameWorld world) {
        float sec_f = (this.startTime - time) / 20.0f;

        if (sec_f > 1) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }

                FrozenPlayer state = this.frozen.getOrDefault(player, new FrozenPlayer());

                if (state.lastPos == null) {
                    state.lastPos = player.getPos();
                }

                player.teleport(state.lastPos.x, state.lastPos.y, state.lastPos.z);
            }
        }

        int sec = (int) Math.floor(sec_f) - 1;

        if ((this.startTime - time) % 20 == 0) {
            if (sec > 0) {
                KothActive.broadcastTitle(new LiteralText(Integer.toString(sec)).formatted(Formatting.BOLD), world);
                world.getPlayerSet().sendSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            } else {
                KothActive.broadcastTitle(new LiteralText("Go!").formatted(Formatting.BOLD), world);
                world.getPlayerSet().sendSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP, SoundCategory.PLAYERS, 1.0F, 2.0F);
            }
        }
    }

    public static class FrozenPlayer {
        public Vec3d lastPos;
    }

    public enum IdleTickResult {
        CONTINUE_TICK,
        TICK_FINISHED,
        GAME_FINISHED,
        GAME_CLOSED,
        OVERTIME,
    }

    private enum SpectatorSetState {
        BEFORE_WIN_CALCULATION,
        NOT_YET_SET,
        SET,
    }
}
