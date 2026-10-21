package com.jorgesox.becarios.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jorgesox.becarios.BecariosDeNotch;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuración del cerebro (LLM). Se guarda en config/becarios-de-notch.json
 * y se crea con valores por defecto la primera vez. Así la URL de Ollama se
 * cambia sin recompilar (PC de desarrollo -> servidor Ubuntu).
 */
public final class BecariosConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static BecariosConfig instance = new BecariosConfig();

	/** Base de un servidor compatible con OpenAI (Ollama, llama-server...). */
	public String llmBaseUrl = "http://localhost:11434";
	public String llmModel = "qwen2.5:7b";
	public int timeoutSeconds = 60;
	/** Segundos mínimos entre dos órdenes de texto al mismo becario. */
	public int cooldownSeconds = 2;

	public static BecariosConfig get() {
		return instance;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("becarios-de-notch.json");
		try {
			if (Files.exists(file)) {
				BecariosConfig loaded = GSON.fromJson(Files.readString(file), BecariosConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} else {
				Files.writeString(file, GSON.toJson(instance));
			}
		} catch (IOException | RuntimeException e) {
			BecariosDeNotch.LOGGER.warn("No se pudo leer la config de Becarios, uso valores por defecto", e);
		}
	}
}
