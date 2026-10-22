package com.jorgesox.becarios.client.screen;

import com.jorgesox.becarios.menu.BecarioInventoryMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Inventario del becario con la textura del cofre de Minecraft, y a la
 * izquierda una columna con su armadura y lo que lleva en la mano.
 */
public class BecarioInventoryScreen extends AbstractContainerScreen<BecarioInventoryMenu> {
	private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int ROWS = 3;
	private static final int OX = BecarioInventoryMenu.OFFSET_X;

	public BecarioInventoryScreen(BecarioInventoryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176 + OX, 114 + ROWS * 18);
		this.titleLabelX = OX + 8;
		this.inventoryLabelX = OX + 8;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		// Columna de equipo (casco, peto, pantalones, botas, mano derecha, mano izquierda).
		GuiStyle.panel(g, x - 2, y + 10, OX + 6, BecarioInventoryMenu.EQUIP_SLOTS * 18 + 14);
		for (int i = 0; i < BecarioInventoryMenu.EQUIP_SLOTS; i++) {
			GuiStyle.slot(g, x + 3, y + 17 + i * 18, 18, 18);
		}
		// Cofre de 3 filas + inventario del jugador (misma textura que el cofre vanilla).
		g.blit(RenderPipelines.GUI_TEXTURED, CHEST, x + OX, y, 0.0F, 0.0F, 176, ROWS * 18 + 17, 256, 256);
		g.blit(RenderPipelines.GUI_TEXTURED, CHEST, x + OX, y + ROWS * 18 + 17, 0.0F, 126.0F, 176, 96, 256, 256);
	}
}
