package io.github.restioson.koth.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.plasmid.game.map.template.MapTemplate;
import xyz.nucleoid.plasmid.game.map.template.TemplateChunkGenerator;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class KothMap {
    private final MapTemplate template;
    public final BlockBounds spawn;
    public final int spawnAngle;
    public final BlockBounds bounds;
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);

    public KothMap(MapTemplate template, BlockBounds spawn, int spawnAngle) {
        this.template = template;
        this.spawn = spawn.offset(ORIGIN);
        this.spawnAngle = spawnAngle;
        this.bounds = template.getBounds().offset(ORIGIN);
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template, ORIGIN);
    }
}
