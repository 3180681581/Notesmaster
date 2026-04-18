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

package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * SqlNote 类是笔记数据的数据库操作封装类。
 * 该类提供了对笔记（包括普通笔记和文件夹）的增删改查操作，
 * 支持JSON格式的数据转换、版本控制和Google Tasks同步。
 *
 * 主要功能：
 * - 从数据库加载笔记信息和关联的数据项
 * - 将笔记转换为JSON格式用于云端同步
 * - 从JSON数据设置笔记属性
 * - 提交笔记到数据库（插入或更新）
 * - 支持版本验证的并发控制
 * - 管理笔记的附件数据列表
 *
 * 支持的笔记类型：
 * - 普通笔记（TYPE_NOTE）：包含内容和附件数据
 * - 文件夹（TYPE_FOLDER）：用于组织笔记的层级结构
 * - 系统文件夹（TYPE_SYSTEM）：特殊的系统预定义文件夹
 */
public class SqlNote {
    /**
     * 日志标签，用于调试和错误日志记录。
     * 使用类名作为标签，便于在日志中识别来源。
     */
    private static final String TAG = SqlNote.class.getSimpleName();

    /**
     * 无效ID常量，用于表示未设置或无效的笔记ID。
     */
    private static final int INVALID_ID = -99999;

    /**
     * 笔记查询投影数组，定义了从数据库查询时需要返回的列。
     * 包含笔记的所有基本属性字段，用于完整的笔记信息查询。
     */
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID, NoteColumns.ALERTED_DATE, NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT, NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT, NoteColumns.PARENT_ID, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    /**
     * 投影数组中ID列的索引位置。
     */
    public static final int ID_COLUMN = 0;

    /**
     * 投影数组中提醒日期列的索引位置。
     */
    public static final int ALERTED_DATE_COLUMN = 1;

    /**
     * 投影数组中背景颜色ID列的索引位置。
     */
    public static final int BG_COLOR_ID_COLUMN = 2;

    /**
     * 投影数组中创建日期列的索引位置。
     */
    public static final int CREATED_DATE_COLUMN = 3;

    /**
     * 投影数组中是否有附件列的索引位置。
     */
    public static final int HAS_ATTACHMENT_COLUMN = 4;

    /**
     * 投影数组中修改日期列的索引位置。
     */
    public static final int MODIFIED_DATE_COLUMN = 5;

    /**
     * 投影数组中子笔记数量列的索引位置。
     */
    public static final int NOTES_COUNT_COLUMN = 6;

    /**
     * 投影数组中父文件夹ID列的索引位置。
     */
    public static final int PARENT_ID_COLUMN = 7;

    /**
     * 投影数组中摘要内容列的索引位置。
     */
    public static final int SNIPPET_COLUMN = 8;

    /**
     * 投影数组中类型列的索引位置。
     */
    public static final int TYPE_COLUMN = 9;

    /**
     * 投影数组中小部件ID列的索引位置。
     */
    public static final int WIDGET_ID_COLUMN = 10;

    /**
     * 投影数组中小部件类型列的索引位置。
     */
    public static final int WIDGET_TYPE_COLUMN = 11;

    /**
     * 投影数组中同步ID列的索引位置。
     */
    public static final int SYNC_ID_COLUMN = 12;

    /**
     * 投影数组中本地修改标记列的索引位置。
     */
    public static final int LOCAL_MODIFIED_COLUMN = 13;

    /**
     * 投影数组中原父文件夹ID列的索引位置。
     */
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;

    /**
     * 投影数组中Google Task ID列的索引位置。
     */
    public static final int GTASK_ID_COLUMN = 15;

    /**
     * 投影数组中版本号列的索引位置。
     */
    public static final int VERSION_COLUMN = 16;

    /**
     * Android上下文，用于获取系统服务和资源。
     */
    private Context mContext;

    /**
     * ContentResolver实例，用于执行数据库操作。
     */
    private ContentResolver mContentResolver;

    /**
     * 创建标记，表示该笔记是否为新建状态。
     * true表示新建，false表示从数据库加载。
     */
    private boolean mIsCreate;

    /**
     * 笔记的ID，如果为INVALID_ID表示未保存到数据库。
     */
    private long mId;

    /**
     * 提醒日期时间戳。
     */
    private long mAlertDate;

    /**
     * 背景颜色ID，对应预定义的颜色资源。
     */
    private int mBgColorId;

    /**
     * 创建日期时间戳。
     */
    private long mCreatedDate;

    /**
     * 是否有附件标记，0表示无附件，1表示有附件。
     */
    private int mHasAttachment;

    /**
     * 最后修改日期时间戳。
     */
    private long mModifiedDate;

    /**
     * 父文件夹ID，用于构建笔记的层级结构。
     */
    private long mParentId;

    /**
     * 笔记摘要内容，通常是笔记的前几行文本。
     */
    private String mSnippet;

    /**
     * 笔记类型：普通笔记、文件夹或系统文件夹。
     */
    private int mType;

    /**
     * 小部件ID，如果笔记被添加到桌面小部件。
     */
    private int mWidgetId;

