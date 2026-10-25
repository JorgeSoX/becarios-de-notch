package com.jorgesox.becarios.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Si te vas a otra dimensión (has cruzado un portal) y él no está trabajando,
 * busca un portal cerca y entra detrás de ti, como haría un compañero.
 * Solo cruza portales en ese caso (ver BecarioEntity#canUsePortal): trabajando
 * al lado de uno no se cuela por accidente.
 */
public class FollowThroughPortalGoal extends Goal {
	private static final int SEARCH = 16;

	private final BecarioEntity becario;
	private @Nullable BlockPos portal;
	private int timeout;

	public FollowThroughPortalGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity owner = becario.getOwner();
		if (owner == null || owner.level() == becario.level() || becario.getTask() != null || becario.getCraftOrder() != null
			|| becario.getBuildOrder() != null || becario.isOrderedToSit() || becario.getCombatMode() == CombatMode.GUARDIA
			|| becario.tickCount % 20 != 0) {
			return false;
		}
		portal = findPortal();
		return portal != null;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity owner = becario.getOwner();
		return portal != null && timeout > 0 && owner != null && owner.level() != becario.level()
			&& becario.level().getBlockState(portal).is(Blocks.NETHER_PORTAL);
	}

	@Override
	public void start() {
		timeout = 400;
		becario.tell("voy detrás de ti por el portal.");
	}

	@Override
	public void tick() {
		timeout--;
		Vec3 target = Vec3.atBottomCenterOf(portal);
		becario.getLookControl().setLookAt(target.x, target.y + 1, target.z);
		if (timeout % 10 == 0) {
			becario.getNavigation().moveTo(target.x, target.y, target.z, 1.1);
		}
		if (becario.position().distanceToSqr(target) < 2.5) {
			becario.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.0);
		}
	}

	@Override
	public void stop() {
		portal = null;
		becario.getNavigation().stop();
	}

	private @Nullable BlockPos findPortal() {
		BlockPos origin = becario.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-SEARCH, -6, -SEARCH), origin.offset(SEARCH, 6, SEARCH))) {
			if (becario.level().getBlockState(p).is(Blocks.NETHER_PORTAL)) {
				double d = p.distSqr(origin);
				if (d < bestDist) {
					bestDist = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}
}
