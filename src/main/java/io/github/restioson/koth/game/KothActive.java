package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;

import java.util.HashSet;
import java.util.Set;

public class KothActive {
    private final KothConfig config;

    public final GameWorld gameWorld;
    private final KothMap gameMap;

    private final Set<ServerPlayerEntity> participants;
    private final KothSpawnLogic spawnLogic;
    private final KothIdle idle;
    private final KothTimerBar timerBar;

    private KothActive(GameWorld gameWorld, KothMap map, KothConfig config, Set<ServerPlayerEntity> participants) {
        this.gameWorld = gameWorld;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new KothSpawnLogic(gameWorld, map);
        this.participants = new HashSet<>(participants);

        this.idle = new KothIdle();
        this.timerBar = new KothTimerBar();
    }

    public static void open(GameWorld gameWorld, KothMap map, KothConfig config) {
        Set<ServerPlayerEntity> participants = new HashSet<>(gameWorld.getPlayers());
        KothActive active = new KothActive(gameWorld, map, config, participants);

        gameWorld.openGame(builder -> {
            builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
            builder.setRule(GameRule.PORTALS, RuleResult.DENY);
            builder.setRule(GameRule.PVP, RuleResult.ALLOW);
            builder.setRule(GameRule.HUNGER, RuleResult.DENY);
            builder.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
            builder.setRule(GameRule.INTERACTION, RuleResult.DENY);
            builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            builder.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);
            builder.setRule(GameRule.UNSTABLE_TNT, RuleResult.DENY);

            builder.on(GameOpenListener.EVENT, active::onOpen);
            builder.on(GameCloseListener.EVENT, active::onClose);

            builder.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            builder.on(PlayerAddListener.EVENT, active::addPlayer);

            builder.on(GameTickListener.EVENT, active::tick);

            builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
        });
    }

    private void onOpen() {
        ServerWorld world = this.gameWorld.getWorld();
        for (ServerPlayerEntity player : this.participants) {
            this.spawnParticipant(player);
        }
        this.idle.onOpen(world.getTime(), this.config);
    }

    private void onClose() {
        this.timerBar.close();
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.contains(player)) {
            this.spawnSpectator(player);
        }
        this.timerBar.addPlayer(player);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnParticipant(player);
        return ActionResult.FAIL;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick() {
        ServerWorld world = this.gameWorld.getWorld();
        long time = world.getTime();

        KothIdle.IdleTickResult result = this.idle.tick(time, gameWorld);

        switch (result) {
            case CONTINUE_TICK:
                break;
            case TICK_FINISHED:
                return;
            case GAME_FINISHED:
                this.broadcastWin(this.getWinner());
                return;
            case GAME_CLOSED:
                this.gameWorld.close();
                return;
        }

        this.timerBar.update(this.idle.finishTime - time, this.config.timeLimitSecs * 20);

        for (ServerPlayerEntity player : this.participants) {
            player.setHealth(20.0f);

            if (!this.gameMap.bounds.contains(player.getBlockPos())) {
                this.spawnParticipant(player);
            }
        }
    }

    protected static void broadcastMessage(Text message, GameWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(message, false);
        };
    }

    protected static void broadcastSound(SoundEvent sound, float pitch, GameWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.playSound(sound, SoundCategory.PLAYERS, 1.0F, pitch);
        };
    }

    protected static void broadcastSound(SoundEvent sound,  GameWorld world) {
        broadcastSound(sound, 1.0f, world);
    }

    protected static void broadcastTitle(Text message, GameWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            TitleS2CPacket packet = new TitleS2CPacket(TitleS2CPacket.Action.TITLE, message, 1, 5,  3);
            player.networkHandler.sendPacket(packet);
        }
    }

    private void broadcastWin(ServerPlayerEntity winner) {
        Text message = winner.getDisplayName().shallowCopy().append(" has won the game!").formatted(Formatting.GOLD);
        broadcastMessage(message, this.gameWorld);
        broadcastSound(SoundEvents.ENTITY_VILLAGER_YES, this.gameWorld);
    }

    private ServerPlayerEntity getWinner() {
        ServerPlayerEntity winner = null;
        for (ServerPlayerEntity player: this.participants) {
            if (winner == null || winner.getBlockPos().getY() < player.getBlockPos().getY()) {
                winner = player;
            }
        }

        return winner;
    }
}
