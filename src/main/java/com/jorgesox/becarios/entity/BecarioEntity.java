package com.jorgesox.becarios.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jorgesox.becarios.BecarioSkins;
import com.jorgesox.becarios.task.BecarioTask;
import com.jorgesox.becarios.task.BlockMiningGoal;
import com.jorgesox.becarios.task.CollectItemsGoal;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * El becario: compañero de un jugador.
 *
 * Hereda de TamableAnimal (la base del lobo) para tener gratis: dueño
 * persistente, orden de "quieto" (sentado), seguir al dueño y teletransporte
 * al dueño si se queda muy lejos. Es un mob con pathfinding de verdad, a
 * diferencia del maniquí vanilla (que no tiene IA).
 *
 * Fase 2: tiene inventario propio (27 huecos) y puede recibir órdenes de
 * trabajo (minar, talar, recoger) que ejecutan los goals de /task.
 */
public class BecarioEntity extends TamableAnimal {
	/** Id de la skin (ver BecarioSkins). Sincronizado servidor -> cliente. */
	private static final EntityDataAccessor<String> DATA_SKIN =
		SynchedEntityData.defineId(BecarioEntity.class, EntityDataSerializers.STRING);

	private static final int INVENTORY_SIZE = 27;

	private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
	/** Orden de trabajo actual (solo servidor, no se guarda). */
	private @Nullable BecarioTask task;

