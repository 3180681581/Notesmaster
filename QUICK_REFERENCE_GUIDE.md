# Notesmaster 快速参考指南

## 项目基本信息

| 项目 | 值 |
|------|-----|
| **应用名称** | Notesmaster (小米便签) |
| **Package** | net.micode.notes |
| **Target SDK** | 36 (Android 15) |
| **Min SDK** | 30 (Android 11) |
| **Java Version** | 11 |
| **Build System** | Gradle |
| **Architecture** | 三层 + Widget + GTask |

---

## 快速导航

### 核心类快查表

| 功能 | 主要类 | 位置 | 行数 |
|------|------|------|------|
| **笔记列表** | NotesListActivity | ui/ | 1028 |
| **笔记编辑** | NoteEditActivity | ui/ | 909 |
| **笔记业务逻辑** | WorkingNote | model/ | 369 |
| **数据操作** | Note | model/ | 254 |
| **数据访问** | NotesProvider | data/ | 306 |
| **数据定义** | Notes | data/ | 280 |
| **数据库** | NotesDatabaseHelper | data/ | - |
| **Widget** | NoteWidgetProvider_2x/4x | widget/ | - |

---

## 数据库表速查

### note 表（笔记表）

```sql
CREATE TABLE note (
    _id INTEGER PRIMARY KEY,
    parent_id INTEGER,          -- 父文件夹ID（-3=回收站，-2=通话记录，-1=临时，0=根）
    created_date LONG,
    modified_date LONG,
    alert_date LONG,            -- 提醒时间
    snippet TEXT,               -- 笔记标题/摘要
    type INTEGER,               -- 0=笔记, 1=文件夹, 2=系统
    widget_id INTEGER,
    widget_type INTEGER,        -- 0=2x, 1=4x
    bg_color_id INTEGER,
    local_modified BOOLEAN
);
```

### data 表（内容表）

```sql
CREATE TABLE data (
    _id INTEGER PRIMARY KEY,
    mime_type TEXT,             -- MIME类型
    note_id INTEGER FOREIGN KEY,
    content TEXT,               -- 笔记内容
    data1 TEXT,
    data2 TEXT,
    data3 TEXT,
    data4 TEXT
);
```

---

## 关键常量

### 文件夹 ID

```java
Notes.ID_ROOT_FOLDER = 0              // 默认文件夹
Notes.ID_TEMPARAY_FOLDER = -1        // 临时文件夹
Notes.ID_CALL_RECORD_FOLDER = -2     // 通话记录
Notes.ID_TRASH_FOLER = -3            // 回收站
```

### 笔记类型

```java
Notes.TYPE_NOTE = 0       // 普通笔记
Notes.TYPE_FOLDER = 1     // 文件夹
Notes.TYPE_SYSTEM = 2     // 系统文件夹
```

### Widget 类型

```java
Notes.TYPE_WIDGET_INVALIDE = -1  // 无效
Notes.TYPE_WIDGET_2X = 0         // 2x2
Notes.TYPE_WIDGET_4X = 1         // 4x4
```

### MIME 类型

```java
TextNote.CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note"
CallNote.CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note"
```

---

## URI 路由表

| URI | 匹配类型 | 对应操作 |
|-----|---------|---------|
| `content://micode_notes/note` | URI_NOTE | 查询所有笔记 |
| `content://micode_notes/note/#` | URI_NOTE_ITEM | 查询单个笔记 |
| `content://micode_notes/data` | URI_DATA | 查询所有数据 |
| `content://micode_notes/data/#` | URI_DATA_ITEM | 查询单个数据 |
| `content://micode_notes/search?query=xxx` | URI_SEARCH | 搜索笔记 |

---

## 常用 API 调用

### 1. 新建笔记

```java
// 在 NoteEditActivity 中
Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, folderId);
intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);
startActivity(intent);
```

### 2. 编辑笔记

```java
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.putExtra(Intent.EXTRA_UID, noteId);
startActivity(intent);
```

### 3. 查询笔记列表

```java
Cursor cursor = contentResolver.query(
    Notes.CONTENT_NOTE_URI,
    new String[]{
        NoteColumns.ID,
        NoteColumns.SNIPPET,
        NoteColumns.MODIFIED_DATE
    },
    "parent_id = ?",
    new String[]{String.valueOf(folderId)},
    "modified_date DESC"
);
```

### 4. 新建笔记（直接使用 WorkingNote）

```java
WorkingNote note = WorkingNote.createEmptyNote(
    context,
    folderId,
    AppWidgetManager.INVALID_APPWIDGET_ID,
    Notes.TYPE_WIDGET_INVALIDE,
    ResourceParser.getDefaultBgId(context)
);
note.setWorkingText("笔记内容");
note.saveNote();
```

