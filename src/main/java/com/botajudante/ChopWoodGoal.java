package com.botajudante;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class ChopWoodGoal extends Goal {
    private final HelperEntity bot;
    private final Set<BlockPos> skipped = new HashSet<>();
    private BlockPos base;
    private int timeout;
    private int searchDelay;
    private int collectTicks;
    private boolean felled;

    public ChopWoodGoal(HelperEntity bot) {
        this.bot = bot;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (bot.getMode() != HelperEntity.Mode.WOOD || bot.isSitting()) {
            return false;
        }
        if (searchDelay > 0) {
            searchDelay--;
            return false;
        }
        searchDelay = 10;
        base = findTree();
        if (base == null) {
            skipped.clear();
            bot.say("Não achei árvores por perto. Voltei a te seguir.");
            bot.setMode(HelperEntity.Mode.FOLLOW);
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        timeout = 0;
        collectTicks = 0;
        felled = false;
        goToBase();
    }

    @Override
    public boolean shouldContinue() {
        if (bot.getMode() != HelperEntity.Mode.WOOD || bot.isSitting() || base == null) {
            return false;
        }
        if (felled) {
            return collectTicks < 50;
        }
        return timeout < 400;
    }

    @Override
    public void stop() {
        if (!felled && base != null) {
            skipped.add(base);
        }
        bot.getNavigation().stop();
        base = null;
    }

    @Override
    public void tick() {
        if (felled) {
            collectTicks++;
            return;
        }
        timeout++;
        World world = bot.getWorld();
        if (!world.getBlockState(base).isIn(BlockTags.LOGS_THAT_BURN)) {
            felled = true;
            collectTicks = 50;
            return;
        }
        bot.getLookControl().lookAt(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5);
        double distSq = bot.squaredDistanceTo(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5);
        if (distSq <= 9.0) {
            fellTree(world);
            felled = true;
            return;
        }
        if (timeout % 10 == 0 && bot.getNavigation().isIdle()) {
            goToBase();
        }
    }

    private BlockPos findTree() {
        World world = bot.getWorld();
        BlockPos origin = bot.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(origin.add(-16, -4, -16), origin.add(16, 8, 16))) {
            if (!world.getBlockState(p).isIn(BlockTags.LOGS_THAT_BURN)) {
                continue;
            }
            if (world.getBlockState(p.down()).isIn(BlockTags.LOGS_THAT_BURN)) {
                continue;
            }
            if (skipped.contains(p)) {
                continue;
            }
            double d = p.getSquaredDistance(origin);
            if (d < bestDist) {
                bestDist = d;
                best = p.toImmutable();
            }
        }
        return best;
    }

    private void goToBase() {
        World world = bot.getWorld();
        BlockPos stand = base;
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos adj = base.offset(d);
            if (world.getBlockState(adj).isAir() && world.getBlockState(adj.down()).isSolidBlock(world, adj.down())) {
                stand = adj;
                break;
            }
        }
        bot.getNavigation().startMovingTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.1);
    }

    private void fellTree(World world) {
        Set<BlockPos> logs = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        logs.add(base);
        queue.add(base);
        while (!queue.isEmpty() && logs.size() < 120) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = cur.add(dx, dy, dz);
                        if (logs.contains(n)) {
                            continue;
                        }
                        BlockState state = world.getBlockState(n);
                        if (state.isIn(BlockTags.LOGS_THAT_BURN)) {
                            logs.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        for (BlockPos p : logs) {
            world.breakBlock(p, true, bot);
        }
        bot.swingHand(Hand.MAIN_HAND);
    }
}
