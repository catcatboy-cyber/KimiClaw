# 应用图标和宣传图设计指南

## 🎨 应用图标设计

### 规格要求

#### Google Play
- **尺寸**：512x512 px
- **格式**：PNG（32位，带透明通道）
- **大小**：不超过 1024 KB
- **圆角**：系统会自动添加，设计时使用方形

#### 国内应用市场
- **尺寸**：512x512 px 或 1024x1024 px
- **格式**：PNG
- **大小**：不超过 512 KB
- **圆角**：部分市场需要圆角版本

### 设计建议

**当前图标分析**：
- 项目中已有小龙虾图片资源（lobster_normal_1.png 等）
- 可以基于现有资源设计

**设计要点**：
1. **主体**：使用可爱的小龙虾形象
2. **背景**：
   - 纯色背景（推荐渐变蓝色或海洋色）
   - 或简单的海洋元素（波浪、气泡）
3. **颜色**：
   - 主色：橙红色（小龙虾）
   - 辅色：蓝色/青色（海洋感）
4. **风格**：可爱、卡通、友好
5. **识别度**：在小尺寸下也能清晰识别

**制作方法**：
```
方案1：使用现有资源
1. 提取 lobster_normal_1.png
2. 放大到 512x512
3. 添加背景色或渐变
4. 添加简单装饰元素

方案2：在线设计工具
- Canva: https://www.canva.com
- Figma: https://www.figma.com
- 创客贴: https://www.chuangkit.com

方案3：AI生成
- 使用 Midjourney/DALL-E 生成
- 提示词："cute cartoon lobster app icon, orange and blue, simple, friendly, mobile app icon design"
```

---

## 🖼️ 宣传图（Feature Graphic）设计

### 规格要求

#### Google Play
- **尺寸**：1024x500 px
- **格式**：PNG 或 JPEG
- **大小**：不超过 1024 KB
- **用途**：在应用商店顶部展示

#### 国内应用市场
- **尺寸**：各市场要求不同
  - 华为：1280x720 px
  - 小米：1080x608 px
  - OPPO：1280x720 px
- **格式**：PNG 或 JPEG

### 设计建议

**布局方案**：
```
┌─────────────────────────────────────────┐
│                                         │
│  [小龙虾图标]  MelodyClaw 小龙虾        │
│                                         │
│  可爱的桌面宠物 · AI智能对话 · 消息提醒 │
│                                         │
└─────────────────────────────────────────┘
```

**设计要点**：
1. **左侧**：放置小龙虾形象（占1/3空间）
2. **右侧**：
   - 应用名称：MelodyClaw 小龙虾
   - 副标题：桌面悬浮电子宠物
   - 核心功能：3-4个关键词
3. **背景**：
   - 渐变色（蓝色到青色）
   - 或海洋主题背景
4. **文字**：
   - 使用清晰易读的字体
   - 白色或深色文字（根据背景调整）
5. **装饰元素**：
   - 气泡、波浪、星星等可爱元素
   - 不要过于复杂

**配色方案**：
- 主色：#FF6B35（橙红色 - 小龙虾）
- 辅色：#4ECDC4（青色 - 海洋）
- 背景：#1A535C 到 #4ECDC4 渐变
- 文字：#FFFFFF（白色）

---

## 📦 其他尺寸图标

### Android 应用内图标
当前项目中的图标位置：
```
app/src/main/res/
├── mipmap-hdpi/ic_launcher.png (72x72)
├── mipmap-mdpi/ic_launcher.png (48x48)
├── mipmap-xhdpi/ic_launcher.png (96x96)
├── mipmap-xxhdpi/ic_launcher.png (144x144)
└── mipmap-xxxhdpi/ic_launcher.png (192x192)
```

**需要更新**：
- 确保所有尺寸的图标风格一致
- 使用 Android Studio 的 Image Asset 工具批量生成

### 生成步骤（Android Studio）：
1. 右键点击 `res` 文件夹
2. 选择 `New` → `Image Asset`
3. 选择 `Launcher Icons (Adaptive and Legacy)`
4. 上传 512x512 的图标
5. 调整前景和背景
6. 点击 `Next` → `Finish`

---

## 🎯 设计检查清单

### 图标检查
- [ ] 尺寸正确（512x512）
- [ ] 格式正确（PNG，32位）
- [ ] 文件大小符合要求
- [ ] 在小尺寸下清晰可辨
- [ ] 颜色鲜明，有辨识度
- [ ] 符合应用主题

### 宣传图检查
- [ ] 尺寸正确（1024x500）
- [ ] 文字清晰可读
- [ ] 核心信息突出
- [ ] 视觉吸引力强
- [ ] 符合品牌风格
- [ ] 无版权问题

---

## 🛠️ 推荐工具

### 在线设计工具
1. **Canva**（推荐）
   - 网址：https://www.canva.com
   - 有应用图标和宣传图模板
   - 免费版功能足够使用

2. **创客贴**
   - 网址：https://www.chuangkit.com
   - 中文界面，适合国内用户
   - 有丰富的模板

3. **Figma**
   - 网址：https://www.figma.com
   - 专业设计工具
   - 免费版可用

### 图片处理工具
- **Photoshop**：专业图像处理
- **GIMP**：免费开源替代品
- **在线压缩**：https://tinypng.com（压缩PNG）

### AI生成工具
- **Midjourney**：高质量AI绘图
- **DALL-E**：OpenAI的图像生成
- **Stable Diffusion**：开源AI绘图

---

## 📝 设计提示词（AI生成参考）

### 应用图标
```
cute cartoon lobster mascot, app icon design, orange and red lobster, 
blue ocean background, simple and clean, friendly expression, 
mobile app icon, flat design, vector style, high quality
```

### 宣传图
```
app banner design, cute lobster mascot on left side, 
blue ocean gradient background, text space on right, 
bubbles and waves decoration, modern and friendly style, 
1024x500 pixels, mobile app promotional banner
```

---

## 📁 文件命名规范

保存设计文件时使用以下命名：
```
D:\KIMI项目\KimiClaw\design\
├── app_icon_512.png          # 应用图标（512x512）
├── app_icon_1024.png         # 应用图标（1024x1024）
├── feature_graphic_1024x500.png  # Google Play宣传图
├── feature_graphic_1280x720.png  # 华为宣传图
└── source_files/             # 源文件（PSD/AI/Figma）
```

---

## 💡 快速方案

如果时间紧迫，可以：
1. 使用项目中现有的 `lobster_normal_1.png`
2. 在 Canva 中：
   - 创建 512x512 画布
   - 添加渐变背景（蓝色到青色）
   - 导入小龙虾图片
   - 调整大小和位置
   - 导出 PNG
3. 同样方法制作 1024x500 宣传图
   - 添加文字："MelodyClaw 小龙虾"
   - 添加副标题："桌面悬浮电子宠物"

---

**提示**：设计完成后，将文件保存到项目的 `design` 文件夹中，并更新到 `app/src/main/res/mipmap-*` 目录。
