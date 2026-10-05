package com.dolphin_kun.villager_gacha.logic;

import net.minecraft.world.item.trading.MerchantOffers;

public final class TradeWatcher {
	private static int received;
	private static int containerId = -1;
	private static MerchantOffers offers = new MerchantOffers();

	private TradeWatcher() {
	}

	public static void onOffers(int id, MerchantOffers newOffers) {
		containerId = id;
		offers = newOffers;
		received++;
	}

	public static int received() {
		return received;
	}

	public static int containerId() {
		return containerId;
	}

	public static MerchantOffers offers() {
		return offers;
	}
}
