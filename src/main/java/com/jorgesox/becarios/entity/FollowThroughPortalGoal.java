package com.jorgesox.becarios.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
	/** Buscar portal es caro: como mucho una vez por segundo (contador propio: el juego no evalúa los goals cada tick). */
	private int cooldown;

	public FollowThroughPortalGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity owner = becario.getOwner();
		if (owner == null || owner.level() == becario.level() || becario.getTask() != null || becario.getCraftOrder() != null
			|| becario.getBuildOrder() != null || becario.isOrderedToSit() || becario.getCombatMode() == CombatMode.GUARDIA) {
			return false;
		}
		if (--cooldown > 0) {
			return false;
		}
		cooldown = 10;
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
		double dx = becario.getX() - target.x;
		double dz = becario.getZ() - target.z;
		boolean close = dx * dx + dz * dz < 2.5 && Math.abs(becario.getY() - target.y) < 1.5;
		if (close) {
			// Ya está delante: un paso al frente y dentro.
			becario.getNavigation().stop();
			becario.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.0);
		} else if (timeout % 10 == 0) {
			// Se va a un hueco libre justo delante del portal (no hacia el bloque de portal:
			// el camino hasta él no siempre existe y acabaría subido encima del marco).
			BlockPos front = frontOf(portal);
			Vec3 walk = front != null ? Vec3.atBottomCenterOf(front) : target;
			becario.getNavigation().moveTo(walk.x, walk.y, walk.z, 1.1);
		}
	}

	/** Sitio donde ponerse delante (o detrás) del portal, el más cercano al becario. */
	private @Nullable BlockPos frontOf(BlockPos p) {
		var state = becario.level().getBlockState(p);
		Direction.Axis axis = state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
			? state.getValue(BlockStateProperties.HORIZONTAL_AXIS) : Direction.Axis.X;
		Direction side = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
		BlockPos best = null;
		for (BlockPos c : new BlockPos[] { p.relative(side), p.relative(side.getOpposite()) }) {
			if (becario.level().getBlockState(c).getCollisionShape(becario.level(), c).isEmpty()
				&& becario.level().getBlockState(c.above()).getCollisionShape(becario.level(), c.above()).isEmpty()
				&& (best == null || c.distSqr(becario.blockPosition()) < best.distSqr(becario.blockPosition()))) {
				best = c;
			}
		}
		return best;
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
			// Solo la fila de abajo: ahí es donde se entra andando.
			if (becario.level().getBlockState(p).is(Blocks.NETHER_PORTAL) && !becario.level().getBlockState(p.below()).is(Blocks.NETHER_PORTAL)) {
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
