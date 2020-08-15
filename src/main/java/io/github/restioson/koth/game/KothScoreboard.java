package io.github.restioson.koth.game;

import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KothScoreboard {
    private final ServerScoreboard scoreboard;
    private final ScoreboardObjective hoops;

    public KothScoreboard(ServerScoreboard scoreboard) {
        ScoreboardObjective scoreboardObjective = new ScoreboardObjective(
                scoreboard,
                "king_of_the_hill",
                ScoreboardCriterion.DUMMY,
                new LiteralText("Longest-reigning king").formatted(Formatting.BLUE, Formatting.BOLD),
                ScoreboardCriterion.RenderType.INTEGER
        );
        scoreboard.addScoreboardObjective(scoreboardObjective);
        scoreboard.setObjectiveSlot(1, scoreboardObjective);
        this.hoops = scoreboardObjective;
        this.scoreboard = scoreboard;
    }

    public void render(List<KothPlayer> leaderboard) {
        List<String> lines = new ArrayList<>();

        for (KothPlayer entry : leaderboard) {
            String line = String.format(
                    "%s%s%s: %ds",
                    Formatting.AQUA,
                    entry.player.getEntityName(),
                    Formatting.RESET,
                    entry.score
            );
            lines.add(line);
        }

        this.renderObjective(this.hoops, lines);
    }

    private void renderObjective(ScoreboardObjective objective, List<String> lines) {
        this.clear(objective);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            this.scoreboard.getPlayerScore(line, objective).setScore(lines.size() - i);
        }
    }

    private void clear(ScoreboardObjective objective) {
        Collection<ScoreboardPlayerScore> existing = this.scoreboard.getAllPlayerScores(objective);
        for (ScoreboardPlayerScore score : existing) {
            this.scoreboard.resetPlayerScore(score.getPlayerName(), objective);
        }
    }

    public void close() {
        scoreboard.removeObjective(this.hoops);
    }
}
