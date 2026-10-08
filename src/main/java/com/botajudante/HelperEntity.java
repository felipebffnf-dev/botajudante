package com.botajudante;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;

public class HelperEntity extends TameableEntity {

    public enum Mode { FOLLOW, WOOD, MINE, BUILD }

    private Mode mode = Mode.FOLLOW;
    private final SimpleInventory botInventory = new SimpleInventory(27);
    private Direction mineDirection = Direction.NORTH;
    private BlockPos buildCenter = null;
    private Direction buildFacing = Direction.NORTH;
    private int warnCooldown = 0;
    private long lastNightWarnDay = -1L;

    public HelperEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createHelperAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new SitGoal(this));
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(4, new ChopWoodGoal(this));
        this.goalSelector.add(4, new MineTunnelGoal(this));
        this.goalSelector.add(4, new BuildHouseGoal(this));
        this.goalSelector.add(5, new FollowOwnerGoal(this, 1.1, 6.0f, 2.0f, false));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
        this.targetSelector.add(2, new AttackWithOwnerGoal(this));
        this.targetSelector.add(3, new RevengeGoal(this));
        this.targetSelector.add(4, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false,
                target -> this.isTamed() && !(target instanceof CreeperEntity)));
    }

    // ---------- modo de trabalho ----------

    public Mode getMode() {
        return this.mode;
    }

    public void setMode(Mode newMode) {
        this.mode = newMode;
        this.navigation.stop();
    }

    public Direction getMineDirection() {
        return this.mineDirection;
    }

    public void setMineDirection(Direction direction) {
        this.mineDirection = direction;
    }

    public BlockPos getBuildCenter() {
        return this.buildCenter;
    }

    public Direction getBuildFacing() {
        return this.buildFacing;
    }

    public void setBuildSite(BlockPos center, Direction facing) {
        this.buildCenter = center;
        this.buildFacing = facing;
    }

    public void sitDown() {
        this.setSitting(true);
        this.setInSittingPose(true);
        this.navigation.stop();
        this.setTarget(null);
    }

    public void wakeUp() {
        this.setSitting(false);
        this.setInSittingPose(false);
    }

    // ---------- conversa ----------

    public void say(String text) {
        if (this.getOwner() instanceof PlayerEntity player) {
            player.sendMessage(Text.literal("<Bot> " + text), false);
        }
    }

    // ---------- inventário ----------

    public SimpleInventory getBotInventory() {
        return this.botInventory;
    }

    public int deliverTo(PlayerEntity player) {
        int total = 0;
        for (int i = 0; i < this.botInventory.size(); i++) {
            ItemStack stack = this.botInventory.getStack(i);
            if (!stack.isEmpty()) {
                total += stack.getCount();
                ItemStack copy = stack.copy();
                player.getInventory().insertStack(copy);
                if (!copy.isEmpty()) {
                    player.dropItem(copy, false);
                }
                this.botInventory.setStack(i, ItemStack.EMPTY);
            }
        }
        return total;
    }

    public String describeInventory() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < this.botInventory.size(); i++) {
            ItemStack stack = this.botInventory.getStack(i);
            if (!stack.isEmpty()) {
                counts.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "Não estou carregando nada.";
        }
        StringBuilder sb = new StringBuilder("Estou carregando:");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            sb.append(" ").append(entry.getValue()).append("x ").append(entry.getKey()).append(";");
        }
        return sb.toString();
    }

    public void craftPlanks() {
        int logs = 0;
        for (int i = 0; i < this.botInventory.size(); i++) {
            ItemStack stack = this.botInventory.getStack(i);
            if (stack.isIn(ItemTags.LOGS_THAT_BURN)) {
                logs += stack.getCount();
                this.botInventory.setStack(i, ItemStack.EMPTY);
            }
        }
        int remaining = logs * 4;
        while (remaining > 0) {
            int amount = Math.min(64, remaining);
            ItemStack rest = this.botInventory.addStack(new ItemStack(Items.OAK_PLANKS, amount));
            if (!rest.isEmpty()) {
                this.dropStack(rest);
            }
            remaining -= amount;
        }
    }

    public int countPlanks() {
        int count = 0;
        for (int i = 0; i < this.botInventory.size(); i++) {
            ItemStack stack = this.botInventory.getStack(i);
            if (stack.isIn(ItemTags.PLANKS)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public ItemStack takePlank() {
        for (int i = 0; i < this.botInventory.size(); i++) {
            ItemStack stack = this.botInventory.getStack(i);
            if (stack.isIn(ItemTags.PLANKS)) {
                ItemStack one = new ItemStack(stack.getItem(), 1);
                stack.decrement(1);
                if (stack.isEmpty()) {
                    this.botInventory.setStack(i, ItemStack.EMPTY);
                }
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------- ciclo do bot ----------

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.getWorld().isClient) {
            return;
        }
        if (this.mode == Mode.WOOD || this.mode == Mode.MINE) {
            this.pickUpNearbyItems();
        }
        if (this.age % 20 == 0) {
            this.tipsForOwner();
        }
    }

    private void pickUpNearbyItems() {
        double range = (this.mode == Mode.WOOD) ? 6.0 : 3.0;
        List<ItemEntity> items = this.getWorld().getEntitiesByClass(
                ItemEntity.class,
                this.getBoundingBox().expand(range, 2.0, range),
                ItemEntity::isAlive);
        for (ItemEntity item : items) {
            ItemStack rest = this.botInventory.addStack(item.getStack().copy());
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setStack(rest);
            }
        }
    }

    private void tipsForOwner() {
        if (!(this.getOwner() instanceof PlayerEntity owner)) {
            return;
        }
        if (this.warnCooldown > 0) {
            this.warnCooldown -= 20;
            return;
        }
        if (owner.getHealth() <= 6.0f) {
            this.say("Cuidado! Sua vida está baixa.");
            this.warnCooldown = 600;
            return;
        }
        if (owner.getHungerManager().getFoodLevel() <= 6) {
            this.say("Você está com fome. Hora de comer!");
            this.warnCooldown = 1200;
            return;
        }
        long timeOfDay = this.getWorld().getTimeOfDay();
        long day = timeOfDay / 24000L;
        long time = timeOfDay % 24000L;
        if (time >= 13000L && time < 14000L && day != this.lastNightWarnDay) {
            this.lastNightWarnDay = day;
            this.say("Está anoitecendo! Os monstros vão aparecer.");
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!this.getWorld().isClient) {
            ItemScatterer.spawn(this.getWorld(), this.getBlockPos(), this.botInventory);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("BotInventory", this.botInventory.toNbtList());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.botInventory.readNbtList(nbt.getList("BotInventory", 10));
    }

    // ---------- interação ----------

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        boolean client = this.getWorld().isClient;

        if (!this.isTamed()) {
            if (!client) {
                this.setOwner(player);
                this.navigation.stop();
                this.setTarget(null);
                this.getWorld().sendEntityStatus(this, (byte) 7);
                player.sendMessage(Text.literal("<Bot> Agora eu sou seu ajudante! Escreva 'bot ajuda' no chat."), false);
            }
            return ActionResult.success(client);
        }

        if (this.isOwner(player)) {
            if (!client) {
                boolean sit = !this.isSitting();
                if (sit) {
                    this.setMode(Mode.FOLLOW);
                    this.sitDown();
                } else {
                    this.wakeUp();
                }
                player.sendMessage(Text.literal(sit ? "<Bot> Fico aqui." : "<Bot> Vamos!"), true);
            }
            return ActionResult.success(client);
        }

        return ActionResult.PASS;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    @Override
    public EntityView method_48926() {
        return this.getWorld();
    }
}
