# Notesmaster 核心功能调用链详解

## 功能 1: 笔记编辑与保存的完整调用链

### 时序图（编辑新笔记）

```
User                NoteEditActivity       WorkingNote          Note              NotesProvider    SQLite
 |                        |                     |                |                   |              |
 |--new note------------->|                     |                |                   |              |
 |                        |                     |                |                   |              |
 |                        |--createEmptyNote--->|                |                   |              |
 |                        |<-----WorkingNote----|                |                   |              |
 |                        |                     |                |                   |              |
 |  编辑笔记内容            |                     |                |                   |              |
 |<------setText-------->|                     |                |                   |              |
 |                        |--setWorkingText---->|                |                   |              |
 |                        |                     |--setTextData-->|                   |              |
 |                        |                     |                |                   |              |
 | 退出界面                |                     |                |                   |              |
 |                        |--onStop/onDestroy-->|                |                   |              |
 |                        |                     |--saveNote()    |                   |              |
 |                        |                     |                |                   |              |
 |                        |                     |--getNewNoteId()--------->|        |              |
 |                        |                     |                |        |insert()|----->|
 |                        |                     |                |        |<--noteId------|
 |                        |                     |<--noteId-------|        |              |
 |                        |                     |                |                   |              |
 |                        |                     |--syncNote(noteId)----->|           |              |
 |                        |                     |                |--update()-------->|----->|
 |                        |                     |                |<-----result-------|      |
 |                        |                     |                |                   |      |
 |                        |                     |            pushIntoContentResolver|      |
 |                        |                     |                |--insert()-------->|----->|
 |                        |                     |                |<--dataId----------|      |
 |                        |                     |                |                   |<-----+
 |                        |                     |                |                   |
 |                        |<-----true-----------|                |                   |
 |<--save complete--------|                     |                |                   |
```

---

## 功能 2: 笔记加载与显示的调用链

### 序列图（加载已有笔记）

```
NoteEditActivity       WorkingNote       ContentResolver    NotesProvider    NotesDatabaseHelper    SQLite
       |                    |                     |                |                  |              |
       |--load(noteId)----->|                     |                |                  |              |
       |                    |                     |                |                  |              |
       |                    |--query note-------->|                |                  |              |
       |                    |                     |--query()------>|                  |              |
       |                    |                     |                |--getReadableDB()->|              |
       |                    |                     |                |                  |--query()---->|
       |                    |                     |                |                  |<-----Cursor--|
       |                    |                     |                |                  |              |
       |                    |                     |                |<--Cursor---------|              |
       |                    |                     |<--Cursor-------|                  |              |
       |                    |<--Cursor-----------|                |                  |              |
       |                    |                     |                |                  |              |
       |                    |--query data-------->|                |                  |              |
       |                    |                     |--query()------>|                  |              |
       |                    |                     |                |--getReadableDB()->|              |
       |                    |                     |                |                  |--query()---->|
       |                    |                     |                |                  |<-----Cursor--|
       |                    |                     |                |<--Cursor---------|              |
       |                    |                     |<--Cursor-------|                  |              |
       |                    |<--Cursor-----------|                |                  |              |
       |                    |                     |                |                  |              |
       |<--WorkingNote------|                     |                |                  |              |
       |                    |                     |                |                  |              |
       |--initNoteScreen()->|                     |                |                  |              |
       |                    |                     |                |                  |              |
       |--display content-->|                     |                |                  |              |
```

---

## 功能 3: 笔记列表查询的调用链

### 类图关系

```
NotesListActivity
    |
    ├── BackgroundQueryHandler
    │   └── AsyncQueryHandler
    │       └── ContentResolver.query()
    │
    ├── NotesListAdapter
    │   ├── Cursor (from NotesProvider)
    │   └── ViewHolder
    │       └── NotesListItem
    │
    └── ListView
        └── [笔记项列表]


NotesProvider (ContentProvider)
    |
    ├── UriMatcher (routing)
    │   ├── URI_NOTE → 查所有笔记
    │   ├── URI_NOTE_ITEM → 查单个笔记
    │   └── URI_SEARCH → 搜索笔记
    │
    └── NotesDatabaseHelper
        └── SQLiteDatabase
            ├── note 表
            └── data 表
```

### 调用链流程

```
NotesListActivity.onCreate()
    ↓
mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
    Notes.CONTENT_NOTE_URI,
    NOTES_LIST_PROJECTION,
    "parent_id = ?",
    [folderId],
    "modified_date DESC"
)
    ↓
AsyncQueryHandler.startQuery()
    ↓
Handler.sendMessage(WorkerHandler)
    ↓
WorkerHandler.handleMessage()
    ↓
ContentResolver.query()
    ↓
NotesProvider.query(uri, projection, selection, selectionArgs, sortOrder)
    ↓
mMatcher.match(uri) → URI_NOTE
    ↓
SQLiteDatabase.query(TABLE.NOTE, projection, selection, selectionArgs, ...)
    ↓
NotesDatabaseHelper.getReadableDatabase()
    ↓
SQLite 数据库查询
    ↓
Cursor 返回
    ↓
AsyncQueryHandler.onQueryComplete(token, cookie, cursor)
    ↓
NotesListAdapter.changeCursor(cursor)
    ↓
ListView.notifyDataSetChanged()
    ↓
UI 更新，显示笔记列表
```

