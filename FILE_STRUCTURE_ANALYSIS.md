# Notesmaster 项目文件分类 - 按功能分组

本文档按照不同的功能模块对项目中的文件进行分类，展示了各层次组件如何协同完成特定功能。

---

## 项目架构概览

```
Notesmaster
├── 数据层（Data Layer）
├── UI层（Presentation Layer）
├── Widget层（Widget Provider Layer）
└── 工具/辅助层
```

---

## 1. 笔记编辑功能模块

**功能描述**：用户创建、编辑、保存笔记的完整流程

### 相关文件（跨层）：

#### UI层（Presentation）
- `app/src/main/java/net/micode/notes/ui/NoteEditActivity.java` (909 lines)
  - 笔记编辑界面主Activity
  - 处理笔记内容编辑、文本格式、背景色等
  - 管理告警、提醒等功能

- `app/src/main/java/net/micode/notes/ui/NoteEditText.java`
  - 自定义EditText组件
  - 提供文本编辑的特殊功能支持

- `app/src/main/java/net/micode/notes/ui/NoteItemData.java`
  - 笔记数据模型（UI展示用）
  - 包装从数据层获取的笔记信息

#### 数据层（Data）
- `app/src/main/java/net/micode/notes/data/NotesProvider.java` (306 lines)
  - ContentProvider，提供笔记数据的增删改查接口
  - 处理数据持久化请求

- `app/src/main/java/net/micode/notes/data/NotesDatabaseHelper.java`
  - SQLite数据库管理
  - 创建和维护数据表结构

- `app/src/main/java/net/micode/notes/data/Notes.java` (280 lines)
  - 数据常量定义（表名、列名、类型常量）
  - 数据库schema定义

- `app/src/main/java/net/micode/notes/data/Contact.java`
  - 联系人数据模型

### 功能流程：
1. 用户在 `NoteEditActivity` 中编辑笔记
2. `NoteEditText` 提供文本输入支持
3. 编辑完成时，通过 `NotesProvider` 将数据写入数据库
4. `NotesDatabaseHelper` 管理数据库操作
5. `Notes.java` 提供数据结构定义

---

## 2. 笔记列表展示功能模块

**功能描述**：显示笔记列表、文件夹管理、笔记查询和删除

### 相关文件（跨层）：

#### UI层（Presentation）
- `app/src/main/java/net/micode/notes/ui/NotesListActivity.java` (1028 lines)
  - 笔记列表主界面Activity
  - 处理笔记列表显示、选择、删除、搜索
  - 文件夹管理和导航

- `app/src/main/java/net/micode/notes/ui/NotesListAdapter.java`
  - 列表适配器
  - 将笔记数据绑定到列表视图

- `app/src/main/java/net/micode/notes/ui/NotesListItem.java`
  - 列表项视图
  - 单个笔记项的UI展示

- `app/src/main/java/net/micode/notes/ui/FoldersListAdapter.java`
  - 文件夹列表适配器
  - 管理文件夹的显示和选择

- `app/src/main/java/net/micode/notes/ui/DropdownMenu.java`
  - 下拉菜单组件
  - 提供排序、过滤等功能菜单

#### 数据层（Data）
- `app/src/main/java/net/micode/notes/data/NotesProvider.java`
  - 提供笔记列表查询接口
  - 支持按文件夹、按类型的查询

- `app/src/main/java/net/micode/notes/data/NotesDatabaseHelper.java`
  - 数据库查询执行

- `app/src/main/java/net/micode/notes/data/Notes.java`
  - 定义系统文件夹常量：ROOT_FOLDER, TRASH_FOLDER等

### 功能流程：
1. `NotesListActivity` 初始化时通过 `NotesProvider` 查询笔记列表
2. `NotesListAdapter` 将数据适配到列表视图
3. 每个 `NotesListItem` 展示单个笔记信息
4. `FoldersListAdapter` 管理文件夹显示
5. `DropdownMenu` 提供操作选项

---

## 3. 笔记搜索功能模块

**功能描述**：支持笔记全文搜索、关键词查询

### 相关文件（跨层）：

#### UI层（Presentation）
- `app/src/main/java/net/micode/notes/ui/NotesListActivity.java`
  - 处理搜索输入和显示搜索结果

#### 数据层（Data）
- `app/src/main/java/net/micode/notes/data/NotesProvider.java`
  - 定义搜索URI处理 (URI_SEARCH)
  - 执行全文搜索查询

- `app/src/main/java/net/micode/notes/data/Notes.java`
  - 定义搜索相关常量

### 功能流程：
1. 用户在 `NotesListActivity` 输入搜索关键词
2. 通过 `NotesProvider` 的搜索接口查询
3. 返回匹配的笔记列表
4. 更新列表显示

