package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import io.github.restioson.koth.game.map.KothMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.world.bubble.BubbleWorldConfig;

import java.util.concurrent.CompletableFuture;

public class KothWaiting {
    private final GameWorld gameWorld;
    private final KothMap map;
    private final KothConfig config;
    private final KothSpawnLogic spawnLogic;

    private KothWaiting(GameWorld gameWorld, KothMap map, KothConfig config) {
        this.gameWorld = gameWorld;
        this.map = map;
        this.config = config;
        this.spawnLogic = new KothSpawnLogic(gameWorld, map);
    }

    public static CompletableFuture<GameWorld> open(GameOpenContext<KothConfig> context) {
        KothConfig config = context.getConfig();
        KothMapBuilder generator = new KothMapBuilder(context.getConfig().map);

        return generator.create().thenCompose(map -> {
            if (!config.winnerTakesAll && map.throne == null) {
                throw new GameOpenException(new LiteralText("throne must exist if winner doesn't take all"));
            }

            BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                    .setGenerator(map.asGenerator(context.getServer()))
                    .setDefaultGameMode(GameMode.SPECTATOR);

            return context.openWorld(worldConfig).thenApply(gameWorld -> {
                KothWaiting waiting = new KothWaiting(gameWorld, map, context.getConfig());

                GameWaitingLobby.open(gameWorld, context.getConfig().playerConfig, builder -> {
                    builder.on(RequestStartListener.EVENT, waiting::requestStart);
                    builder.on(GameTickListener.EVENT, waiting::tick);
                    builder.on(PlayerAddListener.EVENT, waiting::addPlayer);
                    builder.on(PlayerDamageListener.EVENT, waiting::onPlayerDamage);
                    builder.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
                });

                return gameWorld;
            });
        });
    }

    private void tick() {
        for (ServerPlayerEntity player : this.gameWorld.getWorld().getPlayers()) {
            if (!this.map.bounds.contains(player.getBlockPos())) {
                this.spawnPlayer(player);
            }
        }
    }

    private StartResult requestStart() {
        KothActive.open(this.gameWorld, this.map, this.config);
        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float value) {
        this.spawnPlayer(player);
        return true;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnPlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}
