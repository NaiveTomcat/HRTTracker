# HRT Tracker

[![Build Debug APK](https://github.com/NaiveTomcat/HRTTracker/actions/workflows/apkdebug.yml/badge.svg?branch=master)](https://github.com/NaiveTomcat/HRTTracker/actions/workflows/apkdebug.yml)

[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/NaiveTomcat/HRTTracker/total?style=flat&logo=github&label=Downloads)](https://github.com/NaiveTomcat/HRTTracker/releases/latest)

一款面向 Android 的 HRT（激素替代治疗）记录与雌二醇浓度趋势工具。  
你可以用它记录每日用药、设置用药方案与提醒，并查看基于药代动力学模型生成的浓度曲线。

> 本应用仅用于个人记录与学习参考，不构成医疗建议。

## 主要功能

- **用药记录**：添加、编辑、删除每一次用药事件。
- **多给药途径**：支持肌肉注射、口服、舌下含服、透皮凝胶、贴片应用/移除。
- **用药方案**：创建按天/按周/自定义间隔的方案，可设置多个时点并启用/停用。
- **用药提醒**：启用方案后自动安排系统提醒通知。
- **浓度曲线**：在首页查看当前浓度与历史/预测曲线（单位：pg/mL）。
- **数据导入导出**：支持文件和剪贴板两种方式导入/导出 JSON（兼容 hrt.mahiro.uk 格式）。
- **个性化设置**：体重、深浅色主题、动态取色、12/24 小时制、自动检查更新。

## 适用平台

- Android 12 及以上（`minSdk = 31`）

## 安装方式

### 方式一：下载Release版本安装包（推荐）

1. 打开项目 Release 页面：<https://github.com/NaiveTomcat/HRTTracker/releases>
2. 下载最新 `apk`。
3. 在手机上安装（如有提示，请允许“安装未知应用”）。

### 方式二：使用 GitHub Action 自动构建的 Debug 版本（测试）

1. 打开项目 Actions 页面：<https://github.com/NaiveTomcat/HRTTracker/actions>
2. 找到最近的 `Build APK Debug` 工作流，点击进入。
3. 在 Artifacts 区域下载压缩包（形如：${{ env.date_today }} - ${{ env.playstore_name }} - ${{ env.repository_name }} - APK(s) debug generated）。
4. 解压后安装 `app-debug.apk`。

**注意**：Debug版本与Release版本的包名和签名均不一致，两者可以共存，但数据不互通。

### 方式三：自行构建（开发者）

```bash
./gradlew assembleRelease
```

生成的安装包通常位于：

`app/release/app-release.apk`

## 快速上手

1. **先去「设置」** 填写体重（用于浓度计算）。
2. **在「记录」** 添加历史用药。
3. **在「方案」** 创建未来用药计划并启用（可同时启用提醒）。
4. **回到「主页」** 查看当前浓度与曲线变化。

## 数据与隐私

- 用药记录与设置保存在本机本地数据库。
- 应用不会把你的记录自动上传到云端。
- 网络权限仅用于检查新版本。

## 常见问题

### 1) 曲线和实际化验不完全一致，正常吗？

正常。模型是基于通用参数的估算，个体代谢差异会造成偏差。请以专业医生建议和实际检测结果为准。

### 2) 可以备份数据吗？

可以。在「设置 → 数据」中使用 JSON 导出（文件或剪贴板），需要时再导入。

### 3) 为什么没有收到提醒？

请检查：

- 对应方案是否已启用；
- 系统是否允许通知权限；
- 设备是否限制后台闹钟/省电策略过严。

## 致谢

- 灵感来源：<https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker>
- PK 参考实现：<https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test>

## 免责声明

本应用仅用于学习、研究与个人记录，不提供诊断或治疗建议。任何医疗相关决策请咨询具备资质的医疗专业人士。

## 请我喝粉魔爪

<img width="1153" height="1153" alt="1000010734" src="https://github.com/user-attachments/assets/b40e595d-fe5c-4dfd-a3f6-bccdda109b6b" />
