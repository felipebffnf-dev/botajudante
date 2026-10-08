package com.botajudante;

import java.util.EnumSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class MineTunnelGoal extends Goal {
    private static final int MAX_STEPS = 40;

    private final HelperEntity bot;
    private BlockPos head;
    private int advanced;
    private int delay;
    private int endTimer;
    private boolean ending;
    private String endReason = "";

    public MineTunnelGoal(HelperEntity bot) {
        this.bot = bot;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return bot.getMode() == HelperEntity.Mode.MINE && !bot.isSitting();
    }

    @Override
    public void start() {
        head = bot.getBlockPos();
        advanced = 0;
        delay = 10;
        endTimer = 0;
        ending = false;
    }

    @Override
    public boolean shouldContinue() {
        return bot.getMode() == HelperEntity.Mode.MINE && !bot.isSitting();
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (ending) {
            endTimer--;
            if (endTimer <= 0) {
                bot.say(endReason + " Cavei " + advanced + " blocos de túnel. Voltei a te seguir.");
                bot.setMode(HelperEntity.Mode.FOLLOW);
            }
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }

        World world = bot.getWorld();
        Direction dir = bot.getMineDirection();

        double dx = bot.getX() - (head.getX() + 0.5);
        double dz = bot.getZ() - (head.getZ() + 0.5);
        if (dx * dx + dz * dz > 6.25 || Math.abs(bot.getY() - head.getY()) > 2.0) {
            bot.getNavigation().startMovingTo(head.getX() + 0.5, head.getY(), head.getZ() + 0.5, 1.0);
            delay = 10;
            return;
        }

        BlockPos next = head.offset(dir);
        BlockPos nextUp = next.up();

        if (!world.getBlockState(next.down()).isSolidBlock(world, next.down())) {
            startEnding("Tem um buraco à frente, parei.");
            return;
        }
        if (hasFluidNear(world, next) || hasFluidNear(world, nextUp)) {
            startEnding("Perigo! Tem água ou lava perto, parei.");
            return;
        }
        if (!canBreak(world, next) || !canBreak(world, nextUp)) {
            startEnding("Achei algo que não posso quebrar, parei.");
            return;
        }

        boolean broke = false;
        if (!world.getBlockState(next).isAir()) {
            world.breakBlock(next, true, bot);
            broke = true;
        }
        if (!world.getBlockState(nextUp).isAir()) {
            world.breakBlock(nextUp, true, bot);
            broke = true;
        }
        bot.swingHand(Hand.MAIN_HAND);

        head = next;
        advanced++;
        bot.getNavigation().startMovingTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
        delay = broke ? 12 : 3;

        if (advanced >= MAX_STEPS) {
            startEnding("Cheguei ao limite do túnel.");
        }
    }

    private void startEnding(String reason) {
        ending = true;
        endTimer = 40;
        endReason = reason;
    }

    private boolean hasFluidNear(World world, BlockPos pos) {
        if (!world.getFluidState(pos).isEmpty()) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (!world.getFluidState(pos.offset(d)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean canBreak(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.getHardness(world, pos) < 0) {
            return false;
        }
        return !state.hasBlockEntity();
    }
}