### 5. 加载已有笔记

```java
WorkingNote note = WorkingNote.load(context, noteId);
String content = note.getContent();
```

### 6. 保存笔记

```java
note.setWorkingText(editText.getText().toString());
note.saveNote();  // 异步保存
```

### 7. 删除笔记（移到回收站）

```java
ContentValues values = new ContentValues();
values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
contentResolver.update(
    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
    values,
    null, null
);
```

### 8. 搜索笔记

```java
Cursor cursor = contentResolver.query(
    Uri.parse("content://micode_notes/search?query=" + keyword),
    SEARCH_PROJECTION,
    null, null, null
);
```

### 9. 设置提醒

```java
note.setAlertDate(alertTimeMillis, true);
note.saveNote();

// 注册告警
AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
PendingIntent pendingIntent = PendingIntent.getBroadcast(
    context, noteId, new Intent(context, AlarmReceiver.class),
    PendingIntent.FLAG_UPDATE_CURRENT
);
alarmManager.set(AlarmManager.RTC_WAKEUP, alertTimeMillis, pendingIntent);
```

### 10. 更新 Widget

```java
AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
RemoteViews remoteViews = new RemoteViews(
    context.getPackageName(),
    R.layout.widget_2x
);
remoteViews.setTextViewText(R.id.note_content, content);
appWidgetManager.updateAppWidget(widgetId, remoteViews);
```

---

## 文件夹操作速查

### 创建文件夹

```java
ContentValues values = new ContentValues();
values.put(NoteColumns.SNIPPET, folderName);
values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
values.put(NoteColumns.PARENT_ID, parentFolderId);
values.put(NoteColumns.CREATED_DATE, System.currentTimeMillis());
values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());

Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);
long folderId = Long.parseLong(uri.getPathSegments().get(1));
```

### 重命名文件夹

```java
ContentValues values = new ContentValues();
values.put(NoteColumns.SNIPPET, newName);

contentResolver.update(
    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId),
    values, null, null
);
```

### 删除文件夹

```java
// 1. 先删除文件夹内的所有笔记
contentResolver.delete(
    Notes.CONTENT_NOTE_URI,
    "parent_id = ?",
    new String[]{String.valueOf(folderId)}
);

// 2. 再删除文件夹本身
contentResolver.delete(
    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId),
    null, null
);
```

---

## 查询参数速查

### 常用 Projection（查询字段）

```java
// 笔记列表需要的字段
String[] NOTES_LIST_PROJECTION = {
    NoteColumns.ID,
    NoteColumns.SNIPPET,
    NoteColumns.MODIFIED_DATE,
    NoteColumns.BG_COLOR_ID,
    NoteColumns.PARENT_ID
};

// 笔记编辑需要的字段
String[] NOTES_EDIT_PROJECTION = {
    NoteColumns.ID,
    NoteColumns.SNIPPET,
    NoteColumns.CREATED_DATE,
    NoteColumns.MODIFIED_DATE,
    NoteColumns.ALERTED_DATE,
    NoteColumns.BG_COLOR_ID,
    NoteColumns.WIDGET_ID,
    NoteColumns.WIDGET_TYPE
};

// 笔记内容数据字段
String[] DATA_PROJECTION = {
    DataColumns.ID,
    DataColumns.CONTENT,
    DataColumns.MIME_TYPE,
    DataColumns.DATA1,
    DataColumns.DATA2,
    DataColumns.DATA3,
    DataColumns.DATA4
};
```

### 常用 Selection（查询条件）

```java
// 按文件夹查询
"parent_id = ?"

// 按类型查询
"type = ?"

// 查询非回收站
"parent_id <> " + Notes.ID_TRASH_FOLER

// 搜索
"snippet LIKE ?"

// 有提醒的笔记
"alert_date > 0"

// 组合查询
"parent_id = ? AND type = ? AND local_modified = 1"
```

### 常用排序

```java
// 按修改时间倒序
"modified_date DESC"

// 按创建时间正序
"created_date ASC"

// 多字段排序
"type ASC, modified_date DESC"
```

---

## 权限清单

```xml
<!-- AndroidManifest.xml 中的权限 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.GET_ACCOUNTS" />
<uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
<uses-permission android:name="android.permission.AUTHENTICATE_ACCOUNTS" />
<uses-permission android:name="android.permission.USE_CREDENTIALS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />
```

---

## 关键 Intent Action

```java
// 新建笔记
Intent.ACTION_INSERT_OR_EDIT

// 查看笔记
Intent.ACTION_VIEW

// 搜索
Intent.ACTION_SEARCH

// 启动时初始化告警
Intent.ACTION_BOOT_COMPLETED

// 通话记录创建笔记
Intent.ACTION_INSERT_OR_EDIT (with EXTRA_PHONE_NUMBER)
```

