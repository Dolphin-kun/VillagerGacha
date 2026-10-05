package com.dolphin_kun.villager_gacha.ui;

import com.dolphin_kun.villager_gacha.VillagerGacha;
import com.dolphin_kun.villager_gacha.config.ModConfig;
import com.dolphin_kun.villager_gacha.logic.GachaRunner;
import com.dolphin_kun.villager_gacha.logic.Books;
import com.dolphin_kun.villager_gacha.logic.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

public final class StatusHud {
	private static final int MARGIN = 4;
	private static final int BORDER = 1;
	private static final int PAD = 5;
	private static final int TEXT_H = 9;
	private static final int LINE = 11;
	private static final int SECTION_GAP = 3;
	private static final int COLUMN_GAP = 10;

	private static final int BACKGROUND = 0xD0101820;
	private static final int FRAME = 0xFF3FA9F5;
	private static final int DIVIDER = 0x803FA9F5;
	private static final int TITLE = 0xFF8FD0FF;
	private static final int LABEL = 0xFF9AA7B4;
	private static final int TEXT = 0xFFE8EEF4;
	private static final int OK = 0xFF6BE675;
	private static final int BAD = 0xFFFF6B6B;
	private static final int WAIT = 0xFFFFD45C;
	private static final int MUTED = 0xFF6F7C88;

	private record Line(Component label, Component value, int color) {
	}

	private record Panel(Component title, List<Line> lines, Component footer) {
	}

	private StatusHud() {
	}

