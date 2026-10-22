package com.jorgesox.becarios.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jorgesox.becarios.BecariosDeNotch;
import com.jorgesox.becarios.entity.BecarioEntity;
import com.jorgesox.becarios.task.BecarioTask;
import com.jorgesox.becarios.task.BlockGroups;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cerebro del becario: el jugador escribe en lenguaje natural, el LLM elige UNA
 * acción de una lista cerrada (JSON) y este código la ejecuta de forma
 * determinista. El modelo nunca ejecuta nada por su cuenta: si responde algo
 * fuera de la lista o con parámetros raros, se descarta.
 */
public final class BecarioBrain {
	private static final String SYSTEM_PROMPT = """
		Eres el cerebro de un becario (compañero) en Minecraft. Hablas en español, \
		con tono breve y algo de humor de becario. Recibes el mensaje de tu jefe (el jugador) \
		y el estado actual, y debes elegir UNA acción.

		Responde SOLO con un objeto JSON con estas claves:
		  "action": una de: "follow", "stay", "come", "mine", "chop", "collect", "stop", "give", "none"
		  "target": solo para "mine"; uno de: %s
		  "amount": entero 1-64, para "mine" y "chop" (si no lo dicen, usa 8)
		  "say": frase corta (máx. 120 caracteres) que dices a tu jefe

		Significado: follow = seguir al jefe; stay = quedarte quieto; come = ir con el jefe ahora; \
		mine = minar bloques del tipo "target"; chop = talar árboles (madera); collect = recoger objetos del suelo; \
		stop = dejar el trabajo actual; give = entregar al jefe lo que llevas; none = solo hablar \
		(saludos, preguntas, o si no puedes hacer lo pedido).
		Si piden algo que no está en la lista (construir, luchar...), usa "none" y explícalo en "say".
		""".formatted(String.join(", ", BlockGroups.names()));

	/** Becarios con una petición en curso (para no encolar varias a la vez). */
	private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

	private BecarioBrain() {
	}

	public static void handle(ServerPlayer player, BecarioEntity becario, String text) {
		UUID id = becario.getUUID();
		long now = System.currentTimeMillis();
		long cooldown = BecariosConfig.get().cooldownSeconds * 1000L;

		if (IN_FLIGHT.containsKey(id)) {
			becario.tell("espera, que aún estoy pensando...");
			return;
		}
		Long last = LAST_REQUEST.get(id);
		if (last != null && now - last < cooldown) {
			becario.tell("un momento, jefe.");
			return;
		}
		IN_FLIGHT.put(id, true);
		LAST_REQUEST.put(id, now);

		MinecraftServer server = player.level().getServer();
		String userPrompt = "Estado actual: " + describeState(becario)
			+ "\nInventario: " + becario.inventorySummary()
			+ "\nMensaje del jefe: " + text;

		LlmClient.chatJson(SYSTEM_PROMPT, userPrompt).whenComplete((reply, error) ->
			// Volvemos al hilo del servidor antes de tocar el mundo.
			server.execute(() -> {
				IN_FLIGHT.remove(id);
				if (becario.isRemoved()) {
					return;
				}
				if (error != null) {
					BecariosDeNotch.LOGGER.warn("Fallo al consultar el LLM: {}", String.valueOf(error));
					becario.tell("no consigo pensar ahora mismo (¿está encendido Ollama?).");
					return;
				}
				apply(player, becario, reply);
			}));
	}

	private static String describeState(BecarioEntity becario) {
		BecarioTask task = becario.getTask();
		if (task != null) {
			return task.describe();
		}
		return becario.isOrderedToSit() ? "quieto" : "siguiendo al jefe";
	}

	/** Valida la respuesta del modelo y ejecuta la acción. Cualquier cosa rara se descarta. */
	private static void apply(ServerPlayer player, BecarioEntity becario, String reply) {
		JsonObject json;
		try {
			json = JsonParser.parseString(reply).getAsJsonObject();
		} catch (RuntimeException e) {
			BecariosDeNotch.LOGGER.warn("Respuesta del LLM no válida: {}", reply);
			becario.tell("me he liado, ¿puedes repetirlo?");
			return;
		}

		String action = str(json, "action", "none").toLowerCase();
		String say = str(json, "say", "");
		if (say.length() > 160) {
			say = say.substring(0, 160);
		}
		int amount = Math.max(1, Math.min(64, intOf(json, "amount", 8)));

		boolean ok = true;
		switch (action) {
			case "follow" -> {
				becario.cancelTask();
				becario.setOrderedToSit(false);
			}
			case "stay" -> {
				becario.cancelTask();
				becario.setOrderedToSit(true);
			}
			case "come" -> {
				becario.cancelTask();
				becario.setOrderedToSit(false);
				becario.setComing(true);
			}
			case "chop" -> becario.startTask(taskFor("madera", amount));
			case "mine" -> {
				BecarioTask task = taskFor(str(json, "target", "").toLowerCase(), amount);
				if (task == null) {
					ok = false;
					say = "no sé minar eso. Sé minar: " + String.join(", ", BlockGroups.names()) + ".";
				} else {
					becario.startTask(task);
				}
			}
			case "collect" -> becario.startTask(BecarioTask.collect());
			case "stop" -> becario.cancelTask();
			case "give" -> becario.giveAllTo(player);
			case "none" -> { }
			default -> {
				ok = false;
				say = "no te he entendido.";
			}
		}
		BecariosDeNotch.LOGGER.info("[becario {}] accion={} ok={}", becario.getName().getString(), action, ok);
		if (!say.isBlank()) {
			becario.tell(say);
		}
	}

	private static BecarioTask taskFor(String target, int amount) {
		BlockGroups.Group group = BlockGroups.get(target);
		return group == null ? null : BecarioTask.mine(group.label(), group.test(), amount);
	}

	private static String str(JsonObject json, String key, String fallback) {
		JsonElement e = json.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	private static int intOf(JsonObject json, String key, int fallback) {
		try {
			JsonElement e = json.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
		} catch (RuntimeException ex) {
			return fallback;
		}
	}
}
