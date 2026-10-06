# Feima's Movements

> **Rename Notice** — versions after 1.0.1 are renamed to **Feima's Movements**.
>
> **改名通知** — 1.0.1 之后的版本更名为 **Feima's Movements**。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.x-DB6D34.svg)](https://files.minecraftforge.net/net/minecraftforge/forge/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](#license)

**Slide, dive, prone, peek — FPS-style movement for Minecraft.**

**滑铲、飞扑、趴下、探头 —— 在 Minecraft 里体验 FPS 式移动。**

---

## Features · 特性

### Slide · 滑铲
Press **`C`** to slide. Momentum follows your look with a limited turn angle; speed decays over time and ends below the end speed. Sprinting is blocked while sliding.

按 **`C`** 触发。动量跟随视角但受最大偏转角限制，速度随时间衰减至末速度以下结束。滑铲期间禁止疾跑。

### Slide-Jump · 滑铲跳
Press **`Space`** while sliding to launch forward. Power scales with your stamina tier.

滑铲中按 **`Space`** 向前飞跃，力度随耐力档位缩放。

### Stamina · 三档耐力
Speed has three tiers based on remaining stamina (`>=60%` / `30%–60%` / `<30%`). Slide, dive and peek consume stamina; regen starts after a short delay.

依据剩余耐力比例分三档（`>=60%` / `30%–60%` / `<30%`）。滑铲、飞扑、探头消耗耐力，停止后延迟回复。

### Prone & Dive · 趴下与飞扑
Press **`Z`** to toggle prone, or to dive forward while sprinting. Prone uses vanilla swimming pose; dive gives a burst of forward momentum and converts to prone on landing.

按 **`Z`** 切换趴下，或在疾跑中向前飞扑。趴下使用原版游泳姿态，飞扑给出一段前冲动量并在落地后自动转入趴下。

### Peek · 探头
Hold **`Q`** / **`E`** to peek left / right. Body leans around a foot-anchored pivot; the head is truly exposed to projectiles and explosions. When both keys are held, the most recently pressed direction wins (configurable).

按住 **`Q`** / **`E`** 向左 / 右探头。身体绕脚底侧倾，头部真实暴露在弹道与爆炸范围内。同时按住两键时以最近按下的方向为准（可配置）。

### Multiplayer · 多人同步
Client prediction with server authority — smooth input, other players see your movement correctly.

客户端预测 + 服务端权威，手感顺滑且其他玩家能看到你的动作。

---

## Controls · 按键

| Key | Action |
|:---:|:---|
| `C` | Slide / 滑铲 |
| `Z` | Prone / Dive / 趴下 / 飞扑 |
| `Q` | Peek Left / 探头（左） |
| `E` | Peek Right / 探头（右） |
| `Space` | Slide-Jump (while sliding) / 滑铲跳 |

All keys are rebindable in **Options → Controls**. 全部可在 **选项 → 控制** 中改键。

---

## Requirements · 需求

- Minecraft **1.20.1**
- Forge **47.x**
- Java **17**

---

## Config · 配置

All values adjustable in `config/feimamovemod-common.toml`.

所有数值均可在 `config/feimamovemod-common.toml` 调整。

---

- Author: Feima
- License: MIT