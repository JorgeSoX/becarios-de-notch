package com.jorgesox.becarios.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.jorgesox.becarios.client.ClientSkins;
import com.jorgesox.becarios.entity.Area;
import com.jorgesox.becarios.network.BecarioNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Panel de gestión de becarios (tecla B o bloque Oficina): todo sin comandos.
 *
 * Izquierda: tu plantilla (hasta 5) y el botón de contratar con su precio.
 * Derecha: el becario elegido, con su skin, qué hace, dónde está, su cargo y
 * botones para darle órdenes, ver su inventario, darte su brújula, nombrarlo
 * jefe, meterlo en un equipo o despedirlo.
 * Abajo: las peticiones de personal de tus jefes, con Aceptar / Rechazar.
 */
public class PanelScreen extends Screen {
	private static final int PANEL_W = 380;
	private static final int PANEL_H = 268;
	/** Las peticiones van en su propia franja, debajo de todo lo demás. */
	private static final int REQUESTS_Y = 222;
	private static final int ROW_H = 24;
	private static final int LIST_W = 128;

	private final BecarioNetwork.OpenPanel data;
	private int selectedIndex;
	private boolean confirmDismiss;
	private int left;
	private int top;

	public PanelScreen(BecarioNetwork.OpenPanel data) {
		super(Component.literal("Becarios"));
		this.data = data;
		this.selectedIndex = 0;
		for (int i = 0; i < data.entries().size(); i++) {
			if (data.entries().get(i).selected()) {
				this.selectedIndex = i;
			}
		}
	}

	private BecarioNetwork.PanelEntry selected() {
		return this.data.entries().isEmpty() ? null : this.data.entries().get(Math.min(this.selectedIndex, this.data.entries().size() - 1));
	}

