# GUI Zoom · 界面缩放

> Minecraft **1.20.1** · Forge **47.x** · 客户端模组
> 作者：**cookedfish** · 版本 **1.0.0** · 协议 MIT
>
> **本模组由 AI 辅助完成。**

在**任意界面**用**鼠标滚轮**缩放整个 UI 的模组，内置多种缩放方案，并且预留了给其他模组扩展的
接口。核心设计目标只有一句话：**尽可能复用原版的缩放代码，最大化与其他模组的兼容性。**

---

## 1. 三种缩放方案

模组内置了 4 个方案（第 4 个同时是"预留方案"的参考实现），可以在设置界面里随时切换。

| ID | 名称 | 实现方式 | 特点 |
|---|---|---|---|
| `guizoom:vanilla` | **原版界面缩放** | 写原版 `Options.guiScale` + 调用 `Minecraft.resizeDisplay()` | **方案一**。和视频设置里的滑块完全同一条代码路径，整数档位，界面重新排版，兼容性最好 |
| `guizoom:magnifier` | **放大镜** | 渲染期对 GUI 姿势矩阵做以鼠标为锚点的缩放 | **方案二**。像素级放大，不改动布局，鼠标移动即平移视野，指针所指的像素不会跑偏，点击依旧精准 |
| `guizoom:fractional` | **分数缩放** | `Window.setGuiScale(double)` + 重排界面 | **方案三**。和方案一同源但支持小数档位，连续平滑；界面会重排，不会有内容被裁掉，所有控件位置自然正确 |
| `guizoom:center` | **居中放大** | 渲染期缩放，锚点为屏幕中心 | **预留/示例方案**。与放大镜只差一个锚点；非指针锚点带来的坐标偏移由模组自己换算（见下），所以点击/悬停/提示框同样精准 |

### 指针坐标换算（居中放大为什么也能点得准）

等比缩放只有一个不动点：锚点放在鼠标上时，指针底下的像素天然不动（放大镜方案）；锚点放在屏幕中心时，
除了中心以外所有控件的**绘制位置**都与它的**逻辑位置**错开了，于是"看到的按钮"和"点到的按钮"不是同一个。

模组不靠 Mixin 解决这个问题，而是用 Forge 事件把指针换算回逻辑坐标后重新派发：

- `ScreenEvent.MouseButtonPressed/Released/Dragged.Pre` → 映射坐标后调用 `Screen.mouseClicked/mouseReleased/mouseDragged`
  并取消原事件（缩放模式、按下、拖动全部对齐）；
- `ScreenEvent.Render.Pre` → 由于 Forge 在 `Render.Pre` 被取消后仍会派发 `Render.Post`，
  模组可以取消它并自己用**映射后的坐标**调用 `Screen.renderWithTooltip(...)`，
  于是**悬停高亮和物品提示框**也落在正确的控件上。

这些处理都是**惰性**的：锚点就是指针的模式（放大镜）与窗口型模式映射结果是恒等变换，直接返回、不取消任何事件，
所以默认配置下这条路径根本不会生效。


### 方案一：原版界面缩放

不自己实现任何缩放，只做两件事：

```java
minecraft.options.guiScale().set(scale);
minecraft.resizeDisplay();   // 原版内部：calculateScale -> setGuiScale -> Screen.resize -> ForgeHooksClient.resizeGuiLayers
```

因此所有原版界面、所有模组界面都会像玩家手动拖动视频设置滑块一样重新排版。缺点只有整数档位。

### 方案二：放大镜（像素缩放）

**完全不改变布局**，只在渲染时缩放 GUI 的姿势矩阵：

```
p' = anchor + scale * (p - anchor)      // anchor = 鼠标指针的 GUI 坐标
```

锚点是不动点，所以**鼠标指针底下的那个像素在缩放前后处于同一坐标**，原版的命中判定
（`Screen.mouseClicked` 收到的 `mouseX/mouseY`）自动与肉眼所见一致——不需要改动任何
`Screen` / `AbstractContainerScreen` / 模组控件。

正因为被缩放的只是渲染，`window.getGuiScaledWidth()` 等逻辑尺寸保持不变，不会触发任何重排，
也不会让别的模组算错尺寸。HUD 同样被放大（通过 `RenderGuiEvent`），所以打开界面时画面是一致的。

### 方案三 & 预留方案

- `guizoom:fractional` 复用原版的三步重排逻辑，只把整数换成小数；
- `guizoom:center` 与放大镜共用同一套渲染钩子，只是锚点不同 —— 它就是下面"扩展"一节里的模板。

---

## 2. 安装

1. 安装 **Minecraft 1.20.1 + Forge 47.x**；
2. 把 `build/libs/guizoom-1.0.0.jar` 放进 `.minecraft/mods/`；
3. 启动游戏。

纯客户端模组（`clientSideOnly=true`），装在服务器上也不会报错，服务器不会加载它。

## 3. 使用

