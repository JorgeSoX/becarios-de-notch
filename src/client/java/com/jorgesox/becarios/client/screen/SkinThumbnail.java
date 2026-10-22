package com.jorgesox.becarios.client.screen;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.jorgesox.becarios.BecarioSkins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.network.chat.Component;

/**
 * Miniatura de una skin ya montada en el muñeco (vista 3D fija, 3/4 de frente).
 * Al hacer clic la selecciona.
 */
public class SkinThumbnail extends AbstractWidget {
	private final Model.Simple model;
	private final Supplier<String> skinId;
	private final Supplier<String> selected;
	private final Consumer<String> onSelect;

	public SkinThumbnail(int x, int y, int w, int h, Model.Simple model,
		Supplier<String> skinId, Supplier<String> selected, Consumer<String> onSelect) {
		super(x, y, w, h, Component.empty());
		this.model = model;
		this.skinId = skinId;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		String id = this.skinId.get();
		if (id == null) {
			return;
		}
		if (id.equals(this.selected.get())) {
			GuiStyle.selectedSlot(g, getX(), getY(), getWidth(), getHeight());
		} else {
			GuiStyle.slot(g, getX(), getY(), getWidth(), getHeight());
		}
		if (this.isHovered()) {
			g.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, 0x40FFFFFF);
		}
		float scale = 0.9F * (getHeight() - 4) / 2.125F;
		g.skin(this.model, BecarioSkins.texture(id), scale, -5.0F, 30.0F, -1.0625F,
			getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2);
		if (this.isHovered()) {
			g.setTooltipForNextFrame(Component.literal(BecarioSkins.displayName(id)), mouseX, mouseY);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		String id = this.skinId.get();
		if (id != null) {
			this.onSelect.accept(id);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
