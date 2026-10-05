package com.dolphin_kun.villager_gacha.mixin;

import com.dolphin_kun.villager_gacha.logic.TradeWatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleMerchantOffers", at = @At("TAIL"))
	private void villager_gacha$recordOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
		TradeWatcher.onOffers(packet.getContainerId(), packet.getOffers());
	}
}
