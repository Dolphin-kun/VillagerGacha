package com.dolphin_kun.villager_gacha.config;

import com.dolphin_kun.villager_gacha.VillagerGacha;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(VillagerGacha.MOD_ID + ".json");
	private static ModConfig current = new ModConfig();

	public Spot lectern = null;
	public List<Target> targets = new ArrayList<>();

	public int breakDelayTicks = 0;
	public int placeDelayTicks = 0;
	public int openDelayTicks = 0;

	public boolean showHud = true;
	public HudPosition hudPosition = HudPosition.TOP_RIGHT;
	public int hudScale = 75;

	public boolean hideTradeScreen = true;
	public boolean chatEverything = false;

	public boolean faceTargets = false;

	public boolean removeFoundTarget = true;

	public boolean protectAxe = true;
	public int axeMinDurability = 10;

	public boolean autoResume = true;
	public int autoResumeWaitTicks = 100;
	public int autoResumeLimit = 5;

	public static ModConfig get() {
		return current;
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			current = new ModConfig();
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
			current = loaded != null ? loaded : new ModConfig();
			current.sanitize();
		} catch (IOException | JsonParseException e) {
			VillagerGacha.LOGGER.error("Could not read {}, using defaults", FILE, e);
			current = new ModConfig();
		}
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			GSON.toJson(current, writer);
		} catch (IOException e) {
			VillagerGacha.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	private void sanitize() {
		if (this.targets == null) {
			this.targets = new ArrayList<>();
		}
		this.targets.removeIf(t -> t == null);
		for (Target t : this.targets) {
			if (t.enchantment == null) {
				t.enchantment = "";
			}
			t.minLevel = Math.max(1, t.minLevel);
			t.maxPrice = Math.clamp(t.maxPrice, 1, 64);
		}
		this.breakDelayTicks = Math.max(0, this.breakDelayTicks);
		this.placeDelayTicks = Math.max(0, this.placeDelayTicks);
		this.openDelayTicks = Math.max(0, this.openDelayTicks);
		this.autoResumeWaitTicks = Math.max(20, this.autoResumeWaitTicks);
		this.autoResumeLimit = Math.max(1, this.autoResumeLimit);
		if (this.hudPosition == null) {
			this.hudPosition = HudPosition.TOP_RIGHT;
		}
		if (!java.util.Arrays.stream(HUD_SCALES).anyMatch(v -> v == this.hudScale)) {
			this.hudScale = 75;
		}
		this.axeMinDurability = Math.clamp(this.axeMinDurability, 1, 200);
	}

	public static final int[] HUD_SCALES = {50, 67, 75, 100};

	public int nextHudScale() {
		for (int i = 0; i < HUD_SCALES.length; i++) {
			if (HUD_SCALES[i] == this.hudScale) {
				return HUD_SCALES[(i + 1) % HUD_SCALES.length];
			}
		}
		return HUD_SCALES[0];
	}

	public enum HudPosition {
		TOP_LEFT(false, -1),
		LEFT(false, 0),
		BOTTOM_LEFT(false, 1),
		TOP_RIGHT(true, -1),
		RIGHT(true, 0),
		BOTTOM_RIGHT(true, 1);

		public final boolean right;
		public final int vertical;

		HudPosition(boolean right, int vertical) {
			this.right = right;
			this.vertical = vertical;
		}

		public HudPosition next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public static final class Spot {
		public int x;
		public int y;
		public int z;

		public Spot(BlockPos pos) {
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
		}

		public BlockPos toBlockPos() {
			return new BlockPos(this.x, this.y, this.z);
		}
	}

	public static final class Target {
		public static final String ANY = "*";

		public String enchantment = "";
		public int minLevel = 1;
		public int maxPrice = 64;
		public boolean cheapestOnly = false;
		public boolean maxLevelOnly = false;

		public boolean wantsMaxLevel() {
			return this.isAny() && this.maxLevelOnly;
		}

		public boolean isAny() {
			return ANY.equals(this.enchantment);
		}

		public static Target anyCheapest() {
			Target t = new Target();
			t.enchantment = ANY;
			t.cheapestOnly = true;
			return t;
		}
	}
}
