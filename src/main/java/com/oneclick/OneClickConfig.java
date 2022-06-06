package com.oneclick;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.*;

@ConfigGroup("oneClick")
public interface OneClickConfig extends Config {
    @ConfigItem(
            keyName = "drawBorders",
            name = "Draw one-click tile borders",
            description = "Configures whether to draw the border of one click tiles"
    )
    default boolean drawBorders() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "borderColor",
            name = "Border Color",
            description = "Configures the color of the border of one click tiles"
    )
    default Color borderColor() {
        return Color.BLUE;
    }

    @ConfigItem(
            keyName = "drawFill",
            name = "Fill one-click tiles",
            description = "Configures whether to fill one click tiles"
    )
    default boolean drawFill() {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "fillColor",
            name = "Fill Color",
            description = "Configures the color of the fill of one click tiles"
    )
    default Color fillColor() {
        return new Color(82, 82, 255, 64);
    }
}
