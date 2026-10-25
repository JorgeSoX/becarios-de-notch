package com.jorgesox.becarios.client.screen;

import java.util.List;

import com.jorgesox.becarios.client.ClientSkins;
import com.jorgesox.becarios.entity.CombatMode;
import com.jorgesox.becarios.network.BecarioNetwork;
import com.jorgesox.becarios.task.BlockGroups;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menú del becario (clic derecho sobre él): órdenes, combate, inventario y despedir.
 * Cada botón manda una acción al servidor, que la valida igual que los comandos.
 */
public class BecarioMenuScreen extends Screen {
	private static final int PANEL_W = 300;
	private static final int PANEL_H = 244;
	private static final int[] AMOUNTS = {8, 16, 32, 64};
	private static final int[] RADII = {8, 16, 24, 32};

	/** Recordados entre aperturas (solo en este cliente). */
	private static int amountIndex = 1;
	private static int targetIndex = 0;
	private static int radiusIndex = 1;

	private final BecarioNetwork.OpenMenu data;
	private final List<String> targets = BlockGroups.names();
	private boolean confirmDismiss;
	private int left;
	private int top;

	public BecarioMenuScreen(BecarioNetwork.OpenMenu data) {
		super(Component.literal(data.name()));
		this.data = data;
	}

	@Override
	protected void init() {
		this.left = (this.width - PANEL_W) / 2;
		this.top = (this.height - PANEL_H) / 2;

		PlayerSkinWidget preview = new PlayerSkinWidget(70, 100, this.minecraft.getEntityModels(), () -> ClientSkins.get(this.data.skin()));
		preview.setX(this.left + 12);
		preview.setY(this.top + 26);
		this.addRenderableWidget(preview);

		int x = this.left + 96;
		int y = this.top + 62;
		int w = 62;
		int gap = 4;

		// Órdenes básicas.
		this.action("Sígueme", "sigue", x, y, w);
		this.action("Quieto", "quieto", x + (w + gap), y, w);
		this.action("Ven aquí", "ven", x + 2 * (w + gap), y, w);
		y += 22;
		this.action("Parar", "para", x, y, w);
		this.action("Recoger", "recoge", x + (w + gap), y, w);
		this.action("Dame todo", "dame", x + 2 * (w + gap), y, w);

		// Trabajo: cantidad, objetivo, minar / talar.
		y += 30;
		this.addRenderableWidget(Button.builder(this.amountLabel(), b -> {
			amountIndex = (amountIndex + 1) % AMOUNTS.length;
			b.setMessage(this.amountLabel());
		}).bounds(x, y, w, 20).build());
		this.addRenderableWidget(Button.builder(this.targetLabel(), b -> {
			targetIndex = (targetIndex + 1) % this.targets.size();
			b.setMessage(this.targetLabel());
		}).bounds(x + (w + gap), y, 2 * w + gap, 20).build());
		y += 22;
		this.addRenderableWidget(Button.builder(Component.literal("Minar"), b ->
			this.send("mina", this.targets.get(targetIndex), AMOUNTS[amountIndex], true)).bounds(x, y, w, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Talar"), b ->
			this.send("tala", "", AMOUNTS[amountIndex], true)).bounds(x + (w + gap), y, w, 20).build());
		Button craft = Button.builder(Component.literal("Fabricar…"), b ->
			this.minecraft.gui.setScreen(new CraftScreen(this.data))).bounds(x + 2 * (w + gap), y, w, 20).build();
		craft.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
			"Elige qué fabricar. Consigue él los materiales que le falten.")));
		this.addRenderableWidget(craft);

		// Obras: delante de ti, mirando hacia ti. Él junta los materiales.
		y += 28;
		this.work("Casa peq.", "casa", 5, "Casa pequeña (5x5) delante de ti, con la puerta hacia ti.", x, y, w);
		this.work("Casa gr.", "casa", 7, "Casa grande (7x7) delante de ti, con la puerta hacia ti.", x + (w + gap), y, w);
		this.work("Portal", "portal", 0, "Portal al Nether delante de ti. Si no tiene obsidiana, va a por ella (con pico de diamante).",
			x + 2 * (w + gap), y, w);

		// Combate: modo y radio.
		y += 30;
		this.addRenderableWidget(Button.builder(this.modeLabel(), b -> {
			CombatMode next = CombatMode.values()[(this.currentMode().ordinal() + 1) % CombatMode.values().length];
			this.send("modo", next.name(), RADII[radiusIndex], false);
		}).bounds(x, y, 2 * w + gap, 20).build());
		this.addRenderableWidget(Button.builder(this.radiusLabel(), b -> {
			radiusIndex = (radiusIndex + 1) % RADII.length;
			b.setMessage(this.radiusLabel());
		}).bounds(x + 2 * (w + gap), y, w, 20).build());

		// Abajo: inventario, despedir, cerrar.
		int by = this.top + PANEL_H - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Inventario"), b -> this.send("inventario", "", 0, true))
			.bounds(this.left + 10, by, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Despedir"), b -> {
			if (!this.confirmDismiss) {
				this.confirmDismiss = true;
				b.setMessage(Component.literal("¿Seguro? Pulsa otra vez"));
			} else {
				this.send("despedir", "", 0, true);
			}
		}).bounds(this.left + 96, by, 130, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Panel"), b -> this.send("panel", "", 0, true))
			.bounds(this.left + PANEL_W - 70, this.top + 6, 60, 16).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
			.bounds(this.left + PANEL_W - 70, by, 60, 20).build());
	}

	private void work(String label, String action, int amount, String tooltip, int x, int y, int w) {
		Button button = Button.builder(Component.literal(label), b -> this.send(action, "", amount, true)).bounds(x, y, w, 20).build();
		button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltip)));
		this.addRenderableWidget(button);
	}

	private void action(String label, String action, int x, int y, int w) {
		this.addRenderableWidget(Button.builder(Component.literal(label), b -> this.send(action, "", 0, true))
			.bounds(x, y, w, 20).build());
	}

	private void send(String action, String arg, int amount, boolean close) {
		ClientPlayNetworking.send(new BecarioNetwork.Action(this.data.id(), action, arg, amount));
		if (close) {
			this.onClose();
		}
	}

	private CombatMode currentMode() {
		try {
			return CombatMode.valueOf(this.data.mode());
		} catch (IllegalArgumentException e) {
			return CombatMode.DEFENSA;
		}
	}

	private Component amountLabel() {
		return Component.literal("Cantidad: " + AMOUNTS[amountIndex]);
	}

	private Component targetLabel() {
		return Component.literal("Objetivo: " + this.targets.get(targetIndex));
	}

	private Component modeLabel() {
		return Component.literal("Modo: " + this.currentMode().label() + " ▸");
	}

	private Component radiusLabel() {
		return Component.literal("Radio " + RADII[radiusIndex]);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		GuiStyle.panel(g, this.left, this.top, PANEL_W, PANEL_H);
		GuiStyle.slot(g, this.left + 10, this.top + 24, 74, 104);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = this.left + 96;
		g.text(this.font, this.data.name(), this.left + 10, this.top + 9, GuiStyle.TEXT, false);
		g.text(this.font, "Vida: " + Math.round(this.data.health()) + "/" + Math.round(this.data.maxHealth()),
			x, this.top + 26, this.data.health() < this.data.maxHealth() / 3 ? GuiStyle.TEXT_RED : GuiStyle.TEXT_GREEN, false);
		g.text(this.font, "Ahora: " + this.data.status(), x, this.top + 37, GuiStyle.TEXT, false);
		g.text(this.font, this.font.plainSubstrByWidth("Dónde: " + this.data.where(), 196), x, this.top + 48, 0xFF505050, false);
		g.text(this.font, "Trabajo", x, this.top + 105, 0xFF707070, false);
		g.text(this.font, "Obras", x, this.top + 157, 0xFF707070, false);
		g.text(this.font, "Combate", x, this.top + 185, 0xFF707070, false);
		g.text(this.font, "Darle algo:", this.left + 10, this.top + 134, 0xFF707070, false);
		g.text(this.font, "Mayús+clic", this.left + 10, this.top + 144, 0xFF707070, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
