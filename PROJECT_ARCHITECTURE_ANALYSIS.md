# Notesmaster 项目架构完整分析

## 一、项目概览

**项目名称**: Notesmaster（小米便签 MIUI Notes）  
**项目类型**: Android 笔记应用  
**编译环境**: Gradle + Android Gradle Plugin  
**Target SDK**: 36 (Android 15)  
**Min SDK**: 30 (Android 11)  
**Java Version**: 11

---

## 二、模块拆分

### 1. **层级结构**

```
Notesmaster 项目
├── UI 层（Presentation Layer）        - 用户界面展示和交互
├── 模型层（Model Layer）              - 业务逻辑和数据处理
├── 数据层（Data Layer）               - 数据持久化和访问
├── Widget 层（Widget Provider Layer）  - 桌面小部件
├── 工具层（Tool Layer）               - 辅助工具类
└── 远程同步层（GTask Layer）          - 云同步功能
```

### 2. **目录结构详解**

```
app/src/main/java/net/micode/notes/
│
├── ui/                              # UI 层 (演示层)
│   ├── NotesListActivity.java        # 笔记列表主界面 (1028 lines)
│   ├── NoteEditActivity.java         # 笔记编辑界面 (909 lines)
│   ├── NoteEditText.java             # 自定义编辑文本控件
│   ├── NotesListAdapter.java         # 列表适配器
│   ├── NotesListItem.java            # 列表项视图
│   ├── FoldersListAdapter.java       # 文件夹列表适配器
│   ├── NoteItemData.java             # 笔记数据模型（UI用）
│   ├── DropdownMenu.java             # 下拉菜单
│   ├── DateTimePickerDialog.java     # 日期时间选择对话框
│   ├── AlarmAlertActivity.java       # 告警提醒界面
│   ├── AlarmReceiver.java            # 告警广播接收器
│   ├── AlarmInitReceiver.java        # 启动时初始化告警
│   └── NotesPreferenceActivity.java  # 设置偏好界面
│
├── data/                            # 数据层 (持久化层)
│   ├── Notes.java                   # 数据常量定义和 Schema (280 lines)
│   ├── NotesProvider.java           # ContentProvider 实现 (306 lines)
│   ├── NotesDatabaseHelper.java      # SQLite 数据库管理
│   └── Contact.java                 # 联系人数据模型
│
├── model/                           # 业务逻辑层
│   ├── WorkingNote.java             # 工作笔记模型 (369 lines)
│   └── Note.java                    # 笔记数据操作 (254 lines)
│
├── widget/                          # Widget 提供者层
│   ├── NoteWidgetProvider.java      # Widget 基类
│   ├── NoteWidgetProvider_2x.java   # 2x 大小 Widget
│   └── NoteWidgetProvider_4x.java   # 4x 大小 Widget
│
├── tool/                            # 工具层
│   ├── DataUtils.java               # 数据工具类
│   ├── ResourceParser.java          # 资源解析器
│   ├── BackupUtils.java             # 备份工具
│   └── ...其他工具
│
├── gtask/                           # 远程同步层
│   └── remote/
│       └── GTaskSyncService.java    # 云同步服务
│
└── MainActivity.java                # 应用入口点
```

---

## 三、数据库设计

### 数据库表结构

#### 表 1: `note` (笔记表)
```
_id                 INTEGER PRIMARY KEY  # 笔记ID
parent_id           INTEGER              # 父文件夹ID (0=根目录, -1=临时, -2=通话记录, -3=回收站)
created_date        LONG                 # 创建时间
modified_date       LONG                 # 修改时间
alert_date          LONG                 # 告警时间
snippet             TEXT                 # 笔记摘要/标题
type                INTEGER              # 类型 (0=笔记, 1=文件夹, 2=系统)
widget_id           INTEGER              # 绑定的 Widget ID
widget_type         INTEGER              # Widget 类型 (0=2x, 1=4x)
bg_color_id         INTEGER              # 背景色ID
local_modified      BOOLEAN              # 本地是否修改
```

#### 表 2: `data` (笔记内容表)
```
_id                 INTEGER PRIMARY KEY  # 数据ID
mime_type           TEXT                 # MIME 类型
note_id             INTEGER FOREIGN KEY  # 关联笔记ID
content             TEXT                 # 笔记内容
data1, data2, data3, data4  TEXT        # 扩展数据字段
```

