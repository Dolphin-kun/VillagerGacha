package com.dolphin_kun.villager_gacha;

import com.dolphin_kun.villager_gacha.config.ModConfig;
import com.dolphin_kun.villager_gacha.logic.GachaRunner;
import com.dolphin_kun.villager_gacha.logic.Texts;
import com.dolphin_kun.villager_gacha.ui.StatusHud;
import com.dolphin_kun.villager_gacha.ui.TargetScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillagerGacha implements ClientModInitializer {
	public static final String MOD_ID = "villager_gacha";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final GachaRunner RUNNER = new GachaRunner();

	public static KeyMapping toggleKey;
	public static KeyMapping emergencyKey;
	public static KeyMapping setSpotKey;
	public static KeyMapping openMenuKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		ModConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		toggleKey = register("toggle", InputConstants.KEY_J, category);
		emergencyKey = register("emergency_stop", InputConstants.KEY_K, category);
		setSpotKey = register("set_spot", InputConstants.KEY_R, category);
		openMenuKey = register("open_menu", InputConstants.KEY_N, category);

		ClientTickEvents.END_CLIENT_TICK.register(VillagerGacha::onClientTick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("status"), StatusHud::draw);
	}

	private static KeyMapping register(String name, int key, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key." + MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, category)
		);
	}

	private static void onClientTick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		while (toggleKey.consumeClick()) {
			RUNNER.toggle(mc);
		}
		while (emergencyKey.consumeClick()) {
			RUNNER.emergencyStop(mc);
		}
		while (setSpotKey.consumeClick()) {
			setSpotFromCrosshair(mc);
		}
		while (openMenuKey.consumeClick()) {
			mc.gui.setScreen(new TargetScreen(null));
		}
		RUNNER.tick(mc);
	}

	public static boolean setSpotFromCrosshair(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return false;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			Texts.notify(mc, Texts.tr("msg.aim_at_block"));
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		if (!mc.level.getBlockState(pos).is(Blocks.LECTERN)) {
			pos = pos.relative(hit.getDirection());
		}
		ModConfig.get().lectern = new ModConfig.Spot(pos);
		ModConfig.save();
		Texts.notify(mc, Texts.tr("msg.spot_set", pos.getX(), pos.getY(), pos.getZ()));
		return true;
	}
}
