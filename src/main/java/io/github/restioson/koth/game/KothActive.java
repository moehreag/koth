package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;

import java.util.*;
import java.util.stream.Collectors;

public class KothActive {
    private final KothConfig config;

    public final GameWorld gameWorld;
    private final KothMap gameMap;

    private final Object2ObjectMap<ServerPlayerEntity, KothPlayer> participants;
    private final KothSpawnLogic spawnLogic;
    private final KothIdle idle;
    private final KothTimerBar timerBar;
    private KothScoreboard scoreboard = null;

    private KothActive(GameWorld gameWorld, KothMap map, KothConfig config, Set<ServerPlayerEntity> participants) {
        this.gameWorld = gameWorld;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new KothSpawnLogic(gameWorld, map);
        this.participants = new Object2ObjectOpenHashMap<>();

        for (ServerPlayerEntity player : participants) {
            this.participants.put(player, new KothPlayer(player));
        }

        if (!config.winnerTakesAll) {
            this.scoreboard = new KothScoreboard(gameWorld.getWorld().getScoreboard());
        }

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
            builder.on(PlayerRemoveListener.EVENT, active::removePlayer);

            builder.on(GameTickListener.EVENT, active::tick);

            builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
            builder.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
        });
    }

    private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float value) {
        return this.gameMap.noPvp.contains(player.getBlockPos());
    }

    private void onOpen() {
        ServerWorld world = this.gameWorld.getWorld();
        for (ServerPlayerEntity player : this.participants.keySet()) {
            this.spawnParticipant(player);
        }
        this.idle.onOpen(world.getTime(), this.config);
    }

    private void onClose() {
        this.timerBar.close();
        if (this.scoreboard != null) {
             this.scoreboard.close();
        }
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(player)) {
            this.spawnSpectator(player);
        }
        this.timerBar.addPlayer(player);
    }

    private void removePlayer(ServerPlayerEntity player) {
        this.participants.remove(player);
        this.timerBar.removePlayer(player);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnParticipant(player);
        return ActionResult.FAIL;
    }

    private void spawnDeadParticipant(ServerPlayerEntity player, long time) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
        this.participants.get(player).deadTime = time;
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

        for (ServerPlayerEntity player : this.participants.keySet()) {
            player.setHealth(20.0f);

            if (!this.gameMap.bounds.contains(player.getBlockPos())) {
                if (player.isSpectator()) {
                    this.spawnLogic.spawnPlayer(player);
                } else {
                    this.spawnDeadParticipant(player, time);
                }
            }

            KothPlayer state = this.participants.get(player);
            assert state != null;

            if (player.isSpectator()) {
                this.tickDead(player, state, time);
                return;
            }

            // If winnerTakesAll is true then throne must not be null
            if (!this.config.winnerTakesAll
                    && this.gameMap.throne.toBox().intersects(player.getBoundingBox())
                    && time % 20 == 0
            ) {
                state.score += 1;
                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.addExperienceLevels(1);
                this.scoreboard.render(this.buildLeaderboard());
            }
        }
    }

    private void tickDead(ServerPlayerEntity player, KothPlayer state, long time) {
        int sec = 5 - (int) Math.floor((time - state.deadTime) / 20.0f);

        if (sec > 0 && (time - state.deadTime) % 20 == 0) {
            Text text = new LiteralText(String.format("Respawning in %ds", sec)).formatted(Formatting.BOLD);
            player.sendMessage(text, true);
        }

        if (time - state.deadTime > 5 * 20) {
            this.spawnParticipant(player);
        }
    }

    private List<KothPlayer> buildLeaderboard() {
        return this.participants.values().stream()
                .filter(player -> player.score != 0)
                .sorted(Comparator.comparingInt(player -> -player.score)) // Descending sort
                .limit(5)
                .collect(Collectors.toList());
    }

    protected static void broadcastMessage(Text message, GameWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(message, false);
        }
    }

    protected static void broadcastSound(SoundEvent sound, float pitch, GameWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.playSound(sound, SoundCategory.PLAYERS, 1.0F, pitch);
        }
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
        Text message;
        if (winner != null) {
             message = winner.getDisplayName().shallowCopy().append(" has won the game!").formatted(Formatting.GOLD);
        } else {
            message = new LiteralText("The game ended, but nobody won!").formatted(Formatting.GOLD);;
        }

        broadcastMessage(message, this.gameWorld);
        broadcastSound(SoundEvents.ENTITY_VILLAGER_YES, this.gameWorld);
    }

    private ServerPlayerEntity getWinner() {
        Map.Entry<ServerPlayerEntity, KothPlayer> winner = null;

        for (Map.Entry<ServerPlayerEntity, KothPlayer> entry : this.participants.entrySet()) {
            if (this.config.winnerTakesAll) {
                if (entry.getKey().isSpectator()) {
                    continue;
                }

                if (winner == null || winner.getKey().getBlockPos().getY() < entry.getKey().getBlockPos().getY() ) {
                    winner = entry;
                }
            } else {
                if (winner == null || winner.getValue().score < entry.getValue().score) {
                    winner = entry;
                }
            }
        }

        return winner != null ? winner.getKey() : null;
    }
}