---

## 4. Widget（桌面小部件）功能模块

**功能描述**：在主屏幕显示笔记小部件，支持多种尺寸

### 相关文件（跨层）：

#### Widget层
- `app/src/main/java/net/micode/notes/widget/NoteWidgetProvider.java` (133 lines)
  - Widget基类提供商
  - 处理Widget生命周期

- `app/src/main/java/net/micode/notes/widget/NoteWidgetProvider_2x.java`
  - 2x大小的Widget具体实现
  - 提供小尺寸的笔记预览

- `app/src/main/java/net/micode/notes/widget/NoteWidgetProvider_4x.java`
  - 4x大小的Widget具体实现
  - 提供大尺寸的笔记预览

#### 数据层（Data）
- `app/src/main/java/net/micode/notes/data/NotesProvider.java`
  - 为Widget提供笔记数据查询

- `app/src/main/java/net/micode/notes/data/Notes.java`
  - 定义Widget相关常量（TYPE_WIDGET_2X, TYPE_WIDGET_4X等）

### 功能流程：
1. `NoteWidgetProvider` 处理Widget的添加/移除事件
2. `NoteWidgetProvider_2x` 和 `NoteWidgetProvider_4x` 分别管理不同尺寸的Widget
3. 通过 `NotesProvider` 查询要显示的笔记数据
4. 使用 `RemoteViews` 更新Widget UI

---

## 5. 笔记设置和偏好功能模块

**功能描述**：用户偏好设置、应用配置管理

### 相关文件（跨层）：

#### UI层（Presentation）
- `app/src/main/java/net/micode/notes/ui/NotesPreferenceActivity.java`
  - 设置界面Activity
  - 管理用户偏好设置

- `app/src/main/java/net/micode/notes/ui/DateTimePickerDialog.java`
  - 日期时间选择对话框
  - 用于设置提醒时间等

#### 相关组件
- `app/src/main/java/net/micode/notes/MainActivity.java` (24 lines)
  - 应用主入口Activity

### 功能流程：
1. 用户打开 `NotesPreferenceActivity` 进行设置
2. 使用 `DateTimePickerDialog` 选择时间相关配置
3. 设置保存到SharedPreferences

---

## 6. 数据持久化和管理模块

**功能描述**：数据库操作、数据同步、数据备份等底层操作

### 相关文件（数据层）：

- `app/src/main/java/net/micode/notes/data/NotesDatabaseHelper.java`
  - SQLite数据库初始化和版本管理
  - 提供数据库表的创建和升级

- `app/src/main/java/net/micode/notes/data/Notes.java`
  - 数据表schema定义
  - 内部类：NoteColumns, TextNote, CallNote等

- `app/src/main/java/net/micode/notes/data/NotesProvider.java`
  - ContentProvider实现
  - 提供统一的数据访问接口
  - 处理数据的增删改查操作

- `app/src/main/java/net/micode/notes/data/Contact.java`
  - 联系人数据表定义

### 功能流程：
1. 应用首次启动，`NotesDatabaseHelper` 创建数据库
2. 应用运行时，各UI层通过 `NotesProvider` 访问数据
3. `NotesProvider` 调用 `NotesDatabaseHelper` 执行SQL操作
4. 数据按 `Notes.java` 中定义的schema存储和检索

---

## 文件依赖关系图

```
UI层：
  NoteEditActivity ─────┐
  NotesListActivity ────├──> NotesProvider ──> NotesDatabaseHelper
  NoteEditText ─────────┤
  NotesListAdapter ─────┤
  DropdownMenu ─────────┤
  FoldersListAdapter ───┘

Widget层：
  NoteWidgetProvider ───┐
  NoteWidgetProvider_2x ├──> NotesProvider
  NoteWidgetProvider_4x ┘

设置层：
  NotesPreferenceActivity ──> SharedPreferences
  DateTimePickerDialog ──────┘

数据层（底层）：
  Notes.java（常量定义）
     ↑
  NotesProvider（ContentProvider）
     ↑
  NotesDatabaseHelper（SQLite）
```

---

## 总结

这个项目采用经典的三层架构设计：

1. **UI层（Presentation Layer）**：负责用户界面和交互
2. **数据层（Data Layer）**：负责数据的存储和访问
3. **Widget层**：特殊的展示层，用于桌面小部件

各功能模块通过跨层文件的组合来实现。例如"笔记编辑功能"需要：
- UI层的 `NoteEditActivity` 提供界面
- 数据层的 `NotesProvider` 和 `NotesDatabaseHelper` 提供数据访问

这种分层设计使得各模块职责清晰，便于维护和扩展。

