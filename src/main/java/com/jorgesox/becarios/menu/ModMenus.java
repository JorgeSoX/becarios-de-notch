package com.jorgesox.becarios.menu;

import com.jorgesox.becarios.BecariosDeNotch;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Registro del tipo de menú (pantalla con huecos) del inventario del becario. */
public final class ModMenus {
	public static final MenuType<BecarioInventoryMenu> BECARIO_INVENTORY = Registry.register(
		BuiltInRegistries.MENU, BecariosDeNotch.id("becario_inventory"),
		new MenuType<>(BecarioInventoryMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	/** Fuerza la carga de la clase (y por tanto el registro). */
	public static void register() {
	}
}
