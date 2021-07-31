package io.github.restioson.koth.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;

public class KothMap {
    private final MapTemplate template;
    public final BlockBounds spawn;
    public final int spawnAngle;
    public final BlockBounds bounds;
    public final BlockBounds noPvp;
    public final BlockBounds throne;

    public KothMap(MapTemplate template, BlockBounds spawn, BlockBounds throne, int spawnAngle) {
        this.template = template;
        this.spawn = spawn;
        this.spawnAngle = spawnAngle;
        this.bounds = template.getBounds();
        this.throne = throne;

        BlockPos max = this.spawn.max();
        this.noPvp = BlockBounds.of(this.spawn.min(), new BlockPos(max.getX(), max.getY() + 3, max.getZ()));
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }
}
