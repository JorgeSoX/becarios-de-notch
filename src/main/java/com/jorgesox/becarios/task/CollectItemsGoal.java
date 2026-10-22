package com.jorgesox.becarios.task;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.jorgesox.becarios.entity.BecarioEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Recoger: va a por los objetos sueltos cercanos y los guarda en su inventario. */
public class CollectItemsGoal extends Goal {
	private static final double RADIUS = 16.0;
	private static final double PICKUP_DISTANCE_SQ = 2.5;
	private static final int MAX_STUCK_TICKS = 200;

	private final BecarioEntity becario;
	/** Ids de objetos a los que no pudo llegar. */
	private final Set<Integer> ignored = new HashSet<>();
	private ItemEntity target;
	private int stuckTicks;

	public CollectItemsGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		BecarioTask task = becario.getTask();
		return task != null && task.kind == BecarioTask.Kind.COLLECT && !becario.isOrderedToSit();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		target = null;
		stuckTicks = 0;
		ignored.clear();
	}

	@Override
	public void stop() {
		target = null;
		becario.getNavigation().stop();
	}

	@Override
	public void tick() {
		BecarioTask task = becario.getTask();
		if (task == null) {
			return;
		}
		ServerLevel level = (ServerLevel) becario.level();

		if (target == null || target.isRemoved() || target.getItem().isEmpty()) {
			target = findNearest(level);
			stuckTicks = 0;
			if (target == null) {
				becario.finishTask("he recogido " + task.done + " objetos.");
				return;
			}
		}

		becario.getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());

		if (becario.distanceToSqr(target) < PICKUP_DISTANCE_SQ) {
			ItemStack stack = target.getItem();
			ItemStack leftover = becario.storeItem(stack.copy());
			task.done += stack.getCount() - leftover.getCount();
			if (leftover.isEmpty()) {
				target.discard();
				target = null;
			} else {
				target.setItem(leftover);
				becario.failTask("tengo el inventario lleno. He recogido " + task.done + " objetos.");
			}
			return;
		}

		if (stuckTicks % 10 == 0) {
			becario.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.1);
		}
		if (++stuckTicks > MAX_STUCK_TICKS) {
			ignored.add(target.getId());
			target = null;
		}
	}

	private ItemEntity findNearest(ServerLevel level) {
		ItemEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(
			ItemEntity.class,
			becario.getBoundingBox().inflate(RADIUS),
			e -> !e.getItem().isEmpty() && !ignored.contains(e.getId()))) {
			double distance = becario.distanceToSqr(item);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = item;
			}
		}
		return best;
	}
}
