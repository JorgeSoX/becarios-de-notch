package com.jorgesox.becarios.build;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Plano de una casa sencilla, como la primera casa de un jugador:
 * suelo de piedra, paredes de tablas con troncos en las esquinas, techo de
 * tablas, una ventana a cada lado, puerta de madera mirando hacia ti y una
 * antorcha dentro.
 *
 * Pequeña: 5x5 por fuera (3x3 por dentro). Grande: 7x7 (5x5 por dentro).
 * Altura de paredes: 3. Se construye en orden: despejar, suelo, paredes
 * (de abajo arriba), techo, puerta y antorcha.
 */
public final class HousePlan {
	/** Qué va en cada sitio: el material (vale cualquier variante: roble, abedul...) o aire. */
	public enum Material {
		AIR(s -> false),
		FLOOR(s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE)),
		PLANKS(s -> s.is(ItemTags.PLANKS)),
		LOG(s -> s.is(ItemTags.LOGS)),
		DOOR(s -> s.is(ItemTags.WOODEN_DOORS)),
		TORCH(s -> s.is(Items.TORCH)),
		OBSIDIAN(s -> s.is(Items.OBSIDIAN)),
		/** Encender el portal con el mechero (no se gasta: pierde durabilidad). */
		FIRE(s -> s.is(Items.FLINT_AND_STEEL));

		public final Predicate<ItemStack> accepts;

		Material(Predicate<ItemStack> accepts) {
			this.accepts = accepts;
		}

		/** ¿Ya está puesto lo que toca en ese sitio? */
		public boolean isDone(BlockState state) {
			return switch (this) {
				// (el agua no se puede quitar: se deja; y el portal ya encendido, tampoco)
				case AIR -> state.isAir() || state.canBeReplaced() || state.is(Blocks.NETHER_PORTAL);
				case FLOOR -> state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE);
				case PLANKS -> state.is(BlockTags.PLANKS);
				case LOG -> state.is(BlockTags.LOGS);
				case DOOR -> state.is(BlockTags.WOODEN_DOORS);
				case TORCH -> state.is(Blocks.WALL_TORCH) || state.is(Blocks.TORCH);
				case OBSIDIAN -> state.is(Blocks.OBSIDIAN);
				case FIRE -> state.is(Blocks.NETHER_PORTAL);
			};
		}

		/** Si no hay torch/carbón no pasa nada: la casa se termina igual. */
		public boolean optional() {
			return this == TORCH;
		}
	}

	/** Un bloque del plano. `facing` solo importa para la puerta y la antorcha. */
	public record Placement(BlockPos pos, Material material, Direction facing) {
		/** El bloque concreto a poner con este objeto (p. ej. tablas de abedul si lleva abedul). */
		public BlockState stateFor(ItemStack item) {
			if (!(item.getItem() instanceof BlockItem blockItem)) {
				return Blocks.AIR.defaultBlockState();
			}
			BlockState state = blockItem.getBlock().defaultBlockState();
			return switch (this.material) {
				case DOOR -> state.setValue(DoorBlock.FACING, this.facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
				case TORCH -> Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, this.facing);
				case LOG -> state.hasProperty(BlockStateProperties.AXIS)
					? state.setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : state;
				default -> state;
			};
		}
	}

	private HousePlan() {
	}

	/**
	 * @param center  centro del suelo interior (a ras del suelo: los pies del jugador)
	 * @param front   hacia dónde mira la fachada (la puerta): hacia el jugador
	 * @param size    lado exterior: 5 (pequeña) o 7 (grande)
	 */
	public static List<Placement> house(BlockPos center, Direction front, int size) {
		int half = size / 2;
		Direction right = front.getClockWise();
		List<Placement> clear = new ArrayList<>();
		List<Placement> floor = new ArrayList<>();
		List<Placement> walls = new ArrayList<>();
		List<Placement> roof = new ArrayList<>();
		List<Placement> finish = new ArrayList<>();

		for (int a = -half; a <= half; a++) {
			for (int b = -half; b <= half; b++) {
				boolean edgeA = Math.abs(a) == half;
				boolean edgeB = Math.abs(b) == half;
				floor.add(new Placement(at(center, right, front, a, -1, b), Material.FLOOR, front));
				roof.add(new Placement(at(center, right, front, a, 3, b), Material.PLANKS, front));
				for (int y = 0; y <= 2; y++) {
					BlockPos pos = at(center, right, front, a, y, b);
					boolean door = a == 0 && b == half && y <= 1;
					boolean torch = a == 0 && b == -half + 1 && y == 1;
					boolean window = y == 1 && b == 0 && edgeA;
					if (door || torch) {
						continue; // los despeja la propia puerta/antorcha al ponerse
					}
					if (!edgeA && !edgeB || window) {
						clear.add(new Placement(pos, Material.AIR, front));
					} else {
						walls.add(new Placement(pos, edgeA && edgeB ? Material.LOG : Material.PLANKS, front));
					}
				}
			}
		}
		// Despejar de arriba abajo (si no, lo de arriba se cae encima).
		clear.sort((p, q) -> Integer.compare(q.pos().getY(), p.pos().getY()));
		walls.sort((p, q) -> Integer.compare(p.pos().getY(), q.pos().getY()));
		finish.add(new Placement(at(center, right, front, 0, 0, half), Material.DOOR, front));
		// Antorcha en la pared del fondo, por dentro, mirando a la puerta.
		finish.add(new Placement(at(center, right, front, 0, 1, -half + 1), Material.TORCH, front));

		List<Placement> all = new ArrayList<>(clear);
		all.addAll(floor);
		all.addAll(walls);
		all.addAll(roof);
		all.addAll(finish);
		return all;
	}

	/**
	 * Portal al Nether de tamaño mínimo: marco de obsidiana de 4x5 sin esquinas
	 * (10 bloques), de cara a `front`, con el hueco de 2x3 y encendido con mechero.
	 * `base` es el bloque de abajo a la izquierda del hueco.
	 */
	public static List<Placement> portal(BlockPos base, Direction front) {
		Direction right = front.getClockWise();
		List<Placement> clear = new ArrayList<>();
		List<Placement> frame = new ArrayList<>();
		for (int a = -1; a <= 2; a++) {
			for (int y = 0; y <= 4; y++) {
				boolean side = a == -1 || a == 2;
				boolean cap = y == 0 || y == 4;
				if (side && cap) {
					continue; // sin esquinas: ahorra 4 de obsidiana
				}
				BlockPos pos = base.relative(right, a).above(y);
				if (side || cap) {
					frame.add(new Placement(pos, Material.OBSIDIAN, front));
				} else if (!(a == 0 && y == 1)) {
					clear.add(new Placement(pos, Material.AIR, front));
				}
			}
		}
		clear.sort((p, q) -> Integer.compare(q.pos().getY(), p.pos().getY()));
		frame.sort((p, q) -> Integer.compare(p.pos().getY(), q.pos().getY()));
		List<Placement> all = new ArrayList<>(clear);
		all.addAll(frame);
		all.add(new Placement(base.relative(right, 0).above(1), Material.FIRE, front));
		return all;
	}

	private static BlockPos at(BlockPos center, Direction right, Direction front, int a, int y, int b) {
		return center.relative(right, a).relative(front, b).above(y);
	}
}
