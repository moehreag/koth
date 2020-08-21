package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import io.github.restioson.koth.game.map.KothMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
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

                gameWorld.openGame(builder -> {
                    builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
                    builder.setRule(GameRule.PORTALS, RuleResult.DENY);
                    builder.setRule(GameRule.PVP, RuleResult.DENY);
                    builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
                    builder.setRule(GameRule.HUNGER, RuleResult.DENY);
                    builder.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);

                    builder.on(RequestStartListener.EVENT, waiting::requestStart);
                    builder.on(OfferPlayerListener.EVENT, waiting::offerPlayer);

                    builder.on(GameTickListener.EVENT, waiting::tick);

                    builder.on(PlayerAddListener.EVENT, waiting::addPlayer);
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

    private JoinResult offerPlayer(ServerPlayerEntity player) {
        if (this.gameWorld.getPlayerCount() >= this.config.playerConfig.getMaxPlayers()) {
            return JoinResult.gameFull();
        }

        return JoinResult.ok();
    }

    private StartResult requestStart() {
        PlayerConfig playerConfig = this.config.playerConfig;
        if (this.gameWorld.getPlayerCount() < playerConfig.getMinPlayers()) {
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        KothActive.open(this.gameWorld, this.map, this.config);

        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        player.setHealth(20.0f);
        this.spawnPlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}