	@Override
	protected void init() {
		this.left = (this.width - PANEL_W) / 2;
		this.top = (this.height - PANEL_H) / 2;
		this.confirmDismiss = false;

		// --- Lista de becarios (clic = elegir).
		List<BecarioNetwork.PanelEntry> entries = this.data.entries();
		for (int i = 0; i < entries.size(); i++) {
			final int index = i;
			BecarioNetwork.PanelEntry entry = entries.get(i);
			String prefix = entry.area().isEmpty() ? (entry.bossId().isEmpty() ? "" : "  · ") : "★ ";
			Button row = Button.builder(Component.literal(prefix + entry.name()), b -> {
				this.selectedIndex = index;
				this.send(entry.id(), "elegir", "", 0);
				this.rebuildWidgets();
			}).bounds(this.left + 8, this.top + 24 + i * ROW_H, LIST_W, 20).build();
			row.active = index != this.selectedIndex;
			this.addRenderableWidget(row);
		}

		// --- Contratar (con su precio).
		boolean full = entries.size() >= this.data.max();
		boolean affordable = this.data.free() || this.data.emeralds() >= this.data.nextCost();
		String hireLabel = full ? "Plantilla completa"
			: "Contratar (" + (this.data.free() ? "gratis" : this.data.nextCost() + " esm.") + ")";
		Button hire = Button.builder(Component.literal(hireLabel), b -> this.send("", "contratar", "", 0))
			.bounds(this.left + 8, this.top + 24 + 5 * ROW_H + 4, LIST_W, 20).build();
		hire.active = !full && affordable;
		this.addRenderableWidget(hire);

		// --- Detalle del elegido.
		BecarioNetwork.PanelEntry entry = this.selected();
		int x = this.left + LIST_W + 18;
		if (entry != null) {
			PlayerSkinWidget preview = new PlayerSkinWidget(58, 88, this.minecraft.getEntityModels(), () -> ClientSkins.get(entry.skin()));
			preview.setX(x);
			preview.setY(this.top + 24);
			this.addRenderableWidget(preview);

			int bx = x;
			int by = this.top + 120;
			int w = 72;
			int gap = 4;
			Button orders = Button.builder(Component.literal("Órdenes…"), b -> this.send(entry.id(), "menu", "", 0))
				.bounds(bx, by, w, 20).build();
			orders.active = entry.loaded();
			this.addRenderableWidget(orders);
			Button inv = Button.builder(Component.literal("Inventario"), b -> this.send(entry.id(), "inventario", "", 0))
				.bounds(bx + (w + gap), by, w, 20).build();
			inv.active = entry.loaded();
			this.addRenderableWidget(inv);
			Button compass = Button.builder(Component.literal("Brújula"), b -> this.send(entry.id(), "brujula", "", 0))
				.bounds(bx + 2 * (w + gap), by, w, 20).build();
			compass.active = entry.loaded();
			this.addRenderableWidget(compass);

			by += 24;
			// Cargo: nada -> jefe de cada área -> nada.
			this.addRenderableWidget(Button.builder(this.roleLabel(entry), b -> {
				this.send(entry.id(), "jefe", this.nextArea(entry.area()), 0);
				this.send("", "panel", "", 0);
			}).bounds(bx, by, w * 3 + gap * 2, 20).build());

			by += 24;
			// Equipo: ninguno -> cada jefe -> ninguno (los jefes no van en equipos).
			Button team = Button.builder(this.teamLabel(entry), b -> {
				this.send(entry.id(), "asignar", this.nextBoss(entry), 0);
				this.send("", "panel", "", 0);
			}).bounds(bx, by, w * 3 + gap * 2, 20).build();
			team.active = entry.area().isEmpty() && !this.bosses(entry).isEmpty();
			this.addRenderableWidget(team);

			by += 24;
			this.addRenderableWidget(Button.builder(Component.literal("Despedir"), b -> {
				if (!this.confirmDismiss) {
					this.confirmDismiss = true;
					b.setMessage(Component.literal("¿Seguro? Pulsa otra vez"));
				} else {
					this.send(entry.id(), "despedir", "", 0);
					this.send("", "panel", "", 0);
				}
			}).bounds(bx, by, w * 2 + gap, 20).build());
		}

		// --- Peticiones de los jefes.
		for (int i = 0; i < Math.min(1, this.data.requests().size()); i++) {
			BecarioNetwork.PanelRequest request = this.data.requests().get(i);
			int yy = this.top + REQUESTS_Y + i * 22;
			Button accept = Button.builder(Component.literal("Aceptar"), b -> {
				this.send("", "peticion", Integer.toString(request.id()), 1);
				this.send("", "panel", "", 0);
			}).bounds(this.left + PANEL_W - 128, yy, 58, 20).build();
			accept.active = this.data.free() || this.data.emeralds() >= request.cost();
			this.addRenderableWidget(accept);
			this.addRenderableWidget(Button.builder(Component.literal("Rechazar"), b -> {
				this.send("", "peticion", Integer.toString(request.id()), 0);
				this.send("", "panel", "", 0);
			}).bounds(this.left + PANEL_W - 66, yy, 58, 20).build());
		}

		this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
			.bounds(this.left + PANEL_W - 66, this.top + PANEL_H - 24, 58, 20).build());
	}

	private Component roleLabel(BecarioNetwork.PanelEntry entry) {
		Area area = Area.of(entry.area());
		return Component.literal("Cargo: " + (area == null ? "ninguno" : "jefe de " + area.label().toLowerCase()) + " ▸");
	}

	private String nextArea(String current) {
		Area area = Area.of(current);
		if (area == null) {
			return Area.values()[0].name();
		}
		int next = area.ordinal() + 1;
		return next < Area.values().length ? Area.values()[next].name() : "";
	}

	/** Los jefes a los que puede ir este becario. */
	private List<BecarioNetwork.PanelEntry> bosses(BecarioNetwork.PanelEntry entry) {
		List<BecarioNetwork.PanelEntry> out = new ArrayList<>();
		for (BecarioNetwork.PanelEntry other : this.data.entries()) {
			if (!other.area().isEmpty() && !other.id().equals(entry.id())) {
				out.add(other);
			}
		}
		return out;
	}

	private Component teamLabel(BecarioNetwork.PanelEntry entry) {
		if (!entry.area().isEmpty()) {
			return Component.literal("Equipo: dirige a " + entry.teamSize());
		}
		return Component.literal("Equipo: " + (entry.bossId().isEmpty() ? "ninguno (te sigue a ti)" : "de " + entry.bossName()) + " ▸");
	}

	private String nextBoss(BecarioNetwork.PanelEntry entry) {
		List<BecarioNetwork.PanelEntry> bosses = this.bosses(entry);
		if (bosses.isEmpty()) {
			return "";
		}
		if (entry.bossId().isEmpty()) {
			return bosses.get(0).id();
		}
		for (int i = 0; i < bosses.size(); i++) {
			if (bosses.get(i).id().equals(entry.bossId())) {
				return i + 1 < bosses.size() ? bosses.get(i + 1).id() : "";
			}
		}
		return "";
	}

	private void send(String target, String action, String arg, int amount) {
		ClientPlayNetworking.send(new BecarioNetwork.Action(target, action, arg, amount));
		if (action.equals("menu") || action.equals("inventario") || action.equals("contratar")) {
			this.onClose(); // el servidor abre otra pantalla
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		GuiStyle.panel(g, this.left, this.top, PANEL_W, PANEL_H);
		if (this.selected() != null) {
			GuiStyle.slot(g, this.left + LIST_W + 16, this.top + 22, 62, 92);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(this.font, "Tus becarios (" + this.data.entries().size() + "/" + this.data.max() + ")",
			this.left + 10, this.top + 9, GuiStyle.TEXT, false);
		String money = this.data.free() ? "Creativo: contratar es gratis" : "Llevas " + this.data.emeralds() + " esmeraldas";
		g.text(this.font, money, this.left + PANEL_W - 10 - this.font.width(money), this.top + 9, GuiStyle.TEXT, false);

		BecarioNetwork.PanelEntry entry = this.selected();
		int x = this.left + LIST_W + 84;
		int width = PANEL_W - (LIST_W + 84) - 10;
		if (entry == null) {
			g.text(this.font, "Aún no tienes becarios.", this.left + LIST_W + 20, this.top + 30, GuiStyle.TEXT, false);
			g.text(this.font, "Pulsa \"Contratar\" para el primero.", this.left + LIST_W + 20, this.top + 42, GuiStyle.TEXT, false);
		} else {
			g.text(this.font, entry.name(), x, this.top + 26, GuiStyle.TEXT, false);
			if (entry.loaded()) {
				g.text(this.font, "Vida: " + Math.round(entry.health()) + "/" + Math.round(entry.maxHealth()), x, this.top + 38,
					entry.health() < entry.maxHealth() / 3 ? GuiStyle.TEXT_RED : GuiStyle.TEXT_GREEN, false);
			}
			int y = this.top + 50;
			for (var line : this.font.split(Component.literal("Ahora: " + entry.status()), width)) {
				g.text(this.font, line, x, y, GuiStyle.TEXT, false);
				y += 10;
			}
			for (var line : this.font.split(Component.literal("Dónde: " + entry.where()), width)) {
				g.text(this.font, line, x, y, 0xFF505050, false);
				y += 10;
			}
			String role = !entry.area().isEmpty()
				? "Jefe (" + entry.teamSize() + " en su equipo)"
				: entry.bossId().isEmpty() ? "Sin equipo" : "En el equipo de " + entry.bossName();
			g.text(this.font, role, x, y + 2, 0xFF707070, false);
		}

		// Peticiones (de una en una; si hay más, salen al resolver esta).
		int n = Math.min(1, this.data.requests().size());
		for (int i = 0; i < n; i++) {
			BecarioNetwork.PanelRequest request = this.data.requests().get(i);
			String text = request.bossName() + " " + request.text()
				+ (this.data.free() ? "" : " (" + request.cost() + " esm.)");
			int y = this.top + REQUESTS_Y + i * 22;
			GuiStyle.slot(g, this.left + 8, y - 2, PANEL_W - 146, 24);
			int ty = y + 1;
			for (var line : this.font.split(Component.literal(text), PANEL_W - 154)) {
				g.text(this.font, line, this.left + 12, ty, GuiStyle.TEXT, false);
				ty += 10;
			}
		}
		String hint = this.data.requests().size() > 1 ? "+" + (this.data.requests().size() - 1) + " peticiones más" : "Tecla B: abrir este panel en cualquier sitio.";
		g.text(this.font, hint, this.left + 10, this.top + PANEL_H - 18, 0xFF707070, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
