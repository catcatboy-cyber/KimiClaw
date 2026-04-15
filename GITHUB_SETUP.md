# 🚀 GitHub 自动打包设置指南

本项目已配置 GitHub Actions，每次推送代码到 main 分支时，会自动构建 APK 并发布到 Releases 页面。

## 📋 快速设置步骤

### 1. 创建 GitHub 仓库

#### 方法一：通过 GitHub 网站创建（推荐新手）
1. 访问 https://github.com/new
2. 填写仓库信息：
   - **Repository name**: `KimiClaw`（或其他你喜欢的名字）
   - **Description**: 🦞 桌面悬浮小龙虾宠物
   - **Visibility**: 选择 Public（公开）或 Private（私有）
   - ✅ 勾选 "Add a README file"
3. 点击 **Create repository**

#### 方法二：通过命令行创建
```bash
# 安装 GitHub CLI（如果还没有）
# macOS: brew install gh
# Windows: winget install --id GitHub.cli
# Linux: 见 https://github.com/cli/cli/blob/trunk/docs/install_linux.md

# 登录 GitHub
gh auth login

# 创建仓库
gh repo create KimiClaw --public --description "🦞 桌面悬浮小龙虾宠物" --add-readme
```

### 2. 上传代码到 GitHub

#### 方法一：通过 GitHub 网站上传（最简单）
1. 进入你创建的仓库页面
2. 点击 **"Add file"** → **"Upload files"**
3. 拖拽或选择 `KimiClawPet` 文件夹中的所有文件
4. 填写提交信息：`Initial commit - KimiClaw v1.0`
5. 点击 **"Commit changes"**

#### 方法二：通过命令行上传
```bash
# 解压项目文件
cd /path/to/KimiClawPet

# 初始化 Git 仓库
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit - KimiClaw v1.0"

# 添加远程仓库（替换 YOUR_USERNAME 为你的 GitHub 用户名）
git remote add origin https://github.com/YOUR_USERNAME/KimiClaw.git

# 推送到 GitHub
git push -u origin main
```

### 3. 等待自动构建

推送代码后，GitHub Actions 会自动开始构建：

1. 进入仓库页面
2. 点击 **"Actions"** 标签
3. 你会看到正在运行的 workflow：`Build KimiClaw APK`
4. 等待约 3-5 分钟

### 4. 下载 APK

构建完成后，你可以通过两种方式获取 APK：

#### 方式一：从 Actions 下载（每次构建）
1. 进入 **Actions** 页面
2. 点击最新的 workflow 运行记录
3. 滚动到底部，找到 **Artifacts** 部分
4. 点击 **KimiClaw-APK** 下载

#### 方式二：从 Releases 下载（推荐）
1. 进入仓库主页
2. 点击右侧的 **Releases**
3. 找到最新版本（如 `v1`）
4. 下载 APK 文件

## 🔧 配置说明

### GitHub Actions 工作流文件

文件位置：`.github/workflows/build.yml`

```yaml
name: Build KimiClaw APK

on:
  push:
    branches: [ main, master ]    # 推送到 main/master 时触发
  pull_request:
    branches: [ main, master ]    # PR 时触发
  workflow_dispatch:              # 手动触发

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: android-actions/setup-android@v3
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: KimiClaw-APK
          path: app/build/outputs/apk/debug/*.apk
```

### 自动发布到 Releases

工作流还会自动创建 GitHub Release：
- 每次推送到 main 分支
- 自动生成版本号（如 v1, v2, v3...）
- 自动附带构建好的 APK 文件

## 🐛 常见问题

### Q: Actions 构建失败怎么办？

**查看日志：**
1. 进入 Actions 页面
2. 点击失败的 workflow
3. 查看红色 ❌ 的步骤
4. 点击展开查看详细错误

**常见错误及解决：**

| 错误 | 解决方法 |
|------|----------|
| `Permission denied` | 确保 `gradlew` 有执行权限 |
| `JAVA_HOME not set` | 检查 `actions/setup-java` 配置 |
| `SDK not found` | 检查 `android-actions/setup-android` |

### Q: 如何手动触发构建？

1. 进入 Actions 页面
2. 点击 **Build KimiClaw APK**
3. 点击 **Run workflow** 按钮
4. 选择分支，点击 **Run workflow**

### Q: 如何修改构建配置？

编辑 `.github/workflows/build.yml` 文件：

```yaml
# 修改 Java 版本
- uses: actions/setup-java@v4
  with:
    java-version: '21'  # 改为 21

# 修改 Gradle 任务
- run: ./gradlew assembleRelease  # 改为 Release 构建

# 添加签名（发布到应用商店需要）
- name: Sign APK
  uses: r0adkll/sign-android-release@v1
  with:
    releaseDirectory: app/build/outputs/apk/release
    signingKeyBase64: ${{ secrets.SIGNING_KEY }}
    alias: ${{ secrets.ALIAS }}
    keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
```

### Q: 私有仓库能用吗？

可以！但注意：
- GitHub Free 私有仓库有 Actions 使用限制
- 公开仓库 Actions 完全免费

## 📝 自定义配置

### 修改版本号

编辑 `app/build.gradle`：

```gradle
android {
    defaultConfig {
        versionCode 2        # 每次发布+1
        versionName "1.1"    # 版本名称
    }
}
```

### 添加签名配置（正式发布需要）

1. 生成签名密钥：
```bash
keytool -genkey -v -keystore my-key.keystore -alias kimiclaw -keyalg RSA -keysize 2048 -validity 10000
```

2. 添加到 GitHub Secrets：
   - 仓库页面 → Settings → Secrets and variables → Actions
   - 添加 `SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`

3. 修改 workflow 添加签名步骤

### 修改自动发布设置

编辑 `.github/workflows/build.yml`：

```yaml
- name: Create Release
  uses: softprops/action-gh-release@v1
  with:
    tag_name: v${{ github.run_number }}
    name: KimiClaw Release v${{ github.run_number }}
    body: |
      ## 更新内容
      - 在这里写更新日志
    draft: false        # true = 草稿，false = 直接发布
    prerelease: false   # true = 预发布版本
```

## 🎉 完成！

现在每次你推送代码到 GitHub，Actions 都会自动：
1. ✅ 构建 APK
2. ✅ 上传到 Artifacts
3. ✅ 发布到 Releases

分享你的 Releases 页面链接，其他人就可以直接下载 APK 了！

---

**示例 Releases 页面：**
```
https://github.com/YOUR_USERNAME/KimiClaw/releases
```
