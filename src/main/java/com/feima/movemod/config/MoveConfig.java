package com.feima.movemod.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class MoveConfig {

    public static final ForgeConfigSpec SPEC;
    public static final MoveConfig INSTANCE;

    static {
        Pair<MoveConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(MoveConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    // ============================================================
    // 字段
    // ============================================================
    // ---- 顶层：通用开关（无分类） ----
    public final ForgeConfigSpec.BooleanValue slideEnabled;
    public final ForgeConfigSpec.BooleanValue proneEnabled;
    public final ForgeConfigSpec.BooleanValue peekEnabled;

    // ---- stamina（顶层，跨动作） ----
    public final ForgeConfigSpec.BooleanValue staminaEnabled;
    public final ForgeConfigSpec.DoubleValue  staminaMax;
    public final ForgeConfigSpec.DoubleValue  staminaRegenPerTick;
    public final ForgeConfigSpec.IntValue     staminaRegenDelayTicks;

    // ---- stamina.slide ----
    public final ForgeConfigSpec.BooleanValue staminaSlideAllowWhenEmpty;
    public final ForgeConfigSpec.DoubleValue  staminaSlideCostOnStart;
    public final ForgeConfigSpec.DoubleValue  staminaSlideCostPerTick;
    public final ForgeConfigSpec.DoubleValue  staminaSlideLevel1Threshold;
    public final ForgeConfigSpec.DoubleValue  staminaSlideLevel2Threshold;
    public final ForgeConfigSpec.DoubleValue  staminaSlideLevel2Speed;
    public final ForgeConfigSpec.DoubleValue  staminaSlideLevel3Speed;

    // ---- stamina.dive ----
    public final ForgeConfigSpec.DoubleValue  staminaDiveCostOnStart;
    public final ForgeConfigSpec.DoubleValue  staminaDiveCostPerTick;

    // ---- stamina.peek ----
    public final ForgeConfigSpec.DoubleValue  staminaPeekCostPerTick;

    // ---- slide ----
    public final ForgeConfigSpec.BooleanValue requireSprint;
    public final ForgeConfigSpec.BooleanValue slideParticle;

    public final ForgeConfigSpec.DoubleValue startSpeed;
    public final ForgeConfigSpec.IntValue    decayDelay;
    public final ForgeConfigSpec.DoubleValue friction;
    public final ForgeConfigSpec.DoubleValue endSpeed;
    public final ForgeConfigSpec.IntValue    slideTriggerCd;

    public final ForgeConfigSpec.BooleanValue followLook;
    public final ForgeConfigSpec.DoubleValue  turnFactor;
    public final ForgeConfigSpec.DoubleValue  maxTurnOffset;
    public final ForgeConfigSpec.DoubleValue  turnOffsetZeroYaw;
    public final ForgeConfigSpec.DoubleValue  turnSpeed;

    public final ForgeConfigSpec.DoubleValue  slideJumpForward;
    public final ForgeConfigSpec.DoubleValue  slideJumpUp;
    public final ForgeConfigSpec.BooleanValue slideJumpFollowLook;

    public final ForgeConfigSpec.BooleanValue hungerEnabled;
    public final ForgeConfigSpec.DoubleValue  hungerPerTick;

    public final ForgeConfigSpec.DoubleValue hitboxWidth;
    public final ForgeConfigSpec.DoubleValue hitboxHeight;
    public final ForgeConfigSpec.DoubleValue eyeHeight;

    // ---- prone / dive ----
    public final ForgeConfigSpec.BooleanValue diveEnabled;
    public final ForgeConfigSpec.BooleanValue diveRequireSprint;
    public final ForgeConfigSpec.DoubleValue  diveHorizontalSpeed;
    public final ForgeConfigSpec.DoubleValue  diveFriction;
    public final ForgeConfigSpec.DoubleValue  diveEndSpeed;
    public final ForgeConfigSpec.DoubleValue  diveUpBoost;
    public final ForgeConfigSpec.BooleanValue diveConvertToProneOnLand;

    // ---- peek ----
    public final ForgeConfigSpec.BooleanValue peekLastPressWins;
    public final ForgeConfigSpec.DoubleValue peekDistance;
    public final ForgeConfigSpec.DoubleValue peekAngleThirdPerson;
    public final ForgeConfigSpec.DoubleValue peekAngleFirstPerson;
    public final ForgeConfigSpec.IntValue    peekTransitionTicks;
    public final ForgeConfigSpec.DoubleValue peekModelOffset;

    public final ForgeConfigSpec.DoubleValue peekHitboxWidth;
    public final ForgeConfigSpec.DoubleValue peekHitboxHeight;
    public final ForgeConfigSpec.DoubleValue peekHitboxCrouchHeight;
    public final ForgeConfigSpec.DoubleValue peekHitboxOffset;

    // ============================================================
    // 构造
    // ============================================================
    private MoveConfig(ForgeConfigSpec.Builder b) {

        // ============================================================
        // 顶层：通用开关（无分类）
        // ============================================================
        slideEnabled = b
                .comment("Enable sliding.", "是否启用滑铲。")
                .translation("feimamovemod.configuration.slideEnabled")
                .define("slideEnabled", true);

        proneEnabled = b
                .comment("Enable prone. Turning this off also disables dive.",
                         "是否启用趴下。关闭后飞扑也会一并关闭。")
                .translation("feimamovemod.configuration.proneEnabled")
                .define("proneEnabled", true);

        peekEnabled = b
                .comment("Enable peeking.", "是否启用探头。")
                .translation("feimamovemod.configuration.peekEnabled")
                .define("peekEnabled", true);

        // ============================================================
        // stamina（顶层）
        // ============================================================
        b.comment("Stamina", "耐力",
                  "Shared stamina system. Consumed by slide, dive and peek.",
                  "跨动作共用的耐力系统。滑铲、飞扑、探头消耗；趴下不消耗。")
                .push("stamina");

        staminaEnabled = b
                .comment("Enable stamina.", "是否启用耐力。")
                .translation("feimamovemod.configuration.stamina.enabled")
                .define("enabled", true);

        staminaMax = b
                .comment("Maximum stamina.", "耐力上限。")
                .translation("feimamovemod.configuration.stamina.max")
                .defineInRange("max", 100.0D, 1.0D, 10000.0D);

        staminaRegenPerTick = b
                .comment("Per-tick regeneration.", "每 tick 恢复量。")
                .translation("feimamovemod.configuration.stamina.regenPerTick")
                .defineInRange("regenPerTick", 0.6D, 0.0D, 100.0D);

        staminaRegenDelayTicks = b
                .comment("Delay before regeneration starts after the last consumption (ticks).",
                         "停止消耗后多久开始恢复（tick）。")
                .translation("feimamovemod.configuration.stamina.regenDelayTicks")
                .defineInRange("regenDelayTicks", 20, 0, 400);

        // ---------- stamina.slide ----------
        b.comment("Slide stamina", "滑铲耐力").push("slide");

        staminaSlideAllowWhenEmpty = b
                .comment("If true, sliding is allowed at zero stamina, but speed drops to tier 3 (slowest).",
                         "If false, insufficient stamina rejects the slide outright.",
                         "true 时耐力为 0 也能滑铲，速度降为三档（最慢）。",
                         "false 时耐力不足直接拒绝滑铲。")
                .translation("feimamovemod.configuration.stamina.slide.allowWhenEmpty")
                .define("allowWhenEmpty", true);

        staminaSlideCostOnStart = b
                .comment("One-time cost when starting a slide.",
                         "启动滑铲时一次性消耗。")
                .translation("feimamovemod.configuration.stamina.slide.costOnStart")
                .defineInRange("costOnStart", 20.0D, 0.0D, 10000.0D);

        staminaSlideCostPerTick = b
                .comment("Per-tick cost while sliding.",
                         "滑铲期间每 tick 消耗。")
                .translation("feimamovemod.configuration.stamina.slide.costPerTick")
                .defineInRange("costPerTick", 0.4D, 0.0D, 100.0D);

        b.comment("Tier thresholds (stamina ratio 0~1).",
                  "档位阈值（耐力比例 0~1）。").push("thresholds");

        staminaSlideLevel1Threshold = b
                .comment("Tier-1 threshold.", "一档阈值。")
                .translation("feimamovemod.configuration.stamina.slide.thresholds.level1")
                .defineInRange("level1", 0.6D, 0.0D, 1.0D);

        staminaSlideLevel2Threshold = b
                .comment("Tier-2 threshold.", "二档阈值。")
                .translation("feimamovemod.configuration.stamina.slide.thresholds.level2")
                .defineInRange("level2", 0.3D, 0.0D, 1.0D);

        b.pop(); // thresholds

        b.comment("Speed per tier (blocks/tick). Tier-1 speed is slide.startSpeed.",
                  "各档速度（方块/tick）。一档速度见 slide.startSpeed。").push("speeds");

        staminaSlideLevel2Speed = b
                .comment("Tier-2 speed.", "二档速度。")
                .translation("feimamovemod.configuration.stamina.slide.speeds.level2Speed")
                .defineInRange("level2Speed", 0.45D, 0.0D, 5.0D);

        staminaSlideLevel3Speed = b
                .comment("Tier-3 speed.", "三档速度。")
                .translation("feimamovemod.configuration.stamina.slide.speeds.level3Speed")
                .defineInRange("level3Speed", 0.3D, 0.0D, 5.0D);

        b.pop(); // speeds
        b.pop(); // slide (stamina)

        // ---------- stamina.dive ----------
        b.comment("Dive stamina", "飞扑耐力").push("dive");

        staminaDiveCostOnStart = b
                .comment("One-time cost when starting a dive.",
                         "启动飞扑时一次性消耗。")
                .translation("feimamovemod.configuration.stamina.dive.costOnStart")
                .defineInRange("costOnStart", 10.0D, 0.0D, 10000.0D);

        staminaDiveCostPerTick = b
                .comment("Per-tick cost while diving.",
                         "飞扑期间每 tick 消耗。")
                .translation("feimamovemod.configuration.stamina.dive.costPerTick")
                .defineInRange("costPerTick", 0.5D, 0.0D, 100.0D);

        b.pop(); // dive (stamina)

        // ---------- stamina.peek ----------
        b.comment("Peek stamina", "探头耐力").push("peek");

        staminaPeekCostPerTick = b
                .comment("Per-tick cost while peeking. Peek auto-exits at zero stamina.",
                         "探头期间每 tick 消耗。耐力耗尽自动退出探头。")
                .translation("feimamovemod.configuration.stamina.peek.costPerTick")
                .defineInRange("costPerTick", 0.3D, 0.0D, 100.0D);

        b.pop(); // peek (stamina)

        b.pop(); // stamina

        // ============================================================
        // slide（不含耐力，仅滑铲本身）
        // ============================================================
        b.comment("Slide", "滑铲").push("slide");

        requireSprint = b
                .comment("If true, sprinting is required to start a slide.",
                         "true 时需要疾跑状态才能触发滑铲。")
                .translation("feimamovemod.configuration.slide.requireSprint")
                .define("requireSprint", false);

        slideParticle = b
                .comment("If true, spawn sprint-like ground particles while sliding.",
                         "true 时滑铲期间会持续生成类似疾跑的地面粒子。")
                .translation("feimamovemod.configuration.slide.slideParticle")
                .define("slideParticle", true);

        startSpeed = b
                .comment("Tier-1 initial speed (blocks/tick).",
                         "一档初速度（方块/tick）。")
                .translation("feimamovemod.configuration.slide.startSpeed")
                .defineInRange("startSpeed", 0.6D, 0.0D, 5.0D);

        decayDelay = b
                .comment("Ticks to hold speed before decay begins.",
                         "速度开始衰减前的保持时长（tick）。")
                .translation("feimamovemod.configuration.slide.decayDelay")
                .defineInRange("decayDelay", 3, 0, 200);

        friction = b
                .comment("Per-tick speed decay factor.",
                         "每 tick 速度衰减系数。")
                .translation("feimamovemod.configuration.slide.friction")
                .defineInRange("friction", 0.9D, 0.0D, 1.0D);

        endSpeed = b
                .comment("End speed; slide stops when speed drops to or below this value.",
                         "末速度，速度降到 ≤ 此值则结束滑铲。")
                .translation("feimamovemod.configuration.slide.endSpeed")
                .defineInRange("endSpeed", 0.2D, 0.0D, 5.0D);

        slideTriggerCd = b
                .comment("Minimum cooldown between two slide starts (ticks).",
                         "两次滑铲启动之间的最短间隔（tick）。")
                .translation("feimamovemod.configuration.slide.slideTriggerCd")
                .defineInRange("slideTriggerCd", 22, 0, 200);

        b.comment("Steering", "转向").push("steering");

        followLook = b
                .comment("Whether view direction affects slide direction.",
                         "是否允许视角影响滑铲方向。")
                .translation("feimamovemod.configuration.slide.steering.followLook")
                .define("followLook", true);

        turnFactor = b
                .comment("Ratio of direction following view (0~1).",
                         "方向跟随视角的比例（0~1）。")
                .translation("feimamovemod.configuration.slide.steering.turnFactor")
                .defineInRange("turnFactor", 0.5D, 0.0D, 1.0D);

        maxTurnOffset = b
                .comment("Maximum offset angle relative to the initial direction (degrees).",
                         "相对初始方向的最大偏移角度（度）。")
                .translation("feimamovemod.configuration.slide.steering.maxTurnOffset")
                .defineInRange("maxTurnOffset", 45.0D, 0.0D, 180.0D);

        turnOffsetZeroYaw = b
                .comment("View offset beyond which the target direction is zeroed (degrees).",
                         "超过此视角偏移则目标方向归零（度）。")
                .translation("feimamovemod.configuration.slide.steering.turnOffsetZeroYaw")
                .defineInRange("turnOffsetZeroYaw", 120.0D, 0.0D, 180.0D);

        turnSpeed = b
                .comment("Max angular speed when chasing the view direction (degrees/tick).",
                         "方向追赶视角的最大角速度（度/tick）。")
                .translation("feimamovemod.configuration.slide.steering.turnSpeed")
                .defineInRange("turnSpeed", 3.0D, 0.0D, 30.0D);

        b.pop(); // steering

        b.comment("Slide jump", "滑铲跳").push("jump");

        slideJumpForward = b
                .comment("Horizontal speed (blocks/tick).",
                         "水平速度（方块/tick）。")
                .translation("feimamovemod.configuration.slide.jump.slideJumpForward")
                .defineInRange("slideJumpForward", 0.5D, 0.0D, 5.0D);

        slideJumpUp = b
                .comment("Upward speed (blocks/tick).",
                         "向上速度（方块/tick）。")
                .translation("feimamovemod.configuration.slide.jump.slideJumpUp")
                .defineInRange("slideJumpUp", 0.42D, 0.0D, 5.0D);

        slideJumpFollowLook = b
                .comment("If true, use current view direction; if false, use the slide's initial direction.",
                         "true 用当前视角方向，false 用滑铲初始方向。")
                .translation("feimamovemod.configuration.slide.jump.slideJumpFollowLook")
                .define("slideJumpFollowLook", true);

        b.pop(); // jump

        hungerEnabled = b
                .comment("Enable hunger consumption while sliding.",
                         "是否启用滑铲饱食度消耗。")
                .translation("feimamovemod.configuration.slide.hungerEnabled")
                .define("hungerEnabled", true);

        hungerPerTick = b
                .comment("hunger consumption added per tick while sliding.",
                         "滑铲期间每 tick 增加的饱食度消耗。")
                .translation("feimamovemod.configuration.slide.hungerPerTick")
                .defineInRange("hungerPerTick", 0.1D, 0.0D, 10.0D);

        b.comment("Slide hitbox and eye height.",
                  "滑铲碰撞箱与眼高。").push("hitbox");

        hitboxWidth = b
                .comment("Width.", "宽度。")
                .translation("feimamovemod.configuration.slide.hitbox.hitboxWidth")
                .defineInRange("hitboxWidth", 0.6D, 0.0D, 5.0D);

        hitboxHeight = b
                .comment("Height.", "高度。")
                .translation("feimamovemod.configuration.slide.hitbox.hitboxHeight")
                .defineInRange("hitboxHeight", 0.6D, 0.0D, 5.0D);

        eyeHeight = b
                .comment("Eye height.", "眼睛高度。")
                .translation("feimamovemod.configuration.slide.hitbox.eyeHeight")
                .defineInRange("eyeHeight", 0.4D, 0.0D, 5.0D);

        b.pop(); // hitbox
        b.pop(); // slide

        // ============================================================
        // prone / dive
        // ============================================================
        b.comment("Prone and Dive", "趴下与飞扑").push("prone");

        b.comment("Dive", "飞扑").push("dive");

        diveEnabled = b
                .comment("Enable dive. Requires proneEnabled to be true.",
                         "是否启用飞扑。总开关 proneEnabled 关闭时此项无效。")
                .translation("feimamovemod.configuration.prone.dive.enabled")
                .define("enabled", true);

        diveRequireSprint = b
                .comment("If true, dive requires sprinting.",
                         "触发飞扑是否必须疾跑。")
                .translation("feimamovemod.configuration.prone.dive.requireSprint")
                .define("requireSprint", true);

        diveHorizontalSpeed = b
                .comment("Initial horizontal speed (blocks/tick).",
                         "飞扑水平初速（方块/tick）。")
                .translation("feimamovemod.configuration.prone.dive.horizontalSpeed")
                .defineInRange("horizontalSpeed", 0.6D, 0.0D, 5.0D);

        diveFriction = b
                .comment("Per-tick horizontal speed decay factor.",
                         "每 tick 水平速度衰减系数。")
                .translation("feimamovemod.configuration.prone.dive.friction")
                .defineInRange("friction", 0.9D, 0.0D, 1.0D);

        diveEndSpeed = b
                .comment("End speed; dive stops when horizontal speed drops to or below this value.",
                         "末速度，水平速度降到 ≤ 此值则结束飞扑。")
                .translation("feimamovemod.configuration.prone.dive.endSpeed")
                .defineInRange("endSpeed", 0.2D, 0.0D, 5.0D);

        diveUpBoost = b
                .comment("Upward boost applied at dive start (blocks/tick).",
                         "飞扑时施加的向上初速（方块/tick）。")
                .translation("feimamovemod.configuration.prone.dive.upBoost")
                .defineInRange("upBoost", 0.32D, 0.0D, 2.0D);

        diveConvertToProneOnLand = b
                .comment("If true, dive converts to prone when it ends on solid ground.",
                         "飞扑在坚实地面结束时是否自动转为趴下。")
                .translation("feimamovemod.configuration.prone.dive.convertToProneOnLand")
                .define("convertToProneOnLand", true);

        b.pop(); // dive
        b.pop(); // prone

        // ============================================================
        // peek
        // ============================================================
        b.comment("Peek", "探头").push("peek");

        peekLastPressWins = b
                .comment("If true, when both peek keys are held, the most recently pressed one wins.",
                         "If false, holding both cancels out and returns to standing.",
                         "true 时同时按住左右探头键，以最近按下的方向为准；",
                         "false 时同时按住两个键会互相抵消，变回站立。")
                .translation("feimamovemod.configuration.peek.lastPressWins")
                .define("lastPressWins", true);

        peekDistance = b
                .comment("Lateral offset of the whole body (blocks).",
                         "Used for the first-person camera shift and eye-height shift.",
                         "整个身体侧倾时的横向偏移量（格）。",
                         "第一人称相机横移与眼高偏移使用它。")
                .translation("feimamovemod.configuration.peek.distance")
                .defineInRange("distance", 0.8D, 0.0D, 1.0D);

        peekAngleThirdPerson = b
                .comment("Whole-body tilt angle in third person (degrees).",
                         "第三人称整个身体绕脚竖轴倾斜的角度（度）。")
                .translation("feimamovemod.configuration.peek.angleThirdPerson")
                .defineInRange("angleThirdPerson", 18.0D, 0.0D, 90.0D);

        peekAngleFirstPerson = b
                .comment("First-person camera roll angle (degrees).",
                         "第一人称相机绕视线轴的 roll 角度（度）。")
                .translation("feimamovemod.configuration.peek.angleFirstPerson")
                .defineInRange("angleFirstPerson", 13.0D, 0.0D, 90.0D);

        peekTransitionTicks = b
                .comment("Transition time to extend / retract peek, in ticks.",
                         "0 = instant snap.",
                         "探头伸出 / 收回的过渡时长（tick），0 表示瞬间完成。")
                .translation("feimamovemod.configuration.peek.transitionTicks")
                .defineInRange("transitionTicks", 6, 0, 40);

        peekModelOffset = b
                .comment("Lateral offset of the third-person model (blocks).",
                         "The peek hitbox also adds this on top of its own alignment offset",
                         "(peek.hitbox.offset), so the hitbox follows the model.",
                         "第三人称模型横向偏移（格）。",
                         "碰撞箱会在自身对准偏移（peek.hitbox.offset）之上再叠加此值，",
                         "因此碰撞箱与模型一同偏移。")
                .translation("feimamovemod.configuration.peek.modelOffset")
                .defineInRange("modelOffset", 0.05D, 0.0D, 1.0D);

        b.comment("Peek hitbox.",
                  "探头碰撞箱。").push("hitbox");

        peekHitboxWidth = b
                .comment("Peek hitbox width.",
                         "探头碰撞箱宽度。")
                .translation("feimamovemod.configuration.peek.hitbox.width")
                .defineInRange("width", 0.65D, 0.0D, 5.0D);

        peekHitboxHeight = b
                .comment("Peek hitbox height while standing.",
                         "站立时的探头碰撞箱高度。")
                .translation("feimamovemod.configuration.peek.hitbox.height")
                .defineInRange("height", 1.8D, 0.0D, 5.0D);

        peekHitboxOffset = b
                .comment("Alignment offset for the peek hitbox (blocks).",
                         "This is a fixed calibration offset added on top of peek.modelOffset.",
                         "探头碰撞箱的对准偏移（格）。",
                         "这是一个固定的对准微调量，在 peek.modelOffset 之上叠加。")
                .translation("feimamovemod.configuration.peek.hitbox.offset")
                .defineInRange("offset", 0.0D, 0.0D, 1.0D);

        peekHitboxCrouchHeight = b
                .comment("Peek hitbox height while crouching.",
                         "蹲下时的探头碰撞箱高度。")
                .translation("feimamovemod.configuration.peek.hitbox.crouchHeight")
                .defineInRange("crouchHeight", 1.5D, 0.0D, 5.0D);

        b.pop(); // hitbox
        b.pop(); // peek
    }
}