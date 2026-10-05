package com.dolphin_kun.villager_gacha.mixin;

import com.dolphin_kun.villager_gacha.VillagerGacha;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void villager_gacha$hideTradeScreen(Screen screen, CallbackInfo ci) {
		if (screen instanceof MerchantScreen && VillagerGacha.RUNNER.hidesTradeScreen()) {
			ci.cancel();
		}
	}
}
