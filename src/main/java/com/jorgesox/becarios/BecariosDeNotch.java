package com.jorgesox.becarios;

import com.jorgesox.becarios.command.BecarioCommands;
import com.jorgesox.becarios.entity.ModEntities;
import com.jorgesox.becarios.menu.ModMenus;
import com.jorgesox.becarios.network.BecarioNetwork;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BecariosDeNotch implements ModInitializer {
	public static final String MOD_ID = "becarios-de-notch";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ModEntities.register();
		ModMenus.register();
		BecarioNetwork.register();
		BecarioCommands.register();

		LOGGER.info("Becarios de Notch cargado.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
