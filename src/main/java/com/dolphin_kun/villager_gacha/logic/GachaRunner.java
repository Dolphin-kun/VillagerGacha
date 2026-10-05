package com.dolphin_kun.villager_gacha.logic;

import com.dolphin_kun.villager_gacha.VillagerGacha;
import com.dolphin_kun.villager_gacha.config.ModConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class GachaRunner {
	private static final double VILLAGER_SEARCH_RANGE = 8.0;
	private static final int LIBRARIAN_TIMEOUT = 200;
	private static final int OFFERS_TIMEOUT = 40;
	private static final int BREAK_TIMEOUT = 100;
	private static final int JOBLESS_TIMEOUT = 100;
	private static final int MAX_PLACE_TRIES = 5;
	private static final int MAX_OPEN_TRIES = 10;

	private Phase phase = Phase.OFF;
	private int wait;
	private int phaseTicks;
	private int villagerId = -1;
	private int menuIdBeforeOpen = -1;
	private int offersBeforeOpen;
	private int placeTries;
	private int openTries;
	private boolean destroyStarted;

	private int pulls;
	private Component status = Component.empty();
	private Component lastSeen = Component.empty();
	private List<Books.Offer> lastBooks = List.of();

	private int resumeIn;
	private int resumeStreak;
	private boolean restartCycle;

	public Phase phase() {
		return this.phase;
	}

	public Component status() {
		return this.status;
	}

	public Component lastSeen() {
		return this.lastSeen;
	}

	public List<Books.Offer> lastBooks() {
		return this.lastBooks;
	}

	public int pulls() {
		return this.pulls;
	}

	public int resumeIn() {
		return this.resumeIn;
	}

	public int resumeStreak() {
		return this.resumeStreak;
	}

	public void toggle(Minecraft mc) {
		switch (this.phase) {
			case OFF, DONE -> this.begin(mc);
			case PAUSED -> {
				this.resumeStreak = 0;
				this.resume(mc);
			}
			default -> this.turnOff(mc, Texts.tr("stop.by_player"));
		}
	}

	public boolean hidesTradeScreen() {
		return ModConfig.get().hideTradeScreen && (this.phase == Phase.OPENING || this.phase == Phase.AWAIT_OFFERS);
	}

	public void emergencyStop(Minecraft mc) {
		if (this.phase != Phase.OFF) {
			this.turnOff(mc, Texts.tr("stop.emergency"));
		}
	}

	public void tick(Minecraft mc) {
		if (this.phase == Phase.PAUSED) {
			if (this.resumeIn > 0 && --this.resumeIn == 0) {
				this.resume(mc);
			}
			return;
		}
		if (!this.phase.running()) {
			return;
		}
		if (this.wait > 0) {
			this.wait--;
			return;
		}
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			this.turnOff(mc, Texts.tr("stop.left_world"));
			return;
		}
		this.phaseTicks++;
		ModConfig cfg = ModConfig.get();

		switch (this.phase) {
			case CHECKING -> this.check(mc, player, level, cfg);
			case NEED_LECTERN -> {
				if (Hotbar.prepare(mc, player, Items.LECTERN)) {
					Texts.notify(mc, Texts.tr("msg.lectern_found"));
					this.enter(Phase.PLACING, 1);
				} else {
					this.wait = 10;
				}
			}
			case PLACING -> this.place(mc, player, level, cfg);
			case AWAIT_LIBRARIAN -> this.awaitLibrarian(mc, level, cfg);
			case OPENING -> this.open(mc, player, level);
			case AWAIT_OFFERS -> {
				if (this.receivedOffers(player).isPresent()) {
					this.enter(Phase.EVALUATING, 0);
				} else if (this.phaseTicks > OFFERS_TIMEOUT) {
					if (++this.openTries >= MAX_OPEN_TRIES) {
						this.pauseNoTradeScreen(mc, player, level);
					} else {
						this.enter(Phase.OPENING, 10);
					}
				}
			}
			case EVALUATING -> this.evaluate(mc, player, cfg);
			case CLOSING -> {
				this.closeTrade(mc, player);
				this.enter(Phase.BREAKING, cfg.breakDelayTicks);
			}
			case BREAKING -> this.breakLectern(mc, player, level, cfg);
			case AWAIT_JOBLESS -> this.awaitJobless(mc, level);
			case COOLDOWN -> this.toPlacingOrWait(mc, player);
			default -> {
			}
		}
	}

	private void check(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig cfg) {
		if (cfg.targets.isEmpty()) {
			this.pause(mc, Texts.tr("error.no_targets"), false);
			return;
		}
		if (cfg.lectern == null) {
			this.pause(mc, Texts.tr("error.no_spot", VillagerGacha.setSpotKey.getTranslatedKeyMessage()), false);
			return;
		}
		BlockPos spot = cfg.lectern.toBlockPos();
		Optional<Villager> villager = this.chooseVillager(player, level, spot);
		if (villager.isEmpty()) {
			this.pause(mc, Texts.tr("error.no_villager"), true);
			return;
		}
		this.villagerId = villager.get().getId();
		this.placeTries = 0;
		boolean lecternPlaced = level.getBlockState(spot).is(Blocks.LECTERN);
		boolean restart = this.restartCycle;
		this.restartCycle = false;
		if (lecternPlaced && restart) {
			this.enter(Phase.BREAKING, 0);
		} else if (lecternPlaced) {
			this.enter(Phase.AWAIT_LIBRARIAN, 0);
		} else {
			this.toPlacingOrWait(mc, player);
		}
	}

	private void place(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig cfg) {
		BlockPos spot = cfg.lectern.toBlockPos();
		BlockState there = level.getBlockState(spot);
		if (there.is(Blocks.LECTERN)) {
			this.enter(Phase.AWAIT_LIBRARIAN, 0);
			return;
		}
		if (!there.canBeReplaced()) {
			this.pause(mc, Texts.tr("error.spot_blocked"), true);
			return;
		}
		if (!Hotbar.hold(player, stack -> stack.is(Items.LECTERN))) {
			this.toPlacingOrWait(mc, player);
			return;
		}
		BlockPos below = spot.below();
		Vec3 top = Vec3.atCenterOf(below).add(0, 0.5, 0);
		if (this.phaseTicks == 1 && this.face(player, top)) {
			this.wait = 1;
			return;
		}
		Clicks.useOnBlock(mc, player, new BlockHitResult(top, Direction.UP, below, false));
		this.placeTries++;
		this.enter(Phase.AWAIT_LIBRARIAN, 2);
	}

	private void awaitLibrarian(Minecraft mc, ClientLevel level, ModConfig cfg) {
		if (!(level.getEntity(this.villagerId) instanceof Villager villager) || !villager.isAlive()) {
			this.pause(mc, Texts.tr("error.villager_lost"), true);
			return;
		}
		BlockPos spot = cfg.lectern.toBlockPos();
		if (!level.getBlockState(spot).is(Blocks.LECTERN)) {
			if (this.phaseTicks > 20) {
				if (this.placeTries >= MAX_PLACE_TRIES) {
					this.pause(mc, Texts.tr("error.place_failed"), true);
				} else {
					this.enter(Phase.PLACING, 0);
				}
			}
			return;
		}
		this.placeTries = 0;
		if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
			this.status = Texts.tr("status.opening");
			this.openTries = 0;
			this.enter(Phase.OPENING, cfg.openDelayTicks);
		} else if (this.phaseTicks > LIBRARIAN_TIMEOUT) {
			this.enter(Phase.BREAKING, 0);
		}
	}

	private void awaitJobless(Minecraft mc, ClientLevel level) {
		if (!(level.getEntity(this.villagerId) instanceof Villager villager) || !villager.isAlive()) {
			this.pause(mc, Texts.tr("error.villager_lost"), true);
			return;
		}
		if (villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
			this.enter(Phase.COOLDOWN, ModConfig.get().placeDelayTicks);
		} else if (this.phaseTicks > JOBLESS_TIMEOUT) {
			this.pause(mc, Texts.tr("error.job_locked"), false);
		}
	}

	private void open(Minecraft mc, LocalPlayer player, ClientLevel level) {
		Entity villager = level.getEntity(this.villagerId);
		if (villager == null || !villager.isAlive()) {
			this.pause(mc, Texts.tr("error.villager_lost"), true);
			return;
		}
		if (villager instanceof Villager v && v.isSleeping()) {
			this.status = Texts.tr("status.villager_sleeping");
			this.enter(Phase.OPENING, 20);
			return;
		}
		Vec3 center = villager.getBoundingBox().getCenter();
		if (this.phaseTicks == 1 && this.face(player, center)) {
			this.wait = 1;
			return;
		}
		EntityHitResult hit = mc.hitResult instanceof EntityHitResult aimed && aimed.getEntity() == villager
			? aimed
			: new EntityHitResult(villager, center);
		this.menuIdBeforeOpen = player.containerMenu.containerId;
		this.offersBeforeOpen = TradeWatcher.received();
		Clicks.useOnEntity(mc, player, villager, hit);
		this.enter(Phase.AWAIT_OFFERS, 0);
	}

	private void evaluate(Minecraft mc, LocalPlayer player, ModConfig cfg) {
		Optional<MerchantOffers> offers = this.receivedOffers(player);
		if (offers.isEmpty()) {
			this.enter(Phase.OPENING, 10);
			return;
		}
		List<Books.Offer> books = Books.enchantedBooks(offers.get());
		this.pulls++;
		this.resumeStreak = 0;
		this.lastBooks = books;
		this.lastSeen = books.isEmpty() ? Texts.tr("book.none") : books.getLast().describe();

		Optional<Books.Match> match = Books.findMatch(cfg.targets, books);
		if (match.isEmpty()) {
			this.enter(Phase.CLOSING, 0);
			return;
		}

		Component found = match.get().offer().describe();
		this.closeTrade(mc, player);
		player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
		cfg.targets.remove(match.get().target());
		ModConfig.save();
		this.phase = Phase.DONE;
		this.status = Texts.tr("status.found", found);
		Texts.announce(mc, cfg.targets.isEmpty()
			? Texts.tr("msg.found_last", found, this.pulls)
			: Texts.tr("msg.found", found, this.pulls, cfg.targets.size()));
	}

	private void breakLectern(Minecraft mc, LocalPlayer player, ClientLevel level, ModConfig cfg) {
		BlockPos spot = cfg.lectern.toBlockPos();
		if (mc.gameMode == null) {
			return;
		}
		if (!level.getBlockState(spot).is(Blocks.LECTERN)) {
			mc.gameMode.stopDestroyBlock();
			this.enter(Phase.AWAIT_JOBLESS, 0);
			return;
		}
		if (this.phaseTicks == 1) {
			if (!cfg.protectAxe) {
				Hotbar.holdAxe(player);
			} else if (Hotbar.holdSafeAxe(player, cfg.axeMinDurability) == Hotbar.AxeCheck.ALL_WORN) {
				this.pause(mc, Texts.tr("error.axe_worn", cfg.axeMinDurability), false);
				return;
			}
			if (this.face(player, Vec3.atCenterOf(spot))) {
				this.wait = 1;
				return;
			}
		}
		if (!this.destroyStarted) {
			mc.gameMode.startDestroyBlock(spot, Direction.UP);
			Clicks.swingWhileMining(player);
			this.destroyStarted = true;
		} else if (mc.gameMode.continueDestroyBlock(spot, Direction.UP)) {
			Clicks.swingWhileMining(player);
		}
		if (this.phaseTicks > BREAK_TIMEOUT) {
			mc.gameMode.stopDestroyBlock();
			this.pause(mc, Texts.tr("error.break_failed"), true);
		}
	}

	private boolean face(LocalPlayer player, Vec3 target) {
		if (!ModConfig.get().faceTargets) {
			return false;
		}
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
		return true;
	}

	private Optional<MerchantOffers> receivedOffers(LocalPlayer player) {
		if (player.containerMenu instanceof MerchantMenu menu
			&& menu.containerId != this.menuIdBeforeOpen
			&& !menu.getOffers().isEmpty()) {
			return Optional.of(menu.getOffers());
		}
		if (TradeWatcher.received() != this.offersBeforeOpen && !TradeWatcher.offers().isEmpty()) {
			return Optional.of(TradeWatcher.offers());
		}
		return Optional.empty();
	}

	private void closeTrade(Minecraft mc, LocalPlayer player) {
		if (player.containerMenu instanceof MerchantMenu) {
			player.closeContainer();
		} else if (TradeWatcher.received() != this.offersBeforeOpen && mc.getConnection() != null) {
			mc.getConnection().send(new ServerboundContainerClosePacket(TradeWatcher.containerId()));
		}
	}

	private static void closeHiddenTrade(Minecraft mc) {
		if (mc.player != null && mc.player.containerMenu instanceof MerchantMenu && mc.gui.screen() == null) {
			mc.player.closeContainer();
		}
	}

	private void toPlacingOrWait(Minecraft mc, LocalPlayer player) {
		if (Hotbar.prepare(mc, player, Items.LECTERN)) {
			this.enter(Phase.PLACING, 1);
		} else {
			Texts.notify(mc, Texts.tr("msg.need_lectern"));
			this.enter(Phase.NEED_LECTERN, 10);
		}
	}

	private Optional<Villager> chooseVillager(LocalPlayer player, ClientLevel level, BlockPos spot) {
		Vec3 center = Vec3.atCenterOf(spot);
		double size = VILLAGER_SEARCH_RANGE * 2;
		boolean lecternPlaced = level.getBlockState(spot).is(Blocks.LECTERN);
		return level.getEntitiesOfClass(Villager.class, AABB.ofSize(center, size, size, size), v -> v.isAlive() && !v.isBaby()).stream()
			.min(Comparator
				.comparing((Villager v) -> !player.isWithinEntityInteractionRange(v, 0))
				.thenComparing(v -> !isResettable(v, lecternPlaced))
				.thenComparingDouble(v -> v.distanceToSqr(center)));
	}

	private static boolean isResettable(Villager villager, boolean lecternPlaced) {
		var profession = villager.getVillagerData().profession();
		return profession.is(VillagerProfession.NONE) || (lecternPlaced && profession.is(VillagerProfession.LIBRARIAN));
	}

	private void pauseNoTradeScreen(Minecraft mc, LocalPlayer player, ClientLevel level) {
		Entity villager = level.getEntity(this.villagerId);
		if (villager instanceof Villager v) {
			VillagerGacha.LOGGER.warn("Trade screen did not open: distance={} reachable={} profession={} sleeping={}",
				String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(player.distanceToSqr(v))),
				player.isWithinEntityInteractionRange(v, 0),
				v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("?"),
				v.isSleeping());
		}
		boolean tooFar = villager != null && !player.isWithinEntityInteractionRange(villager, 0);
		this.pause(mc, Texts.tr(tooFar ? "error.no_trade_screen_far" : "error.no_trade_screen"), true);
	}

	private void begin(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		this.pulls = 0;
		this.resumeStreak = 0;
		this.lastSeen = Component.empty();
		this.lastBooks = List.of();
		Texts.notify(mc, Texts.tr("msg.started", VillagerGacha.emergencyKey.getTranslatedKeyMessage()));
		this.enter(Phase.CHECKING, 0);
	}

	private void resume(Minecraft mc) {
		this.resumeIn = 0;
		if (mc.player == null) {
			return;
		}
		if (mc.player.containerMenu != mc.player.inventoryMenu) {
			mc.player.closeContainer();
		}
		if (mc.gameMode != null) {
			mc.gameMode.stopDestroyBlock();
		}
		this.restartCycle = true;
		Texts.notify(mc, Texts.tr("msg.resumed"));
		this.enter(Phase.CHECKING, 0);
	}

	private void turnOff(Minecraft mc, Component reason) {
		this.phase = Phase.OFF;
		this.resumeIn = 0;
		this.status = reason;
		closeHiddenTrade(mc);
		Texts.notify(mc, Texts.tr("msg.stopped", reason));
	}

	private void pause(Minecraft mc, Component reason, boolean canRetry) {
		ModConfig cfg = ModConfig.get();
		this.phase = Phase.PAUSED;
		this.status = reason;
		this.resumeIn = 0;
		VillagerGacha.LOGGER.info("Paused: {}", reason.getString());
		closeHiddenTrade(mc);
		if (mc.player == null) {
			return;
		}
		Texts.notify(mc, Texts.tr("msg.paused", reason));
		Component toggleKey = VillagerGacha.toggleKey.getTranslatedKeyMessage();
		if (!canRetry || !cfg.autoResume) {
			Texts.detail(mc, Texts.tr("msg.resume_manually", toggleKey));
		} else if (this.resumeStreak >= cfg.autoResumeLimit) {
			Texts.detail(mc, Texts.tr("msg.auto_resume_gave_up", toggleKey));
		} else {
			this.resumeStreak++;
			this.resumeIn = cfg.autoResumeWaitTicks;
			Texts.detail(mc, Texts.tr(
				"msg.auto_resume",
				seconds(this.resumeIn),
				this.resumeStreak,
				cfg.autoResumeLimit,
				VillagerGacha.emergencyKey.getTranslatedKeyMessage()
			));
		}
	}

	private void enter(Phase next, int waitTicks) {
		this.phase = next;
		this.wait = waitTicks;
		this.phaseTicks = 0;
		this.destroyStarted = false;
		Component message = next.message(this.pulls);
		if (message != null) {
			this.status = message;
		}
	}

	public static int seconds(int ticks) {
		return (ticks + 19) / 20;
	}

	public enum Phase {
		OFF(null),
		CHECKING("status.checking"),
		NEED_LECTERN("status.need_lectern"),
		PLACING("status.placing"),
		AWAIT_LIBRARIAN("status.await_librarian"),
		OPENING(null),
		AWAIT_OFFERS("status.await_offers"),
		EVALUATING("status.evaluating"),
		CLOSING("status.no_match"),
		BREAKING("status.breaking"),
		AWAIT_JOBLESS("status.await_jobless"),
		COOLDOWN("status.cooldown"),
		DONE(null),
		PAUSED(null);

		private final String statusKey;

		Phase(String statusKey) {
			this.statusKey = statusKey;
		}

		public boolean running() {
			return this != OFF && this != DONE && this != PAUSED;
		}

		public boolean waiting() {
			return this == NEED_LECTERN || this == AWAIT_LIBRARIAN || this == AWAIT_OFFERS || this == AWAIT_JOBLESS || this == COOLDOWN;
		}

		Component message(int pulls) {
			if (this == PLACING) {
				return Texts.tr(this.statusKey, pulls + 1);
			}
			return this.statusKey == null ? null : Texts.tr(this.statusKey);
		}
	}
}