	public static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = ModConfig.get();
		GachaRunner runner = VillagerGacha.RUNNER;
		GachaRunner.Phase phase = runner.phase();
		if (!cfg.showHud || phase == GachaRunner.Phase.OFF || mc.gui.hud.isHidden()) {
			return;
		}
		boolean compact = phase.running() && phase != GachaRunner.Phase.NEED_LECTERN;
		Panel panel = compact ? compactPanel(cfg, runner) : detailedPanel(cfg, runner);
		drawPanel(graphics, mc.font, cfg, panel);
	}

	private static Panel compactPanel(ModConfig cfg, GachaRunner runner) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(Texts.tr("hud.pulls"), Component.literal(String.valueOf(runner.pulls())), TEXT));
		boolean first = true;
		for (ModConfig.Target target : cfg.targets) {
			Component label = first ? Texts.tr("hud.aim") : Component.empty();
			lines.add(new Line(label, Texts.tr("hud.aim_value", Books.targetName(target), conditionText(target)), TEXT));
			first = false;
		}
		lines.add(new Line(Texts.tr("hud.last_seen"), lastSeen(runner), MUTED));
		return new Panel(null, lines, null);
	}

	private static Panel detailedPanel(ModConfig cfg, GachaRunner runner) {
		GachaRunner.Phase phase = runner.phase();
		List<Line> lines = new ArrayList<>();
		int stateColor = switch (phase) {
			case DONE -> OK;
			case PAUSED -> BAD;
			default -> phase.waiting() ? WAIT : TEXT;
		};
		lines.add(new Line(Texts.tr("hud.state"), runner.status(), stateColor));
		if (phase == GachaRunner.Phase.PAUSED) {
			Component resume = runner.resumeIn() > 0
				? Texts.tr("hud.resume_in", GachaRunner.seconds(runner.resumeIn()), runner.resumeStreak(), cfg.autoResumeLimit)
				: Texts.tr("hud.resume_key", VillagerGacha.toggleKey.getTranslatedKeyMessage());
			lines.add(new Line(Texts.tr("hud.resume"), resume, WAIT));
		}
		lines.add(cfg.lectern == null
			? new Line(Texts.tr("hud.spot"), Texts.tr("hud.spot_unset", VillagerGacha.setSpotKey.getTranslatedKeyMessage()), BAD)
			: new Line(Texts.tr("hud.spot"), Component.literal(cfg.lectern.x + ", " + cfg.lectern.y + ", " + cfg.lectern.z), TEXT));
		lines.add(new Line(Texts.tr("hud.pulls"), Component.literal(String.valueOf(runner.pulls())), TEXT));
		for (ModConfig.Target target : cfg.targets) {
			lines.add(targetLine(target, runner.lastBooks()));
		}
		if (cfg.targets.isEmpty()) {
			lines.add(new Line(Texts.tr("hud.targets"), Texts.tr("hud.no_targets"), BAD));
		}
		lines.add(new Line(Texts.tr("hud.last_seen"), lastSeen(runner), MUTED));

		Component keys = Texts.tr(
			"hud.keys",
			VillagerGacha.toggleKey.getTranslatedKeyMessage(),
			VillagerGacha.emergencyKey.getTranslatedKeyMessage(),
			VillagerGacha.openMenuKey.getTranslatedKeyMessage()
		);
		return new Panel(Texts.tr("hud.title"), lines, keys);
	}

	private static void drawPanel(GuiGraphicsExtractor graphics, Font font, ModConfig cfg, Panel panel) {
		int labelWidth = panel.lines().stream().mapToInt(l -> font.width(l.label())).max().orElse(0);
		int valueWidth = panel.lines().stream().mapToInt(l -> font.width(l.value())).max().orElse(0);
		int innerWidth = labelWidth + COLUMN_GAP + valueWidth;
		int innerHeight = panel.lines().size() * LINE - (LINE - TEXT_H);
		if (panel.title() != null) {
			innerWidth = Math.max(innerWidth, font.width(panel.title()));
			innerHeight += TEXT_H + SECTION_GAP * 2 + 1;
		}
		if (panel.footer() != null) {
			innerWidth = Math.max(innerWidth, font.width(panel.footer()));
			innerHeight += SECTION_GAP * 2 + 1 + TEXT_H;
		}
		int boxWidth = BORDER * 2 + PAD * 2 + innerWidth;
		int boxHeight = BORDER * 2 + PAD * 2 + innerHeight;

		int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		float scale = crispScale(cfg.hudScale, guiScale);
		float scaledWidth = boxWidth * scale;
		float scaledHeight = boxHeight * scale;
		ModConfig.HudPosition position = cfg.hudPosition;
		float screenX = position.right ? graphics.guiWidth() - MARGIN - scaledWidth : MARGIN;
		float screenY = switch (position.vertical) {
			case -1 -> MARGIN;
			case 1 -> graphics.guiHeight() - MARGIN - scaledHeight;
			default -> (graphics.guiHeight() - scaledHeight) / 2;
		};
		screenX = Math.round(screenX * guiScale) / (float) guiScale;
		screenY = Math.round(screenY * guiScale) / (float) guiScale;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(screenX, screenY);
		pose.scale(scale, scale);

		graphics.fill(BORDER, BORDER, boxWidth - BORDER, boxHeight - BORDER, BACKGROUND);
		graphics.outline(0, 0, boxWidth, boxHeight, FRAME);

		int x = BORDER + PAD;
		int y = BORDER + PAD;
		if (panel.title() != null) {
			graphics.text(font, panel.title(), x + (innerWidth - font.width(panel.title())) / 2, y, TITLE);
			y += TEXT_H + SECTION_GAP;
			graphics.fill(x, y, x + innerWidth, y + 1, DIVIDER);
			y += 1 + SECTION_GAP;
		}
		for (Line line : panel.lines()) {
			graphics.text(font, line.label(), x, y, LABEL);
			graphics.text(font, line.value(), x + labelWidth + COLUMN_GAP, y, line.color());
			y += LINE;
		}
		if (panel.footer() != null) {
			y += SECTION_GAP - (LINE - TEXT_H);
			graphics.fill(x, y, x + innerWidth, y + 1, DIVIDER);
			y += 1 + SECTION_GAP;
			graphics.text(font, panel.footer(), x + (innerWidth - font.width(panel.footer())) / 2, y, MUTED);
		}
		pose.popMatrix();
	}

	public static float crispScale(int percent, int guiScale) {
		int pixelsPerDot = Math.max(1, Math.round(guiScale * percent / 100f));
		return Math.min(pixelsPerDot, guiScale) / (float) guiScale;
	}

	private static Component lastSeen(GachaRunner runner) {
		Component seen = runner.lastSeen();
		return seen.getString().isEmpty() ? Component.literal("-") : seen;
	}

	private static Line targetLine(ModConfig.Target target, List<Books.Offer> lastBooks) {
		Component name = Books.targetName(target);
		Optional<Books.Offer> hit = lastBooks.stream().filter(b -> Books.satisfies(target, b)).findFirst();
		if (hit.isPresent()) {
			return new Line(name, hit.get().describe(), OK);
		}
		Optional<Books.Offer> near = lastBooks.stream().filter(b -> sameEnchantment(target, b)).findFirst();
		if (near.isPresent()) {
			return new Line(name, Texts.tr("hud.not_enough", near.get().describe()), BAD);
		}
		return new Line(name, conditionText(target), LABEL);
	}

	private static boolean sameEnchantment(ModConfig.Target target, Books.Offer book) {
		if (target.isAny()) {
			return true;
		}
		Identifier id = Identifier.tryParse(target.enchantment);
		return id != null && book.enchantment().is(id);
	}

	public static Component conditionText(ModConfig.Target target) {
		if (target.wantsMaxLevel()) {
			return target.cheapestOnly
				? Texts.tr("hud.condition_max_cheapest")
				: Texts.tr("hud.condition_max", target.maxPrice);
		}
		return target.cheapestOnly
			? Texts.tr("hud.condition_cheapest", target.minLevel)
			: Texts.tr("hud.condition", target.minLevel, target.maxPrice);
	}
}
