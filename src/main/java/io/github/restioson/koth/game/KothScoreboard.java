package io.github.restioson.koth.game;

import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;
import xyz.nucleoid.plasmid.widget.SidebarWidget;

import java.util.List;

public class KothScoreboard implements AutoCloseable {
    private final SidebarWidget sidebar;
    private final boolean winnerTakesAll;
    private final boolean deathMatch;

    public KothScoreboard(GlobalWidgets widgets, String name, boolean wta, boolean dm) {
        this.sidebar = widgets.addSidebar(
                new LiteralText(name).formatted(Formatting.BLUE, Formatting.BOLD)
        );
        this.winnerTakesAll = wta;
        this.deathMatch = dm;
    }

    public void renderTitle() {
        this.sidebar.set(content -> {
        });
    }

    public void render(List<KothPlayer> leaderboard) {
        this.sidebar.set(content -> {
            for (KothPlayer entry : leaderboard) {
                String line;

                if (this.winnerTakesAll) {
                    line = String.format("Ruler: %s%s%s", Formatting.AQUA, entry.player.getEntityName(), Formatting.RESET);
                } else if (this.deathMatch) {
                    line = String.format(
                            "%s%s%s: %d rounds",
                            Formatting.AQUA,
                            entry.player.getEntityName(),
                            Formatting.RESET,
                            entry.wins
                    );
                } else {
                    line = String.format(
                            "%s%s%s: %ds",
                            Formatting.AQUA,
                            entry.player.getEntityName(),
                            Formatting.RESET,
                            entry.score
                    );
                }

                content.writeLine(line);
            }
        });
    }


    public void close() {
        this.sidebar.close();
    }
}
