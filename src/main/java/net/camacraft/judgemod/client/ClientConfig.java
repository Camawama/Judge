package net.camacraft.judgemod.client;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue DISPLAY_PERSISTENT_SCORE;
    public static final ForgeConfigSpec.BooleanValue DISPLAY_PERSISTENT_LEVEL;
    public static final ForgeConfigSpec.BooleanValue DISPLAY_ACTION_POPUPS;

    public static final ForgeConfigSpec.IntValue SCORE_HUD_X;
    public static final ForgeConfigSpec.IntValue SCORE_HUD_Y;
    public static final ForgeConfigSpec.DoubleValue SCORE_HUD_SCALE;

    public static final ForgeConfigSpec.IntValue LEVEL_HUD_X;
    public static final ForgeConfigSpec.IntValue LEVEL_HUD_Y;
    public static final ForgeConfigSpec.DoubleValue LEVEL_HUD_SCALE;

    public static final ForgeConfigSpec.IntValue ACTION_POPUP_X;
    public static final ForgeConfigSpec.IntValue ACTION_POPUP_Y;
    public static final ForgeConfigSpec.DoubleValue ACTION_POPUP_SCALE;

    static {
        BUILDER.push("Client HUD Settings");

        DISPLAY_PERSISTENT_SCORE = BUILDER
                .comment("If true, the player's current Life Score will always be displayed on the HUD.")
                .define("displayPersistentScore", false);

        DISPLAY_PERSISTENT_LEVEL = BUILDER
                .comment("If true, the player's current Judge Level will always be displayed on the HUD.")
                .define("displayPersistentLevel", false);
        
        DISPLAY_ACTION_POPUPS = BUILDER
                .comment("If true, pop-ups for score changes and combos will be displayed.")
                .define("displayActionPopups", true);

        BUILDER.push("Position and Scale");

        SCORE_HUD_X = BUILDER.comment("X offset for the Score HUD element (0 is center)").defineInRange("scoreHudX", 0, -10000, 10000);
        SCORE_HUD_Y = BUILDER.comment("Y offset for the Score HUD element (0 is top)").defineInRange("scoreHudY", 15, -10000, 10000);
        SCORE_HUD_SCALE = BUILDER.comment("Scale of the Score HUD element").defineInRange("scoreHudScale", 1.0, 0.1, 5.0);

        LEVEL_HUD_X = BUILDER.comment("X offset for the Level HUD element (0 is center)").defineInRange("levelHudX", 0, -10000, 10000);
        LEVEL_HUD_Y = BUILDER.comment("Y offset for the Level HUD element (0 is top)").defineInRange("levelHudY", 25, -10000, 10000);
        LEVEL_HUD_SCALE = BUILDER.comment("Scale of the Level HUD element").defineInRange("levelHudScale", 1.0, 0.1, 5.0);

        ACTION_POPUP_X = BUILDER.comment("X offset for the Action Popups (0 is center)").defineInRange("actionPopupX", 0, -10000, 10000);
        ACTION_POPUP_Y = BUILDER.comment("Y offset for the Action Popups (0 is center)").defineInRange("actionPopupY", 0, -10000, 10000);
        ACTION_POPUP_SCALE = BUILDER.comment("Scale of the Action Popups").defineInRange("actionPopupScale", 1.0, 0.1, 5.0);

        BUILDER.pop();
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
