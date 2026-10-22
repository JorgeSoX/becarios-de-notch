package com.jorgesox.becarios.menu;

import com.jorgesox.becarios.entity.BecarioEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Inventario del becario: 27 huecos + armadura (4) + mano (1), y abajo el
 * inventario del jugador. Se coge y se deja lo que quieras, como en un cofre.
 *
 * Índices de huecos: 0-26 inventario, 27-31 equipo, 32-67 jugador.
 * En el cliente se crean contenedores vacíos; el servidor sincroniza el contenido.
 */
public class BecarioInventoryMenu extends AbstractContainerMenu {
	public static final int INV_SLOTS = 27;
	public static final int EQUIP_SLOTS = 5;
	/** Desplazamiento horizontal del cofre para dejar sitio a la columna de equipo. */
	public static final int OFFSET_X = 22;

	private static final EquipmentSlot[] EQUIP_ORDER = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
	};
	private static final Identifier[] EQUIP_ICONS = {
		InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
		InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, null
	};

	private final @Nullable BecarioEntity becario;

	/** Constructor del cliente (lo llama el MenuType). */
	public BecarioInventoryMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, null, new SimpleContainer(INV_SLOTS), clientEquipment());
	}

	/** Constructor del servidor. */
	public static BecarioInventoryMenu server(int containerId, Inventory playerInventory, BecarioEntity becario) {
		Container[] equipment = new Container[EQUIP_SLOTS];
		for (int i = 0; i < EQUIP_SLOTS; i++) {
			equipment[i] = becario.createEquipmentSlotContainer(EQUIP_ORDER[i]);
		}
		return new BecarioInventoryMenu(containerId, playerInventory, becario, becario.getInventory(), equipment);
	}

	private static Container[] clientEquipment() {
		Container[] equipment = new Container[EQUIP_SLOTS];
		for (int i = 0; i < EQUIP_SLOTS; i++) {
			equipment[i] = new SimpleContainer(1);
		}
		return equipment;
	}

	private BecarioInventoryMenu(int containerId, Inventory playerInventory, @Nullable BecarioEntity becario,
		Container inventory, Container[] equipment) {
		super(ModMenus.BECARIO_INVENTORY, containerId);
		this.becario = becario;

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9, OFFSET_X + 8 + col * 18, 18 + row * 18));
			}
		}
		for (int i = 0; i < EQUIP_SLOTS; i++) {
			this.addSlot(new EquipSlot(equipment[i], EQUIP_ORDER[i], 4, 18 + i * 18, EQUIP_ICONS[i]));
		}
		this.addStandardInventorySlots(playerInventory, OFFSET_X + 8, 18 + 3 * 18 + 13);
	}

	/** Hueco de equipo: solo acepta piezas de esa ranura (la mano acepta cualquier cosa). */
	private static class EquipSlot extends Slot {
		private final EquipmentSlot type;
		private final @Nullable Identifier icon;

		EquipSlot(Container container, EquipmentSlot type, int x, int y, @Nullable Identifier icon) {
			super(container, 0, x, y);
			this.type = type;
			this.icon = icon;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (this.type == EquipmentSlot.MAINHAND) {
				return true;
			}
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			return equippable != null && equippable.slot() == this.type;
		}

		@Override
		public int getMaxStackSize() {
			return this.type == EquipmentSlot.MAINHAND ? 64 : 1;
		}

		@Override
		public @Nullable Identifier getNoItemIcon() {
			return this.icon;
		}
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.becario == null) {
			return true; // cliente
		}
		return this.becario.isAlive() && this.becario.isOwnedBy(player) && this.becario.distanceToSqr(player) < 64.0;
	}

	/** Mayús + clic: mueve entre el becario y el jugador. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int becarioEnd = INV_SLOTS + EQUIP_SLOTS;
		if (index < becarioEnd) {
			if (!this.moveItemStackTo(stack, becarioEnd, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// Primero a su hueco de armadura si encaja; si no, al inventario.
			boolean moved = false;
			for (int i = INV_SLOTS; i < INV_SLOTS + 4; i++) {
				Slot equip = this.slots.get(i);
				if (!equip.hasItem() && equip.mayPlace(stack)) {
					moved = this.moveItemStackTo(stack, i, i + 1, false);
					break;
				}
			}
			if (!moved && !this.moveItemStackTo(stack, 0, INV_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}
}