---

## 功能 4: 笔记搜索的调用链

### 搜索流程

```
SearchView 输入关键字
    ↓
SearchManager.onQueryTextChange(query)
    ↓
Intent.ACTION_SEARCH
    ↓
NotesListActivity.onNewIntent(intent)
    ↓
String query = intent.getStringExtra(SearchManager.QUERY)
    ↓
mBackgroundQueryHandler.startQuery(
    SEARCH_QUERY_TOKEN,
    null,
    Uri.parse("content://micode_notes/search?query=" + query),
    SEARCH_PROJECTION,
    null, null, null
)
    ↓
ContentResolver.query()
    ↓
NotesProvider.query()
    ↓
mMatcher.match(uri) → URI_SEARCH
    ↓
执行 SQL:
    SELECT id, snippet, ...
    FROM note
    WHERE snippet LIKE '%query%'
      AND parent_id != ID_TRASH_FOLER
      AND type = TYPE_NOTE
    ↓
Cursor 返回搜索结果
    ↓
NotesListAdapter 更新显示搜索结果
```

---

## 功能 5: Widget 更新的调用链

### Widget 创建与更新流程

```
用户在桌面添加 Widget
    ↓
AppWidgetManager.notifyAppWidgetViewsUpdated(widgetId)
    ↓
系统回调 NoteWidgetProvider.onUpdate()
    ↓
AppWidgetManager 获取实例
    ↓
根据 Widget ID 查询关联笔记
    ↓
ContentResolver.query(
    Notes.CONTENT_NOTE_URI,
    PROJECTION,
    "_id = ?",
    [widgetId],
    null
)
    ↓
NotesProvider.query()
    ↓
SQLiteDatabase.query()
    ↓
Cursor 返回笔记数据
    ↓
构建 RemoteViews
    ├── setTextViewText(R.id.note_title, title)
    ├── setTextViewText(R.id.note_content, content)
    ├── setBackgroundResource(R.id.note_bg, bgResId)
    └── setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    ↓
AppWidgetManager.updateAppWidget(widgetId, remoteViews)
    ↓
系统刷新桌面 Widget UI
```

### 笔记修改时更新 Widget

```
用户修改笔记内容
    ↓
WorkingNote.setWorkingText(content)
    ↓
WorkingNote.saveNote()
    ↓
若关联 Widget:
    if (mWidgetId != INVALID_APPWIDGET_ID)
        mNoteSettingStatusListener.onWidgetChanged()
    ↓
NoteEditActivity.onWidgetChanged()
    ↓
NoteWidgetProvider_2x.notifyDataSetChanged() / 
NoteWidgetProvider_4x.notifyDataSetChanged()
    ↓
AppWidgetManager.notifyAppWidgetViewsUpdated(widgetId)
    ↓
系统重新调用 onUpdate()
    ↓
重复上面的 Widget 更新流程
```

---

## 功能 6: 告警提醒的调用链

### 设置告警流程

```
用户点击 NoteEditActivity 中的"提醒"按钮
    ↓
DateTimePickerDialog.show()
    ↓
用户选择日期时间
    ↓
OnDateTimeSetListener.onDateTimeSet(year, month, day, hour, minute)
    ↓
NoteEditActivity.onDateTimeSet()
    ↓
WorkingNote.setAlertDate(alertDate, true)
    ↓
Note.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(alertDate))
    ↓
WorkingNote.saveNote()
    ↓
Note.syncNote()
    ↓
ContentResolver.update(
    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
    values{ALERTED_DATE: alertDate},
    null, null
)
    ↓
NotesProvider.update()
    ↓
SQLiteDatabase.update()
    ↓
note 表的 alert_date 字段更新
    ↓
注册 AlarmManager
    ↓
AlarmManager.set(
    AlarmManager.RTC_WAKEUP,
    alertTime,
    PendingIntent(AlarmReceiver)
)
```

### 告警触发流程

```
系统时间达到 alert_date
    ↓
系统触发 PendingIntent
    ↓
AlarmReceiver.onReceive()
    ↓
启动 AlarmAlertActivity
    ↓
显示告警提醒界面
    ├── 笔记标题
    ├── 笔记内容预览
    └── [打开笔记] [关闭] 按钮
    ↓
用户点击 [打开笔记]
    ↓
启动 NoteEditActivity 显示完整笔记
```

### 启动时恢复告警流程

```
设备启动完成
    ↓
系统发送 BOOT_COMPLETED 广播
    ↓
AlarmInitReceiver.onReceive()
    ↓
ContentResolver.query(
    Notes.CONTENT_NOTE_URI,
    [ID, ALERTED_DATE],
    "alerted_date > 0",
    null, null
)
    ↓
NotesProvider.query()
    ↓
获取所有有提醒的笔记
    ↓
遍历每个笔记
    ↓
if (alertDate > System.currentTimeMillis())
    AlarmManager.set() 重新注册告警
```

