/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * NotesProvider 类是笔记应用的ContentProvider实现。
 * 该类继承自Android的ContentProvider，负责管理笔记和数据的数据库操作，
 * 包括查询、插入、更新、删除等CRUD操作。
 * 它提供了统一的接口供应用的其他组件访问笔记数据，
 * 并支持搜索功能和数据变更通知。
 * 
 * 主要功能：
 * - 提供笔记和附件数据的CRUD操作
 * - 支持搜索笔记内容
 * - 管理数据版本控制
 * - 处理URI匹配和路由
 * - 实现数据变更通知机制
 */
public class NotesProvider extends ContentProvider {
    /**
     * URI匹配器，用于匹配不同的URI模式并路由到相应的处理逻辑
     * (UriMatcher for matching different URI patterns and routing to appropriate handlers)
     */
    private static final UriMatcher mMatcher;

    /**
     * 数据库助手实例，用于获取数据库连接和管理数据库操作
     * (Database helper instance for getting database connections and managing operations)
     */
    private NotesDatabaseHelper mHelper;

    /**
     * 日志标签，用于调试和错误日志记录
     * (Log tag for debugging and error logging)
     */
    private static final String TAG = "NotesProvider";

    /**
     * URI类型常量：笔记列表URI
     * (URI type constant: notes list URI)
     */
    private static final int URI_NOTE            = 1;

    /**
     * URI类型常量：单个笔记项URI
     * (URI type constant: single note item URI)
     */
    private static final int URI_NOTE_ITEM       = 2;

    /**
     * URI类型常量：数据列表URI
     * (URI type constant: data list URI)
     */
    private static final int URI_DATA            = 3;

    /**
     * URI类型常量：单个数据项URI
     * (URI type constant: single data item URI)
     */
    private static final int URI_DATA_ITEM       = 4;

    /**
     * URI类型常量：搜索URI
     * (URI type constant: search URI)
     */
    private static final int URI_SEARCH          = 5;

    /**
     * URI类型常量：搜索建议URI
     * (URI type constant: search suggestion URI)
     */
    private static final int URI_SEARCH_SUGGEST  = 6;

