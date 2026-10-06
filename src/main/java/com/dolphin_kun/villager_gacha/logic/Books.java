package com.dolphin_kun.villager_gacha.logic;

import com.dolphin_kun.villager_gacha.config.ModConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class Books {
	private Books() {
	}

	public record Offer(Holder<Enchantment> enchantment, int level, int price, int basePrice) {
		public boolean isCheapest() {
			return this.basePrice <= cheapestPrice(this.enchantment, this.level);
		}

		public Component describe() {
			return Texts.tr("book.offer", nameWithLevel(this.enchantment, this.level), this.price);
		}
	}

	public record Match(ModConfig.Target target, Offer offer) {
	}

	public static List<Offer> enchantedBooks(MerchantOffers offers) {
		List<Offer> books = new ArrayList<>();
		for (MerchantOffer offer : offers) {
			ItemStack result = offer.getResult();
			if (!result.is(Items.ENCHANTED_BOOK)) {
				continue;
			}
			ItemEnchantments stored = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
			for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
				books.add(new Offer(e.getKey(), e.getIntValue(), offer.getCostA().getCount(), offer.getBaseCostA().getCount()));
			}
		}
		return books;
	}

	public static Optional<Match> findMatch(List<ModConfig.Target> targets, List<Offer> books) {
		for (ModConfig.Target target : targets) {
			for (Offer book : books) {
				if (satisfies(target, book)) {
					return Optional.of(new Match(target, book));
				}
			}
		}
		return Optional.empty();
	}

	public static boolean satisfies(ModConfig.Target target, Offer book) {
		if (!target.isAny()) {
			Identifier id = Identifier.tryParse(target.enchantment);
			if (id == null || !book.enchantment().is(id)) {
				return false;
			}
		}
		int requiredLevel = target.wantsMaxLevel() ? book.enchantment().value().getMaxLevel() : target.minLevel;
		if (book.level() < requiredLevel) {
			return false;
		}
		return target.cheapestOnly ? book.isCheapest() : book.price() <= target.maxPrice;
	}

	public static int cheapestPrice(Holder<Enchantment> enchantment, int level) {
		int price = 2 + 3 * level;
		if (enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
			price *= 2;
		}
		return Math.min(price, 64);
	}

	public static MutableComponent nameWithLevel(Holder<Enchantment> enchantment, int level) {
		MutableComponent name = enchantment.value().description().copy();
		if (enchantment.value().getMaxLevel() > 1) {
			name.append(" ").append(Component.translatable("enchantment.level." + level));
		}
		return name;
	}

	public static Stream<Holder.Reference<Enchantment>> allEnchantments() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return Stream.empty();
		}
		return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements();
	}

	public static Optional<Holder.Reference<Enchantment>> find(String id) {
		Identifier parsed = Identifier.tryParse(id.trim());
		if (parsed == null) {
			return Optional.empty();
		}
		return allEnchantments().filter(h -> h.is(parsed)).findFirst();
	}

	public static Component targetName(ModConfig.Target target) {
		if (target.isAny()) {
			return Texts.tr("book.any");
		}
		if (target.enchantment.isBlank()) {
			return Texts.tr("book.unset");
		}
		return find(target.enchantment)
			.<Component>map(h -> h.value().description())
			.orElseGet(() -> Component.literal(target.enchantment));
	}

	public static int maxLevelOf(String id) {
		return find(id)
			.map(h -> h.value().getMaxLevel())
			.orElseGet(() -> allEnchantments().mapToInt(h -> h.value().getMaxLevel()).max().orElse(5));
	}
}
