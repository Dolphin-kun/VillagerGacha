package com.dolphin_kun.villager_gacha.logic;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Hotbar {
	private static final int HOTBAR_SIZE = 9;
	private static final int MAIN_INVENTORY_END = 36;

	private Hotbar() {
	}

	public static int find(LocalPlayer player, Predicate<ItemStack> condition) {
		Inventory inv = player.getInventory();
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (condition.test(inv.getItem(slot))) {
				return slot;
			}
		}
		return -1;
	}

	public static boolean hold(LocalPlayer player, Predicate<ItemStack> condition) {
		int slot = find(player, condition);
		if (slot < 0) {
			return false;
		}
		player.getInventory().setSelectedSlot(slot);
		return true;
	}

	public static boolean holdAxe(LocalPlayer player) {
		return hold(player, stack -> stack.is(ItemTags.AXES));
	}

	public enum AxeCheck {
		READY,
		NO_AXE,
		ALL_WORN
	}

	public static AxeCheck holdSafeAxe(LocalPlayer player, int minDurability) {
		if (hold(player, stack -> stack.is(ItemTags.AXES) && remainingDurability(stack) > minDurability)) {
			return AxeCheck.READY;
		}
		return find(player, stack -> stack.is(ItemTags.AXES)) >= 0 ? AxeCheck.ALL_WORN : AxeCheck.NO_AXE;
	}

	public static int remainingDurability(ItemStack stack) {
		return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
	}

	public static boolean prepare(Minecraft mc, LocalPlayer player, Item item) {
		if (find(player, stack -> stack.is(item)) >= 0) {
			return true;
		}
		if (mc.gameMode == null || player.containerMenu != player.inventoryMenu) {
			return false;
		}
		Inventory inv = player.getInventory();
		int from = -1;
		for (int slot = HOTBAR_SIZE; slot < MAIN_INVENTORY_END && from < 0; slot++) {
			if (inv.getItem(slot).is(item)) {
				from = slot;
			}
		}
		if (from < 0) {
			return false;
		}
		int to = find(player, ItemStack::isEmpty);
		for (int slot = HOTBAR_SIZE - 1; slot >= 0 && to < 0; slot--) {
			if (!inv.getItem(slot).is(ItemTags.AXES)) {
				to = slot;
			}
		}
		if (to < 0) {
			return false;
		}
		mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, from, to, ContainerInput.SWAP, player);
		return find(player, stack -> stack.is(item)) >= 0;
	}
}
