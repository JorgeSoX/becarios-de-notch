package com.jorgesox.becarios.client.screen;

import java.util.List;

import com.jorgesox.becarios.BecarioSkins;
import com.jorgesox.becarios.client.ClientSkins;
import com.jorgesox.becarios.network.BecarioNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;

/**
 * Pantalla para contratar un becario: nombre + skin.
 *
 * A la izquierda, la skin elegida en grande (arrastra con el ratón para girarla 360°).
 * A la derecha, la rejilla con todas las skins del catálogo ya montadas en el muñeco.
 */
public class HireScreen extends Screen {
	private static final int PANEL_W = 320;
	private static final int PANEL_H = 248;
	private static final int COLS = 6;
	private static final int ROWS = 3;
	private static final int THUMB_W = 28;
	private static final int THUMB_H = 40;
	private static final int PAGE_SIZE = COLS * ROWS;

	private final List<BecarioSkins.Skin> skins = BecarioSkins.ALL;
	private String selected = BecarioSkins.DEFAULT;
	private int page;
	private EditBox nameBox;
	private Button hireButton;
	private Button prevButton;
	private Button nextButton;
	private int left;
	private int top;

	public HireScreen() {
		super(Component.literal("Contratar becario"));
	}

	@Override
	protected void init() {
		this.left = (this.width - PANEL_W) / 2;
		this.top = (this.height - PANEL_H) / 2;

		// Vista grande, girable arrastrando.
		PlayerSkinWidget preview = new PlayerSkinWidget(90, 130, this.minecraft.getEntityModels(), () -> ClientSkins.get(this.selected));
		preview.setX(this.left + 10);
		preview.setY(this.top + 26);
		this.addRenderableWidget(preview);

		// Nombre opcional (máx. 32): si se deja vacío, se llama como la skin.
		int rightX = this.left + 112;
		String previousName = this.nameBox != null ? this.nameBox.getValue() : "";
		this.nameBox = new EditBox(this.font, rightX, this.top + 26, 198, 18, Component.literal("Nombre"));
		this.nameBox.setMaxLength(32);
		this.nameBox.setHint(Component.literal("Nombre (opcional: si no, el de la skin)"));
		this.nameBox.setValue(previousName);
		this.nameBox.setResponder(text -> this.updateButtons());
		this.addRenderableWidget(this.nameBox);

		// Rejilla de miniaturas (paginada).
		Model.Simple model = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
		for (int i = 0; i < PAGE_SIZE; i++) {
			final int slot = i;
			int x = rightX + (i % COLS) * (THUMB_W + 6);
			int y = this.top + 64 + (i / COLS) * (THUMB_H + 4);
			this.addRenderableWidget(new SkinThumbnail(x, y, THUMB_W, THUMB_H, model,
				() -> this.skinAt(slot), () -> this.selected, id -> this.selected = id));
		}

		int pagesY = this.top + 64 + ROWS * (THUMB_H + 4);
		this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.changePage(-1))
			.bounds(rightX, pagesY, 20, 16).build());
		this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.changePage(1))
			.bounds(rightX + 178, pagesY, 20, 16).build());

		this.hireButton = this.addRenderableWidget(Button.builder(Component.literal("Contratar"), b -> this.hire())
			.bounds(this.left + PANEL_W - 170, this.top + PANEL_H - 26, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> this.onClose())
			.bounds(this.left + PANEL_W - 86, this.top + PANEL_H - 26, 76, 20).build());

		this.setInitialFocus(this.nameBox);
		this.updateButtons();
	}

	private String skinAt(int slotInPage) {
		int index = this.page * PAGE_SIZE + slotInPage;
		return index < this.skins.size() ? this.skins.get(index).id() : null;
	}

	private int pageCount() {
		return Math.max(1, (this.skins.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void changePage(int delta) {
		this.page = Math.floorMod(this.page + delta, this.pageCount());
		this.updateButtons();
	}

	private void updateButtons() {
		boolean paged = this.pageCount() > 1;
		if (this.prevButton != null) {
			this.prevButton.visible = paged;
			this.nextButton.visible = paged;
		}
	}

	private void hire() {
		String name = this.nameBox.getValue().trim();
		ClientPlayNetworking.send(new BecarioNetwork.Hire(name, this.selected));
		this.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		GuiStyle.panel(g, this.left, this.top, PANEL_W, PANEL_H);
		GuiStyle.slot(g, this.left + 8, this.top + 24, 94, 134);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(this.font, this.title, this.left + 10, this.top + 9, GuiStyle.TEXT, false);
		String shownName = this.nameBox.getValue().trim().isEmpty() ? BecarioSkins.displayName(this.selected) : this.nameBox.getValue().trim();
		g.text(this.font, "Se llamará: " + shownName, this.left + 112, this.top + 50, GuiStyle.TEXT, false);
		g.text(this.font, "Arrastra: girar", this.left + 13, this.top + 162, 0xFF707070, false);
		if (this.pageCount() > 1) {
			String label = "Página " + (this.page + 1) + "/" + this.pageCount();
			int pagesY = this.top + 64 + ROWS * (THUMB_H + 4);
			g.centeredText(this.font, label, this.left + 112 + 99, pagesY + 4, GuiStyle.TEXT);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