---

## 四、核心功能模块详解

### 功能 1: 笔记编辑和保存

#### 调用链：完整流程

```
1. 用户启动应用
   ↓
   NotesListActivity.onCreate()
   
2. 用户点击编辑笔记或新建笔记
   ↓
   NotesListActivity.onItemClick()
   ↓
   Intent(ACTION_VIEW) or Intent(ACTION_INSERT_OR_EDIT)
   
3. 启动编辑界面
   ↓
   NoteEditActivity.onCreate()
   ↓
   initActivityState(Intent intent)
   
4. 加载或创建笔记
   ├─ 若为编辑：WorkingNote.load(context, noteId)
   │  ├─ WorkingNote 构造函数
   │  ├─ loadNote() → 查询 note 表
   │  └─ loadNoteData() → 查询 data 表
   │
   └─ 若为新建：WorkingNote.createEmptyNote(...)
      └─ 创建空笔记对象
   
5. 用户编辑笔记内容
   ↓
   NoteEditText.onTextChanged() 或 setWorkingText()
   ↓
   WorkingNote.setWorkingText(text)
   ↓
   Note.setTextData(DataColumns.CONTENT, mContent)
   
6. 用户保存笔记（退出或主动保存）
   ↓
   NoteEditActivity.onStop() 或 onDestroy()
   ↓
   WorkingNote.saveNote()
   ↓
   isWorthSaving() [检查是否值得保存]
   
7. 保存逻辑
   ├─ 若笔记不存在于数据库：
   │  └─ Note.getNewNoteId(context, folderId)
   │     └─ ContentResolver.insert(CONTENT_NOTE_URI, values)
   │        → NotesProvider.insert()
   │           → NotesDatabaseHelper.insert()
   │              → SQLite 数据库写入 note 表
   │
   └─ 若笔记已存在：
      ├─ ContentResolver.update(CONTENT_NOTE_URI, mNoteDiffValues)
      │  → NotesProvider.update()
      │     → NotesDatabaseHelper.update()
      │        → SQLite 更新 note 表
      │
      └─ mNoteData.pushIntoContentResolver(context, noteId)
         ├─ 若为新文本数据：ContentResolver.insert(CONTENT_DATA_URI)
         │  → NotesProvider.insert()
         │     → SQLite 写入 data 表
         │
         └─ 若为更新数据：ContentResolver.applyBatch()
            → ContentProviderOperation 批量操作
               → SQLite 更新 data 表

8. 更新 Widget（若有关联）
   ↓
   NoteWidgetProvider_2x.onUpdate() / NoteWidgetProvider_4x.onUpdate()
   ↓
   RemoteViews.setTextViewText()
   ↓
   AppWidgetManager.updateAppWidget()
```

#### 关键代码片段

**WorkingNote.saveNote() 核心逻辑：**
```java
public synchronized boolean saveNote() {
    if (isWorthSaving()) {
        if (!existInDatabase()) {
            // 新笔记：创建新的 note 记录
            if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                return false;
            }
        }
        
        // 同步笔记数据到数据库
        mNote.syncNote(mContext, mNoteId);
        
        // 更新 Widget
        if (mWidgetId != INVALID && mWidgetType != INVALID 
            && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();
        }
        return true;
    }
    return false;
}
```

**Note.syncNote() - 批量更新数据：**
```java
public boolean syncNote(Context context, long noteId) {
    if (!isLocalModified()) {
        return true;
    }
    
    // 1. 更新 note 表
    context.getContentResolver().update(
        ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
        mNoteDiffValues, null, null
    );
    mNoteDiffValues.clear();
    
    // 2. 更新 data 表
    if (mNoteData.isLocalModified()) {
        mNoteData.pushIntoContentResolver(context, noteId);
    }
    
    return true;
}
```

---

### 功能 2: 笔记列表查询和显示

#### 调用链