| 操作 | 说明 |
|---|---|
| **滚轮**（任意界面） | 缩放，具体行为取决于"滚轮行为"设置（见下） |
| **按住 C + 滚轮** | 「按住 C 键缩放」模式下用滚轮缩放（C 为可改的独立按键绑定） |
| **Home** | 重置缩放到默认值 |
| **K** | 打开界面缩放设置（仅在未打开任何界面时） |
| **切换缩放方案** | 默认未绑定按键 |
| **视频设置右上角「界面缩放」按钮** / **模组列表的 Config 按钮** | 打开设置界面 |

> 缩放**只由滚轮驱动**（外加「按住键缩放」的那个修饰键），没有单独的"点按放大/缩小"按键，
> 因此 C 键在默认配置里只作为「按住键缩放」的修饰键使用，不会和任何东西冲突。

### 滚轮行为

| 设置 | 鼠标在列表/会响应滚轮的按钮内 | 鼠标在列表外的空白处 | 不按按键时 |
|---|---|---|---|
| **按住 C 键缩放**（默认） | 按住键时缩放；不按键时列表正常滚动、按钮正常切换 | 按住键时缩放；不按键时不缩放 | 滚轮完全交给原版 |
| **SMART** | **只滚动列表 / 只切换按钮状态，绝不缩放**（列表滚到尽头也只是不动，不会变成缩放） | 缩放 | — |
| **ALWAYS** | 缩放（列表不滚动、按钮状态不被滚轮改变） | 缩放 | — |

「按住 C 键缩放」中的 **C 是实时读取的按键绑定**（`key.guizoom.hold`），模式名会显示成「按住 X 键缩放」，
X 就是你实际绑定的那个键；在按键设置里改键后，重新打开设置界面即可看到新名字。

**没有打开任何界面时**（HUD，需要打开 `zoomInGame` 游戏内缩放，默认已打开），游戏内的滚轮默认属于**快捷栏**：

| 滚轮行为 | 无界面时的滚轮 |
|---|---|
| 按住 C 键缩放（默认） | **按住键才缩放**；不按键时滚轮照常切换快捷栏 |
| SMART | **滚轮照常切换快捷栏**（不缩放） |
| ALWAYS | 直接缩放（会吃掉滚轮，不能切快捷栏） |

也就是说：默认配置下按住 C 滚轮就能缩放 HUD，松开滚轮依旧切换快捷栏；完全不想让模组碰滚轮就选 SMART。

**怎么判断"滚轮该归谁"**：模组**先把滚轮原样交给界面自己处理**——调用的就是原版会调用的那个
`Screen.mouseScrolled(...)`。于是列表滚动、`CycleButton` 切换状态、任何模组自己实现的滚动列表（例如配方查看器）
都按原版行为运作，只有当指针下**没有任何控件要这次滚轮**（也就是空白处）时才把它变成缩放。
不需要去猜控件的类，也就不会出现"列表滚到尽头反而变成缩放"的问题。

### 缩放提示

设置界面里的缩放提示绘制在**最上层**：有界面时画在 `ScreenEvent.Render.Post`（界面本体、控件与物品提示框之后），
没有界面时画在 HUD 之上。

### 配置文件

`.minecraft/config/guizoom-client.toml`，所有选项都能在设置界面里改：

```toml
[general]
    mode = "guizoom:magnifier"      # 当前方案
    zoomStep = 0.25                 # 每一格滚轮的缩放量
    maxZoom = 8.0                   # 全局上限
    scrollBehavior = "HOLD_KEY"     # HOLD_KEY（默认）/ SMART / ALWAYS（旧值 MODIFIER_ONLY 仍兼容）
    invertScroll = false
    smoothScrolling = true          # 平滑缩放（默认开）
    smoothingSpeed = 0.35
    showZoomIndicator = true        # 缩放提示（默认开）
    zoomInGame = true               # 游戏内缩放（默认开）
    restoreVanillaScaleOnExit = false
    debugLogging = false            # 打开后缩放流水会写进 latest.log，便于排查
```

默认值一览：**方案 = 放大镜**、**滚轮行为 = 按住 C 键缩放**、**平滑缩放 = 开**、**缩放提示 = 开**、**游戏内缩放 = 开**、
缩放步进 = 0.25、上限 = 8×、反转滚轮 = 关、退出还原原版缩放 = 关、诊断日志 = 关。

---

## 4. 兼容性设计（为什么它能和别的模组共存）

**本模组没有使用任何 Mixin，也没有 Access Transformer。** 全部通过原版代码路径与 Forge 公开事件实现：