---

## 功能 7: 文件夹操作的调用链

### 创建文件夹

```
NotesListActivity 菜单 → 新建文件夹
    ↓
弹出输入对话框
    ↓
用户输入文件夹名称
    ↓
ContentResolver.insert(
    Notes.CONTENT_NOTE_URI,
    values{
        SNIPPET: folderName,
        TYPE: TYPE_FOLDER,
        CREATED_DATE: now,
        MODIFIED_DATE: now,
        PARENT_ID: currentFolderId
    }
)
    ↓
NotesProvider.insert()
    ↓
SQLiteDatabase.insert(TABLE.NOTE, ...)
    ↓
note 表插入新记录（type = FOLDER）
    ↓
返回 folderId
    ↓
刷新列表显示新文件夹
```

### 删除文件夹

```
长按文件夹 → 删除
    ↓
弹出确认对话框
    ↓
用户确认
    ↓
递归删除文件夹下的所有笔记
    ↓
ContentResolver.delete(
    Notes.CONTENT_NOTE_URI,
    "parent_id = ?",
    [folderId]
)
    ↓
NotesProvider.delete()
    ↓
SQLiteDatabase.delete()
    ↓
删除文件夹本身
    ↓
刷新列表
```

---

## 功能 8: 笔记删除的调用链

### 删除到回收站

```
长按笔记 → 删除
    ↓
NotesListActivity.onContextMenuSelected()
    ↓
ContentResolver.update(
    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
    values{PARENT_ID: ID_TRASH_FOLER},
    null, null
)
    ↓
NotesProvider.update()
    ↓
SQLiteDatabase.update()
    ↓
笔记的 parent_id 改为 -3（回收站）
    ↓
从笔记列表中隐藏该笔记
    ↓
可从"回收站"文件夹恢复
```

### 恢复笔记

```
进入回收站文件夹
    ↓
选择已删除笔记 → 恢复
    ↓
ContentResolver.update(
    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
    values{PARENT_ID: originalFolderId},
    null, null
)
    ↓
NotesProvider.update()
    ↓
笔记的 parent_id 恢复为原文件夹
    ↓
笔记重新出现在笔记列表中
```

---

## 关键数据流

### INSERT 流程

```
ContentResolver.insert(uri, ContentValues)
    ↓
NotesProvider.insert(uri, ContentValues)
    ↓
匹配 URI 类型
    ├─ URI_NOTE → 插入 note 表
    └─ URI_DATA → 插入 data 表
    ↓
db.insert(table, null, values)
    ↓
SQLiteDatabase.insert()
    ↓
返回行 ID（Uri.withAppendedId()）
```

### UPDATE 流程

```
ContentResolver.update(uri, ContentValues, selection, selectionArgs)
    ↓
NotesProvider.update(uri, values, selection, args)
    ↓
匹配 URI，确定表和条件
    ↓
db.update(table, values, where, whereArgs)
    ↓
SQLiteDatabase.update()
    ↓
返回影响行数
```

### QUERY 流程

```
ContentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
    ↓
NotesProvider.query(uri, projection, selection, args, order)
    ↓
db.query(table, columns, where, whereArgs, group, having, order)
    ↓
SQLiteDatabase.query()
    ↓
返回 Cursor
    ↓
调用方遍历 Cursor：
    while (cursor.moveToNext()) {
        // 读取数据
    }
    ↓
cursor.close()
```

---

## 异步操作处理

### AsyncQueryHandler 模式

```
NotesListActivity
    |
    ├── mBackgroundQueryHandler (extends AsyncQueryHandler)
    │   |
    │   ├── startQuery(token, cookie, uri, projection, ...)
    │   │   |
    │   │   └── 发送 WorkerHandler.MESSAGE_QUERY
    │   │
    │   ├── WorkerHandler (UI Thread)
    │   │   |
    │   │   └── handleMessage()
    │   │       └── 调用 ContentResolver.query()
    │   │
    │   └── onQueryComplete(token, cookie, cursor)
    │       |
    │       └── 在 UI 线程执行
    │           └── 更新 Adapter，刷新 UI
    │
    └── 避免阻塞 UI 线程
```

---

## 总结

这个项目的核心是通过 **ContentProvider** 实现的统一数据访问层，所有的 UI 层操作都通过 ContentResolver 与 ContentProvider 通信，ContentProvider 再调用 NotesDatabaseHelper 进行数据库操作。

主要特点：
1. ✅ **Separation of Concerns**: UI、业务逻辑、数据层分离
2. ✅ **Async Operations**: 异步查询避免 ANR
3. ✅ **Batch Updates**: 使用 ContentProviderOperation 批量操作
4. ✅ **Event-Driven**: 通过 Listener 模式更新 Widget 和其他组件
5. ✅ **Type Safety**: 常量定义避免硬编码


