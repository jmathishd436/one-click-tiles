package com.oneclick;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class OneClickOverlay extends Overlay {
    private final Client client;
    private final OneClickPlugin plugin;

    @Inject
    private OneClickConfig config;

    @Inject
    private OneClickOverlay(Client client, OneClickConfig config, OneClickPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.MED);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (final WorldPoint point : plugin.points) {
            if (point.getPlane() != client.getPlane()) {
                continue;
            }

            drawTile(graphics, point);
        }
        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point) {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        if (point.distanceTo(playerLocation) >= 30) {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return;
        }

        if (config.drawBorders()) {
            OverlayUtil.renderPolygon(graphics, poly, config.borderColor());
        }

        if (config.drawFill()) {
            graphics.setColor(config.fillColor());
            graphics.fill(poly);
        }
    }
}
