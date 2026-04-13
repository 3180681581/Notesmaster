# Notesmaster 模块依赖关系与设计模式

## 一、模块依赖图

### 1. 完整模块依赖关系

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI 层 (Presentation)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  NotesListActivity          NoteEditActivity        Widget        │
│      (列表展示)               (笔记编辑)            (桌面显示)     │
│         |                        |                   |           │
│         ├─ NotesListAdapter      ├─ NoteEditText   ├─NoteWidgetProvider_2x
│         ├─ FoldersListAdapter    ├─ HeadViewHolder └─NoteWidgetProvider_4x
│         ├─ DropdownMenu          └─DateTimePickerDialog
│         ├─ AlarmAlertActivity                                    │
│         └─ NotesPreferenceActivity                               │
│                                                                   │
└───────────────────────────────────────────────┬───────────────────┘
                                                 │ 依赖
                                                 ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Model 层 (Business Logic)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│                  WorkingNote (工作笔记模型)                       │
│                      (369 lines)                                 │
│                         │                                        │
│                         ├─ Note (笔记数据操作)                   │
│                         │     (254 lines)                        │
│                         │     └─ NoteData (内部类)              │
│                         │                                        │
│                         └─ Listener 模式                        │
│                           └─ NoteSettingChangedListener         │
│                              ├─ onBackgroundColorChanged()      │
│                              ├─ onClockAlertChanged()           │
│                              ├─ onWidgetChanged()               │
│                              └─ onCheckListModeChanged()        │
│                                                                   │
└───────────────────────────────────────────────┬───────────────────┘
                                                 │ 依赖
                                                 ↓
┌─────────────────────────────────────────────────────────────────┐
│                   Data 层 (Data Access Layer)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│           NotesProvider (ContentProvider)                         │
│              (306 lines)                                          │
│                │                                                 │
│                ├─ UriMatcher (URI 路由)                          │
│                │  ├─ URI_NOTE (查所有笔记)                       │
│                │  ├─ URI_NOTE_ITEM (查单个笔记)                  │
│                │  ├─ URI_DATA (查所有数据)                       │
│                │  ├─ URI_DATA_ITEM (查单个数据)                  │
│                │  ├─ URI_SEARCH (搜索)                           │
│                │  └─ URI_SEARCH_SUGGEST (搜索建议)              │
│                │                                                 │
│                └─ NotesDatabaseHelper                            │
│                    └─ SQLiteDatabase                             │
│                                                                   │
│           Notes.java (Schema 定义)                               │
│           (280 lines)                                            │
│             ├─ NoteColumns (note 表列定义)                       │
│             ├─ DataColumns (data 表列定义)                       │
│             ├─ TextNote (文本笔记常量)                           │
│             ├─ CallNote (通话记录常量)                           │
│             └─ DataConstants                                     │
│                                                                   │
└───────────────────────────────────────────────┬───────────────────┘
                                                 │ 依赖
                                                 ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Database 层 (SQLite)                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│     ┌──────────────────────────────────────────────────┐        │
│     │  note 表                                          │        │
│     ├──────────────────────────────────────────────────┤        │
│     │ _id, parent_id, created_date, modified_date     │        │
│     │ alert_date, snippet, type, widget_id, etc.      │        │
│     └──────────────────────────────────────────────────┘        │
│                                                                   │
│     ┌──────────────────────────────────────────────────┐        │
│     │  data 表                                          │        │
│     ├──────────────────────────────────────────────────┤        │
│     │ _id, mime_type, note_id, content, data1-4      │        │
│     └──────────────────────────────────────────────────┘        │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Tool 层 (Utilities)                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  DataUtils             ResourceParser          BackupUtils      │
│  (数据工具)            (资源解析)              (备份工具)       │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│               GTask 层 (Remote Sync - 可选)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  GTaskSyncService      (云同步服务)                              │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、核心类之间的关系

### 类 UML 图（简化版）