	public BecarioEntity(EntityType<? extends BecarioEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.MAX_HEALTH, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_SKIN, BecarioSkins.DEFAULT);
	}

	@Override
	protected void registerGoals() {
		// Menor número = mayor prioridad. "Quieto" (2) manda sobre el trabajo (3),
		// y el trabajo manda sobre seguir al dueño (4).
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new BlockMiningGoal(this));
		this.goalSelector.addGoal(3, new CollectItemsGoal(this));
		// Empieza a seguir a >8 bloques, se detiene a 2 bloques del dueño.
		this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0, 8.0F, 2.0F));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	// ---------------------------------------------------------------- skin

	public String getSkin() {
		return this.entityData.get(DATA_SKIN);
	}

	public void setSkin(String skinId) {
		this.entityData.set(DATA_SKIN, BecarioSkins.isValid(skinId) ? skinId : BecarioSkins.DEFAULT);
	}

	// --------------------------------------------------------------- tareas

	public @Nullable BecarioTask getTask() {
		return this.task;
	}

	/** Empieza una orden de trabajo; si estaba "quieto", se levanta. */
	public void startTask(BecarioTask newTask) {
		this.task = newTask;
		this.setOrderedToSit(false);
	}

	/** Cancela el trabajo en curso sin avisar. */
	public void cancelTask() {
		this.task = null;
		this.getNavigation().stop();
	}

	/** Termina el trabajo y avisa al dueño. */
	public void finishTask(String message) {
		this.task = null;
		this.getNavigation().stop();
		this.tell(message);
	}

	/** Mensaje al dueño, con el nombre del becario delante. */
	public void tell(String text) {
		if (this.getOwner() instanceof ServerPlayer owner) {
			owner.sendSystemMessage(Component.literal("<" + this.getName().getString() + "> " + text));
		}
	}

	// ----------------------------------------------------------- inventario

	public SimpleContainer getInventory() {
		return this.inventory;
	}

	/** Guarda el objeto en el inventario; devuelve lo que no cupo. */
	public ItemStack storeItem(ItemStack stack) {
		return this.inventory.addItem(stack);
	}

	/** Guarda el objeto; si no cabe, lo suelta al suelo. */
	public void storeOrDrop(ServerLevel level, ItemStack stack) {
		ItemStack leftover = this.storeItem(stack);
		if (!leftover.isEmpty()) {
			this.spawnAtLocation(level, leftover);
		}
	}

	/**
	 * Deja en la mano principal la mejor herramienta (la de mano o del
	 * inventario) para romper este bloque. Devuelve false si el bloque exige
	 * una herramienta concreta para soltar drops y no tiene ninguna válida.
	 */
	public boolean equipBestToolFor(BlockState state) {
		boolean needsTool = state.requiresCorrectToolForDrops();
		int bestSlot = -2; // -2 = ninguna válida, -1 = la mano, >=0 = hueco del inventario
		float bestSpeed = -1.0F;

		ItemStack inHand = this.getMainHandItem();
		if (!needsTool || inHand.isCorrectToolForDrops(state)) {
			bestSlot = -1;
			bestSpeed = inHand.getDestroySpeed(state);
		}
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (stack.isEmpty() || (needsTool && !stack.isCorrectToolForDrops(state))) {
				continue;
			}
			float speed = stack.getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				bestSlot = slot;
			}
		}

		if (bestSlot == -2) {
			return false;
		}
		if (bestSlot >= 0) {
			// Intercambia la mano con el hueco del inventario.
			ItemStack best = this.inventory.getItem(bestSlot).copy();
			this.inventory.setItem(bestSlot, inHand.copy());
			this.setItemSlot(EquipmentSlot.MAINHAND, best);
		}
		return true;
	}

	/** "3x Hierro bruto, 12x Piedra..." o "inventario vacío". */
	public String inventorySummary() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		ItemStack inHand = this.getMainHandItem();
		if (!inHand.isEmpty()) {
			counts.merge(inHand.getHoverName().getString() + " (en la mano)", inHand.getCount(), Integer::sum);
		}
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (!stack.isEmpty()) {
				counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
			}
		}
		if (counts.isEmpty()) {
			return "no llevo nada encima.";
		}
		List<String> parts = new ArrayList<>();
		counts.forEach((name, count) -> parts.add(count + "x " + name));
		return "llevo " + String.join(", ", parts) + ".";
	}

	/** Entrega al jugador todo el inventario y lo que lleva en la mano. Devuelve cuántos objetos. */
	public int giveAllTo(ServerPlayer player) {
		int total = 0;
		List<ItemStack> stacks = new ArrayList<>();
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			stacks.add(this.inventory.removeItemNoUpdate(slot));
		}
		stacks.add(this.getMainHandItem().copy());
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			total += stack.getCount();
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				this.spawnAtLocation(player.level(), stack);
			}
		}
		return total;
	}

	/**
	 * Clic derecho del dueño: con un objeto en la mano se lo guarda (herramientas,
	 * materiales...); con la mano vacía cuenta lo que lleva.
	 */
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.isOwnedBy(player)) {
			return super.mobInteract(player, hand);
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			this.tell(this.inventorySummary());
			return InteractionResult.SUCCESS;
		}

		String name = held.getHoverName().getString();
		int before = held.getCount();
		ItemStack leftover = this.inventory.addItem(held.copy());
		int moved = before - leftover.getCount();
		if (moved <= 0) {
			this.tell("no me caben más cosas.");
			return InteractionResult.SUCCESS;
		}
		if (!player.getAbilities().instabuild) {
			player.setItemInHand(hand, leftover);
		}
		this.tell("guardado " + moved + "x " + name + ".");
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------ guardado

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("skin", this.getSkin());

		List<ItemStack> items = new ArrayList<>();
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			items.add(this.inventory.getItem(slot));
		}
		output.store("inventory", ItemStack.OPTIONAL_CODEC.listOf(), items);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setSkin(input.getStringOr("skin", BecarioSkins.DEFAULT));

		input.read("inventory", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(items -> {
			for (int slot = 0; slot < Math.min(items.size(), this.inventory.getContainerSize()); slot++) {
				this.inventory.setItem(slot, items.get(slot));
			}
		});
	}

	/** Si muere, suelta todo lo que llevaba. */
	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
				ItemStack stack = this.inventory.removeItemNoUpdate(slot);
				if (!stack.isEmpty()) {
					this.spawnAtLocation(level, stack);
				}
			}
		}
		super.die(source);
	}

	// ------------------------------------------------------------ varios

	/** Nada es comida para el becario: así no entra en modo cría. */
	@Override
	public boolean isFood(ItemStack itemStack) {
		return false;
	}

	/** El becario no se reproduce. */
	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}
}
