# 🖐️ 手势控制抖音

通过手机前置摄像头实时识别手势，在抖音应用中执行对应操作。支持**自定义手势映射**。

## ✨ 功能

| 手势 | 默认操作 | 说明 |
|------|----------|------|
| 🖐️ 张开手掌 | 暂停/播放 | 五指全部张开伸直 |
| ✊ 握拳 | 收藏 | 五指握紧成拳 |
| 👍 点赞手势 | 点赞 | 握拳，大拇指竖起 |
| ☝️ 食指指向 | 评论区 | 仅食指伸出，其余握拳 |
| ✌️ V字手势 | 作者主页 | 食指+中指伸出成V形 |
| ⬆️ 手向上挥 | 上划 | 手掌向上快速挥动 |
| ⬇️ 手向下挥 | 下划 | 手掌向下快速挥动 |

> 💡 所有手势→操作均可在**配置界面**中自由修改。

## 🏗️ 技术架构

```
┌─────────────────────────────────────────┐
│            悬浮窗 (OverlayService)        │
│  ┌──────────┐  ┌──────────────────────┐ │
│  │ CameraX  │  │  GestureDetector     │ │
│  │ 前置摄像头 │──▶ 手势识别 (防抖+冷却)  │ │
│  └──────────┘  └─────────┬────────────┘ │
│                          │ 识别到手势     │
│                 ┌────────▼────────────┐ │
│                 │ GestureMappingStore │ │
│                 │ 查映射表(用户可配)    │ │
│                 └────────┬────────────┘ │
└──────────────────────────┼──────────────┘
                           │ DouyinAction
┌──────────────────────────▼──────────────┐
│     GestureActionService                │
│     (AccessibilityService)              │
│     dispatchGesture → 模拟滑动/点击       │
└─────────────────────────────────────────┘
```

- **CameraX**：管理前置摄像头，低分辨率（224×224）帧分析保证实时性
- **MediaPipe Hands**：Google 的 21 点手部关键点检测，运行在设备端
- **GestureDetector**：基于手指关节角度判断静态手势，追踪手腕位移检测滑动；防抖 8 帧 + 25 帧冷却防重复触发
- **GestureActionService**：通过 Android AccessibilityService 的 `dispatchGesture` API 模拟屏幕滑动和点击
- **GestureMappingStore**：基于 DataStore 的持久化映射表，用户可自由修改

## 📱 使用步骤

### 1. 环境要求

- Android 8.0 (API 26) 及以上
- 模型文件已内置 (`app/src/main/assets/hand_landmarker.task` ✅)

### 2. 部署到手机 — 三种方式

### 方式一：Android Studio 一键运行（推荐 ✅）

1. 安装 [Android Studio](https://developer.android.com/studio) (自带 JDK + SDK)
2. 用 Android Studio 打开本项目目录
3. 手机开启 **USB 调试**，连接电脑
4. 点击 ▶️ **Run** → 自动编译并安装到手机

### 方式二：GitHub Actions 自动构建（无需电脑编译 🔥）

1. 将项目推送到你的 GitHub 仓库
2. 进入 **Actions** 标签页 → 点击 **Build APK** → **Run workflow**
3. 构建完成后，下载 artifact `hand-control-phone-debug.zip`
4. 解压得到 `app-debug.apk`，传到手机安装

### 方式三：命令行编译

```bash
# Windows: 双击 build.bat 一键编译安装
# 或手动：
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. 授权

打开应用后按提示完成两项授权：

1. **悬浮窗权限** — 系统设置 → 应用 → 手势控制 → 允许在其他应用上层显示
2. **无障碍服务** — 系统设置 → 无障碍 → 手势控制 → 开启

### 4. 开始使用

1. 点击「**启动手势识别**」→ 悬浮窗出现并自动返回桌面
2. 打开**抖音**
3. 将手机放在支架上，前置摄像头对准你的手
4. 在摄像头前做手势即可控制抖音 🎉

## 📂 项目结构

```
hand-control-phone/
├── app/src/main/java/com/handcontrol/phone/
│   ├── App.kt                          # Application
│   ├── MainActivity.kt                 # 主界面
│   ├── camera/
│   │   ├── CameraManager.kt            # CameraX + MediaPipe 管理
│   │   └── OverlayService.kt           # 悬浮窗服务（前台服务）
│   ├── gesture/
│   │   ├── GestureType.kt              # 手势/操作枚举定义
│   │   ├── HandLandmarkHelper.kt       # 21点关键点计算
│   │   └── GestureDetector.kt          # 手势匹配 + 防抖
│   ├── action/
│   │   └── GestureActionService.kt     # 无障碍服务（模拟操作）
│   └── config/
│       ├── ConfigActivity.kt           # 手势映射配置界面
│       └── GestureMappingStore.kt      # DataStore 持久化存储
├── app/src/main/res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   ├── activity_config.xml
│   │   └── item_gesture_mapping.xml
│   ├── values/ (strings, colors, themes)
│   └── xml/accessibility_service_config.xml
└── app/src/main/assets/
    └── hand_landmarker.task             # ✅ 已包含（7.5MB MediaPipe 模型）
```

## 🔧 自定义手势

在配置界面中，每个手势右侧的下拉菜单可以重新选择对应的抖音操作。修改即时生效，无需重启服务。

## ⚠️ 注意事项

- 手势识别需要**充足光线**和**清晰的手部可见度**
- 前置摄像头会持续运行，请注意**电量消耗**
- 悬浮窗可以**拖拽**到屏幕任意位置
- 抖音版本更新可能导致按钮坐标偏移，可通过修改 `GestureActionService` 中的比例常量来适配
- 手势有防抖和冷却机制（防抖 8 帧 + 冷却 25 帧 ≈ 触发一次后约 1 秒内不会再次触发）

## 📄 许可

本项目仅用于学习交流。
