package com.oneclick;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Example"
)
public class OneClickPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OneClickConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private OneClickOverlay overlay;

	private static final String CONFIG_GROUP = "tilemanMode";
	private static final String REGION_PREFIX = "region_";
	private static final Gson GSON = new Gson();

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
		overlayManager.add(this.overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
		overlayManager.remove(this.overlay);
		this.points.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!configChanged.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		this.loadPoints();
	}

	public final List<WorldPoint> points = new ArrayList<>();
	private void loadPoints() {
		points.clear();

		int[] regions = client.getMapRegions();

		if (regions == null) {
			return;
		}

		for (int regionId : regions) {
			// load points for region
			log.debug("Loading points for region {}", regionId);
			Collection<WorldPoint> worldPoint = translateToWorldPoint(getTiles(regionId));
			points.addAll(worldPoint);
		}

		log.info("NATH loaded points " + this.points.size());
	}

	private Collection<TilemanModeTile> getConfiguration(String configGroup, String key) {
		String json = configManager.getConfiguration(configGroup, key);

		if (Strings.isNullOrEmpty(json)) {
			return Collections.emptyList();
		}

		return GSON.fromJson(json, new TypeToken<List<TilemanModeTile>>() {
		}.getType());
	}

	Collection<TilemanModeTile> getTiles(int regionId) {
		return getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
	}

	private Collection<WorldPoint> translateToWorldPoint(Collection<TilemanModeTile> points) {
		if (points.isEmpty()) {
			return Collections.emptyList();
		}

		return points.stream()
				.map(point -> WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()))
				.flatMap(worldPoint ->
				{
					final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, worldPoint);
					return localWorldPoints.stream();
				})
				.collect(Collectors.toList());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);
			this.loadPoints();
		}
	}

	@Provides
	OneClickConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OneClickConfig.class);
	}
}
