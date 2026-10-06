package com.feima.movemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    private KeyBindings() {}

    public static final String CATEGORY = "key.categories.feimamovemod";

    /** 滑铲，默认 C 键 */
    public static final KeyMapping SLIDE = new KeyMapping(
            "key.feimamovemod.slide",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );

    /** 趴下 / 飞扑，默认 Z 键（上下文切换） */
    public static final KeyMapping PRONE = new KeyMapping(
            "key.feimamovemod.prone",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    /** 探头（左），默认 Q 键 */
    public static final KeyMapping PEEK_LEFT = new KeyMapping(
            "key.feimamovemod.peek_left",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q,
            CATEGORY
    );

    /** 探头（右），默认 E 键 */
    public static final KeyMapping PEEK_RIGHT = new KeyMapping(
            "key.feimamovemod.peek_right",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            CATEGORY
    );
}