package com.jorgesox.becarios.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jorgesox.becarios.BecarioSkins;
import com.jorgesox.becarios.command.BecarioCommands;
import com.jorgesox.becarios.task.BecarioTask;
import com.jorgesox.becarios.task.BlockGroups;
import com.jorgesox.becarios.task.BlockMiningGoal;
import com.jorgesox.becarios.task.CollectItemsGoal;
import com.jorgesox.becarios.task.ExpeditionGoal;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
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
	/** Encargo de fabricación en curso (CraftGoal); mientras, `task` puede ser la recogida de materia prima. */
	private com.jorgesox.becarios.craft.@Nullable CraftOrder craftOrder;

	public BecarioEntity(EntityType<? extends BecarioEntity> type, Level level) {
		super(type, level);
		// Navegación "de jugador": abre puertas y nada.
		this.getNavigation().setCanOpenDoors(true);
		this.getNavigation().setCanFloat(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.MAX_HEALTH, 20.0)
			// Rango de búsqueda de rutas: permite planificar caminos largos.
			.add(Attributes.FOLLOW_RANGE, 48.0)
			// Daño base a puñetazos (como un jugador); el arma en mano suma el suyo.
			.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_SKIN, BecarioSkins.DEFAULT);
	}

	@Override
	protected void registerGoals() {
		// Menor número = mayor prioridad. Pelear (1) manda sobre "quieto" (2),
		// "quieto" sobre trabajar (3), y trabajar sobre seguir al dueño (4).
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
		this.goalSelector.addGoal(3, new BlockMiningGoal(this));
		this.goalSelector.addGoal(3, new ExpeditionGoal(this));
		this.goalSelector.addGoal(3, new CollectItemsGoal(this));
		this.goalSelector.addGoal(3, new com.jorgesox.becarios.craft.CraftGoal(this));
		this.goalSelector.addGoal(3, new FetchThrownItemsGoal(this));
		this.goalSelector.addGoal(4, new ReturnToPostGoal(this));
		this.goalSelector.addGoal(4, new ReturnAlongTrailGoal(this));
		this.goalSelector.addGoal(4, new BecarioFollowOwnerGoal(this));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

		// Objetivos de combate (según el modo).
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return BecarioEntity.this.fights() && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this) {
			@Override
			public boolean canUse() {
				return BecarioEntity.this.fights() && super.canUse();
			}
		});
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return BecarioEntity.this.fights() && super.canUse();
			}
		});
		// Escolta/guardia: monstruos que VEA (mustSee = true) dentro del radio.
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, true,
			(target, level) -> this.isThreatInZone(target)));
	}

	/** ¿Pelea en el modo actual? (sentado no pelea, como el lobo). */
	private boolean fights() {
		return this.combatMode != CombatMode.PASIVO && !this.isOrderedToSit();
	}

	/** Monstruo dentro de la zona vigilada (alrededor del dueño o del puesto de guardia). */
	private boolean isThreatInZone(LivingEntity target) {
		if (!this.fights() || !(target instanceof Enemy) || target instanceof Creeper) {
			return false;
		}
		double r2 = (double) this.guardRadius * this.guardRadius;
		if (this.combatMode == CombatMode.ESCOLTA) {
			LivingEntity owner = this.getOwner();
			return owner != null && owner.level() == this.level() && target.distanceToSqr(owner) <= r2;
		}
		if (this.combatMode == CombatMode.GUARDIA && this.guardPos != null) {
			return target.blockPosition().distSqr(this.guardPos) <= r2;
		}
		return false;
	}

	/** A quién NO atacar nunca (adaptado del lobo). */
	@Override
	public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
		if (target instanceof Creeper || target instanceof ArmorStand) {
			return false;
		}
		if (target instanceof TamableAnimal tamed && tamed.isTame()) {
			return false; // mascotas y otros becarios
		}
		if (target instanceof Player victim && owner instanceof Player boss && !boss.canHarmPlayer(victim)) {
			return false;
		}
		return true;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !this.isOwnedBy(target) && super.canAttack(target);
	}

	// ------------------------------------------------------------ combate

	private CombatMode combatMode = CombatMode.DEFENSA;
	private @Nullable BlockPos guardPos;
	private int guardRadius = 16;

	public CombatMode getCombatMode() {
		return this.combatMode;
	}

	public @Nullable BlockPos getGuardPos() {
		return this.guardPos;
	}

	public int getGuardRadius() {
		return this.guardRadius;
	}

	/** Cambia el modo de combate. En GUARDIA, guardPos es el punto a proteger. */
	public void setCombatMode(CombatMode mode, int radius, @Nullable BlockPos post) {
		this.combatMode = mode;
		this.guardRadius = Math.max(4, Math.min(48, radius));
		this.guardPos = mode == CombatMode.GUARDIA ? post : null;
		this.setTarget(null);
		if (mode == CombatMode.GUARDIA) {
			this.cancelTask();
			this.setOrderedToSit(false);
		}
	}

	// ---------------------------------------------------------------- skin

	public String getSkin() {
		return this.entityData.get(DATA_SKIN);
	}

	public void setSkin(String skinId) {
		this.entityData.set(DATA_SKIN, BecarioSkins.isValid(skinId) ? skinId : BecarioSkins.DEFAULT);
	}

	// ------------------------------------------------------- movimiento

	/** Orden "ven": seguir aunque esté cerca, hasta llegar a tu lado. */
	private boolean coming;

	public boolean isComing() {
		return this.coming;
	}

	public void setComing(boolean coming) {
		this.coming = coming;
	}

	/** El becario NUNCA se teletransporta: viene andando o corriendo. */
	@Override
	public void tryToTeleportToOwner() {
	}

	@Override
	public boolean shouldTryTeleportToOwner() {
		return false;
	}

	// --------------------------------------------------------------- tareas

	public @Nullable BecarioTask getTask() {
		return this.task;
	}

	/** Empieza una orden de trabajo; si estaba "quieto", se levanta. Una orden nueva del dueño anula el encargo. */
	public void startTask(BecarioTask newTask) {
		if (!newTask.forCraft) {
			this.craftOrder = null;
		}
		this.task = newTask;
		this.setOrderedToSit(false);
	}

	/** Cancela el trabajo (y el encargo de fabricación) sin avisar. */
	public void cancelTask() {
		this.task = null;
		this.craftOrder = null;
		this.getNavigation().stop();
	}

	/**
	 * Termina el trabajo con éxito y avisa al dueño. Si era parte de un encargo
	 * de fabricación, no vuelve: sigue con el encargo.
	 */
	public void finishTask(String message) {
		BecarioTask finished = this.task;
		this.task = null;
		this.getNavigation().stop();
		if (finished != null && finished.forCraft && this.craftOrder != null) {
			this.tell("ya tengo " + finished.done + " de " + finished.label + ". Sigo con el encargo.");
			return;
		}
		this.tell(message);
	}

	/** El trabajo no puede seguir (inventario lleno...): se abandona todo, también el encargo. */
	public void failTask(String message) {
		this.task = null;
		this.craftOrder = null;
		this.getNavigation().stop();
		this.tell(message);
	}

	/**
	 * Le falta el pico adecuado para un bloque. En una orden normal de minar,
	 * se fabrica el pico más básico que sirva (con todo lo que haga falta) y
	 * luego retoma la orden. Si ya estaba fabricando, se rinde.
	 */
	public void needToolFor(BlockState state, String message) {
		BecarioTask current = this.task;
		net.minecraft.world.item.Item pick = com.jorgesox.becarios.craft.Gatherables.basicPickaxeFor(state);
		if (current == null || current.forCraft || this.craftOrder != null || pick == null) {
			this.failTask(message);
			return;
		}
		current.needsExpedition = false;
		this.task = null;
		this.getNavigation().stop();
		this.craftOrder = new com.jorgesox.becarios.craft.CraftOrder(pick, this.countItem(pick) + 1, 1);
		this.craftOrder.resume = current;
		this.tell(Component.literal("no tengo pico para esto. Me fabrico ")
			.append(new ItemStack(pick).getHoverName()).append(" y sigo."));
	}

	/** ¿Ya lleva encima lo que buscaba para el encargo? */
	public boolean gatheredEnough(BecarioTask t) {
		if (t.gatherItems == null) {
			return false;
		}
		int count = 0;
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (t.gatherItems.test(stack)) {
				count += stack.getCount();
			}
		}
		for (ItemStack stack : new ItemStack[] {this.getMainHandItem(), this.getOffhandItem()}) {
			if (t.gatherItems.test(stack)) {
				count += stack.getCount();
			}
		}
		return count >= t.gatherUntil;
	}

	public com.jorgesox.becarios.craft.@Nullable CraftOrder getCraftOrder() {
		return this.craftOrder;
	}

	/** Nuevo encargo: tener `amount` más de ese objeto. */
	public void startCraft(net.minecraft.world.item.Item item, int amount) {
		this.task = null;
		this.craftOrder = new com.jorgesox.becarios.craft.CraftOrder(item, this.countItem(item) + amount, amount);
		this.setOrderedToSit(false);
		this.getNavigation().stop();
	}

	/** Encargo terminado: avisa y, si había un trabajo pendiente, lo retoma. */
	public void completeCraft() {
		com.jorgesox.becarios.craft.CraftOrder order = this.craftOrder;
		this.craftOrder = null;
		if (order == null) {
			return;
		}
		Component itemName = new ItemStack(order.item).getHoverName();
		if (order.resume != null) {
			this.task = order.resume;
			this.tell(Component.literal("ya tengo ").append(itemName).append(". Sigo con " + order.resume.label + "."));
		} else {
			this.tell(Component.literal("listo: he fabricado " + order.asked + "x ").append(itemName).append("."));
		}
	}

	public void failCraft(Component message) {
		this.craftOrder = null;
		this.task = null;
		this.getNavigation().stop();
		this.tell(message);
	}

	/** Mensaje al dueño, con el nombre del becario delante. */
	public void tell(String text) {
		this.tell(Component.literal(text));
	}

	/** Igual, pero con Component: los nombres de objeto se traducen en el idioma del cliente. */
	public void tell(Component text) {
		if (this.getOwner() instanceof ServerPlayer owner) {
			owner.sendSystemMessage(Component.literal("<" + this.getName().getString() + "> ").append(text));
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

	/** Para despejar el camino: saca lo que rompa más rápido ese bloque, sea o no la herramienta "correcta". */
	public void equipFastestToolFor(BlockState state) {
		float best = this.getMainHandItem().getDestroySpeed(state);
		int bestSlot = -1;
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			float speed = this.inventory.getItem(slot).getDestroySpeed(state);
			if (speed > best) {
				best = speed;
				bestSlot = slot;
			}
		}
		if (bestSlot >= 0) {
			ItemStack tool = this.inventory.getItem(bestSlot).copy();
			this.inventory.setItem(bestSlot, this.getMainHandItem().copy());
			this.setItemSlot(EquipmentSlot.MAINHAND, tool);
		}
	}

	/** "llevo 3x Hierro bruto, 12x Piedra..." (nombres traducidos por el cliente) o "no llevo nada encima." */
	public Component inventorySummary() {
		// Suma por tipo de objeto (clave = id del item), conservando el orden.
		Map<String, ItemStack> totals = new LinkedHashMap<>();
		ItemStack inHand = this.getMainHandItem();
		if (!inHand.isEmpty()) {
			totals.put(inHand.getItem().toString(), inHand.copy());
		}
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack total = totals.get(stack.getItem().toString());
			if (total == null) {
				totals.put(stack.getItem().toString(), stack.copy());
			} else {
				total.grow(stack.getCount());
			}
		}
		if (totals.isEmpty()) {
			return Component.literal("no llevo nada encima.");
		}
		MutableComponent text = Component.literal("llevo ");
		boolean first = true;
		for (ItemStack total : totals.values()) {
			if (!first) {
				text.append(", ");
			}
			first = false;
			text.append(total.getCount() + "x ").append(total.getHoverName());
		}
		return text.append(".");
	}

	/** Las 4 piezas de armadura que puede llevar puestas. */
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Suelta al suelo TODO: inventario, mano y armadura (al morir o retirarse). */
	private void dropEverything(ServerLevel level) {
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.removeItemNoUpdate(slot);
			if (!stack.isEmpty()) {
				this.spawnAtLocation(level, stack);
			}
		}
		for (EquipmentSlot slot : allWornSlots()) {
			ItemStack stack = this.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				this.spawnAtLocation(level, stack.copy());
				this.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	private static EquipmentSlot[] allWornSlots() {
		return new EquipmentSlot[] {
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
		};
	}

	/** Entrega al jugador todo el inventario y lo que lleva en la mano. Devuelve cuántos objetos. */
	public int giveAllTo(ServerPlayer player) {
		return this.giveAllTo(player, false);
	}

	/** Igual, y si includeArmor también se quita la armadura (al despedirlo). */
	public int giveAllTo(ServerPlayer player, boolean includeArmor) {
		int total = 0;
		List<ItemStack> stacks = new ArrayList<>();
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			stacks.add(this.inventory.removeItemNoUpdate(slot));
		}
		stacks.add(this.getMainHandItem().copy());
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		if (includeArmor) {
			stacks.add(this.getItemBySlot(EquipmentSlot.OFFHAND).copy());
			this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				stacks.add(this.getItemBySlot(slot).copy());
				this.setItemSlot(slot, ItemStack.EMPTY);
			}
		}

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
	 * Clic derecho del dueño: abre el menú del becario.
	 * Mayús + clic derecho con un objeto: se lo da (armadura = se la pone).
	 */
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.isOwnedBy(player)) {
			return super.mobInteract(player, hand);
		}
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		// Clic derecho normal: abre su menú. Mayús + clic con un objeto: se lo das directamente.
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() || !player.isShiftKeyDown()) {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
				BecarioCommands.openMenu(serverPlayer, this);
			}
			return InteractionResult.SUCCESS;
		}

		Component name = held.getHoverName();

		// Armadura: se la pone (y guarda la que llevaba, si había).
		EquipmentSlot armorSlot = this.getEquipmentSlotForItem(held);
		if (armorSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			ItemStack previous = this.getItemBySlot(armorSlot).copy();
			this.setItemSlot(armorSlot, held.copyWithCount(1));
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			if (!previous.isEmpty() && this.level() instanceof ServerLevel level) {
				this.storeOrDrop(level, previous);
			}
			this.tell(Component.literal("me pongo ").append(name).append("."));
			return InteractionResult.SUCCESS;
		}
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
		this.refreshEquipment();
		this.tell(Component.literal("guardado " + moved + "x ").append(name).append("."));
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

		output.putString("combat_mode", this.combatMode.name());
		output.putInt("guard_radius", this.guardRadius);
		if (this.guardPos != null) {
			output.putInt("guard_x", this.guardPos.getX());
			output.putInt("guard_y", this.guardPos.getY());
			output.putInt("guard_z", this.guardPos.getZ());
		}

		// Orden de trabajo en curso: se guarda para que sobreviva a reinicios.
		if (this.craftOrder != null) {
			output.putString("craft_item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.craftOrder.item).toString());
			output.putInt("craft_total", this.craftOrder.total);
			output.putInt("craft_asked", this.craftOrder.asked);
			if (this.craftOrder.resume != null) {
				output.putString("craft_resume_label", this.craftOrder.resume.label);
				output.putInt("craft_resume_target", this.craftOrder.resume.target);
				output.putInt("craft_resume_done", this.craftOrder.resume.done);
			}
		}
		if (this.task != null) {
			output.putBoolean("task_for_craft", this.task.forCraft);
			output.putString("task_kind", this.task.kind.name());
			output.putString("task_label", this.task.label);
			output.putInt("task_target", this.task.target);
			output.putInt("task_done", this.task.done);
		}
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

		try {
			this.combatMode = CombatMode.valueOf(input.getStringOr("combat_mode", CombatMode.DEFENSA.name()));
		} catch (IllegalArgumentException e) {
			this.combatMode = CombatMode.DEFENSA;
		}
		this.guardRadius = input.getIntOr("guard_radius", 16);
		if (this.combatMode == CombatMode.GUARDIA) {
			this.guardPos = new BlockPos(input.getIntOr("guard_x", 0), input.getIntOr("guard_y", 0), input.getIntOr("guard_z", 0));
		}

		// Restaura la orden de trabajo (si el grupo de bloques ya no existe, se ignora).
		String kind = input.getStringOr("task_kind", "");
		if (kind.equals(BecarioTask.Kind.COLLECT.name())) {
			this.task = BecarioTask.collect();
			this.task.done = input.getIntOr("task_done", 0);
		} else if (kind.equals(BecarioTask.Kind.MINE.name())) {
			BlockGroups.Group group = BlockGroups.get(input.getStringOr("task_label", ""));
			if (group != null) {
				this.task = BecarioTask.mine(group.label(), group.test(), input.getIntOr("task_target", 1));
				this.task.done = input.getIntOr("task_done", 0);
				this.task.forCraft = input.getBooleanOr("task_for_craft", false);
			}
		}

		// Encargo de fabricación.
		String craftItem = input.getStringOr("craft_item", "");
		net.minecraft.resources.Identifier craftId = craftItem.isEmpty() ? null : net.minecraft.resources.Identifier.tryParse(craftItem);
		if (craftId != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(craftId)) {
			this.craftOrder = new com.jorgesox.becarios.craft.CraftOrder(
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(craftId),
				input.getIntOr("craft_total", 1), input.getIntOr("craft_asked", 1));
			BlockGroups.Group resume = BlockGroups.get(input.getStringOr("craft_resume_label", ""));
			if (resume != null) {
				this.craftOrder.resume = BecarioTask.mine(resume.label(), resume.test(), input.getIntOr("craft_resume_target", 1));
				this.craftOrder.resume.done = input.getIntOr("craft_resume_done", 0);
			}
		} else if (this.task != null && this.task.forCraft) {
			this.task.forCraft = false;
		}
	}

	/** Si muere, suelta todo lo que llevaba (inventario, herramienta y armadura). */
	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			this.dropEverything(level);
		}
		super.die(source);
	}

	// ------------------------------------------- recoger, equipar y comer

	// ------------------------------------------ rastro y bloques de relleno

	/** Camino que ha recorrido excavando/explorando, para volver por él. */
	private final Deque<BlockPos> trail = new ArrayDeque<>();

	public void addTrail(BlockPos pos) {
		if (this.trail.isEmpty() || !this.trail.peekLast().equals(pos)) {
			this.trail.addLast(pos.immutable());
			if (this.trail.size() > 1024) {
				this.trail.pollFirst();
			}
		}
	}

	public Deque<BlockPos> getTrail() {
		return this.trail;
	}

	public void clearTrail() {
		this.trail.clear();
	}

	/** Bloques que usa para tapar lava o hacer puentes (escombros que suele llevar). */
	private static final java.util.Set<net.minecraft.world.item.Item> FILLERS = java.util.Set.of(
		net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.COBBLED_DEEPSLATE,
		net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.STONE, net.minecraft.world.item.Items.DEEPSLATE,
		net.minecraft.world.item.Items.NETHERRACK, net.minecraft.world.item.Items.ANDESITE, net.minecraft.world.item.Items.DIORITE,
		net.minecraft.world.item.Items.GRANITE, net.minecraft.world.item.Items.TUFF, net.minecraft.world.item.Items.SANDSTONE);

	/**
	 * Pone un bloque de relleno (piedra, tierra...) de su inventario en esa
	 * posición, como haría un jugador para tapar lava o cruzar un hueco.
	 * Solo si el sitio está libre o es líquido. Devuelve true si lo ha puesto.
	 */
	public boolean placeFillerAt(ServerLevel level, BlockPos pos) {
		BlockState current = level.getBlockState(pos);
		if (!current.canBeReplaced() && level.getFluidState(pos).isEmpty()) {
			return false;
		}
		for (int slot = -1; slot < this.inventory.getContainerSize(); slot++) {
			// -1 = mano izquierda (como un jugador con bloques en la otra mano)
			ItemStack stack = slot < 0 ? this.getOffhandItem() : this.inventory.getItem(slot);
			if (!stack.isEmpty() && FILLERS.contains(stack.getItem())
				&& stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
				level.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
				stack.shrink(1);
				this.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
				return true;
			}
		}
		return false;
	}

	/**
	 * Atascado yendo hacia un punto: si justo delante (1-2 bloques) hay lava o un
	 * hueco sobre lava, pone bloques de relleno para poder pasar.
	 */
	public void bridgeToward(ServerLevel level, net.minecraft.world.phys.Vec3 target) {
		net.minecraft.core.Direction towards = net.minecraft.core.Direction.getApproximateNearest(
			target.x - this.getX(), 0, target.z - this.getZ());
		if (!towards.getAxis().isHorizontal()) {
			return;
		}
		BlockPos feet = this.blockPosition();
		for (int i = 1; i <= 2; i++) {
			BlockPos front = feet.relative(towards, i);
			if (level.getFluidState(front).is(net.minecraft.tags.FluidTags.LAVA)) {
				this.placeFillerAt(level, front);
			}
			BlockPos floor = front.below();
			boolean floorLava = level.getFluidState(floor).is(net.minecraft.tags.FluidTags.LAVA);
			boolean holeOverLava = level.getBlockState(floor).isAir()
				&& level.getFluidState(floor.below()).is(net.minecraft.tags.FluidTags.LAVA);
			if (floorLava || holeOverLava) {
				this.placeFillerAt(level, floor);
			}
		}
	}

	// ------------------------------------------------- brújula del becario

	public static final String COMPASS_TAG = "becario_brujula";

	/** Crea la brújula que apunta a este becario. */
	public ItemStack createCompass() {
		ItemStack compass = new ItemStack(net.minecraft.world.item.Items.COMPASS);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString(COMPASS_TAG, this.getUUID().toString());
		compass.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
		compass.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
			Component.literal("Brújula de " + this.getName().getString()));
		return compass;
	}

	/** Cada segundo, las brújulas de este becario que lleve el dueño apuntan a donde está. */
	private void updateOwnerCompasses(ServerLevel level) {
		if (!(this.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		String me = this.getUUID().toString();
		net.minecraft.world.item.component.LodestoneTracker tracker = new net.minecraft.world.item.component.LodestoneTracker(
			java.util.Optional.of(net.minecraft.core.GlobalPos.of(level.dimension(), this.blockPosition())), false);
		for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
			ItemStack stack = owner.getInventory().getItem(slot);
			if (!stack.is(net.minecraft.world.item.Items.COMPASS)) {
				continue;
			}
			net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
			if (data != null && me.equals(data.copyTag().getStringOr(COMPASS_TAG, ""))) {
				stack.set(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER, tracker);
			}
		}
	}

	/** Cuántas unidades de ese objeto lleva en el inventario. */
	public int countItem(net.minecraft.world.item.Item item) {
		int count = this.inventory.countItem(item);
		if (this.getMainHandItem().is(item)) {
			count += this.getMainHandItem().getCount();
		}
		if (this.getOffhandItem().is(item)) {
			count += this.getOffhandItem().getCount();
		}
		return count;
	}

	public boolean hasInventorySpace() {
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			if (this.inventory.getItem(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Recoge los objetos que toca (lo que le tiras con Q, drops...), sin depender
	 * de la regla mobGriefing como los mobs vanilla.
	 */
	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.tickCount % 2 != 0) {
			return;
		}
		boolean picked = false;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.0, 0.5, 1.0))) {
			if (entity.isRemoved() || entity.hasPickUpDelay() || entity.getItem().isEmpty()) {
				continue;
			}
			ItemStack stack = entity.getItem();
			int before = stack.getCount();
			ItemStack leftover = this.inventory.addItem(stack.copy());
			int taken = before - leftover.getCount();
			if (taken > 0) {
				this.take(entity, taken);
				stack.shrink(taken);
				if (stack.isEmpty()) {
					entity.discard();
				}
				picked = true;
			}
		}
		if (picked) {
			this.refreshEquipment();
		}
	}

	/** Daño cuerpo a cuerpo que haría con este objeto en la mano. */
	private static double attackScore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
	}

	/** Puntos de armadura que da este objeto en esa ranura. */
	private static double armorScore(ItemStack stack, EquipmentSlot slot) {
		return stack.isEmpty() ? 0.0 : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ARMOR, 0.0, slot);
	}

	/** Se pone la mejor armadura que lleve en el inventario. */
	public void refreshEquipment() {
		for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
			for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
				ItemStack candidate = this.inventory.getItem(slot);
				if (candidate.isEmpty() || this.getEquipmentSlotForItem(candidate) != armorSlot) {
					continue;
				}
				ItemStack worn = this.getItemBySlot(armorSlot);
				if (armorScore(candidate, armorSlot) > armorScore(worn, armorSlot)) {
					this.inventory.setItem(slot, worn.copy());
					this.setItemSlot(armorSlot, candidate.copyWithCount(1));
					if (candidate.getCount() > 1) {
						this.storeItem(candidate.copyWithCount(candidate.getCount() - 1));
					}
				}
			}
		}
	}

	/** En combate, saca a la mano el arma que más daño hace. */
	private void equipBestWeapon() {
		double best = attackScore(this.getMainHandItem());
		int bestSlot = -1;
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			double score = attackScore(this.inventory.getItem(slot));
			if (score > best + 0.01) {
				best = score;
				bestSlot = slot;
			}
		}
		if (bestSlot >= 0) {
			ItemStack weapon = this.inventory.getItem(bestSlot).copy();
			this.inventory.setItem(bestSlot, this.getMainHandItem().copy());
			this.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
	}

	/** Si está herido y no está peleando, come algo de su inventario. */
	private void eatIfHurt(ServerLevel level) {
		if (this.getHealth() > this.getMaxHealth() - 4.0F || this.getTarget() != null) {
			return;
		}
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null && food.nutrition() > 0) {
				this.heal(food.nutrition());
				stack.shrink(1);
				this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
				return;
			}
		}
	}

	// ------------------------------------------------- uno por jugador

	/**
	 * Cada 5 s comprueba en el registro que sigue siendo EL becario de su dueño.
	 * Si el dueño invocó otro mientras este estaba en una zona no cargada,
	 * este se retira (soltando lo que llevaba). Si el dueño no tiene ninguno
	 * registrado (becarios antiguos), se registra.
	 */
	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount % 10 == 0 && this.getTarget() != null) {
			this.equipBestWeapon();
		}
		if (this.tickCount % 60 == 0) {
			this.eatIfHurt(level);
		}
		if (this.tickCount % 20 == 0) {
			this.updateOwnerCompasses(level);
		}
		// Trabajando lejos (o volviendo): mantener cargada su zona, igual que un
		// jugador carga los chunks a su alrededor. Si no, se "congelaría" al alejarse.
		if (this.tickCount % 20 == 0 && (this.task != null || this.craftOrder != null || !this.trail.isEmpty())) {
			level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.ENDER_PEARL,
				net.minecraft.world.level.ChunkPos.containing(this.blockPosition()), 2);
		}
		if (this.tickCount % 100 != 0) {
			return;
		}
		LivingEntity owner = this.getOwner();
		if (!(owner instanceof ServerPlayer)) {
			return; // dueño desconectado: no decidimos nada
		}
		BecarioRegistry registry = BecarioRegistry.get(level.getServer());
		UUID registered = registry.becarioOf(owner.getUUID());
		if (registered == null) {
			registry.set(owner.getUUID(), this.getUUID());
		} else if (!registered.equals(this.getUUID())) {
			this.dropEverything(level);
			this.discard();
		}
	}

	/** Al morir o ser despedido (no al descargarse el chunk), libera su hueco en el registro. */
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
			LivingEntity owner = this.getOwner();
			UUID ownerId = owner != null ? owner.getUUID() : null;
			if (ownerId != null) {
				BecarioRegistry.get(level.getServer()).clearIf(ownerId, this.getUUID());
			}
		}
		super.remove(reason);
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
