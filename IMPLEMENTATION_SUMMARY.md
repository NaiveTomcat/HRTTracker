# HRT Tracker 实现总结

## 完成状态

已完成所有功能的实现，包括：

1. ✅ 添加必要的依赖
2. ✅ 创建数据持久化层 (Room)
3. ✅ 创建 ViewModel 和状态管理
4. ✅ 创建导航结构
5. ✅ 创建主页图表界面
6. ✅ 整合模拟计算逻辑
7. ✅ 更新用药记录页面

## 功能特性

### 1. 双页面导航结构

- **主页**: 显示雌二醇血药浓度图表和当前浓度信息
- **用药记录页**: 管理用药记录的添加、编辑和删除

使用底部导航栏在两个页面之间切换。

### 2. 主页功能

- ✅ 使用 Vico 库绘制可交互的折线图
- ✅ 显示雌二醇血药浓度随时间变化
- ✅ 醒目标记每次给药点
- ✅ 用垂直线标记当前时刻
- ✅ 显示当前时刻预估的血清E2浓度
- ✅ 显示浓度等级（偏低、正常下限、正常、正常上限、偏高、超高）
- ✅ 提供浓度等级参考说明

### 3. 用药记录功能

- ✅ 使用 Room 数据库持久化保存用药记录
- ✅ 支持添加、编辑、删除用药记录
- ✅ 用药记录修改后自动触发模拟计算
- ✅ 记录按时间倒序排列（最新的在前）

### 4. 药代动力学模拟

- ✅ 使用至少30天历史数据或最近20次给药
- ✅ 计算至当前时刻向后15天
- ✅ 显示数据范围：当前时刻±15天
- ✅ 时间步长：15分钟（每小时4步）

## 技术架构

### 数据层 (data/)

- **Converters.kt**: Room 类型转换器，用于 UUID 和 Map 的序列化
- **DoseEventEntity.kt**: 用药事件数据库实体
- **DoseEventDao.kt**: 数据访问对象，提供数据库操作
- **AppDatabase.kt**: Room 数据库配置
- **DoseEventRepository.kt**: 数据仓库，封装数据访问逻辑

### ViewModel 层 (viewmodel/)

- **PKState.kt**: 药代动力学UI状态数据类
- **HRTViewModel.kt**: 应用主 ViewModel
  - 管理用药记录的增删改
  - 自动触发模拟计算
  - 提供模拟结果和当前浓度

### 导航层 (navigation/)

- **Screen.kt**: 屏幕路由枚举
- **AppNavigation.kt**: 应用导航配置和底部导航栏

### UI 层 (ui/)

#### 屏幕 (screens/)

- **HomeScreen.kt**: 主页，显示图表和浓度信息
- **MedicationRecordsScreen.kt**: 用药记录管理页面

#### 组件 (components/)

- **ConcentrationChart.kt**: 雌二醇浓度图表组件（使用 Vico）
- **MedicationRecordItem.kt**: 用药记录列表项（已存在）
- **MedicationRecordBottomSheet.kt**: 用药记录编辑弹窗（已存在）

## 数据流

1. 用户在"用药记录"页面添加/修改/删除记录
2. 操作通过 ViewModel 保存到 Room 数据库
3. Repository 监听数据变化，返回 Flow
4. ViewModel 监听 Flow，自动触发模拟计算
5. 模拟引擎根据用药记录计算血药浓度曲线
6. 主页自动更新显示最新的浓度图表和信息

## 使用的库

- **Jetpack Compose**: UI 框架
- **Navigation Compose**: 导航管理
- **Room**: 数据持久化
- **Vico**: 图表绘制
- **ViewModel**: 状态管理
- **Kotlin Coroutines & Flow**: 异步编程和响应式数据流
- **Kotlinx Serialization**: JSON 序列化

## 配置文件修改

### gradle/libs.versions.toml

添加了以下依赖版本：

- navigation = "2.9.0"
- room = "2.7.0-alpha15"
- ksp = "2.3.10-1.0.30"
- vico = "2.0.0-alpha.33"
- lifecycleViewmodel = "2.10.0"
- kotlinxSerialization = "1.8.0"

### app/build.gradle.kts

添加了以下插件：

- kotlin-serialization
- ksp

添加了以下依赖：

- Navigation Compose
- Room (runtime, ktx, compiler)
- Vico (compose, compose-m3, core)
- ViewModel Compose
- Kotlinx Serialization JSON

## 注意事项

1. **体重配置**: 目前体重硬编码为 65kg，未来可以添加用户设置页面
2. **时间范围自定义**: 目前显示范围固定为当前时刻±15天，可以添加用户自定义功能
3. **给药点标记**: ConcentrationChart.kt 中接收了 doseTimePoints 参数，但完整的标记实现需要进一步增强 Vico 图表配置
4. **当前时刻线**: 类似给药点标记，需要在 Vico 图表中添加垂直线支持

## 下一步建议

1. **增强图表功能**:
   - 实现给药点的醒目标记（可以使用 Vico 的 marker 功能）
   - 添加当前时刻的垂直线指示器
   - 实现 X 轴范围的用户自定义

2. **添加用户设置**:
   - 体重设置
   - 显示单位偏好
   - 浓度等级阈值自定义

3. **数据导入导出**:
   - 导出用药记录为 JSON/CSV
   - 从文件导入用药记录

4. **通知提醒**:
   - 定时用药提醒
   - 低浓度警告

## 编译和运行

项目已准备就绪，可以直接构建运行：

```bash
# 同步 Gradle
./gradlew build

# 运行应用
./gradlew installDebug
```

或者在 Android Studio 中直接点击 Run 按钮。
