# 📦 GitHub Actions 自动打包 APK 操作指南

## 一、完整流程图

```
本地代码  →  推送到 GitHub  →  Actions 自动触发  →  构建 APK  →  下载安装
```

---

## 二、前置准备

### 1. 安装 Git（如已安装可跳过）
下载地址：https://git-scm.com/download/win

### 2. 注册 GitHub 账号
注册地址：https://github.com

---

## 三、第一次推送代码到 GitHub（逐步操作）

### 步骤 1：在 GitHub 上创建新仓库

1. 登录 GitHub，点击右上角 **「+」→「New repository」**
2. 填写：
   - Repository name：`CallAnalyzer`（或你喜欢的名字）
   - 选择 **Private**（私有，推荐）或 Public
   - **不要勾选** "Add a README file"（我们已经有了）
3. 点击 **「Create repository」**
4. 复制页面上显示的仓库地址，例如：
   ```
   https://github.com/你的用户名/CallAnalyzer.git
   ```

### 步骤 2：在本地初始化 Git 并推送

打开 **PowerShell** 或 **CMD**，执行以下命令：

```powershell
# 进入项目目录
cd "C:\Users\EDY\WorkBuddy\20260420103757\CallAnalyzer"

# 初始化 Git 仓库
git init

# 添加所有文件
git add .

# 提交（首次提交）
git commit -m "Initial commit: CallAnalyzer Android App"

# 关联远程仓库（把下面的地址替换成你的）
git remote add origin https://github.com/你的用户名/CallAnalyzer.git

# 推送到 main 分支
git branch -M main
git push -u origin main
```

> 💡 推送时会弹出 GitHub 登录窗口，用浏览器授权即可。

---

## 四、GitHub Actions 自动构建

推送成功后，GitHub 会自动触发构建。查看方式：

1. 打开你的 GitHub 仓库页面
2. 点击顶部 **「Actions」** 标签
3. 可以看到正在运行的 **「Build APK」** 工作流
4. 构建成功后（约 5-10 分钟），点击工作流名称
5. 在页面底部 **「Artifacts」** 区域，下载 **`CallAnalyzer-Debug-APK`**
6. 解压后得到 `app-debug.apk`，传到手机安装即可

---

## 五、后续每次更新代码

修改代码后，只需：

```powershell
cd "C:\Users\EDY\WorkBuddy\20260420103757\CallAnalyzer"
git add .
git commit -m "描述你的修改内容"
git push
```

推送后 Actions 会自动重新构建。

---

## 六、发布正式版本（可选）

打 Git Tag 会自动触发 GitHub Release 并附上 APK：

```powershell
git tag v1.0.0
git push origin v1.0.0
```

然后在仓库的 **「Releases」** 页面就能看到带下载链接的正式版本。

---

## 七、配置签名（生产 Release 版，可选）

> ⚠️ Debug 版本可以直接安装使用，签名配置是可选项。

### 7.1 生成 keystore 文件

在 Android Studio Terminal 中运行：

```bash
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias
```

按提示设置密码，生成 `my-release-key.jks`。

### 7.2 将 keystore 转为 Base64

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-release-key.jks")) | clip
```

这会把 Base64 内容复制到剪贴板。

### 7.3 在 GitHub 仓库中添加 Secrets

1. 进入仓库 **Settings → Secrets and variables → Actions**
2. 点击 **「New repository secret」**，逐一添加：

| Secret 名称 | 值 |
|------------|---|
| `KEYSTORE_BASE64` | 第 7.2 步复制的 Base64 字符串 |
| `KEY_ALIAS` | keytool 中设置的 alias（如 `my-key-alias`） |
| `KEY_PASSWORD` | key 的密码 |
| `STORE_PASSWORD` | keystore 的密码 |

3. 下次推送，Actions 会同时构建 **有签名的 Release APK**。

---

## 八、常见问题

| 问题 | 解决方案 |
|------|---------|
| 构建失败：`gradlew not found` | 确保 `gradlew` 文件已提交（不在 .gitignore 中） |
| 构建失败：`SDK not found` | Actions 环境自带 Android SDK，无需手动配置 |
| APK 无法安装 | 手机设置 → 允许安装未知来源应用 |
| 推送需要密码 | 使用 GitHub 个人访问令牌（Settings → Developer settings → PAT） |
| 构建超时 | Gradle 首次构建需下载依赖，约 5-10 分钟，属正常现象 |

---

## 九、项目文件结构（关键文件）

```
CallAnalyzer/
├── .github/
│   └── workflows/
│       └── build-apk.yml     ← Actions 工作流（已配置好）
├── .gitignore                ← 忽略规则（已配置好）
├── gradlew                   ← Linux 构建脚本（Actions 使用）
├── gradlew.bat               ← Windows 构建脚本（本地使用）
├── gradle/wrapper/
│   └── gradle-wrapper.properties  ← Gradle 版本配置
├── app/
│   └── build.gradle          ← 应用构建配置
└── ...
```