| 功能 | 钩子 | 说明 |
|---|---|---|
| 界面内滚轮 | `ScreenEvent.MouseScrolled.Pre` | Forge 在 `MouseHandler` 里**先**发这个事件，再调用 `screen.mouseScrolled`；取消它即可把滚轮截走，不取消则界面完全接管——三种滚轮行为就是这么实现的 |
| 无界面滚轮 | `InputEvent.MouseScrollingEvent` | 仅在 `zoomInGame` 打开时使用 |
| HUD 缩放 | `RenderGuiEvent.Pre/Post` | 包住整个 `ForgeGui.render` |
| 界面缩放 | `ScreenEvent.Render.Pre/Post` | 包住 `Screen.renderWithTooltip`（1.20.1 里它是 `final`，所以**一个钩子就覆盖了所有原版与模组界面**，连物品提示框一起缩放） |
| 指针坐标换算 | `ScreenEvent.MouseButtonPressed/Released/Dragged.Pre` + 上面的 `Render.Pre` | 仅当锚点不是指针时（居中放大）才取消事件并换算坐标重新派发，`LOWEST` 优先级保证其他模组先看到原始事件 |
| 窗口级缩放 | `TickEvent.RenderTickEvent(START)` | 在 `GameRenderer.render` 计算 GUI 投影矩阵**之前**应用新缩放，避免出现"旧投影 + 新布局"的一帧错位 |
| 界面初始化 | `ScreenEvent.Init.Post` | 维持窗口级缩放（窗口大小变化、进出视频设置后自动恢复），并注入设置按钮 |
| 按键 | `InputEvent.Key` / `ScreenEvent.KeyPressed.Pre` | 后者负责"界面打开时"的按键；焦点在 `EditBox`/`MultiLineEditBox` 上时一概不拦截 |

几个刻意的防护：

- **姿势栈平衡**：只有真的 push 过、并且是**同一个** `PoseStack` 实例时才 pop。由于 `GameRenderer` 每帧都新建
  `GuiGraphics`/`PoseStack`，即使别的模组取消了 `RenderGuiEvent.Pre`（导致 `Post` 不触发），也绝不会出现
  栈下溢崩溃。
- **不在输入派发中重排界面**：缩放值的写入与 `screen.resize()` 被拆开，重排统一延后到帧首执行。
- **配置读取全防御**：任何配置项读取失败都会退回默认值，模组永不因为配置而崩溃。
- **未知方案 ID 自动回退**：配置里写了不存在的方案（例如卸载了某个扩展模组）时会退回内置方案。

---

## 5. 扩展：添加你自己的缩放方案（预留接口）

第三方模组可以在 `FMLClientSetupEvent` 里注册自己的方案，注册后它会自动出现在设置界面、
滚轮切换与配置文件里。

```java
public final class MyZoomMode implements IZoomMode {

    @Override public ResourceLocation id() { return new ResourceLocation("example", "my_zoom"); }
    @Override public Component displayName() { return Component.literal("我的缩放"); }
    @Override public double minZoom() { return 1.0D; }
    @Override public double maxZoom() { return 4.0D; }
    @Override public double defaultZoom() { return 1.0D; }
    @Override public double step() { return 0.25D; }

    // 渲染型方案：不改布局，只缩放姿势矩阵
    @Override public boolean poseBased() { return true; }

    @Override public ZoomTransform getTransform(Minecraft mc, double zoom, double mouseX, double mouseY) {
        return new ZoomTransform(zoom, mc.getWindow().getGuiScaledWidth() / 2.0D, 0.0D);
    }
}
```

```java
@Mod.EventBusSubscriber(modid = "example", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExampleClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ZoomModeRegistry.register(new MyZoomMode());
    }
}
```

两种可用的接入方式：

- **渲染型**（`poseBased() == true`）：实现 `getTransform`，返回一个"锚点 + 倍数"。锚点是不动点，
  如果锚点取鼠标坐标，原版命中判定就自动正确。`MagnifierMode` / `CenterZoomMode` 是范例。
- **窗口型**（`poseBased() == false`）：实现 `applyWindowScale` 真正改变 GUI 缩放，必要时再实现
  `isWindowScaleApplied`（用于窗口大小变化后自动重设）与 `readZoom`（回读被原版钳制后的真实值）。
  `VanillaScaleMode` / `FractionalScaleMode` 是范例。

主要 API 都在 `com.cookedfish.guizoom.api` 包：

- `IZoomMode` —— 方案接口；
- `ZoomModeRegistry` —— 注册与查询；
- `ZoomTransform` —— 锚点缩放变换。

---

## 6. 从源码构建

需要 **JDK 17**：

```bash
./gradlew build          # Windows: gradlew.bat build
```

产物：`build/libs/guizoom-1.0.0.jar`

开发环境运行：

```bash
./gradlew runClient
```

## 7. 已知限制

- **放大镜**在放大后，超出屏幕的部分会被裁掉——这是放大镜的固有行为，把鼠标移到想看的位置即可"平移到"该区域。
  需要"所有内容都完整可见"时请使用原版缩放或分数缩放。
- **居中放大**的缩放倍数越大，边缘被裁掉的内容越多（放大镜同理）。它的点击/悬停已经由坐标换算对齐，
  但如果想要"内容全部保留且一定点得准"，用分数缩放。
- **原版界面缩放方案**会真的修改原版 GUI 缩放选项（这也是它兼容性最好的原因）。如果希望离开该
  方案时还原，请打开"退出时还原原版缩放"。
- 缩放档位受屏幕分辨率限制：原版方案会像视频设置一样被钳制在屏幕放得下的最大档位。
- 缩放完全由滚轮驱动；没有"点按放大/缩小"按键，C 只作为「按住键缩放」的修饰键。
- 「按住 C 键缩放」的按住键默认是 **C**，可在按键设置里改成任意键，模式名会跟着变。