```
┌──────────────────────────┐
│   NotesListActivity      │
│  + onCreate()            │
│  + onItemClick()         │
│  + startQuery()          │
└──────────────┬───────────┘
               │ uses
               ↓
┌──────────────────────────┐         ┌─────────────────────┐
│  NotesListAdapter        │ ← uses ─│  NotesListItem      │
│  + bindView()            │         │  (ViewHolder)       │
│  + changeCursor()        │         └─────────────────────┘
└──────────────────────────┘

┌──────────────────────────┐
│   NoteEditActivity       │
│  + onCreate()            │
│  + saveNote()            │
│  + onDateTimeSet()       │
└──────────────┬───────────┘
               │ uses
               ↓
┌──────────────────────────┐
│    WorkingNote           │◄───────┐ implements
│  + load()                │        │
│  + saveNote()            │        ├──NoteSettingChangedListener
│  + createEmptyNote()     │        │
│  + setAlertDate()        │        │ (callback interface)
│  + setWorkingText()      │        │
└──────────────┬───────────┘        │
               │ uses               │
               ↓                    │
┌──────────────────────────┐        │
│      Note                ├────────┘
│  + getNewNoteId()        │
│  + syncNote()            │
│  + setNoteValue()        │
│  + setTextData()         │
│  - NoteData              │ (inner class)
│    - pushIntoContentResolver()
└──────────────┬───────────┘
               │ uses
               ↓
┌──────────────────────────────────┐
│    NotesProvider                 │
│    (extends ContentProvider)     │
│  + onCreate()                    │
│  + query()                       │
│  + insert()                      │
│  + update()                      │
│  + delete()                      │
│  - UriMatcher mMatcher           │
└──────────────┬──────────────────┘
               │ uses
               ↓
┌──────────────────────────────────┐
│  NotesDatabaseHelper             │
│  (extends SQLiteOpenHelper)      │
│  + onCreate()                    │
│  + onUpgrade()                   │
│  + getReadableDatabase()         │
│  + getWritableDatabase()         │
└──────────────────────────────────┘
```

---

## 三、设计模式

### 1. **MVC 架构模式**

```
Model              View                    Controller
─────              ────                    ──────────

WorkingNote    ← → NoteEditActivity     ← → User Input
  Note              NotesListActivity
                    NoteEditText

Notes.java     ← → UI Components
NotesDatabaseHelper
```

**实施**:
- **Model**: WorkingNote, Note, Notes.java
- **View**: Activity, Adapter, Layout XML
- **Controller**: Activity (处理用户交互)

---

### 2. **ContentProvider 模式**

```
┌────────────────────────────────────────────────────────┐
│              应用层（UI & Business Logic）              │
│         使用 ContentResolver 访问数据                   │
└─────────────────────────┬──────────────────────────────┘
                          │
                    IPC (Binder)
                          │
┌─────────────────────────▼──────────────────────────────┐
│            NotesProvider (ContentProvider)             │
│              - 统一的数据访问接口                       │
│              - 支持多进程访问                           │
│              - URI 路由数据请求                         │
└─────────────────────────┬──────────────────────────────┘
                          │
┌─────────────────────────▼──────────────────────────────┐
│         NotesDatabaseHelper (SQLiteOpenHelper)        │
│              - 数据库版本管理                          │
│              - 表结构创建                              │
└─────────────────────────┬──────────────────────────────┘
                          │
┌─────────────────────────▼──────────────────────────────┐
│            SQLite Database                             │
│              - 数据持久化                              │
└─────────────────────────────────────────────────────────┘
```

**优点**:
✅ 统一数据访问接口  
✅ 支持跨应用访问  
✅ 支持多进程同时访问  
✅ 自动处理事务  

---

### 3. **观察者模式（Listener Pattern）**

```
WorkingNote
    │
    ├─ implements NoteSettingChangedListener 接口定义
    │   ├─ onBackgroundColorChanged()
    │   ├─ onClockAlertChanged()
    │   ├─ onWidgetChanged()
    │   └─ onCheckListModeChanged()
    │
    └─ mNoteSettingStatusListener (observer reference)
        │
        └─ 当笔记属性变化时调用
           ├─ setBgColorId() → onBackgroundColorChanged()
           ├─ setAlertDate() → onClockAlertChanged()
           ├─ saveNote() → onWidgetChanged()
           └─ setCheckListMode() → onCheckListModeChanged()

实现者：
NoteEditActivity (implements NoteSettingChangedListener)
    ├─ onBackgroundColorChanged()
    │   └─ 更新 UI 背景色
    ├─ onClockAlertChanged()
    │   └─ 更新告警 Icon
    ├─ onWidgetChanged()
    │   └─ 触发 Widget 更新
    └─ onCheckListModeChanged()
        └─ 切换检查列表模式 UI
```

