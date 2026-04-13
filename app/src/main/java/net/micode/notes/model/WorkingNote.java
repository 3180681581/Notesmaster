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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;


/**
 * WorkingNote 类
 * 用于管理正在编辑的笔记，提供笔记的创建、加载、修改和保存功能
 * 该类处理笔记的所有数据操作，包括内容、颜色、提醒等设置
 */
public class WorkingNote {
    /** 笔记对象，包含笔记的所有数据 */
    private Note mNote;
    
    /** 笔记ID */
    private long mNoteId;
    
    /** 笔记内容文本 */
    private String mContent;
    
    /** 笔记模式（如清单模式） */
    private int mMode;

    /** 笔记的警告/提醒时间戳 */
    private long mAlertDate;

    /** 笔记最后修改时间戳 */
    private long mModifiedDate;

    /** 笔记背景颜色ID */
    private int mBgColorId;

    /** 绑定的小部件ID */
    private int mWidgetId;

    /** 小部件类型 */
    private int mWidgetType;

    /** 笔记所属文件夹ID */
    private long mFolderId;

    /** Android 应用程序上下文 */
    private Context mContext;

    /** 日志标签 */
    private static final String TAG = "WorkingNote";

    /** 标记笔记是否已删除 */
    private boolean mIsDeleted;

    /** 笔记设置变化监听器 */
    private NoteSettingChangedListener mNoteSettingStatusListener;

    /** 查询笔记数据时的列投影 */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    /** 查询笔记元信息时的列投影 */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    /** DATA_PROJECTION中ID列的索引位置 */
    private static final int DATA_ID_COLUMN = 0;

    /** DATA_PROJECTION中CONTENT列的索引位置 */
    private static final int DATA_CONTENT_COLUMN = 1;

    /** DATA_PROJECTION中MIME_TYPE列的索引位置 */
    private static final int DATA_MIME_TYPE_COLUMN = 2;

    /** DATA_PROJECTION中MODE列的索引位置 */
    private static final int DATA_MODE_COLUMN = 3;

    /** NOTE_PROJECTION中PARENT_ID列的索引位置 */
    private static final int NOTE_PARENT_ID_COLUMN = 0;

    /** NOTE_PROJECTION中ALERTED_DATE列的索引位置 */
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;

    /** NOTE_PROJECTION中BG_COLOR_ID列的索引位置 */
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;

    /** NOTE_PROJECTION中WIDGET_ID列的索引位置 */
    private static final int NOTE_WIDGET_ID_COLUMN = 3;

    /** NOTE_PROJECTION中WIDGET_TYPE列的索引位置 */
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;

    /** NOTE_PROJECTION中MODIFIED_DATE列的索引位置 */
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    /**
     * 创建新笔记的私有构造方法
     * @param context 应用程序上下文
     * @param folderId 笔记所属文件夹ID
     */
    // New note construct
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    /**
     * 加载已存在笔记的私有构造方法
     * @param context 应用程序上下文
     * @param noteId 笔记ID
     * @param folderId 笔记所属文件夹ID
     */
    // Existing note construct
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 从数据库加载笔记的元信息
     * 包括笔记的颜色、部件ID、类型、提醒时间等
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    /**
     * 从数据库加载笔记的具体内容数据
     * 处理文本内容、通话记录、图片附件等不同类型的数据
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.IMAGE_NOTE.equals(type)) {
                        // Image attachments are managed directly in NoteEditActivity.
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 创建一个新的空笔记
     * @param context 应用程序上下文
     * @param folderId 笔记所属文件夹ID
     * @param widgetId 关联的小部件ID
     * @param widgetType 小部件类型
     * @param defaultBgColorId 默认背景颜色ID
     * @return 新创建的WorkingNote对象
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 从数据库加载一个已存在的笔记
     * @param context 应用程序上下文
     * @param id 笔记ID
     * @return 加载的WorkingNote对象
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 同步保存笔记到数据库
     * 如果笔记值得保存（有内容变化），则进行以下操作：
     * 1. 如果笔记不存在于数据库，创建新笔记并分配ID
     * 2. 同步笔记数据到数据库
     * 3. 如果有关联的小部件，通知监听器更新小部件
     * @return 保存是否成功
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * 更新与此笔记相关的小部件内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 确保笔记在数据库中存在
     * 附件可能先于正文保存，需先确保 note 主记录存在。
     * @return 笔记是否成功存在或创建于数据库中
     */
    /**
     * 确保笔记在数据库中存在
     * 附件可能先于正文保存，需先确保 note 主记录存在。
     * @return 笔记是否成功存在或创建于数据库中
     */
    public synchronized boolean ensureNoteExists() {
        // 附件可能先于正文保存，需先确保 note 主记录存在
        if (existInDatabase()) {
            return true;
        }
        mNoteId = Note.getNewNoteId(mContext, mFolderId);
        return mNoteId > 0;
    }

    /**
     * 检查笔记是否存在于数据库中
     * @return 笔记是否已保存到数据库
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断笔记是否值得保存
     * 规则：
     * - 如果笔记已删除，不保存
     * - 如果笔记未在数据库中且内容为空，不保存
     * - 如果笔记在数据库中但没有本地修改，不保存
     * @return 笔记是否需要保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置笔记设置变化监听器
     * @param l 监听器
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置笔记的提醒时间
     * @param date 提醒时间戳
     * @param set 是否设置提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记笔记为已删除或未删除
     * @param mark true表示标记为删除，false表示取消删除标记
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置笔记的背景颜色ID
     * @param id 背景颜色ID
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置笔记的清单模式
     * @param mode 清单模式值
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置小部件类型
     * @param type 小部件类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置绑定的小部件ID
     * @param id 小部件ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置笔记的工作文本内容
     * @param text 笔记文本内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将笔记转换为通话记录笔记
     * @param phoneNumber 电话号码
     * @param callDate 通话时间戳
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    /**
     * 检查笔记是否有时钟提醒
     * @return 如果有提醒则返回true，否则返回false
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    /**
     * 获取笔记的文本内容
     * @return 笔记内容
     */
    public String getContent() {
        return mContent;
    }

    /**
     * 获取笔记的提醒时间戳
     * @return 提醒时间戳
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取笔记的最后修改时间戳
     * @return 最后修改时间戳
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取笔记背景的资源ID
     * @return 背景颜色资源ID
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    /**
     * 获取笔记背景颜色ID
     * @return 背景颜色ID
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取笔记标题背景的资源ID
     * @return 标题背景颜色资源ID
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    /**
     * 获取笔记的清单模式
     * @return 清单模式值
     */
    public int getCheckListMode() {
        return mMode;
    }

    /**
     * 获取笔记ID
     * @return 笔记ID
     */
    public long getNoteId() {
        return mNoteId;
    }

    /**
     * 获取笔记所属文件夹ID
     * @return 文件夹ID
     */
    public long getFolderId() {
        return mFolderId;
    }

    /**
     * 获取绑定的小部件ID
     * @return 小部件ID
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取小部件类型
     * @return 小部件类型
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 笔记设置变化监听器接口
     * 用于监听笔记属性变化事件
     */
    public interface NoteSettingChangedListener {
        /**
         * 当笔记背景颜色改变时调用
         */
        void onBackgroundColorChanged();

        /**
         * 当用户设置时钟提醒时调用
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 当用户从小部件创建笔记时调用
         */
        void onWidgetChanged();

        /**
         * 当切换清单模式和普通模式时调用
         * @param oldMode 切换前的模式
         * @param newMode 新模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