```
1. NotesListActivity 启动
   ↓
   onCreate() → initContentResolver()
   
2. 查询笔记列表
   ↓
   BackgroundQueryHandler.startQuery(token, null, uri, projection, selection, args, order)
   
3. 异步查询
   ↓
   ContentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
   ↓
   NotesProvider.query(uri, ...)
   ├─ 匹配 URI：
   │  ├─ URI_NOTE → 查询所有笔记
   │  ├─ URI_NOTE_ITEM → 查询单个笔记
   │  └─ URI_SEARCH → 搜索笔记
   │
   └─ SQLiteDatabase.query(TABLE.NOTE, projection, selection, ...)
      → 调用 NotesDatabaseHelper
         → SQLite 数据库查询
   
4. 返回 Cursor
   ↓
   onQueryComplete(token, cookie, cursor)
   
5. 绑定数据到 UI
   ↓
   NotesListAdapter.changeCursor(cursor)
   ↓
   ListView 显示笔记项
   ↓
   NotesListItem 绘制单个笔记

6. 用户交互
   ├─ 点击笔记 → 启动 NoteEditActivity
   ├─ 长按笔记 → 显示上下文菜单
   │  ├─ 移动到文件夹
   │  ├─ 删除笔记 → 移至回收站
   │  └─ ...
   └─ 搜索笔记 → 调用搜索功能模块
```

---

### 功能 3: 笔记搜索

#### 调用链

```
1. 用户在搜索框输入关键字
   ↓
   SearchManager 处理搜索请求
   
2. 调用搜索 URI
   ↓
   NotesProvider.query(content://micode_notes/search?query=keyword)
   ↓
   mMatcher 匹配 → URI_SEARCH
   
3. 执行搜索查询
   ↓
   执行 SQL：
   SELECT SNIPPET FROM note 
   WHERE SNIPPET LIKE '%keyword%' 
   AND parent_id <> TRASH_ID 
   AND type = TYPE_NOTE
   
4. 返回搜索结果
   ↓
   NotesListActivity 显示搜索结果
   
5. 用户点击搜索结果
   ↓
   启动 NoteEditActivity 查看笔记
```

**NOTES_SNIPPET_SEARCH_QUERY SQL:**
```sql
SELECT id, SNIPPET, SNIPPET, ..., action, mime_type
FROM note
WHERE SNIPPET LIKE ?
  AND parent_id <> -3 (trash)
  AND type = 0 (note)
```

---

### 功能 4: Widget 显示和更新

#### 调用链

```
1. 用户在桌面添加 Widget
   ↓
   系统调用 NoteWidgetProvider.onEnabled() / onUpdate()
   
2. 根据 Widget 大小
   ├─ NoteWidgetProvider_2x (2x2 格子)
   │  └─ 显示笔记简要内容
   │
   └─ NoteWidgetProvider_4x (4x4 格子)
      └─ 显示更多笔记内容
   
3. 查询关联的笔记
   ↓
   ContentResolver.query(CONTENT_NOTE_URI, ...)
   ↓
   NotesProvider.query()
   ↓
   查询 note 表获取笔记信息
   
4. 构建 RemoteViews
   ↓
   setTextViewText(R.id.note_content, content)
   setTextColor(...)
   setBackgroundResource(...)
   
5. 更新 Widget
   ↓
   AppWidgetManager.updateAppWidget(widgetId, remoteViews)
   ↓
   系统刷新桌面 Widget
   
6. 用户点击 Widget
   ↓
   PendingIntent.getActivity() → 启动 NoteEditActivity
   
7. 笔记修改时更新 Widget
   ↓
   WorkingNote.mNoteSettingStatusListener.onWidgetChanged()
   ↓
   AppWidgetManager.updateAppWidget()
```

---

### 功能 5: 告警提醒

#### 调用链

```
1. 用户为笔记设置提醒时间
   ↓
   NoteEditActivity 点击提醒按钮
   ↓
   DateTimePickerDialog.show()
   
2. 用户选择日期时间
   ↓
   OnDateTimeSetListener.onDateTimeSet(date)
   ↓
   WorkingNote.setAlertDate(date, true)
   ↓
   Note.setNoteValue(NoteColumns.ALERTED_DATE, date)
   
3. 保存提醒时间
   ↓
   WorkingNote.saveNote()
   ↓
   Note.syncNote()
   ↓
   ContentResolver.update() → 更新 note 表中的 alert_date

4. 注册告警
   ↓
   NoteEditActivity.setAlarm()
   ↓
   AlarmManager.set(AlarmManager.RTC_WAKEUP, time, pendingIntent)
   
5. 到期时触发告警
   ↓
   系统广播 → AlarmReceiver.onReceive()
   ↓
   启动 AlarmAlertActivity
   ↓
   显示告警提醒界面

6. 启动时恢复所有告警
   ↓
   AlarmInitReceiver.onReceive()
      (BOOT_COMPLETED 权限)
   ↓
   查询所有有提醒的笔记
   ↓
   重新注册所有告警
```

