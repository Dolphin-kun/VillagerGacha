package com.dolphin_kun.villager_gacha.ui;

import com.dolphin_kun.villager_gacha.config.ModConfig;
import com.dolphin_kun.villager_gacha.logic.Books;
import com.dolphin_kun.villager_gacha.logic.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TargetScreen extends Screen {
	private static final int CARD_PAD = 4;
	private static final int ICON_W = 16;
	private static final int NAME_W = 120;
	private static final int STEP_W = 16;
	private static final int LEVEL_W = 56;
	private static final int PRICE_W = 30;
	private static final int CHEAP_W = 40;
	private static final int DELETE_W = 20;
	private static final int GAP = 4;
	private static final int CONTENT_W = ICON_W + GAP + NAME_W + GAP + LEVEL_W + GAP + PRICE_W + GAP + CHEAP_W + GAP + DELETE_W;
	private static final int CARD_W = CONTENT_W + CARD_PAD * 2;
	private static final int CARD_H = 20 + CARD_PAD * 2;
	private static final int CARD_SPACING = CARD_H + 4;
	private static final int SCROLLBAR_W = 4;
	private static final int SUGGEST_W = 90;
	private static final int LIST_TOP = 58;
	private static final int LIST_BOTTOM_MARGIN = 36;
	private static final int MAX_SUGGESTIONS = 6;

	private static final int GOLD = 0xFFFFC94D;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int GREEN = 0xFF6BE675;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int CARD_BG = 0x90101820;
	private static final int CARD_FRAME = 0x603FA9F5;
	private static final int CARD_FRAME_OK = 0xA06BE675;
	private static final int TRACK = 0x40FFFFFF;
	private static final int THUMB = 0xFF3FA9F5;

	private static final ItemStack BOOK_ICON = new ItemStack(Items.ENCHANTED_BOOK);

	private record Candidate(String id, Component name) {
	}

	private final class Row {
		final ModConfig.Target target;
		final int cardX;
		final int cardY;
		EditBox name;
		Button minus;
		Button plus;
		EditBox price;
		Button cheapest;
		int maxLevel;

		Row(ModConfig.Target target, int cardX, int cardY) {
			this.target = target;
			this.cardX = cardX;
			this.cardY = cardY;
		}

		int contentY() {
			return this.cardY + CARD_PAD;
		}

		boolean recognized() {
			return this.target.isAny() || Books.find(this.target.enchantment).isPresent();
		}

		void refreshLevel() {
			this.maxLevel = Books.maxLevelOf(this.target.enchantment);
			this.target.minLevel = Math.clamp(this.target.minLevel, 1, this.maxLevel);
			if (!this.target.isAny()) {
				this.target.maxLevelOnly = false;
			}
			this.minus.active = this.target.minLevel > 1 || this.target.maxLevelOnly;
			this.plus.active = this.target.minLevel < this.maxLevel || (this.target.isAny() && !this.target.maxLevelOnly);
		}

		void levelUp() {
			if (this.target.minLevel < this.maxLevel) {
				this.target.minLevel++;
			} else if (this.target.isAny()) {
				this.target.maxLevelOnly = true;
			}
			this.refreshLevel();
		}

		void levelDown() {
			if (this.target.maxLevelOnly) {
				this.target.maxLevelOnly = false;
			} else {
				this.target.minLevel--;
			}
			this.refreshLevel();
		}

		String levelText() {
			return this.target.maxLevelOnly ? Texts.tr("ui.level_max").getString() : this.target.minLevel + "/" + this.maxLevel;
		}

		void refreshCheapest() {
			this.cheapest.setMessage(Texts.tr("ui.cheapest").withColor(this.target.cheapestOnly ? GREEN : GRAY));
			this.price.setEditable(!this.target.cheapestOnly);
			this.price.active = !this.target.cheapestOnly;
		}
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final List<Button> suggestionButtons = new ArrayList<>();
	private List<Candidate> candidates = List.of();
	private Row suggestingFor;
	private int scroll;
	private int left;

	public TargetScreen(Screen parent) {
		super(Texts.tr("ui.targets.title"));
		this.parent = parent;
	}

	private int visibleCount() {
		return Math.max(1, (this.height - LIST_BOTTOM_MARGIN - LIST_TOP) / CARD_SPACING);
	}

	private int maxScroll() {
		return Math.max(0, ModConfig.get().targets.size() - this.visibleCount());
	}

	@Override
	protected void init() {
		this.rows.clear();
		this.suggestionButtons.clear();
		this.suggestingFor = null;
		this.left = Math.max(6, (this.width - CARD_W - GAP - SCROLLBAR_W - GAP - SUGGEST_W) / 2);
		this.scroll = Math.clamp(this.scroll, 0, this.maxScroll());

		List<Candidate> list = new ArrayList<>();
		list.add(new Candidate(ModConfig.Target.ANY, Texts.tr("book.any")));
		Books.allEnchantments()
			.map(h -> new Candidate(h.key().identifier().toString(), h.value().description()))
			.sorted((a, b) -> a.id().compareTo(b.id()))
			.forEach(list::add);
		this.candidates = list;

		List<ModConfig.Target> targets = ModConfig.get().targets;
		int end = Math.min(targets.size(), this.scroll + this.visibleCount());
		for (int i = this.scroll; i < end; i++) {
			this.addRow(targets.get(i), this.left, LIST_TOP + (i - this.scroll) * CARD_SPACING);
		}

		int buttonY = this.height - 28;
		int x = this.left;
		this.addRenderableWidget(Button.builder(Texts.tr("ui.add"), b -> this.addTarget(new ModConfig.Target()))
			.bounds(x, buttonY, 90, 20).build());
		x += 94;
		Button addAny = Button.builder(Texts.tr("ui.add_any"), b -> this.addTarget(ModConfig.Target.anyCheapest()))
			.bounds(x, buttonY, 120, 20).build();
		addAny.setTooltip(Tooltip.create(Texts.tr("ui.add_any.tooltip")));
		this.addRenderableWidget(addAny);
		x += 124;
		this.addRenderableWidget(Button.builder(Texts.tr("ui.settings"), b -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
			.bounds(x, buttonY, 80, 20).build());
		int doneX = Math.max(x + 84, this.left + CARD_W + GAP + SCROLLBAR_W + GAP + SUGGEST_W - 80);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
			.bounds(doneX, buttonY, 80, 20).build());
	}

	private void addRow(ModConfig.Target target, int cardX, int cardY) {
		Row row = new Row(target, cardX, cardY);
		int x = cardX + CARD_PAD + ICON_W + GAP;
		int y = row.contentY();

		row.name = new EditBox(this.font, x, y, NAME_W, 20, Texts.tr("ui.col.enchantment"));
		row.name.setMaxLength(64);
		row.name.setValue(target.enchantment);
		row.name.setHint(Texts.tr("ui.name_hint"));
		x += NAME_W + GAP;

		row.minus = Button.builder(Component.literal("-"), b -> row.levelDown()).bounds(x, y, STEP_W, 20).build();
		row.plus = Button.builder(Component.literal("+"), b -> row.levelUp()).bounds(x + LEVEL_W - STEP_W, y, STEP_W, 20).build();
		x += LEVEL_W + GAP;

		row.price = new EditBox(this.font, x, y, PRICE_W, 20, Texts.tr("ui.col.price"));
		row.price.setMaxLength(2);
		row.price.setValue(String.valueOf(target.maxPrice));
		row.price.setResponder(text -> {
			try {
				target.maxPrice = Math.clamp(Integer.parseInt(text.trim()), 1, 64);
			} catch (NumberFormatException ignored) {
			}
		});
		x += PRICE_W + GAP;

		row.cheapest = Button.builder(Component.empty(), b -> {
			target.cheapestOnly = !target.cheapestOnly;
			row.refreshCheapest();
		}).bounds(x, y, CHEAP_W, 20).build();
		row.cheapest.setTooltip(Tooltip.create(Texts.tr("ui.cheapest.tooltip")));
		x += CHEAP_W + GAP;

		Button delete = Button.builder(Component.literal("✕"), b -> {
			ModConfig.get().targets.remove(target);
			this.rebuild();
		}).bounds(x, y, DELETE_W, 20).build();
		delete.setTooltip(Tooltip.create(Texts.tr("ui.delete")));

		row.refreshLevel();
		row.refreshCheapest();
		row.name.setResponder(text -> {
			target.enchantment = text.trim();
			row.refreshLevel();
			this.showSuggestions(row);
		});

		this.addRenderableWidget(row.name);
		this.addRenderableWidget(row.minus);
		this.addRenderableWidget(row.plus);
		this.addRenderableWidget(row.price);
		this.addRenderableWidget(row.cheapest);
		this.addRenderableWidget(delete);
		this.rows.add(row);
	}

	private void showSuggestions(Row row) {
		this.clearSuggestions();
		String typed = row.target.enchantment;
		String query = typed.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		if (query.isEmpty() || this.candidates.stream().anyMatch(c -> c.id().equals(typed))) {
			return;
		}
		List<Candidate> hits = this.candidates.stream()
			.filter(c -> c.id().replace("minecraft:", "").contains(query)
				|| c.name().getString().toLowerCase(Locale.ROOT).contains(query))
			.limit(MAX_SUGGESTIONS)
			.toList();

		int x = this.suggestX();
		int y = row.cardY;
		for (Candidate c : hits) {
			Button b = Button.builder(c.name(), btn -> {
				row.name.setValue(c.id());
				this.clearSuggestions();
			}).bounds(x, y, SUGGEST_W, 16).build();
			b.setTooltip(Tooltip.create(Component.literal(c.id())));
			this.addRenderableWidget(b);
			this.suggestionButtons.add(b);
			y += 17;
		}
		this.suggestingFor = row;
	}

	private int suggestX() {
		return this.left + CARD_W + GAP + SCROLLBAR_W + GAP;
	}

	private void clearSuggestions() {
		this.suggestionButtons.forEach(this::removeWidget);
		this.suggestionButtons.clear();
		this.suggestingFor = null;
	}

	private void addTarget(ModConfig.Target target) {
		ModConfig.get().targets.add(target);
		this.scroll = Integer.MAX_VALUE;
		this.rebuild();
	}

	private void rebuild() {
		ModConfig.save();
		this.clearWidgets();
		this.init();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		boolean overList = mouseX >= this.left && mouseX < this.left + CARD_W + GAP + SCROLLBAR_W
			&& mouseY >= LIST_TOP && mouseY < this.height - LIST_BOTTOM_MARGIN;
		if (overList && this.maxScroll() > 0 && scrollY != 0) {
			int next = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, this.maxScroll());
			if (next != this.scroll) {
				this.scroll = next;
				this.rebuild();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		for (Row row : this.rows) {
			int x2 = row.cardX + CARD_W;
			int y2 = row.cardY + CARD_H;
			graphics.fill(row.cardX + 1, row.cardY + 1, x2 - 1, y2 - 1, CARD_BG);
			graphics.outline(row.cardX, row.cardY, CARD_W, CARD_H, row.recognized() ? CARD_FRAME_OK : CARD_FRAME);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 10, GOLD);
		Component hint = Texts.tr("ui.targets.hint");
		graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 24, GRAY);

		List<ModConfig.Target> targets = ModConfig.get().targets;
		if (targets.isEmpty()) {
			Component empty = Texts.tr("ui.targets.empty");
			graphics.text(this.font, empty, (this.width - this.font.width(empty)) / 2, LIST_TOP + 8, GRAY);
			return;
		}

		int headerY = LIST_TOP - 12;
		int x = this.left + CARD_PAD + ICON_W + GAP;
		graphics.text(this.font, Texts.tr("ui.col.enchantment"), x, headerY, GRAY);
		x += NAME_W + GAP;
		graphics.text(this.font, Texts.tr("ui.col.level"), x, headerY, GRAY);
		x += LEVEL_W + GAP;
		graphics.text(this.font, Texts.tr("ui.col.price"), x, headerY, GRAY);
		Component count = Texts.tr("ui.targets.count", targets.size());
		graphics.text(this.font, count, this.left + CARD_W - this.font.width(count), headerY, GRAY);

		for (Row row : this.rows) {
			int y = row.contentY();
			graphics.item(BOOK_ICON, row.cardX + CARD_PAD, y + 2);
			int levelX = row.cardX + CARD_PAD + ICON_W + GAP + NAME_W + GAP;
			String level = row.levelText();
			graphics.text(this.font, level, levelX + (LEVEL_W - this.font.width(level)) / 2, y + 6, WHITE);
			if (row != this.suggestingFor && row.recognized()) {
				graphics.text(this.font, Books.targetName(row.target), this.suggestX(), y + 6, GREEN);
			}
		}

		this.drawScrollbar(graphics);
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics) {
		int total = ModConfig.get().targets.size();
		int visible = this.visibleCount();
		if (total <= visible) {
			return;
		}
		int x = this.left + CARD_W + GAP;
		int trackTop = LIST_TOP;
		int trackHeight = visible * CARD_SPACING - 4;
		int thumbHeight = Math.max(10, trackHeight * visible / total);
		int thumbTop = trackTop + (trackHeight - thumbHeight) * this.scroll / this.maxScroll();
		graphics.fill(x, trackTop, x + SCROLLBAR_W, trackTop + trackHeight, TRACK);
		graphics.fill(x, thumbTop, x + SCROLLBAR_W, thumbTop + thumbHeight, THUMB);
	}

	@Override
	public void onClose() {
		ModConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