---

## 关键 Intent Extra

```java
// 笔记 ID
Intent.EXTRA_UID

// 笔记背景颜色
Notes.INTENT_EXTRA_BACKGROUND_ID

// 文件夹 ID
Notes.INTENT_EXTRA_FOLDER_ID

// Widget ID
Notes.INTENT_EXTRA_WIDGET_ID

// Widget 类型
Notes.INTENT_EXTRA_WIDGET_TYPE

// 电话号码（通话记录）
Intent.EXTRA_PHONE_NUMBER

// 通话时间
Notes.INTENT_EXTRA_CALL_DATE

// 搜索查询
SearchManager.EXTRA_DATA_KEY
SearchManager.USER_QUERY
```

---

## 常见操作流程

### 打开笔记编辑界面

```
用户点击笔记
  ↓
NotesListActivity.onItemClick()
  ↓
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.putExtra(Intent.EXTRA_UID, noteId);
startActivity(intent);
  ↓
NoteEditActivity.onCreate()
  ↓
initActivityState(intent)
  ↓
WorkingNote.load(noteId)
  ↓
显示笔记内容
```

### 保存笔记

```
用户编辑完成后退出
  ↓
NoteEditActivity.onStop()
  ↓
WorkingNote.saveNote()
  ↓
检查 isWorthSaving()
  ↓
若是新笔记：Note.getNewNoteId()
  ↓
Note.syncNote()
  ↓
更新 note 表 + data 表
  ↓
更新 Widget（如有）
  ↓
保存完成
```

### 删除笔记

```
用户长按笔记 → 点击删除
  ↓
NotesListActivity.onContextItemSelected()
  ↓
ContentResolver.update()
  ↓
设置 parent_id = ID_TRASH_FOLER
  ↓
从列表中隐藏（搜索时排除）
  ↓
可从回收站恢复
```

---

## 调试技巧

### 查看数据库

```bash
# 进入 adb shell
adb shell

# 进入数据库目录
cd /data/data/net.micode.notes/databases

# 使用 sqlite3 打开数据库
sqlite3 notes.db

# 查看表结构
.tables
.schema note
.schema data

# 查询数据
SELECT * FROM note;
SELECT * FROM data;
```

### 打印日志

```java
// 关键位置添加日志
Log.d("NotesTag", "笔记ID: " + noteId);
Log.e("NotesTag", "错误信息: " + error);
Log.v("NotesTag", "详细信息: " + details);
```

### ContentProvider 查询调试

```java
// 在 NotesProvider 中添加日志
@Override
public Cursor query(Uri uri, String[] projection, String selection, 
        String[] selectionArgs, String sortOrder) {
    Log.d("NotesProvider", "query() - URI: " + uri);
    Log.d("NotesProvider", "query() - selection: " + selection);
    // ...
}
```

---

## 性能优化建议

| 问题 | 解决方案 |
|------|---------|
| UI 卡顿 | 使用 AsyncQueryHandler 异步查询 |
| 内存占用高 | 及时关闭 Cursor，避免内存泄漏 |
| 数据库查询慢 | 使用 projection 只查必要字段，使用 where 过滤 |
| 频繁保存 | 使用防抖（debounce）或节流（throttle） |
| Widget 更新慢 | 使用批量操作减少 IPC 次数 |

---

## 常见错误

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| `ANR` | 在 UI 线程做数据库操作 | 使用异步查询 |
| `Cursor 泄漏` | 没有关闭 Cursor | 使用 try-finally 确保关闭 |
| `NullPointerException` | Cursor 为空或数据为空 | 检查 moveToFirst() 返回值 |
| `IllegalStateException` | ContentProvider 操作出错 | 检查 URI 和权限 |
| `Widget 不更新` | 没有调用 onWidgetChanged | 确保注册 Listener |

---

## 相关文档

- 📄 **PROJECT_ARCHITECTURE_ANALYSIS.md** - 完整架构分析
- 📄 **CALL_CHAIN_ANALYSIS.md** - 调用链详解
- 📄 **MODULE_DEPENDENCY_AND_PATTERNS.md** - 模块依赖与设计模式
- 📄 **FILE_STRUCTURE_ANALYSIS.md** - 原始文件分类

---

## 快速命令

### 编译项目

```bash
./gradlew build
```

### 运行测试

```bash
./gradlew test
```

### 生成 APK

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

### 安装应用

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 运行应用

```bash
adb shell am start -n net.micode.notes/.ui.NotesListActivity
```

### 查看日志

```bash
adb logcat | grep -i notes
```

---

**最后更新**: 2024年4月12日  
**适用版本**: 1.0+  
**维护者**: AI Code Assistant

