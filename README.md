# Feima's Move Mod

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-DB6D34.svg)](https://files.minecraftforge.net/net/minecraftforge/forge/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](#license)

**Slide, crawl, peek, and move like an FPS character — in Minecraft.**

**像 FPS 角色一样滑铲、趴下、探头 —— 在 Minecraft 里。**

- Slide, crawl, peek, three-tier stamina, inertia steering, slide-jump.
- 滑铲、趴下、探头、三档耐力、惯性转向、滑铲跳。


---

## Features · 特性

### 🛝 Slide · 滑铲

Press **`C`** to slide. Requires being on the ground, holding forward, and having enough space ahead.

按 **`C`** 触发。需要处于地面、按住前进键、前方有足够空间。

- Sprinting is blocked during a slide, and the slide cancels sprint on start.
- Hitting a block face-on ends the slide instantly — no wall-hugging.

- 滑铲期间禁止疾跑，启动时自动退出疾跑。
- 正面撞墙立即结束，不会尴尬地贴墙打滑。

### 🦘 Slide-Jump · 滑铲跳

Press **`Space`** while sliding to launch forward. Horizontal speed scales with your current stamina tier. Direction follows your current look by default.

滑铲中按 **`Space`** 向前飞跃。水平速度随当前耐力档位缩放，方向默认跟随当前视角。

### 🎯 Inertia Steering · 惯性转向

Momentum follows your aim, but with weight — a limited turn angle and angular speed, not an instant snap.

动量跟随视角，但有分量感 —— 最大偏转角与角速度限制，不会瞬间掉头。

### ⚡ Stamina I / II / III · 三档耐力

| Stamina | Tier | Default Speed |
|---|---:|---:|
| `>= 60%` | I | `0.60 block/tick` |
| `30% ~ <60%` | II | `0.45 block/tick` |
| `< 30%` | III | `0.30 block/tick` |

Each slide costs stamina on start, drains while sliding, and regenerates after a short delay. Chain slides naturally step down through the tiers.

每次滑铲启动扣一次耐力，滑铲期间持续消耗，停止后延迟回复。连续滑铲会自然依次降档。

### 🧍 Crawl · 趴下

Toggle with **`Z`**. Uses vanilla `Pose.SWIMMING` — hitbox and eye height handled by the game. Mutually exclusive with sliding and peeking.

按 **`Z`** 切换。使用原版 `Pose.SWIMMING`，碰撞箱与眼高交给游戏本体处理，与滑铲、探头互斥。

### 👀 Peek · 探头

Hold **`Q`** / **`E`** to peek left / right. Your whole body leans out around a foot-anchored pivot — feet stay planted, head extends beyond cover. The peeked head is truly exposed to projectiles and explosions.

按住 **`Q`** / **`E`** 向左 / 右探头。整个身体绕脚底向侧面探出，双脚原地不动，头部越过掩体。探出的头部会真实暴露在弹道与爆炸范围内。

### 🌐 Multiplayer Sync · 多人同步

Optimistic client prediction with server authority. Smooth input, anti-cheat intact, and other players see your slides correctly.

客户端乐观预测 + 服务端权威。手感顺滑、防作弊、别人也能看到你滑。

---

## Controls · 按键

| Key | Action |
|:---:|:---|
| `C` | Slide / 滑铲 |
| `Z` | Crawl / 趴下 |
| `Q` | Peek Left / 探头（左） |
| `E` | Peek Right / 探头（右） |
| `Space` | Slide-Jump (while sliding) / 滑铲跳（滑铲中） |

All keys are rebindable in **Options → Controls**.
全部可在 **选项 → 控制** 中改键。

---

## Requirements · 需求

- Minecraft **1.20.1**
- Minecraft Forge **47.x**
- Java **17**
- [Player Animator](https://github.com/KosmX/minecraftPlayerAnimator) — **required / 必需**

---

## Configuration · 配置

Tune it your way in `config/feimamovemod-common.toml`.

在 `config/feimamovemod-common.toml` 中按你的手感调整。

**General · 通用** — Master switch, per-action toggles.
总开关、各动作独立开关。

**Slide · 滑铲** — Initial speed, decay delay, friction, end speed, trigger cooldown, hunger cost.
初速度、衰减延迟、摩擦系数、末速度、触发冷却、饱食度消耗。

**Slide — Steering · 转向** — Follow look, max turn offset, zeroing angle, turn speed.
是否跟随视角、最大偏转角、归零角度、转向角速度。

**Slide — Jump · 滑铲跳** — Forward speed, upward speed, follow look.
水平速度、向上速度、是否跟随视角。

**Slide — Stamina · 耐力** — Max, costs, regen rate & delay, tier thresholds, per-tier speeds.
上限、启动与每 tick 消耗、恢复速率与延迟、档位阈值、各档速度。

**Slide — Hitbox · 滑铲碰撞箱** — Width, height, eye height.
宽度、高度、眼高。

**Peek · 探头** — Camera offset, tilt angles, model offset, transition time.
相机偏移量、倾斜角度、模型偏移、过渡时间。

**Peek — Hitbox · 探头碰撞箱** — Width, standing / crouching height, alignment offset.
宽度、站立 / 蹲下高度、对准偏移。

---

- Author: Feima
- License: MIT