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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SqlData 类是笔记数据项的数据库操作封装类。
 * 该类提供了对笔记附件数据（图片、录音等）的增删改查操作，
 * 支持JSON格式的数据转换和版本控制的更新操作。
 * 
 * 主要功能：
 * - 从数据库游标加载数据项信息
 * - 将数据项转换为JSON格式
 * - 从JSON数据设置数据项属性
 * - 提交数据项到数据库（插入或更新）
 * - 支持版本验证的更新操作
 * 
 * 数据项特点：
 * - 属于特定的笔记（通过noteId关联）
 * - 支持多种MIME类型的数据
 * - 包含通用数据字段（data1-data5）
 * - 支持增量更新和完整更新
 */
public class SqlData {

    /**
     * 日志标签，用于调试和错误日志记录。
     * 使用类名作为标签，便于在日志中识别来源。
     */
    private static final String TAG = SqlData.class.getSimpleName();

    /**
     * 无效ID常量，用于表示未设置或无效的数据ID。
     */
    private static final int INVALID_ID = -99999;

    /**
     * 数据查询投影数组，定义了从数据库查询时需要返回的列。
     * 包含ID、MIME类型、内容、data1和data3字段。
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    /**
     * 投影数组中ID列的索引位置。
     */
    public static final int DATA_ID_COLUMN = 0;

    /**
     * 投影数组中MIME类型列的索引位置。
     */
    public static final int DATA_MIME_TYPE_COLUMN = 1;

    /**
     * 投影数组中内容列的索引位置。
     */
    public static final int DATA_CONTENT_COLUMN = 2;

    /**
     * 投影数组中data1列的索引位置。
     */
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;

    /**
     * 投影数组中data3列的索引位置。
     */
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    /**
     * ContentResolver实例，用于执行数据库操作。
     */
    private ContentResolver mContentResolver;

    /**
     * 创建标记，表示该数据项是否为新建状态。
     * true表示新建，false表示从数据库加载。
     */
    private boolean mIsCreate;

    /**
     * 数据项的ID，如果为INVALID_ID表示未保存到数据库。
     */
    private long mDataId;

    /**
     * 数据项的MIME类型，默认为NOTE类型。
     */
    private String mDataMimeType;

    /**
     * 数据项的内容字符串。
     */
    private String mDataContent;

    /**
     * 数据项的data1字段，存储长整型数据。
     */
    private long mDataContentData1;

    /**
     * 数据项的data3字段，存储字符串数据。
     */
    private String mDataContentData3;

    /**
     * 差异数据值，用于存储需要更新的字段。
     * 只包含相对于当前值的变更，提高更新效率。
     */
    private ContentValues mDiffDataValues;

    /**
     * 构造函数，创建一个新的数据项对象。
     * 初始化所有字段为默认值，并标记为创建状态。
     * 
     * @param context Android上下文，用于获取ContentResolver
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 构造函数，从数据库游标加载数据项信息。
     * 从游标中读取数据项的所有字段，并标记为非创建状态。
     * 
     * @param context Android上下文，用于获取ContentResolver
     * @param c 包含数据项信息的数据库游标
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从数据库游标加载数据项字段值。
     * 根据预定义的列索引从游标中读取各个字段的值。
     * 
     * @param c 包含数据项信息的数据库游标
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 从JSON对象设置数据项内容。
     * 解析JSON对象中的各个字段，并与当前值比较，
     * 只将发生变化的字段添加到差异更新集合中。
     * 
     * @param js 包含数据项信息的JSONObject对象
     * @throws JSONException JSON解析异常
     */
    public void setContent(JSONObject js) throws JSONException {
        // 处理ID字段
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 处理MIME类型字段
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 处理内容字段
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 处理data1字段
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 处理data3字段
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将数据项内容转换为JSON对象。
     * 如果数据项尚未保存到数据库（创建状态），返回null。
     * 否则将所有字段打包成JSONObject返回。
     * 
     * @return 包含数据项信息的JSONObject对象，创建状态时返回null
     * @throws JSONException JSON构建异常
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 提交数据项到数据库。
     * 根据创建状态执行插入或更新操作，
     * 支持版本验证的条件更新。
     * 
     * @param noteId 所属笔记的ID
     * @param validateVersion 是否启用版本验证
     * @param version 版本号，用于版本验证
     * @throws ActionFailureException 操作失败时抛出异常
     */
    public void commit(long noteId, boolean validateVersion, long version) {
        if (mIsCreate) {
            // 新建数据项的插入操作
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // 现有数据项的更新操作
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 无版本验证的普通更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 带版本验证的条件更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清理差异数据，重置状态
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取数据项的ID。
     * 
     * @return 数据项的ID，未保存时返回INVALID_ID
     */
    public long getId() {
        return mDataId;
    }
}
