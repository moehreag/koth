package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import io.github.restioson.koth.game.map.KothMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.event.*;

public class KothWaiting {
    private final GameSpace gameSpace;
    private final KothMap map;
    private final KothConfig config;
    private final KothSpawnLogic spawnLogic;

    private KothWaiting(GameSpace gameSpace, KothMap map, KothConfig config) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.spawnLogic = new KothSpawnLogic(gameSpace, map);
    }

    public static GameOpenProcedure open(GameOpenContext<KothConfig> context) {
        KothConfig config = context.getConfig();
        KothMapBuilder generator = new KothMapBuilder(context.getConfig().map);
        KothMap map = generator.create();

        if (!config.winnerTakesAll && map.throne == null) {
            throw new GameOpenException(new LiteralText("throne must exist if winner doesn't take all"));
        }

        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                .setGenerator(map.asGenerator(context.getServer()))
                .setDefaultGameMode(GameMode.SPECTATOR);

        return context.createOpenProcedure(worldConfig, game -> {
            KothWaiting waiting = new KothWaiting(game.getSpace(), map, context.getConfig());

            GameWaitingLobby.applyTo(game, config.playerConfig);

            game.on(RequestStartListener.EVENT, waiting::requestStart);
            game.on(GameTickListener.EVENT, waiting::tick);
            game.on(PlayerAddListener.EVENT, waiting::addPlayer);
            game.on(PlayerDamageListener.EVENT, waiting::onPlayerDamage);
            game.on(PlayerDeathListener.EVENT, waiting::onPlayerDeath);
            worldConfig.setTimeOfDay(config.map.time);
        });
    }

    private void tick() {
        for (ServerPlayerEntity player : this.gameSpace.getWorld().getPlayers()) {
            if (!this.map.bounds.contains(player.getBlockPos())) {
                this.spawnPlayer(player);
            }
        }
    }

    private StartResult requestStart() {
        KothActive.open(this.gameSpace, this.map, this.config);
        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float value) {
        if (source.isFire()) {
            this.spawnPlayer(player);
        }

        return ActionResult.FAIL;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnPlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetAndRespawn(player, GameMode.ADVENTURE, null);
    }
}
