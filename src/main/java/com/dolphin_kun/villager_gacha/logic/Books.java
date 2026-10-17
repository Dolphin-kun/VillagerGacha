package com.dolphin_kun.villager_gacha.logic;

import com.dolphin_kun.villager_gacha.config.ModConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
//? if >=1.21 {
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?} else {
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.Enchantment;
*///?}

public final class Books {
	private Books() {
	}

	public record Offer(Identifier id, int level, int price, int basePrice) {
		public boolean isCheapest() {
			return this.basePrice <= Books.cheapestPrice(this.id, this.level);
		}

		public Component describe() {
			return Texts.tr("book.offer", Books.displayName(this.id, this.level), this.price);
		}
	}

	public record Match(ModConfig.Target target, Offer offer) {
	}

	public record Candidate(String id, Component name) {
	}

	public static List<Offer> enchantedBooks(MerchantOffers offers) {
		List<Offer> books = new ArrayList<>();
		for (MerchantOffer offer : offers) {
			ItemStack result = offer.getResult();
			if (!result.is(Items.ENCHANTED_BOOK)) {
				continue;
			}
			//? if >=1.21 {
			ItemEnchantments stored = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
			for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
				e.getKey().unwrapKey().map(k -> k.identifier()).ifPresent(id -> {
					books.add(new Offer(id, e.getIntValue(), offer.getCostA().getCount(), offer.getBaseCostA().getCount()));
				});
			}
			//?} else {
			/*ListTag stored = EnchantedBookItem.getEnchantments(result);
			for (int i = 0; i < stored.size(); i++) {
				CompoundTag tag = stored.getCompound(i);
				Identifier id = Identifier.tryParse(tag.getString("id"));
				if (id != null) {
					books.add(new Offer(id, tag.getShort("lvl"), offer.getCostA().getCount(), offer.getBaseCostA().getCount()));
				}
			}
			*///?}
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
			Identifier wanted = Identifier.tryParse(target.enchantment);
			if (wanted == null || !wanted.equals(book.id())) {
				return false;
			}
		}
		int requiredLevel = target.wantsMaxLevel() ? maxLevelOfId(book.id()) : target.minLevel;
		if (book.level() < requiredLevel) {
			return false;
		}
		return target.cheapestOnly ? book.isCheapest() : book.price() <= target.maxPrice;
	}

	public static int cheapestPrice(Identifier id, int level) {
		int price = 2 + 3 * level;
		//? if >=1.21 {
		if (isDoublePriced(id)) {
			price *= 2;
		}
		//?} else {
		/*Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
		if (enchantment != null && enchantment.isTreasureOnly()) {
			price *= 2;
		}
		*///?}
		return Math.min(price, 64);
	}

	public static Component displayName(Identifier id, int level) {
		//? if >=1.21 {
		Optional<Holder.Reference<Enchantment>> ref = find(id.toString());
		if (ref.isPresent()) {
			Enchantment enchantment = ref.get().value();
			MutableComponent name = enchantment.description().copy();
			if (enchantment.getMaxLevel() > 1) {
				name.append(" ").append(Component.translatable("enchantment.level." + level));
			}
			return name;
		}
		return Component.literal(id.toString());
		//?} else {
		/*Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
		if (enchantment != null) {
			return enchantment.getFullname(level);
		}
		return Component.literal(id.toString());
		*///?}
	}

	public static Component targetName(ModConfig.Target target) {
		if (target.isAny()) {
			return Texts.tr("book.any");
		}
		if (target.enchantment.isBlank()) {
			return Texts.tr("book.unset");
		}
		Identifier parsed = Identifier.tryParse(target.enchantment.trim());
		if (parsed == null) {
			return Component.literal(target.enchantment);
		}
		//? if >=1.21 {
		return find(target.enchantment)
			.<Component>map(h -> h.value().description())
			.orElseGet(() -> Component.literal(target.enchantment));
		//?} else {
		/*Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(parsed);
		if (enchantment != null) {
			return Component.translatable(enchantment.getDescriptionId());
		}
		return Component.literal(target.enchantment);
		*///?}
	}

	public static int maxLevelOf(String id) {
		Identifier parsed = Identifier.tryParse(id.trim());
		if (parsed == null) {
			return 5;
		}
		return maxLevelOfId(parsed);
	}

	private static int maxLevelOfId(Identifier id) {
		//? if >=1.21 {
		return find(id.toString())
			.map(h -> h.value().getMaxLevel())
			.orElseGet(() -> allEnchantments().mapToInt(h -> h.value().getMaxLevel()).max().orElse(5));
		//?} else {
		/*Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
		return enchantment != null ? enchantment.getMaxLevel() : 5;
		*///?}
	}

	public static boolean knows(String id) {
		String trimmed = id.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		if (ModConfig.Target.ANY.equals(trimmed)) {
			return true;
		}
		Identifier parsed = Identifier.tryParse(trimmed);
		if (parsed == null) {
			return false;
		}
		//? if >=1.21 {
		return find(trimmed).isPresent();
		//?} else {
		/*return BuiltInRegistries.ENCHANTMENT.get(parsed) != null;
		*///?}
	}

	public static List<Candidate> candidates() {
		List<Candidate> list = new ArrayList<>();
		list.add(new Candidate(ModConfig.Target.ANY, Texts.tr("book.any")));
		//? if >=1.21 {
		allEnchantments()
			.map(h -> new Candidate(h.key().identifier().toString(), h.value().description()))
			.sorted((a, b) -> a.id().compareTo(b.id()))
			.forEach(list::add);
		//?} else {
		/*for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
			Identifier key = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
			if (key != null) {
				list.add(new Candidate(key.toString(), Component.translatable(enchantment.getDescriptionId())));
			}
		}
		list.sort((a, b) -> a.id().compareTo(b.id()));
		*///?}
		return list;
	}

	//? if >=1.21 {
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

	private static boolean isDoublePriced(Identifier id) {
		return find(id.toString()).map(h -> h.is(EnchantmentTags.DOUBLE_TRADE_PRICE)).orElse(false);
	}
	//?}
}
