package ai.bfsx.heroes.grave;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Finds a safe spot for a grave: standing room that is not liquid, on solid ground.
 * Rule: nearest safe block, always. Nothing is ever lost.
 */
public final class SafeLocation {

    private SafeLocation() {}

    public static Location find(Location death, int radius) {
        World world = death.getWorld();
        if (world == null) return death;
        Location base = death.clone();

        // Void: go up to the highest surface at x/z
        if (base.getY() < world.getMinHeight()) {
            base.setY(world.getHighestBlockYAt(base) + 1);
        }
        if (base.getY() >= world.getMaxHeight()) {
            base.setY(world.getMaxHeight() - 2);
        }

        Block b = base.getBlock();
        if (isSafe(b)) return center(b.getLocation());

        // Straight column first (cheap and usually right: falling / drowning / burning)
        for (int dy = 1; dy <= radius; dy++) {
            Block up = b.getRelative(0, dy, 0);
            if (isSafe(up)) return center(up.getLocation());
            Block down = b.getRelative(0, -dy, 0);
            if (isSafe(down)) return center(down.getLocation());
        }

        // Expanding cube search around the death spot
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -r; dy <= r; dy++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                        Block c = b.getRelative(dx, dy, dz);
                        if (isSafe(c)) return center(c.getLocation());
                    }
                }
            }
        }

        // Fallback: surface at x/z (creates a spot on top of whatever is there)
        Location top = base.clone();
        top.setY(world.getHighestBlockYAt(base) + 1);
        return center(top.getBlock().getLocation());
    }

    /** Safe = this block and the one above are passable and not liquid, and the block below is solid. */
    static boolean isSafe(Block b) {
        World w = b.getWorld();
        if (b.getY() <= w.getMinHeight() || b.getY() >= w.getMaxHeight() - 1) return false;
        Block below = b.getRelative(0, -1, 0);
        return passable(b) && passable(b.getRelative(0, 1, 0)) && GraveManager.isSolidGround(below);
    }

    private static boolean passable(Block b) {
        return (b.isPassable() || b.getType().isAir()) && !b.isLiquid();
    }

    private static Location center(Location blockLoc) {
        return new Location(blockLoc.getWorld(), blockLoc.getBlockX() + 0.5, blockLoc.getBlockY(), blockLoc.getBlockZ() + 0.5);
    }
}