**流程**:
```
WorkingNote 状态变化
    ↓
检查 mNoteSettingStatusListener 是否为空
    ↓
调用相应的 callback 方法
    ↓
NoteEditActivity 收到通知
    ↓
执行相应的 UI 更新操作
```

---

### 4. **异步处理模式（AsyncQueryHandler）**

```
UI Thread                       Worker Thread
──────────                       ─────────────

startQuery()
    │
    ├─ 发送 MESSAGE_QUERY
    │       │
    │       └─────────────► Handler.handleMessage()
    │                           │
    │                           ├─ ContentResolver.query()
    │                           │
    │                           └─ 返回 Cursor
    │
    └─ onQueryComplete()◄────── sendMessage(UI 消息)
        │
        └─ 更新 Adapter


好处：
✅ 避免 ANR (Application Not Responding)
✅ UI 保持响应
✅ 数据库查询在后台进行
```

---

### 5. **工厂模式**

```
WorkingNote.createEmptyNote(context, folderId, widgetId, widgetType, bgId)
    ↓
创建并返回空笔记对象
    └─ new WorkingNote(context, folderId)
        └─ 初始化默认值

WorkingNote.load(context, id)
    ↓
创建并加载已有笔记对象
    └─ new WorkingNote(context, id, 0)
        ├─ loadNote()
        └─ loadNoteData()
```

---

### 6. **Adapter 模式**

```
Cursor (Raw Data)
    ↓
    └─ NotesListAdapter (Data Adapter)
        ├─ bindView(view, context, cursor)
        │   └─ 将 Cursor 中的数据映射到 View
        │
        └─ changeCursor(newCursor)
            └─ 更新数据源并刷新 UI


Widget 和 Cursor 之间的转换：
        
Note 对象 ←→ ContentValues (用于数据库操作)
                └─ NotesProvider 处理转换
```

---

## 四、数据流向

### 编辑流程中的数据流

```
用户输入（文本）
    │
    ↓
NoteEditText
    │
    ├─ onTextChanged()
    │   └─ setWorkingText(text)
    │
    ↓
WorkingNote (内存中)
    │
    ├─ mContent: String (当前编辑内容)
    └─ Note (数据待同步标记)
    │
    └─ saveNote()
        │
        ├─ isWorthSaving()
        │   └─ 检查是否值得保存
        │
        └─ Note.syncNote()
            ├─ mNoteDiffValues → ContentValues
            │   └─ ContentResolver.update()
            │
            └─ mNoteData.pushIntoContentResolver()
                └─ ContentResolver.insert/update/applyBatch()
                    │
                    ↓
                    NotesProvider
                    │
                    ↓
                    SQLiteDatabase
                    │
                    ↓
                    ❯ note 表更新
                    ❯ data 表更新
```

---

### 查询流程中的数据流

```
用户触发查询（打开列表、搜索）
    │
    ↓
ContentResolver.query()
    │
    ↓
NotesProvider.query()
    │
    ├─ UriMatcher 匹配 URI
    │
    └─ 构建 SQL 查询
        │
        ↓
        SQLiteDatabase.query()
        │
        ↓
        SQLite 数据库返回 Cursor
        │
        ↓
        NotesProvider 返回 Cursor
        │
        ↓
        NotesListAdapter
        │
        ├─ cursor.moveToFirst()
        ├─ cursor.moveToNext()
        │
        └─ bindView()
            │
            ├─ 读取 cursor 中的数据
            └─ 绑定到 View
                │
                ↓
                ListView / RecyclerView
                │
                ↓
                ❯ 屏幕显示笔记列表
```

---

## 五、关键接口

### 1. NotesProvider 接口

```java
public class NotesProvider extends ContentProvider {
    // 数据访问入口
    query()      // 查询数据
    insert()     // 插入数据
    update()     // 更新数据
    delete()     // 删除数据
    getType()    // 返回 MIME 类型
}
```

### 2. WorkingNote 内部接口

```java
public interface NoteSettingChangedListener {
    void onBackgroundColorChanged();
    void onClockAlertChanged(long date, boolean set);
    void onWidgetChanged();
    void onCheckListModeChanged(int oldMode, int newMode);
}
```

### 3. Note 内部数据类

```java
private class NoteData {
    ContentValues mTextDataValues;
    ContentValues mCallDataValues;
    
    Uri pushIntoContentResolver(Context context, long noteId)
    // 将数据同步到数据库
}
```