    /**
     * 小部件类型，定义小部件的显示样式。
     */
    private int mWidgetType;

    /**
     * 原始父文件夹ID，用于同步时的冲突解决。
     */
    private long mOriginParent;

    /**
     * 版本号，用于并发控制和版本验证。
     */
    private long mVersion;

    /**
     * 笔记差异数据值，用于存储需要更新的字段。
     * 只包含相对于当前值的变更，提高更新效率。
     */
    private ContentValues mDiffNoteValues;

    /**
     * 关联的数据项列表，存储笔记的附件数据（如图片、录音等）。
     */
    private ArrayList<SqlData> mDataList;

    /**
     * 构造函数，创建一个新的笔记对象。
     * 初始化所有字段为默认值，并标记为创建状态。
     * 
     * @param context Android上下文，用于获取系统服务和默认资源
     */
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mId = INVALID_ID;
        mAlertDate = 0;
        mBgColorId = ResourceParser.getDefaultBgId(context);
        mCreatedDate = System.currentTimeMillis();
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();
        mParentId = 0;
        mSnippet = "";
        mType = Notes.TYPE_NOTE;
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();
        mDataList = new ArrayList<SqlData>();
    }

    /**
     * 构造函数，从数据库游标加载笔记信息。
     * 从游标中读取笔记的所有字段，并加载关联的数据项。
     * 
     * @param context Android上下文，用于获取系统服务
     * @param c 包含笔记信息的数据库游标
     */
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    /**
     * 构造函数，通过笔记ID从数据库加载笔记信息。
     * 根据ID查询数据库并加载完整的笔记信息和数据项。
     * 
     * @param context Android上下文，用于获取系统服务
     * @param id 笔记的数据库ID
     */
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();

    }

    /**
     * 通过笔记ID从数据库加载笔记信息。
     * 执行数据库查询并将结果加载到对象字段中。
     * 
     * @param id 要加载的笔记ID
     */
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(id)
                    }, null);
            if (c != null) {
                c.moveToNext();
                loadFromCursor(c);
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * 从数据库游标加载笔记字段值。
     * 根据预定义的列索引从游标中读取各个字段的值。
     * 
     * @param c 包含笔记信息的数据库游标
     */
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
    }

    /**
     * 加载笔记关联的数据项内容。
     * 查询数据库中属于该笔记的所有数据项，并创建SqlData对象列表。
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();
        try {
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] {
                        String.valueOf(mId)
                    }, null);
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");
                    return;
                }
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * 从JSON对象设置笔记内容。
     * 解析JSON对象中的笔记信息，根据笔记类型处理不同的字段。
     * 支持文件夹和普通笔记的差异化处理。
     * 
     * @param js 包含笔记信息的JSONObject对象
     * @return 设置是否成功，JSON解析失败时返回false
     */
    public boolean setContent(JSONObject js) {
        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                Log.w(TAG, "cannot set system folder");
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // for folder we can only update the snnipet and type
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }

                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 将笔记内容转换为JSON对象。
     * 根据笔记类型生成相应的JSON结构，用于云端同步。
     * 普通笔记包含完整信息和数据项，文件夹只包含基本信息。
     * 
     * @return 包含笔记信息的JSONObject对象，创建状态时返回null
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            JSONObject note = new JSONObject();
            if (mType == Notes.TYPE_NOTE) {
                // 普通笔记的完整信息
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                // 添加数据项数组
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // 文件夹的基本信息
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 设置笔记的父文件夹ID。
     * 用于移动笔记到不同的文件夹中。
     * 
     * @param id 新的父文件夹ID
     */
    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    /**
     * 设置Google Task ID，用于与云端任务关联。
     * 
     * @param gid Google Task的唯一标识符
     */
    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    /**
     * 设置同步ID，用于同步过程中的标识。
     * 
     * @param syncId 同步ID
     */
    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    /**
     * 重置本地修改标记。
     * 用于表示笔记已经同步，不再有本地修改。
     */
    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    /**
     * 获取笔记的ID。
     * 
     * @return 笔记的ID，未保存时返回INVALID_ID
     */
    public long getId() {
        return mId;
    }

    /**
     * 获取笔记的父文件夹ID。
     * 
     * @return 父文件夹ID
     */
    public long getParentId() {
        return mParentId;
    }

    /**
     * 获取笔记的摘要内容。
     * 
     * @return 笔记摘要字符串
     */
    public String getSnippet() {
        return mSnippet;
    }

    /**
     * 判断笔记是否为普通笔记类型。
     * 
     * @return true如果是普通笔记，false如果是文件夹或系统文件夹
     */
    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    /**
     * 提交笔记到数据库。
     * 根据创建状态执行插入或更新操作，
     * 支持版本验证的并发控制。
     * 
     * @param validateVersion 是否启用版本验证
     * @throws ActionFailureException 操作失败时抛出异常
     */
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else {
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }
            if (mDiffNoteValues.size() > 0) {
                mVersion ++;
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                        String.valueOf(mId)
                    });
                } else {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] {
                                    String.valueOf(mId), String.valueOf(mVersion)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 刷新本地信息
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}
