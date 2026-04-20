# 快速开始指南

## 将项目推送到 GitHub 并自动打包 APK

### 步骤 1: 创建 GitHub 仓库

1. 访问 https://github.com/ztj555/apk （你已经创建了这个仓库）
2. 克隆仓库到本地：
   ```bash
   git clone https://github.com/ztj555/apk.git
   ```

### 步骤 2: 将项目文件复制到仓库

将 `CallStats` 文件夹中的所有内容复制到克隆的仓库目录中。

### 步骤 3: 推送代码到 GitHub

```bash
cd apk
git add .
git commit -m "Initial commit: 通话统计 App"
git push -u origin main
```

### 步骤 4: 获取自动打包的 APK

GitHub Actions 会自动构建项目，构建完成后：

1. 进入你的仓库页面
2. 点击 **Actions** 标签
3. 查看 Workflow 运行状态
4. 构建成功后，点击 workflow 详情
5. 在 **Artifacts** 部分下载 `app-debug.apk`

### 手动下载 APK 的方法

每次推送到 main 分支后，GitHub Actions 会自动创建一个 Release：

1. 进入仓库的 **Releases** 页面
2. 点击最新的 release
3. 下载附带的 APK 文件

## 功能说明

### 水印功能
- 透明度：50%
- 倾斜角度：45度
- 颜色：深灰色，与白色背景形成对比
- 内容：显示选定的统计时间段

### 通话记录统计
- 真实读取手机通话记录（需要 READ_CALL_LOG 权限）
- 支持任意时间段选择
- 分类统计：来电、去电、未接来电

## 本地开发

如果需要在本地编译：

1. 确保安装了 JDK 17
2. 安装 Android SDK
3. 运行：
   ```bash
   ./gradlew assembleDebug
   ```
4. APK 输出在 `app/build/outputs/apk/debug/`