---

## 六、事务与数据一致性

### 原子性操作

```
使用 ContentProviderOperation 进行批量操作：

ArrayList<ContentProviderOperation> operations = new ArrayList<>();

// 操作1：更新文本数据
operations.add(
    ContentProviderOperation.newUpdate(dataUri)
        .withValues(textValues)
        .build()
);

// 操作2：更新通话记录数据
operations.add(
    ContentProviderOperation.newUpdate(callUri)
        .withValues(callValues)
        .build()
);

// 一次性执行所有操作（原子性）
context.getContentResolver().applyBatch(AUTHORITY, operations);

优点：
✅ 多个操作要么全部成功，要么全部失败
✅ 避免中间状态不一致
✅ 提高性能（减少 IPC 调用次数）
```

---

## 七、扩展点

### 1. 添加新的数据类型

```
步骤：
1. 在 Notes.java 中定义新的常量
2. 在 NotesDatabaseHelper 中创建新表
3. 在 NotesProvider 中添加新的 URI 匹配
4. 在 Note 类中添加新的数据操作方法
5. 在 UI 层中调用新的接口
```

### 2. 添加新的 Widget

```
步骤：
1. 创建 NoteWidgetProvider_Nx 类
2. 在 AndroidManifest.xml 中声明
3. 创建 Widget 的 AppWidgetProviderInfo XML
4. 在 Notes.java 中添加新的 TYPE_WIDGET_NX 常量
5. 实现 onUpdate(), onReceive() 等方法
```

### 3. 添加新的存储后端

```
步骤：
1. 创建新的 DatabaseHelper（如 FirebaseHelper）
2. 修改 NotesProvider 使用新的 DatabaseHelper
3. 保持 ContentProvider 接口不变
4. UI 层代码无需修改
```

---

## 八、性能优化

### 1. 数据库查询优化

```
✓ 只查询必要的列（使用 projection）
✓ 使用 where 子句过滤数据
✓ 使用 ORDER BY 排序
✓ 创建索引提高查询速度

例：
String[] projection = {
    NoteColumns.ID,
    NoteColumns.SNIPPET,
    NoteColumns.MODIFIED_DATE
};

Cursor c = resolver.query(
    uri,
    projection,          // 只查询必要的列
    "parent_id = ?",     // where 子句
    new String[]{folderId},
    "modified_date DESC"  // 排序
);
```

### 2. 异步操作

```
✓ 使用 AsyncQueryHandler 进行后台查询
✓ 避免在 UI 线程进行数据库操作
✓ 使用 Handler 切换回 UI 线程更新 UI
```

### 3. 批量操作

```
✓ 使用 ContentProviderOperation.applyBatch()
✓ 一次性执行多个操作
✓ 减少 IPC 调用次数
```

### 4. 缓存

```
✓ WorkingNote 在内存中缓存笔记数据
✓ 减少重复查询
✓ 只在必要时同步到数据库
```

---

## 九、总体架构总结

```
┌──────────────────────────────────────────────────────────┐
│  Notesmaster 应用架构特点                                │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  1. 分层清晰                                             │
│     ├─ UI 层（Activity, Adapter, Dialog）               │
│     ├─ Model 层（WorkingNote, Note）                    │
│     ├─ Data 层（ContentProvider, DatabaseHelper）      │
│     └─ Database 层（SQLite）                            │
│                                                           │
│  2. 设计模式完整                                         │
│     ├─ MVC 架构                                          │
│     ├─ ContentProvider 模式                             │
│     ├─ 观察者模式（Listener）                            │
│     ├─ 异步处理模式                                      │
│     └─ 工厂模式                                          │
│                                                           │
│  3. 数据一致性保障                                       │
│     ├─ 事务支持                                          │
│     ├─ 批量操作原子性                                    │
│     └─ LOCAL_MODIFIED 标记                              │
│                                                           │
│  4. 扩展性好                                             │
│     ├─ ContentProvider 接口稳定                         │
│     ├─ 支持多进程访问                                    │
│     └─ 易于添加新功能                                    │
│                                                           │
│  5. 性能优化                                             │
│     ├─ 异步查询                                          │
│     ├─ 批量操作                                          │
│     ├─ 内存缓存                                          │
│     └─ 索引优化                                          │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

**这个架构设计是 Android 应用开发的参考标准，值得深入学习。**