    /**
     * 静态初始化块，配置URI匹配器，为不同的URI模式添加匹配规则。
     * 该块在类加载时执行，设置了所有支持的URI路径和对应的匹配码。
     * 
     * 支持的URI模式：
     * - note：笔记列表
     * - note/#：单个笔记（#为ID）
     * - data：数据列表
     * - data/#：单个数据项
     * - search：搜索
     * - search_suggest_query：搜索建议
     */
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索投影字符串，用于定义搜索结果中包含的列。
     * 该投影将笔记的ID、内容等信息映射为Android搜索框架所需的格式，
     * 包括建议文本、图标、Intent动作等。
     * 
     * 投影内容：
     * - 笔记ID
     * - 搜索建议的额外数据（笔记ID）
     * - 主要显示文本（处理换行符后的摘要）
     * - 次要显示文本（同上）
     * - 图标资源
     * - Intent动作
     * - Intent数据类型
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 笔记摘要搜索查询SQL语句。
     * 该查询用于在笔记摘要中搜索匹配的文本，
     * 排除回收站中的笔记，只搜索普通笔记类型。
     * 
     * 查询条件：
     * - 摘要内容LIKE匹配搜索字符串
     * - 父ID不等于回收站文件夹ID
     * - 类型等于普通笔记
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * ContentProvider的创建方法，在应用启动时调用。
     * 该方法初始化数据库助手实例，为后续的数据库操作做准备。
     * 
     * @return 总是返回true，表示ContentProvider创建成功
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据的方法，根据不同的URI执行相应的数据库查询操作。
     * 该方法支持笔记列表、单个笔记、数据列表、单个数据项以及搜索查询。
     * 
     * @param uri 要查询的URI，决定查询的类型和范围
     * @param projection 要返回的列数组，null表示返回所有列
     * @param selection 查询条件WHERE子句
     * @param selectionArgs 查询条件参数，用于替换selection中的?占位符
     * @param sortOrder 排序顺序
     * @return 查询结果的Cursor对象，包含查询到的数据
     * @throws IllegalArgumentException 当URI不匹配或参数无效时抛出
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询所有笔记
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 查询单个笔记，通过URI中的ID
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询所有数据项
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_DATA_ITEM:
                // 查询单个数据项，通过URI中的ID
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索查询，不支持自定义排序和投影
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 从URI路径中获取搜索字符串
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 从查询参数中获取搜索字符串
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 格式化搜索字符串为LIKE模式
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 为Cursor设置通知URI，当数据变化时会自动更新
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据的方法，根据URI将新数据插入到相应的数据库表中。
     * 支持插入笔记和数据项，并在插入成功后发送数据变更通知。
     * 
     * @param uri 要插入数据的URI，决定插入的目标表
     * @param values 要插入的数据，以ContentValues形式提供
     * @return 新插入数据的URI，包含生成的ID
     * @throws IllegalArgumentException 当URI不匹配时抛出
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入新笔记
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 插入新数据项，检查是否包含笔记ID
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 通知笔记URI的数据变更
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // 通知数据URI的数据变更
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据的方法，根据URI从数据库中删除相应的数据。
     * 支持删除笔记和数据项，系统文件夹（ID<=0）不允许删除。
     * 删除成功后发送数据变更通知。
     * 
     * @param uri 要删除数据的URI，决定删除的目标和范围
     * @param selection 删除条件WHERE子句
     * @param selectionArgs 删除条件参数，用于替换selection中的?占位符
     * @return 删除的行数
     * @throws IllegalArgumentException 当URI不匹配时抛出
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除笔记，排除系统笔记（ID>0）
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 删除单个笔记，系统文件夹不允许删除
                id = uri.getPathSegments().get(1);
                /**
                 * ID that smaller than 0 is system folder which is not allowed to
                 * trash
                 */
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 删除数据项
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                // 删除单个数据项
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 发送数据变更通知
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据的方法，根据URI更新数据库中的相应数据。
     * 支持更新笔记和数据项，笔记更新时会自动增加版本号。
     * 更新成功后发送数据变更通知。
     * 
     * @param uri 要更新数据的URI，决定更新的目标和范围
     * @param values 要更新的数据，以ContentValues形式提供
     * @param selection 更新条件WHERE子句
     * @param selectionArgs 更新条件参数，用于替换selection中的?占位符
     * @return 更新的行数
     * @throws IllegalArgumentException 当URI不匹配时抛出
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新笔记前增加版本号
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 更新单个笔记前增加版本号
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 更新数据项
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                // 更新单个数据项
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 发送数据变更通知
        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 解析选择条件字符串的私有方法。
     * 如果选择条件不为空，则将其包装在AND子句中返回，
     * 用于构建完整的WHERE条件语句。
     * 
     * @param selection 原始的选择条件字符串
     * @return 解析后的选择条件，如果为空则返回空字符串，否则返回" AND (selection)"
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记版本号的私有方法。
     * 当笔记被更新时，自动增加其版本号以支持数据同步和版本控制。
     * 支持通过ID指定单个笔记或通过条件指定多个笔记。
     * 
     * @param id 要增加版本的笔记ID，如果为-1则表示通过selection条件更新
     * @param selection 选择条件，用于指定要更新的笔记范围
     * @param selectionArgs 选择条件参数，用于替换selection中的?占位符
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        // 创建StringBuilder用于构建SQL语句，初始容量120字符
        StringBuilder sql = new StringBuilder(120);
        
        // 构建UPDATE语句的基本部分：UPDATE TABLE.NOTE SET VERSION = VERSION + 1
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        // 如果有ID条件或选择条件，则添加WHERE子句
        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        
        // 如果指定了ID，则添加ID条件
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        
        // 如果有选择条件，则处理并添加
        if (!TextUtils.isEmpty(selection)) {
            // 根据是否有ID决定是否使用parseSelection包装条件
            String selectString = id > 0 ? parseSelection(selection) : selection;
            
            // 替换选择条件中的占位符?为实际参数值
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            
            // 将处理后的选择条件添加到SQL语句中
            sql.append(selectString);
        }

        // 执行构建的SQL语句来增加笔记版本号
        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 获取URI对应的MIME类型的抽象方法。
     * 该方法用于返回指定URI所代表的数据类型，
     * 但在当前实现中未实现具体的类型返回逻辑。
     * 
     * @param uri 要查询类型的URI
     * @return 总是返回null，表示未实现
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

}
