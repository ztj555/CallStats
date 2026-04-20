# 通话统计 App

一款用于统计手机通话时长和通话次数的 Android 应用。

## 功能特性

- **真实通话记录统计**：直接读取手机通话记录，非模拟数据
- **任意时间段统计**：支持自定义选择开始和结束日期
- **详细分类统计**：
  - 总通话次数和总时长
  - 来电（呼入）次数和时长
  - 去电（呼出）次数和时长
  - 未接来电次数
- **防伪水印功能**：
  - 透明度 50%
  - 倾斜 45 度
  - 与背景有明显对比
  - 显示统计时间段，确保截图真实性

## 权限说明

本应用需要以下权限：
- `READ_CALL_LOG` - 读取通话记录（核心权限）

## 使用说明

1. 首次启动时会请求通话记录权限，请授权
2. 选择统计的开始日期和结束日期
3. 点击"查询"按钮获取统计数据
4. 统计结果界面会自动显示水印，可直接截图保存

## 项目结构

```
CallStats/
├── app/
│   ├── src/main/
│   │   ├── java/com/callstats/app/
│   │   │   ├── MainActivity.kt          # 主界面
│   │   │   ├── WatermarkContainer.kt   # 水印容器
│   │   │   └── WatermarkView.kt        # 水印组件
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml # 界面布局
│   │   │   └── values/                 # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/android.yml        # GitHub Actions 配置
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## CI/CD 自动打包

本项目配置了 GitHub Actions，每次推送到 main 分支或合并 PR 时会自动：
1. 编译 Debug 版本 APK
2. 上传到构建产物
3. 自动创建 Release（推送到 main 分支时）

APK 下载位置：`app/build/outputs/apk/debug/app-debug.apk`

## 开发环境

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2

## 本地构建

```bash
./gradlew assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## License

MIT License
