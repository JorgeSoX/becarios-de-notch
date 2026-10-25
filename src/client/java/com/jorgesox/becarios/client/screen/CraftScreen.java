package com.jorgesox.becarios.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.jorgesox.becarios.network.BecarioNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Fabricar": eliges el objeto con un clic (sin escribir comandos) y la cantidad.
 * El becario consigue él solo los materiales que le falten y lo fabrica.
 * Si es jefe de fabricación, reparte el encargo entre su equipo.
 */
public class CraftScreen extends Screen {
	private static final int COLS = 9;
	private static final int CELL = 22;
	private static final int PANEL_W = COLS * CELL + 20;
	private static final int[] AMOUNTS = {1, 2, 4, 8, 16, 32, 64};
	/** Lo más útil que un becario sabe conseguir y fabricar por su cuenta. */
	private static final Item[] ITEMS = {
		Items.CRAFTING_TABLE, Items.FURNACE, Items.CHEST, Items.TORCH, Items.STICK, Items.LADDER, Items.OAK_DOOR, Items.OAK_FENCE, Items.BARREL,
		Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD, Items.WOODEN_HOE, Items.BOWL, Items.SHIELD, Items.BUCKET, Items.FLINT_AND_STEEL,
		Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_HOE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.CHARCOAL, Items.SMOOTH_STONE,
		Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_SWORD, Items.IRON_HOE, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
		Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.DIAMOND_HOE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
		Items.STONE_BRICKS, Items.GLASS, Items.COBBLESTONE_STAIRS, Items.COBBLESTONE_SLAB, Items.STONE_BUTTON, Items.LEVER, Items.RAIL, Items.MINECART, Items.CAMPFIRE,
	};

	/** Recordada entre aperturas (solo en este cliente). */
	private static int amountIndex = 0;

	private final BecarioNetwork.OpenMenu data;
	private final List<Slot> slots = new ArrayList<>();
	private int left;
	private int top;
	private int panelH;

	private record Slot(ItemStack stack, int x, int y) {
	}

	public CraftScreen(BecarioNetwork.OpenMenu data) {
		super(Component.literal("Fabricar"));
		this.data = data;
	}

	@Override
	protected void init() {
		int rows = (ITEMS.length + COLS - 1) / COLS;
		this.panelH = 30 + rows * CELL + 36;
		this.left = (this.width - PANEL_W) / 2;
		this.top = (this.height - this.panelH) / 2;
		this.slots.clear();
		for (int i = 0; i < ITEMS.length; i++) {
			ItemStack stack = new ItemStack(ITEMS[i]);
			int x = this.left + 10 + (i % COLS) * CELL;
			int y = this.top + 24 + (i / COLS) * CELL;
			this.slots.add(new Slot(stack, x, y));
			Button button = Button.builder(Component.empty(), b -> this.order(stack.getItem())).bounds(x, y, CELL - 2, CELL - 2).build();
			button.setTooltip(Tooltip.create(stack.getHoverName()));
			this.addRenderableWidget(button);
		}
		int by = this.top + this.panelH - 28;
		this.addRenderableWidget(Button.builder(this.amountLabel(), b -> {
			amountIndex = (amountIndex + 1) % AMOUNTS.length;
			b.setMessage(this.amountLabel());
		}).bounds(this.left + 10, by, 80, 20).tooltip(Tooltip.create(Component.literal(
			"Pulsa un objeto y lo fabrica. Si le faltan materiales, va a por ellos."))).build());
		this.addRenderableWidget(Button.builder(Component.literal("Volver"), b ->
			ClientPlayNetworking.send(new BecarioNetwork.Action(this.data.id(), "menu", "", 0)))
			.bounds(this.left + PANEL_W - 130, by, 58, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
			.bounds(this.left + PANEL_W - 68, by, 58, 20).build());
	}

	private void order(Item item) {
		ClientPlayNetworking.send(new BecarioNetwork.Action(this.data.id(), "fabrica",
			BuiltInRegistries.ITEM.getKey(item).toString(), AMOUNTS[amountIndex]));
		this.onClose();
	}

	private Component amountLabel() {
		return Component.literal("Cantidad: " + AMOUNTS[amountIndex]);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		GuiStyle.panel(g, this.left, this.top, PANEL_W, this.panelH);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(this.font, "Fabricar (" + this.data.name() + ")", this.left + 10, this.top + 9, GuiStyle.TEXT, false);
		for (Slot slot : this.slots) {
			g.item(slot.stack(), slot.x() + 2, slot.y() + 2);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
