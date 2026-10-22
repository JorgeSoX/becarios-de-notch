package com.jorgesox.becarios.entity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Si su dueño le tira algo (tecla Q) cerca, va andando a por ello.
 * La recogida en sí la hace BecarioEntity al pasar por encima.
 */
public class FetchThrownItemsGoal extends Goal {
	private static final double RANGE = 10.0;

	private final BecarioEntity becario;
	private ItemEntity item;
	private int timeout;

	public FetchThrownItemsGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (becario.isOrderedToSit() || becario.getTask() != null || becario.getTarget() != null
			|| becario.getCombatMode() == CombatMode.GUARDIA || !becario.hasInventorySpace()) {
			return false;
		}
		LivingEntity owner = becario.getOwner();
		if (owner == null) {
			return false;
		}
		List<ItemEntity> items = becario.level().getEntitiesOfClass(ItemEntity.class,
			becario.getBoundingBox().inflate(RANGE, 4.0, RANGE),
			e -> e.isAlive() && e.getOwner() == owner);
		item = items.stream().min(Comparator.comparingDouble(becario::distanceToSqr)).orElse(null);
		return item != null;
	}

	@Override
	public boolean canContinueToUse() {
		return item != null && item.isAlive() && timeout > 0 && becario.getTarget() == null;
	}

	@Override
	public void start() {
		timeout = 200;
		becario.getNavigation().moveTo(item, 1.1);
	}

	@Override
	public void tick() {
		timeout--;
		if (timeout % 10 == 0) {
			becario.getNavigation().moveTo(item, 1.1);
		}
	}

	@Override
	public void stop() {
		item = null;
		becario.getNavigation().stop();
	}
}
