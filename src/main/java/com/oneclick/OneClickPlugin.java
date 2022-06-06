package com.oneclick;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;

import javax.inject.Inject;

import com.oneclick.pathfinder.CollisionMap;
import com.oneclick.pathfinder.Pathfinder;
import com.oneclick.pathfinder.SplitFlagMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@PluginDescriptor(
        name = "One Click Tiles",
        description = "Highlight one-click-able tiles in Tileman Mode",
        tags = {"overlay", "tiles", "tileman"}
)
public class OneClickPlugin extends Plugin {
    private static final String CONFIG_GROUP = "tilemanMode";
    private static final String REGION_PREFIX = "region_";
    private static final Gson GSON = new Gson();

    public static final int MAX_DRAW_DISTANCE = 32;

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

    private final List<WorldPoint> points = new ArrayList<>();
    public final Set<WorldPoint> oneClickTiles = new HashSet<>();
    private Pathfinder pathfinder;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(this.overlay);

        Map<SplitFlagMap.Position, byte[]> compressedRegions = new HashMap<>();
        HashMap<WorldPoint, List<WorldPoint>> transports = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(OneClickPlugin.class.getResourceAsStream("/collision-map.zip"))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String[] n = entry.getName().split("_");

                compressedRegions.put(
                        new SplitFlagMap.Position(Integer.parseInt(n[0]), Integer.parseInt(n[1])),
                        Util.readAllBytes(in)
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            String s = new String(Util.readAllBytes(OneClickPlugin.class.getResourceAsStream("/transports.txt")), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                String[] l = line.split(" ");
                WorldPoint a = new WorldPoint(Integer.parseInt(l[0]), Integer.parseInt(l[1]), Integer.parseInt(l[2]));
                WorldPoint b = new WorldPoint(Integer.parseInt(l[3]), Integer.parseInt(l[4]), Integer.parseInt(l[5]));
                transports.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        CollisionMap map = new CollisionMap(64, compressedRegions);
        pathfinder = new Pathfinder(map, transports);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(this.overlay);
        this.points.clear();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        this.updateOneClickTiles();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            this.loadPoints();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (!configChanged.getGroup().equals(CONFIG_GROUP)) {
            return;
        }

        this.loadPoints();
        this.updateOneClickTiles();
    }

    private void loadPoints() {
        points.clear();

        int[] regions = client.getMapRegions();

        if (regions == null) {
            return;
        }

        for (int regionId : regions) {
            Collection<WorldPoint> worldPoint = translateToWorldPoint(getTiles(regionId));
            points.addAll(worldPoint);
        }
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

    private void updateOneClickTiles() {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        if (this.points.isEmpty()) {
            return;
        }

        Set<WorldPoint> localPoints = new HashSet<>();
        for (final WorldPoint point : this.points) {
            if (point.distanceTo(playerLocation) < MAX_DRAW_DISTANCE) {
                localPoints.add(point);
            }
        }

        oneClickTiles.clear();
        for (final WorldPoint localPoint : localPoints) {
            Pathfinder.Path path = pathfinder.new Path(playerLocation, localPoint);
            if (localPoints.containsAll(path.getPath())) {
                oneClickTiles.add(localPoint);
            }
        }
    }

    @Provides
    OneClickConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OneClickConfig.class);
    }
}