---

## 五、数据流向图

### 完整数据流

```
┌─────────────────┐
│   用户交互       │
│  (UI Layer)     │
└────────┬────────┘
         │
    ┌────┴─────────────────────────┬──────────────────┐
    │                              │                  │
    v                              v                  v
┌────────────┐          ┌──────────────────┐  ┌─────────────┐
│ NotesListActivity    │ NoteEditActivity │  │ Widget      │
│ - 列表展示  │          │ - 编辑笔记       │  │ - 桌面显示  │
│ - 删除/搜索 │          │ - 格式设置      │  │            │
└──────┬─────┘          └────────┬─────────┘  └──────┬──────┘
       │                         │                    │
       └──────────┬──────────────┴────────────────────┘
                  │
                  v
         ┌─────────────────┐
         │  Model Layer    │
         │ WorkingNote     │
         │ Note            │
         └────────┬────────┘
                  │
                  v
         ┌─────────────────┐
         │  ContentProvider │  (Binder IPC)
         │  NotesProvider  │
         └────────┬────────┘
                  │
                  v
         ┌──────────────────────┐
         │  Database Layer      │
         │ NotesDatabaseHelper  │
         │  SQLiteDatabase      │
         └────────┬─────────────┘
                  │
                  v
         ┌──────────────────────┐
         │  SQLite Database     │
         │  - note 表           │
         │  - data 表           │
         │  - folder 表         │
         └──────────────────────┘
```

---

## 六、关键类及职责

| 类名 | 所在包 | 行数 | 主要职责 |
|------|------|------|--------|
| **NotesListActivity** | ui | 1028 | 笔记列表展示、管理、删除、搜索 |
| **NoteEditActivity** | ui | 909 | 笔记编辑、格式设置、告警设置 |
| **WorkingNote** | model | 369 | 笔记业务逻辑、数据加载与保存 |
| **Note** | model | 254 | 笔记数据操作、数据库同步 |
| **NotesProvider** | data | 306 | ContentProvider 实现、数据访问接口 |
| **Notes** | data | 280 | 数据常量、表结构定义 |
| **NotesDatabaseHelper** | data | - | SQLite 数据库管理、表创建 |
| **NoteWidgetProvider_2x** | widget | - | 2x Widget 实现 |
| **NoteWidgetProvider_4x** | widget | - | 4x Widget 实现 |

---

## 七、数据持久化流程详解

### 插入新笔记

```
Step 1: 获取新笔记 ID
WorkingNote.saveNote()
  └─ Note.getNewNoteId(context, folderId)
     └─ ContentValues values = {
          CREATED_DATE: now,
          MODIFIED_DATE: now,
          TYPE: TYPE_NOTE,
          LOCAL_MODIFIED: 1,
          PARENT_ID: folderId
        }
     └─ ContentResolver.insert(CONTENT_NOTE_URI, values)
        └─ NotesProvider.insert()
           └─ NotesDatabaseHelper.insert()
              └─ db.insert(TABLE.NOTE, null, values)
                 └─ 数据库返回行 ID

Step 2: 同步笔记内容
Note.syncNote(context, noteId)
  └─ mNoteData.pushIntoContentResolver(context, noteId)
     └─ ContentValues values = {
          CONTENT: content_text,
          MIME_TYPE: TEXT_NOTE,
          NOTE_ID: noteId
        }
     └─ ContentResolver.insert(CONTENT_DATA_URI, values)
        └─ NotesProvider.insert()
           └─ db.insert(TABLE.DATA, null, values)
              └─ 数据库返回行 ID，保存为 mTextDataId
```

### 更新现有笔记

