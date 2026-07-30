package io.github.salyvn.omnipet.gui;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import io.github.salyvn.omnipet.api.LoreProvider;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.impl.OmniPetConfig;
import io.github.salyvn.omnipet.impl.component.GeneralComponent;
import io.github.salyvn.omnipet.impl.component.trigger.TriggerComponent;
import io.github.salyvn.omnipet.language.LanguageConfig;
import io.github.salyvn.omnipet.utils.ItemUtils;
import io.github.salyvn.omnipet.utils.PaginationUtils;
import io.github.salyvn.omnipet.utils.ParseUtils;
import io.github.nahkd123.tinyexpr.impl.NullValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class PetMenu implements PluginChestInterface {
	private OmniPet api;
	private PetPlayer data;
	private GuiConfig config;
	private OmniPetConfig global;
	private LanguageConfig language;
	private int page;
	private Inventory inventory;

	public PetMenu(OmniPet api, PetPlayer data, GuiConfig config, OmniPetConfig global, LanguageConfig language, int page) {
		this.api = api;
		this.data = data;
		this.config = config;
		this.global = global;
		this.language = language;
		this.page = Math.max(page, 0);
		this.inventory = Bukkit.createInventory(this, config.size(), config.title());
		showBorder();
		update();
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = Math.clamp(page, 0, getPageCount() - 1);
		update();
	}

	private int getPerPage() {
		return config.size() - 18 - (config.size() / 9 - 2) * 2;
	}

	private int getMaxEntries() {
		int currentEntries = data.pets().size() + (data.currentEgg() != null ? 1 : 0);
		return Math.max(Math.max(currentEntries, data.capacity()), global.globalMaxSlots());
	}

	private int getPageCount() {
		return Math.max(PaginationUtils.getPageCount(getPerPage(), getMaxEntries()), 1);
	}

	public void showBorder() {
		ItemStack borderStack = config.border().create();

		for (int i = 0; i < 9; i++) {
			inventory.setItem(i, borderStack);
			inventory.setItem(config.size() - 9 + i, borderStack);
		}

		for (int i = 1; i < config.size() / 9 - 1; i++) {
			inventory.setItem(i * 9, borderStack);
			inventory.setItem(i * 9 + 8, borderStack);
		}
	}

	public void update() {
		int perPage = getPerPage();
		int pageCount = getPageCount();
		page = Math.clamp(page, 0, pageCount - 1);
		int start = perPage * page;

		MiniMessage mm = MiniMessage.miniMessage();
		TagResolver pageResolver = Placeholder.unparsed("page", Integer.toString(page + 1));
		TagResolver maxPageResolver = Placeholder.unparsed("max_page", Integer.toString(pageCount));
		ItemStack emptySlotStack = config.emptySlot().create();
		ItemStack lockedSlotStack = config.lockedSlot().create();
		ItemStack voidSlotStack = config.voidSlot().create();
		ItemStack nextPageStack = config.nextPage().create(mm, new TagResolver[] { pageResolver, maxPageResolver }, null);
		ItemStack prevPageStack = config.prevPage().create(mm, new TagResolver[] { pageResolver, maxPageResolver }, null);

		for (int i = 0; i < perPage; i++) {
			int index = start + i;
			int slot = 10 + 9 * (i / 7) + (i % 7);

			if (index < data.pets().size()) {
				Pet pet = data.pets().get(index);
				boolean active = data.currentPet() == pet;
				String texture = pet.component(GeneralComponent.class) != null
						? pet.component(GeneralComponent.class).texture()
						: null;

				ItemStack stack = (active ? config.activePet() : config.pet()).create(
						mm,
						new TagResolver[] {
								TagResolver.resolver("pet_name", (q, ctx) -> {
									GeneralComponent general = pet.component(GeneralComponent.class);
									Component content = general != null
											? general.name()
											: Component.text(api.pets().inverse().get(pet.type()));
									return Tag.inserting(content);
								})},
						name -> {
							LoreProvider provider = pet.lore(name);
							return provider != null ? provider.provideLore() : List.of();
						});

				if (texture != null) {
					stack = stack.withType(Material.PLAYER_HEAD);
					if (stack.getItemMeta() instanceof SkullMeta skullMeta) {
						skullMeta.setOwnerProfile(ItemUtils.skinFromUrl(texture));
						stack.setItemMeta(skullMeta);
					}
				}

				inventory.setItem(slot, stack);
			} else if (index == data.pets().size() && data.currentEgg() != null) {
				ItemStack stack = config.egg().create(mm, new TagResolver[] {
						Placeholder.component("egg_name", data.currentEgg().type().name()),
						Placeholder.unparsed("egg_hatch_duration", ParseUtils.toString(data.currentEgg().timeLeft()))
				}, null);
				inventory.setItem(slot, stack);
			} else {
				inventory.setItem(slot, index < data.capacity()
						? emptySlotStack
						: index < global.globalMaxSlots() ? lockedSlotStack
						: voidSlotStack);
			}
		}

		inventory.setItem(config.size() - 7, prevPageStack);
		inventory.setItem(config.size() - 3, nextPageStack);
	}

	@Override
	public @NotNull Inventory getInventory() {
		return inventory;
	}

	@Override
	public void onClick(InventoryClickEvent event) {
		event.setResult(Result.DENY);
		if (event.getClickedInventory() != inventory) return;
		int perPage = getPerPage();

		if (event.getSlot() == config.size() - 7) {
			if (page > 0) setPage(page - 1);
			return;
		}

		if (event.getSlot() == config.size() - 3) {
			if (page < getPageCount() - 1) setPage(page + 1);
			return;
		}

		int x = event.getSlot() % 9;
		int y = event.getSlot() / 9;

		if (y != 0 && y != config.size() / 9 - 1 && x != 0 && x != 8) {
			int start = perPage * page;
			int index = start + (y - 1) * 7 + x - 1;
			if (index >= data.pets().size()) return;
			Pet pet = data.pets().get(index);

			switch (event.getClick()) {
			case LEFT: {
				if (data.player() != null) {
					Player player = data.player();
					List<Component> message = data.currentPet() == pet
							? language.messages().getRecall(pet)
							: data.currentPet() != null ? language.messages().getSummonAndRecall(pet, data.currentPet())
							: language.messages().getSummon(pet);
					for (Component line : message) player.sendMessage(line);
				}

				data.summon(data.currentPet() == pet ? null : pet);
				update();
				return;
			}
			case SHIFT_RIGHT:
				TriggerComponent triggerer = pet.component(TriggerComponent.class);
				if (triggerer != null) triggerer.trigger("release", NullValue.NULL);

				if (data.player() != null) {
					Player player = data.player();
					language.messages().getReleased(pet).forEach(player::sendMessage);
					player.playSound(player.getEyeLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1f, 1f, 0L);
				}

				data.removePet(pet);
				update();
				return;
			default:
				break;
			}
		}
	}

	@Override
	public void onClose(InventoryCloseEvent event) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onRefresh() {
		update();
	}
}
