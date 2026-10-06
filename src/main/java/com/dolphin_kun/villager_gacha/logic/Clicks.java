package com.dolphin_kun.villager_gacha.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class Clicks {
	private Clicks() {
	}

	public static boolean useOnEntity(Minecraft mc, LocalPlayer player, Entity target, EntityHitResult hit) {
		if (mc.gameMode == null) {
			return false;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			if (mc.gameMode.interact(player, target, hit, hand) instanceof InteractionResult.Success success) {
				if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
					player.swing(hand);
				}
				return true;
			}
		}
		return false;
	}

	public static boolean useOnBlock(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
		if (mc.gameMode == null) {
			return false;
		}
		if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit) instanceof InteractionResult.Success success) {
			if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
				player.swing(InteractionHand.MAIN_HAND);
			}
			return true;
		}
		return false;
	}

	public static void swingWhileMining(LocalPlayer player) {
		player.swing(InteractionHand.MAIN_HAND);
	}
}
