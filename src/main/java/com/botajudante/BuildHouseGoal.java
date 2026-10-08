package com.botajudante;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class BuildHouseGoal extends Goal {
    private final HelperEntity bot;
    private final List<BlockPos> plan = new ArrayList<>();
    private int index;
    private int delay;
    private int retries;
    private boolean finished;

    public BuildHouseGoal(HelperEntity bot) {
        this.bot = bot;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return bot.getMode() == HelperEntity.Mode.BUILD
                && !bot.isSitting()
                && bot.getBuildCenter() != null;
    }

    @Override
    public void start() {
        plan.clear();
        index = 0;
        delay = 0;
        retries = 0;
        finished = false;

        World world = bot.getWorld();
        BlockPos center = bot.getBuildCenter();
        Direction facing = bot.getBuildFacing();

        bot.craftPlanks();

        int doorX = -facing.getOffsetX() * 2;
        int doorZ = -facing.getOffsetZ() * 2;

        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (!edge) {
                        continue;
                    }
                    if (dy < 2 && dx == doorX && dz == doorZ) {
                        continue;
                    }
                    addIfFree(world, center.add(dx, dy, dz));
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                addIfFree(world, center.add(dx, 3, dz));
            }
        }

        if (plan.isEmpty()) {
            bot.say("O lugar já está ocupado ou a casa já está pronta.");
            abort();
            return;
        }

        int have = bot.countPlanks();
        if (have < plan.size()) {
            int missingLogs = (plan.size() - have + 3) / 4;
            bot.say("Preciso de " + plan.size() + " tábuas (cada tronco vira 4) e tenho " + have
                    + ". Faltam uns " + missingLogs + " troncos. Diga 'bot madeira' primeiro.");
            abort();
            return;
        }
        bot.say("Começando a construir! Vou usar " + plan.size() + " tábuas.");
    }

    @Override
    public boolean shouldContinue() {
        return !finished && bot.getMode() == HelperEntity.Mode.BUILD && !bot.isSitting();
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (finished) {
            return;
        }
        World world = bot.getWorld();
        BlockPos center = bot.getBuildCenter();

        if (bot.squaredDistanceTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) > 36.0) {
            if (bot.getNavigation().isIdle()) {
                bot.getNavigation().startMovingTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.2);
            }
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        if (index >= plan.size()) {
            bot.say("Casa pronta! Só faltou a porta e a decoração.");
            abort();
            return;
        }

        BlockPos pos = plan.get(index);
        index++;

        BlockState current = world.getBlockState(pos);
        if (!(current.isAir() || current.isReplaceable())) {
            return;
        }

        ItemStack plank = bot.takePlank();
        if (plank.isEmpty()) {
            bot.say("Acabaram as tábuas!");
            abort();
            return;
        }

        BlockState state = Block.getBlockFromItem(plank.getItem()).getDefaultState();
        if (!world.canPlace(state, pos, ShapeContext.absent())) {
            bot.getBotInventory().addStack(plank);
            if (retries < 200) {
                plan.add(pos);
                retries++;
            }
            delay = 5;
            return;
        }

        world.setBlockState(pos, state);
        world.playSound(null, pos, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
        bot.swingHand(Hand.MAIN_HAND);
        bot.getLookControl().lookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        delay = 2;
    }

    private void addIfFree(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.isReplaceable()) {
            plan.add(pos);
        }
    }

    private void abort() {
        finished = true;
        bot.setMode(HelperEntity.Mode.FOLLOW);
    }
}
