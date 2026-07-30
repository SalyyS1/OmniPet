package io.github.salyvn.omnipet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;

import io.github.salyvn.omnipet.api.Egg;
import io.github.salyvn.omnipet.api.EggType;
import io.github.salyvn.omnipet.api.OmniPet;
import io.github.salyvn.omnipet.api.Pet;
import io.github.salyvn.omnipet.api.PetComponent;
import io.github.salyvn.omnipet.api.PetComponent.Config;
import io.github.salyvn.omnipet.api.PetPlayer;
import io.github.salyvn.omnipet.api.PetType;
import io.github.salyvn.omnipet.api.ScopeSource;
import io.github.salyvn.omnipet.api.item.ItemIdentifier;
import io.github.salyvn.omnipet.api.item.PetItem;
import io.github.salyvn.omnipet.gui.GuiConfig;
import io.github.salyvn.omnipet.gui.PluginChestInterface;
import io.github.salyvn.omnipet.impl.EggTypeImpl;
import io.github.salyvn.omnipet.impl.OmniPetConfig;
import io.github.salyvn.omnipet.impl.PetPlayerImpl;
import io.github.salyvn.omnipet.impl.PetTypeImpl;
import io.github.salyvn.omnipet.impl.component.DisplayComponent;
import io.github.salyvn.omnipet.impl.component.GeneralComponent;
import io.github.salyvn.omnipet.impl.component.HatchingComponent;
import io.github.salyvn.omnipet.impl.component.LevelingComponent;
import io.github.salyvn.omnipet.impl.component.StaminaComponent;
import io.github.salyvn.omnipet.impl.component.trigger.TriggerComponent;
import io.github.salyvn.omnipet.impl.event.InventoryListener;
import io.github.salyvn.omnipet.impl.event.PlayerActionListener;
import io.github.salyvn.omnipet.impl.event.PlayerLifecycleListener;
import io.github.salyvn.omnipet.impl.item.ItemIdentifierImpl;
import io.github.salyvn.omnipet.impl.item.PetItemConfig;
import io.github.salyvn.omnipet.impl.item.PetItemKeys;
import io.github.salyvn.omnipet.language.LanguageConfig;
import io.github.salyvn.omnipet.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.persistence.LegacyDataMigrator;
import io.github.salyvn.omnipet.thirdparty.ThirdPartyHook;
import io.github.salyvn.omnipet.thirdparty.mmoitems.MMOItemsHook;
import io.github.salyvn.omnipet.thirdparty.mythiclib.MythicLibHook;
import io.github.salyvn.omnipet.utils.expr.MaterialValue;
import io.github.salyvn.omnipet.utils.expr.MethodValues;
import io.github.salyvn.omnipet.utils.expr.PlayerValue;
import io.github.nahkd123.tinyexpr.Value;
import io.github.nahkd123.tinyexpr.impl.MethodValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class OmniPetPlugin extends JavaPlugin implements OmniPet {
	private static final long AUTOSAVE_INTERVAL_TICKS = 20L * 30L;
	private static final int AUTOSAVE_BATCH_SIZE = 16;
	private static final String LEGACY_DATA_FOLDER_NAME = "Passive" + "Pet";
	private static final Map<String, Supplier<ThirdPartyHook>> HOOKS = Map.ofEntries(
			Map.entry("MMOItems", MMOItemsHook::new),
			Map.entry("MythicLib", MythicLibHook::new));

	private BiMap<String, Codec<? extends PetComponent.Config<?>>> components = HashBiMap.create();
	private List<ItemIdentifier> itemIdentifiers = new ArrayList<>();
	private Map<Class<?>, List<Pair<String, Function<ScopeSource, Value>>>> providers = new HashMap<>();
	private List<ThirdPartyHook> hooks = new ArrayList<>();

	private BiMap<String, PetTypeImpl> pets = HashBiMap.create();
	private BiMap<String, EggTypeImpl> eggs = HashBiMap.create();
	private Map<UUID, PetPlayerImpl> players = new HashMap<>();
	private final Deque<UUID> autosaveQueue = new ArrayDeque<>();
	private final Set<UUID> quarantinedPlayers = new HashSet<>();
	private final Set<UUID> quarantineWarnings = new HashSet<>();
	private OmniPetConfig globalConfig;
	private GuiConfig guiConfig;
	private LanguageConfig langConfig;
	private PetItemConfig itemConfig;
	private ItemIdentifierImpl defaultItemIdentifier;

	private File petsFolder;
	private File playerDataFolder;
	private File playerQuarantineFolder;

	@Override
	public void onLoad() {
		petsFolder = new File(getDataFolder(), "pets");
		playerDataFolder = new File(getDataFolder(), "data/players");
		playerQuarantineFolder = new File(getDataFolder(), "data/quarantine");

		registerComponent("display", DisplayComponent.Config.CODEC);
		registerComponent("general", GeneralComponent.Config.CODEC);
		registerComponent("hatching", HatchingComponent.Config.CODEC);
		registerComponent("leveling", LevelingComponent.Config.CODEC);
		registerComponent("stamina", StaminaComponent.Config.CODEC);
		registerComponent("trigger", TriggerComponent.Config.CODEC);

		registerItemIdentifier(defaultItemIdentifier = new ItemIdentifierImpl(() -> itemConfig, new PetItemKeys(this)));

		MethodValue print = MethodValues.ofVoid(msg -> getComponentLogger().info(Component.text(msg).color(NamedTextColor.AQUA)), String.class);
		registerProvider("player", ScopeSource.Player.class, s -> new PlayerValue(s.player()));
		registerProvider("items", ScopeSource.Global.class, s -> MaterialValue.VALUE);
		registerProvider("print", ScopeSource.Global.class, s -> print);

		for (Map.Entry<String, Supplier<ThirdPartyHook>> e : HOOKS.entrySet()) {
			String name = e.getKey();
			Supplier<ThirdPartyHook> factory = e.getValue();

			if (getServer().getPluginManager().getPlugin(name) != null) {
				getLogger().info("Found plugin %s, initializing hook...".formatted(name));
				ThirdPartyHook hook = factory.get();
				hooks.add(hook);
				hook.onLoad(this);
			}
		}
	}

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerActionListener(this), this);
		getServer().getPluginManager().registerEvents(new InventoryListener(), this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			for (Player p : Bukkit.getOnlinePlayers()) {
				PetPlayerImpl data = players.get(p.getUniqueId());
				if (data == null) data = loadData(p.getUniqueId());
				Egg egg = data.currentEgg();

				if (egg != null && !egg.timeLeft().isPositive() && egg.type().pets().size() != 0
						&& data.pets().size() < data.capacity()) {
					int i = ThreadLocalRandom.current().nextInt(egg.type().pets().size());
					PetType petType = egg.type().pets().get(i);
					Pet pet = petType.createDefault();
					data.setEgg(null);
					data.addPet(pet);
					HatchingComponent hatching = pet.component(HatchingComponent.class);
					if (hatching != null) hatching.roll(egg.type());
					langConfig.messages().getHatched(egg.type(), pet).forEach(c -> p.sendMessage(c));
				}

				if (p.getOpenInventory().getTopInventory() != null && p.getOpenInventory().getTopInventory().getHolder() instanceof PluginChestInterface ui) ui.onRefresh();
				data.refreshLiveData(this);
			}
		}, 0, 5L);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, this::autosavePlayers,
				AUTOSAVE_INTERVAL_TICKS, AUTOSAVE_INTERVAL_TICKS);
		loadData();
		for (ThirdPartyHook hook : hooks) hook.onEnable(this);
	}

	@Override
	public void onDisable() {
		for (ThirdPartyHook hook : hooks) hook.onDisable(this);
		unloadData();
	}

	private void loadData() {
		importLegacyDataIfNeeded();
		if (!new File(getDataFolder(), "config.yml").exists()) {
			saveResource("example/MANUAL.md", "MANUAL.md");
			saveResource("example/config.yml", "config.yml");
			saveResource("example/eggs.yml", "eggs.yml");
			saveResource("example/gui.yml", "gui.yml");
			saveResource("example/lang.yml", "lang.yml");
			saveResource("example/items.yml", "items.yml");
			saveResource("example/pets/example_pet.yml", "pets/example_pet.yml");
			saveResource("example/pets/nahara.yml", "pets/nahara.yml");
		}

		getLogger().info("Loading plugin configurations and data...");

		// Global and GUI configs must be loaded or it will malfunction
		// Hence the lack of error check here
		globalConfig = loadYaml(OmniPetConfig.CODEC, new File(getDataFolder(), "config.yml")).getPartialOrThrow();
		guiConfig = loadYaml(GuiConfig.CODEC, new File(getDataFolder(), "gui.yml")).getPartialOrThrow();
		langConfig = loadYaml(LanguageConfig.CODEC, new File(getDataFolder(), "lang.yml")).getPartialOrThrow();
		itemConfig = loadYaml(PetItemConfig.CODEC, new File(getDataFolder(), "items.yml")).getPartialOrThrow();

		if (petsFolder.exists()) {
			File[] petFiles = petsFolder.listFiles(file -> {
				String name = file.getName().toLowerCase(Locale.ROOT);
				return file.isFile() && (name.endsWith(".yml") || name.endsWith(".yaml"));
			});
			if (petFiles == null) {
				getLogger().warning("Unable to list pet configuration folder: %s".formatted(petsFolder));
			} else {
				List<File> sortedPetFiles = new ArrayList<>(List.of(petFiles));
				sortedPetFiles.sort(Comparator.comparing(File::getName));
				for (File petFile : sortedPetFiles) {
					String filename = petFile.getName();
					String id = filename.substring(0, filename.lastIndexOf('.'));
					getLogger().info("Loaded pet type with ID %s".formatted(id));

					DataResult<PetTypeImpl> result = loadYaml(PetTypeImpl.CODEC, petFile);

					if (!result.hasResultOrPartial()) {
						getLogger().severe("Failed to load %s: %s".formatted(filename, result.error().get().message()));
						continue;
					} else if (result.isError()) {
						getLogger().warning("Loaded %s with error: %s".formatted(filename, result.error().get().message()));
					}

					pets.put(id, result.getPartialOrThrow());
				}
			}
		}

		DataResult<Map<String, EggTypeImpl>> eggsResult = loadYaml(Codec.unboundedMap(Codec.STRING, EggTypeImpl.CODEC), new File(getDataFolder(), "eggs.yml"));
		if (!eggsResult.hasResultOrPartial()) {
			getLogger().severe("Failed to load eggs.yml: %s".formatted(eggsResult.error().get().message()));
		} else {
			if (eggsResult.isError()) getLogger().warning("Loaded eggs.yml with error: %s".formatted(eggsResult.error().get().message()));
			eggs.putAll(eggsResult.getPartialOrThrow());

			for (Map.Entry<String, EggTypeImpl> e : eggs.entrySet()) {
				if (e.getValue().pets().size() == 0) getLogger().warning("Egg with ID %s have no pets!".formatted(e.getKey()));
			}
		}

		for (Player p : getServer().getOnlinePlayers()) loadPlayer(p);
	}

	public void loadPlayer(Player p) {
		PetPlayerImpl data = loadData(p.getUniqueId());
		players.put(p.getUniqueId(), data);
		data.setupLiveData(this);
	}

	private void unloadData() {
		for (PetPlayerImpl data : players.values().toArray(PetPlayerImpl[]::new)) {
			data.destroyLiveData();
			saveData(data);
		}

		for (Player p : Bukkit.getOnlinePlayers()) {
			Inventory inv = p.getOpenInventory().getTopInventory();
			if (inv != null && inv.getHolder() instanceof PluginChestInterface) p.closeInventory();
		}

		players.clear();
		autosaveQueue.clear();
		eggs.clear();
		pets.clear();
	}

	public void unloadPlayer(Player p) {
		PetPlayerImpl data = players.get(p.getUniqueId());
		if (data == null) return;
		data.destroyLiveData();
		saveData(data);
		players.remove(p.getUniqueId());
		autosaveQueue.remove(p.getUniqueId());
	}

	public OmniPetConfig getGlobalConfig() {
		return globalConfig;
	}

	public GuiConfig getGuiConfig() {
		return guiConfig;
	}

	public LanguageConfig getLanguageConfig() {
		return langConfig;
	}

	public PetItemConfig getItemConfig() {
		return itemConfig;
	}

	public ItemIdentifierImpl getDefaultItemIdentifier() {
		return defaultItemIdentifier;
	}

	@Override
	public BiMap<String, Codec<? extends PetComponent.Config<?>>> components() {
		return components;
	}

	@Override
	public BiMap<String, ? extends PetType> pets() {
		return pets;
	}

	@Override
	public BiMap<String, ? extends EggType> eggs() {
		return eggs;
	}

	@Override
	public <T extends PetComponent> void registerComponent(String id, Codec<? extends Config<T>> codec) {
		if (components.containsKey(id)) throw new IllegalArgumentException("Another component with ID %s is already registered".formatted(id));
		components.put(id, codec);
	}

	@Override
	public void registerItemIdentifier(ItemIdentifier identifier) {
		if (itemIdentifiers.contains(identifier)) return;
		itemIdentifiers.add(identifier);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends ScopeSource> void registerProvider(String id, Class<T> type, Function<T, Value> provider) {
		List<Pair<String, Function<ScopeSource, Value>>> list = providers.computeIfAbsent(type, $ -> new ArrayList<>());
		list.add(Pair.of(id, (Function<ScopeSource, Value>) provider));
	}

	@Override
	public void reloadData() {
		unloadData();
		loadData();
	}

	@Override
	public PetPlayer player(UUID uuid) {
		return players.get(uuid);
	}

	@Override
	public PetPlayerImpl loadData(UUID uuid) {
		File dataFile = new File(playerDataFolder, "%s.yml".formatted(uuid.toString()));
		if (!dataFile.exists()) {
			if (hasQuarantine(uuid)) {
				quarantinedPlayers.add(uuid);
				warnQuarantined(uuid);
			}
			return emptyPlayer(uuid);
		}

		DataResult<PetPlayerImpl> result = loadYaml(PetPlayerImpl.CODEC, dataFile);

		if (result.isError()) {
			getLogger().warning("Failed to load data for %s: %s".formatted(uuid, result.error().get().message()));
			quarantinedPlayers.add(uuid);
			quarantinePlayerData(uuid, dataFile);
			return emptyPlayer(uuid);
		}

		PetPlayerImpl loaded = result.getOrThrow();
		if (!uuid.equals(loaded.uuid())) {
			getLogger().warning("Rejected player data %s: embedded UUID %s does not match the requested UUID.".formatted(uuid, loaded.uuid()));
			quarantinedPlayers.add(uuid);
			quarantinePlayerData(uuid, dataFile);
			return emptyPlayer(uuid);
		}

		quarantinedPlayers.remove(uuid);
		quarantineWarnings.remove(uuid);
		return loaded;
	}

	@Override
	public void saveData(PetPlayer data) {
		if (!(data instanceof PetPlayerImpl impl)) throw new IllegalArgumentException("Invalid PetPlayer implementation");
		if (quarantinedPlayers.contains(data.uuid())) {
			warnQuarantined(data.uuid());
			return;
		}
		Egg liveEgg = impl.currentEgg();
		if (liveEgg != null) impl.setEgg(liveEgg);
		File dataFile = new File(playerDataFolder, "%s.yml".formatted(data.uuid().toString()));
		DataResult<Void> result = saveYaml(PetPlayerImpl.CODEC, impl, dataFile);

		if (result.isError()) {
			if (result.hasResultOrPartial()) getLogger().warning("Saved data for %s with error: %s".formatted(data.uuid(), result.error().get().message()));
			else getLogger().warning("Failed to save data for %s: %s".formatted(data.uuid(), result.error().get().message()));
		}
	}

	@Override
	public PetItem identifyItem(ItemStack stack) {
		PetItem out;

		for (ItemIdentifier i : itemIdentifiers) {
			out = i.identify(stack, this);
			if (out != null) return out;
		}

		return null;
	}

	public List<Pair<String, Value>> forScope(ScopeSource source) {
		List<Pair<String, Function<ScopeSource, Value>>> list = providers.get(source.getClass());
		if (list == null) return Collections.emptyList();
		List<Pair<String, Value>> out = new ArrayList<>(list.size());
		for (Pair<String, Function<ScopeSource, Value>> p : list) out.add(Pair.of(p.getFirst(), p.getSecond().apply(source)));
		return out;
	}

	private void saveResource(String resourcePath, String outputPath) {
		File outputFile = new File(getDataFolder(), outputPath);

		if (!outputFile.exists()) {
			getLogger().info("Copying %s to plugin's folder...".formatted(outputPath));

			try (InputStream in = getClassLoader().getResourceAsStream(resourcePath)) {
				if (in == null) throw new IOException("Bundled resource not found: " + resourcePath);
				AtomicFileStore.write(outputFile.toPath(), in.readAllBytes());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private <T> DataResult<T> loadYaml(Codec<T> codec, File file) {
		if (!file.exists()) return DataResult.error(() -> "File '%s' does not exists".formatted(file));

		try (var stream = Files.newInputStream(file.toPath())) {
			Object obj = new Yaml().load(stream);
			return codec.decode(JavaOps.INSTANCE, obj).map(Pair::getFirst);
		} catch (IOException | RuntimeException e) {
			return DataResult.error(e::getLocalizedMessage);
		}
	}

	private <T> DataResult<Void> saveYaml(Codec<T> codec, T data, File file) {
		try {
			DataResult<Object> result = codec.encodeStart(JavaOps.INSTANCE, data);
			if (result.isError()) return result.map(o -> null);

			StringWriter writer = new StringWriter();
			new Yaml().dump(result.getOrThrow(), writer);
			AtomicFileStore.write(file.toPath(), writer.toString().getBytes(StandardCharsets.UTF_8));
			return DataResult.success(null);
		} catch (IOException | RuntimeException e) {
			return DataResult.error(e::getLocalizedMessage);
		}
	}

	private PetPlayerImpl emptyPlayer(UUID uuid) {
		return new PetPlayerImpl(uuid, new ArrayList<>(), null, null, 0);
	}

	private void autosavePlayers() {
		if (autosaveQueue.isEmpty()) autosaveQueue.addAll(players.keySet());
		int processed = 0;
		while (processed++ < AUTOSAVE_BATCH_SIZE && !autosaveQueue.isEmpty()) {
			UUID uuid = autosaveQueue.removeFirst();
			PetPlayerImpl data = players.get(uuid);
			if (data != null) saveData(data);
		}
	}

	private void importLegacyDataIfNeeded() {
		Path target = getDataFolder().toPath();
		Path legacy = target.resolveSibling(LEGACY_DATA_FOLDER_NAME);
		try {
			if (LegacyDataMigrator.copyIfTargetEmpty(legacy, target)) {
				getLogger().info("Imported legacy plugin data into %s; the source directory was retained as a backup.".formatted(target));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to import legacy plugin data", e);
		}
	}

	private boolean hasQuarantine(UUID uuid) {
		try {
			return AtomicFileStore.hasQuarantine(playerQuarantineFolder.toPath(), uuid.toString());
		} catch (IOException e) {
			getLogger().severe("Unable to inspect quarantined data for %s: %s".formatted(uuid, e.getMessage()));
			return true;
		}
	}

	private void quarantinePlayerData(UUID uuid, File dataFile) {
		try {
			if (dataFile.isFile()) {
				Path quarantined = AtomicFileStore.quarantine(dataFile.toPath(), playerQuarantineFolder.toPath(), uuid.toString());
				getLogger().warning("Quarantined unreadable player data for %s at %s".formatted(uuid, quarantined));
			}
		} catch (IOException e) {
			getLogger().severe("Unable to quarantine unreadable player data for %s: %s".formatted(uuid, e.getMessage()));
		}
	}

	private void warnQuarantined(UUID uuid) {
		if (quarantineWarnings.add(uuid)) {
			getLogger().warning("Player data for %s is quarantined; refusing to overwrite it until it is repaired and reloaded.".formatted(uuid));
		}
	}
}