```
Step 1: 更新笔记元数据
Note.syncNote(context, noteId)
  └─ ContentValues values = {
       MODIFIED_DATE: now,
       LOCAL_MODIFIED: 1,
       ... 其他改动字段
     }
  └─ ContentResolver.update(
       ContentUris.withAppendedId(CONTENT_NOTE_URI, noteId),
       values, null, null
     )
     └─ NotesProvider.update()
        └─ db.update(TABLE.NOTE, values, "_id=?", [noteId])

Step 2: 更新笔记内容
  └─ mNoteData.pushIntoContentResolver(context, noteId)
     └─ ArrayList<ContentProviderOperation> operationList
     └─ 若 mTextDataId > 0（已存在）:
        └─ ContentProviderOperation.newUpdate(
             ContentUris.withAppendedId(CONTENT_DATA_URI, mTextDataId)
           ).withValues(newValues).build()
     └─ ContentResolver.applyBatch(AUTHORITY, operationList)
        └─ 批量执行 ContentProviderOperation
           └─ db.update(TABLE.DATA, values, "_id=?", [dataId])
```

---

## 八、重要常量和配置

### 系统文件夹 ID
```java
ID_ROOT_FOLDER = 0           // 默认/根文件夹
ID_TEMPARAY_FOLDER = -1      // 临时文件夹
ID_CALL_RECORD_FOLDER = -2   // 通话记录文件夹
ID_TRASH_FOLER = -3          // 回收站
```

### 笔记类型
```java
TYPE_NOTE = 0    // 普通笔记
TYPE_FOLDER = 1  // 文件夹
TYPE_SYSTEM = 2  // 系统文件夹
```

### Widget 类型
```java
TYPE_WIDGET_INVALIDE = -1  // 无效 Widget
TYPE_WIDGET_2X = 0         // 2x2 Widget
TYPE_WIDGET_4X = 1         // 4x4 Widget
```

### ContentProvider 权限
```
权限: micode_notes
Authority: net.micode.notes
```

---

## 九、权限清单

```xml
android.permission.WRITE_EXTERNAL_STORAGE  <!-- 写外部存储 -->
android.permission.INTERNET                <!-- 网络访问 -->
android.permission.READ_CONTACTS           <!-- 读取联系人 -->
android.permission.GET_ACCOUNTS            <!-- 获取账户 -->
android.permission.RECEIVE_BOOT_COMPLETED  <!-- 启动时初始化 -->
com.android.launcher.permission.INSTALL_SHORTCUT  <!-- 安装快捷方式 -->
```

---

## 十、核心流程总结

### 笔记编辑保存的完整流程

```
1. 启动 NoteEditActivity
   ↓
2. 初始化笔记数据
   - 新笔记：WorkingNote.createEmptyNote()
   - 已有笔记：WorkingNote.load(noteId) 
     → 查询 note 表 + data 表
   ↓
3. 用户编辑
   - NoteEditText.onTextChanged()
   - WorkingNote.setWorkingText(content)
   - Note.setTextData(CONTENT, content)
   ↓
4. Activity 生命周期
   - onStop() / onDestroy()
   - WorkingNote.saveNote()
   ↓
5. 检查是否保存
   - isWorthSaving()
   - 判断：已删除/为空/未修改 → 不保存
   ↓
6. 获取或创建笔记 ID
   - 新笔记：Note.getNewNoteId() 
     → ContentResolver.insert(CONTENT_NOTE_URI)
     → NotesProvider.insert()
     → SQLite 写入 note 表
   ↓
7. 同步笔记数据
   - Note.syncNote(noteId)
   - 更新 note 表记录
   - 更新 data 表内容
   ↓
8. 更新 Widget（如有关联）
   - onWidgetChanged()
   - AppWidgetManager.updateAppWidget()
   ↓
9. 完成保存
```

---

## 十一、扩展功能

### 云同步（GTask）
- **包**: `net.micode.notes.gtask`
- **服务**: `GTaskSyncService`
- **功能**: 与 Google Tasks 或云存储同步

### 备份工具
- **类**: `BackupUtils`
- **功能**: 笔记数据备份和恢复

### 联系人集成
- **类**: `Contact`
- **功能**: 从通话记录创建笔记

---

## 十二、总体架构优点

✅ **分层清晰**: UI、业务逻辑、数据层分离  
✅ **ContentProvider 模式**: 统一数据访问接口，支持多进程  
✅ **异步查询**: 使用 AsyncQueryHandler 避免 ANR  
✅ **批量操作**: ContentProviderOperation 提高性能  
✅ **Widget 支持**: 完整的桌面小部件集成  
✅ **云同步**: 支持远程数据同步  

---

**文档生成时间**: 2024  
**项目版本**: 1.0

