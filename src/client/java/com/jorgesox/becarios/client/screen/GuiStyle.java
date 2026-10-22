package com.jorgesox.becarios.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dibujo de paneles y huecos con la estética del inventario de Minecraft
 * (gris claro con bordes en relieve), sin depender de texturas propias.
 */
public final class GuiStyle {
	public static final int TEXT = 0xFF404040;
	public static final int TEXT_LIGHT = 0xFFFFFFFF;
	public static final int TEXT_GREEN = 0xFF2E7D32;
	public static final int TEXT_RED = 0xFFB71C1C;

	private static final int BLACK = 0xFF000000;
	private static final int BODY = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int DARK = 0xFF555555;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_BODY = 0xFF8B8B8B;

	private GuiStyle() {
	}

	/** Panel como el fondo del inventario. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 1, y, x + w - 1, y + h, BLACK);
		g.fill(x, y + 1, x + w, y + h - 1, BLACK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BODY);
		g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
		g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
		g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, DARK);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, DARK);
		g.fill(x + 3, y + 3, x + w - 3, y + h - 3, BODY);
	}

	/** Hueco hundido (como el de un objeto). */
	public static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, SLOT_DARK);
		g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BODY);
	}

	/** Hueco resaltado (seleccionado). */
	public static void selectedSlot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
		g.fill(x, y, x + w, y + h, 0xFF5A8F3C);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFA5D6A7);
	}
}
