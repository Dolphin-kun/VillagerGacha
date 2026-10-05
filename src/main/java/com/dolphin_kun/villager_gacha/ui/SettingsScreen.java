package com.dolphin_kun.villager_gacha.ui;

import com.dolphin_kun.villager_gacha.VillagerGacha;
import com.dolphin_kun.villager_gacha.config.ModConfig;
import com.dolphin_kun.villager_gacha.logic.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
	private static final int ROW_H = 22;
	private static final int LABEL_W = 150;
	private static final int CONTROL_W = 130;
	private static final int STEP_W = 20;
	private static final int BUTTON_GAP = 2;
	private static final int TAB_W = 90;
	private static final int TAB_Y = 22;
	private static final int FIRST_ROW_Y = 50;

	private static final int GOLD = 0xFFFFC94D;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF6BE675;
	private static final int RED = 0xFFFF6B6B;

	private enum Tab {
		BASIC, DISPLAY, RESUME
	}

	private record Label(Component text, int y, String tooltipKey) {
	}

	private record Value(Supplier<Component> text, int x, int width, int y, Runnable refresh) {
	}

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private final List<Value> values = new ArrayList<>();
	private Tab tab = Tab.BASIC;
	private int left;
	private int controlX;
	private int spotY = -1;

	public SettingsScreen(Screen parent) {
		super(Texts.tr("ui.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.labels.clear();
		this.values.clear();
		this.spotY = -1;
		this.left = (this.width - LABEL_W - CONTROL_W) / 2;
		this.controlX = this.left + LABEL_W;

		int tabX = (this.width - TAB_W * Tab.values().length - BUTTON_GAP * (Tab.values().length - 1)) / 2;
		for (Tab t : Tab.values()) {
			Button button = Button.builder(Texts.tr("ui.tab." + t.name().toLowerCase(Locale.ROOT)), b -> {
				this.tab = t;
				this.clearWidgets();
				this.init();
			}).bounds(tabX, TAB_Y, TAB_W, 20).build();
			button.active = t != this.tab;
			this.addRenderableWidget(button);
			tabX += TAB_W + BUTTON_GAP;
		}

		ModConfig cfg = ModConfig.get();
		int y = switch (this.tab) {
			case BASIC -> this.basicTab(cfg, FIRST_ROW_Y);
			case DISPLAY -> this.displayTab(cfg, FIRST_ROW_Y);
			case RESUME -> this.resumeTab(cfg, FIRST_ROW_Y);
		};

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
			.bounds((this.width - 120) / 2, Math.min(y + 6, this.height - 24), 120, 20).build());
	}

	private int basicTab(ModConfig cfg, int y) {
		this.spotY = y;
		int half = (CONTROL_W - BUTTON_GAP) / 2;
		this.addRenderableWidget(Button.builder(Texts.tr("ui.spot.set"), b -> VillagerGacha.setSpotFromCrosshair(this.minecraft))
			.bounds(this.controlX, y, half, 20).tooltip(Tooltip.create(Texts.tr("ui.spot.set.tooltip"))).build());
		this.addRenderableWidget(Button.builder(Texts.tr("ui.spot.clear"), b -> {
			cfg.lectern = null;
			ModConfig.save();
		}).bounds(this.controlX + half + BUTTON_GAP, y, half, 20).build());
		y += ROW_H;

		this.label(y, "ui.break_delay");
		this.stepper(this.controlX, CONTROL_W, y, () -> cfg.breakDelayTicks, v -> cfg.breakDelayTicks = v, 0, 100, 5, SettingsScreen::secondsText);
		y += ROW_H;
		this.label(y, "ui.place_delay");
		this.stepper(this.controlX, CONTROL_W, y, () -> cfg.placeDelayTicks, v -> cfg.placeDelayTicks = v, 0, 200, 5, SettingsScreen::secondsText);
		y += ROW_H;
		this.label(y, "ui.open_delay");
		this.stepper(this.controlX, CONTROL_W, y, () -> cfg.openDelayTicks, v -> cfg.openDelayTicks = v, 0, 100, 5, SettingsScreen::secondsText);
		y += ROW_H;

		int toggleW = 40;
		this.label(y, "ui.protect_axe");
		this.toggle(this.controlX, toggleW, y, () -> cfg.protectAxe, v -> cfg.protectAxe = v);
		this.stepper(this.controlX + toggleW + BUTTON_GAP, CONTROL_W - toggleW - BUTTON_GAP, y,
			() -> cfg.axeMinDurability, v -> cfg.axeMinDurability = v, 1, 200, 5, n -> Texts.tr("ui.axe_remaining", n));
		y += ROW_H;

		this.label(y, "ui.hide_trade_screen");
		this.toggle(this.controlX, CONTROL_W, y, () -> cfg.hideTradeScreen, v -> cfg.hideTradeScreen = v);
		y += ROW_H;
		this.label(y, "ui.face_targets");
		this.toggle(this.controlX, CONTROL_W, y, () -> cfg.faceTargets, v -> cfg.faceTargets = v);
		return y + ROW_H;
	}

	private int displayTab(ModConfig cfg, int y) {
		this.label(y, "ui.show_hud");
		this.toggle(this.controlX, CONTROL_W, y, () -> cfg.showHud, v -> cfg.showHud = v);
		y += ROW_H;

		this.label(y, "ui.hud_position");
		this.addRenderableWidget(Button.builder(positionName(cfg.hudPosition), b -> {
			cfg.hudPosition = cfg.hudPosition.next();
			b.setMessage(positionName(cfg.hudPosition));
		}).bounds(this.controlX, y, CONTROL_W, 20).build());
		y += ROW_H;

		this.label(y, "ui.hud_scale");
		this.addRenderableWidget(Button.builder(Component.literal(cfg.hudScale + "%"), b -> {
			cfg.hudScale = cfg.nextHudScale();
			b.setMessage(Component.literal(cfg.hudScale + "%"));
		}).bounds(this.controlX, y, CONTROL_W, 20).build());
		y += ROW_H;

		this.label(y, "ui.chat");
		this.addRenderableWidget(Button.builder(chatName(cfg.chatEverything), b -> {
			cfg.chatEverything = !cfg.chatEverything;
			b.setMessage(chatName(cfg.chatEverything));
		}).bounds(this.controlX, y, CONTROL_W, 20).build());
		return y + ROW_H;
	}

	private int resumeTab(ModConfig cfg, int y) {
		this.label(y, "ui.auto_resume");
		this.toggle(this.controlX, CONTROL_W, y, () -> cfg.autoResume, v -> cfg.autoResume = v);
		y += ROW_H;
		this.label(y, "ui.auto_resume_wait");
		this.stepper(this.controlX, CONTROL_W, y, () -> cfg.autoResumeWaitTicks, v -> cfg.autoResumeWaitTicks = v, 20, 600, 20, SettingsScreen::secondsText);
		y += ROW_H;
		this.label(y, "ui.auto_resume_limit");
		this.stepper(this.controlX, CONTROL_W, y, () -> cfg.autoResumeLimit, v -> cfg.autoResumeLimit = v, 1, 20, 1, n -> Texts.tr("ui.times", n));
		return y + ROW_H;
	}

	private void label(int y, String key) {
		this.labels.add(new Label(Texts.tr(key), y, key + ".tooltip"));
	}

	private void stepper(int x, int width, int y, IntSupplier get, IntConsumer set, int min, int max, int step, IntFunction<Component> format) {
		Button minus = Button.builder(Component.literal("-"), b -> set.accept(Math.max(min, get.getAsInt() - step)))
			.bounds(x, y, STEP_W, 20).build();
		Button plus = Button.builder(Component.literal("+"), b -> set.accept(Math.min(max, get.getAsInt() + step)))
			.bounds(x + width - STEP_W, y, STEP_W, 20).build();
		this.addRenderableWidget(minus);
		this.addRenderableWidget(plus);
		this.values.add(new Value(() -> format.apply(get.getAsInt()), x, width, y, () -> {
			minus.active = get.getAsInt() > min;
			plus.active = get.getAsInt() < max;
		}));
	}

	private void toggle(int x, int width, int y, BooleanSupplier get, Consumer<Boolean> set) {
		this.addRenderableWidget(Button.builder(onOff(get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(onOff(get.getAsBoolean()));
		}).bounds(x, y, width, 20).build());
	}

	private static Component positionName(ModConfig.HudPosition position) {
		return Texts.tr("ui.hud_position." + position.name().toLowerCase(Locale.ROOT));
	}

	private static Component chatName(boolean everything) {
		return Texts.tr(everything ? "ui.chat.everything" : "ui.chat.found_only");
	}

	private static Component onOff(boolean on) {
		return on ? CommonComponents.OPTION_ON.copy().withColor(GREEN) : CommonComponents.OPTION_OFF.copy().withColor(RED);
	}

	private static Component secondsText(int ticks) {
		return Texts.tr("ui.seconds", String.format(Locale.ROOT, "%.2f", ticks / 20.0).replaceAll("\\.?0+$", ""));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 8, GOLD);

		if (this.spotY >= 0) {
			ModConfig cfg = ModConfig.get();
			Component spot = cfg.lectern == null
				? Texts.tr("ui.spot.unset")
				: Texts.tr("ui.spot.value", cfg.lectern.x, cfg.lectern.y, cfg.lectern.z);
			Component spotLabel = Texts.tr("ui.spot");
			graphics.text(this.font, spotLabel, this.left, this.spotY + 6, WHITE);
			graphics.text(this.font, spot, this.left + this.font.width(spotLabel) + 8, this.spotY + 6, cfg.lectern == null ? RED : GRAY);
		}

		for (Label label : this.labels) {
			graphics.text(this.font, label.text(), this.left, label.y() + 6, WHITE);
			if (mouseX >= this.left && mouseX < this.controlX && mouseY >= label.y() && mouseY < label.y() + 20) {
				graphics.setTooltipForNextFrame(this.font, this.font.split(Texts.tr(label.tooltipKey()), 220), mouseX, mouseY);
			}
		}
		for (Value value : this.values) {
			value.refresh().run();
			Component text = value.text().get();
			graphics.text(this.font, text, value.x() + (value.width() - this.font.width(text)) / 2, value.y() + 6, WHITE);
		}
	}

	@Override
	public void onClose() {
		ModConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
