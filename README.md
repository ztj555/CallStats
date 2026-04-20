# 通话统计分析 APP - Android

## 项目简介

原生 Android 应用，使用 **Kotlin + Room + Material Design 3** 开发。

### 核心功能

| 功能 | 说明 |
|------|------|
| 通话数据读取 | 读取系统通话记录（来电/去电/未接） |
| 本地缓存 | Room 数据库缓存，支持增量同步 |
| 多维筛选 | 时间范围 × 类型 × 号码姓名 × 最短时长 |
| 统计分析 | 次数、时长汇总弹窗 |
| 水印 | Canvas 绘制，半透明 + -15° 倾斜 + 平铺，与查询条件实时绑定 |

---

## 项目结构

```
CallAnalyzer/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # 权限声明
│   │   ├── java/com/callanalyzer/app/
│   │   │   ├── data/
│   │   │   │   ├── AppDatabase.kt       # Room 数据库
│   │   │   │   ├── entity/
│   │   │   │   │   └── CallLogEntity.kt # 通话记录实体
│   │   │   │   ├── dao/
│   │   │   │   │   └── CallLogDao.kt    # 数据访问对象（多条件查询+统计）
│   │   │   │   └── repository/
│   │   │   │       └── CallLogRepository.kt # 数据仓库（系统读取+筛选）
│   │   │   ├── ui/
│   │   │   │   ├── main/
│   │   │   │   │   ├── MainActivity.kt      # 主界面
│   │   │   │   │   ├── MainViewModel.kt     # ViewModel（状态管理）
│   │   │   │   │   └── CallLogAdapter.kt    # RecyclerView 适配器
│   │   │   │   └── widget/
│   │   │   │       └── WatermarkView.kt     # 水印自定义 View
│   │   │   └── utils/
│   │   │       └── QueryFilter.kt           # 查询条件模型 + 时长格式化
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml        # 主界面布局（含水印层）
│   │       │   ├── item_call_log.xml        # 通话记录 Item
│   │       │   └── dialog_query.xml         # 查询弹窗
│   │       ├── values/
│   │       │   ├── colors.xml
│   │       │   ├── strings.xml
│   │       │   └── themes.xml
│   │       └── drawable/                    # 图标、背景 Drawable
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

---

## 构建步骤

### 环境要求

- **Android Studio** Hedgehog (2023.1.1) 或更新版本
- **JDK 17**
- **Android SDK** API 26+（minSdk 26）

### 步骤

1. **用 Android Studio 打开项目**
   ```
   File → Open → 选择 CallAnalyzer 文件夹
   ```

2. **等待 Gradle 同步**
   - 首次同步会下载依赖，需要联网

3. **连接设备或启动模拟器**
   - 实体机需开启「USB调试」
   - 模拟器建议 API 30+

4. **运行**
   - 点击 ▶ 运行，或 `Shift+F10`

---

## 权限说明

```xml
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

- **READ_CALL_LOG**：读取通话记录（必需）
- **READ_CONTACTS**：匹配联系人姓名（可选，拒绝后仅显示号码）

首次启动会弹出系统权限请求框，授权后自动同步。

---

## 水印实现说明

`WatermarkView.kt` 核心逻辑：

```kotlin
canvas.rotate(-15f, w / 2, h / 2)   // 旋转画布 -15 度

paint.color = Color.parseColor("#33888888")  // 透明度约 20%

// 扩展绘制范围覆盖旋转空白区
// 错行平铺（奇偶行偏移 stepX/2）
```

水印内容通过 `WatermarkView.setWatermark(text)` 更新，
ViewModel 的 `filter` Flow 变化时自动驱动刷新。

---

## 查询条件联动水印

```
查询弹窗确认 → applyFilter(newFilter)
    → filter StateFlow 更新
    → callLogs Flow 重新查询
    → watermarkView.setWatermark(filter.rangeLabel)
    → WatermarkView.invalidate() → onDraw() 重绘
```

水印标签示例：
- `2026/04/20`（今天）
- `本周（2026/04/14-2026/04/20）`
- `本月（2026/04/01-2026/04/30）`
- `2026/04/01 - 2026/04/20`（自定义）

---

## 统计弹窗内容示例

```
📅 统计时间段
本月（2026/04/01-2026/04/30）

📊 通话次数
  合计：128 次
  去电：73 次
  来电：52 次
  未接：3 次

⏱ 通话时长
  合计：12时34分56秒
  去电：8时12分30秒
  来电：4时22分26秒
```
