package com.dolphin_kun.villager_gacha.logic;

import com.dolphin_kun.villager_gacha.VillagerGacha;
import com.dolphin_kun.villager_gacha.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Texts {
	private Texts() {
	}

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatable(VillagerGacha.MOD_ID + "." + key, args);
	}

	public static void announce(Minecraft mc, Component message) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(tr("chat.prefix").append(message));
		}
	}

	public static void notify(Minecraft mc, Component message) {
		if (mc.player == null) {
			return;
		}
		mc.gui.hud.setOverlayMessage(tr("chat.prefix").append(message), false);
		if (ModConfig.get().chatEverything) {
			mc.player.sendSystemMessage(tr("chat.prefix").append(message));
		}
	}

	public static void detail(Minecraft mc, Component message) {
		if (mc.player != null && ModConfig.get().chatEverything) {
			mc.player.sendSystemMessage(tr("chat.prefix").append(message));
		}
	}
}
