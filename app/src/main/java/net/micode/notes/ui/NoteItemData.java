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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;


public class NoteItemData {
    // 便签列表查询使用的字段投影，顺序必须与下方 *_COLUMN 索引一一对应。
    static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.CREATED_DATE,
        NoteColumns.HAS_ATTACHMENT,
        NoteColumns.MODIFIED_DATE,
        NoteColumns.NOTES_COUNT,
        NoteColumns.PARENT_ID,
        NoteColumns.SNIPPET,
        NoteColumns.TYPE,
        NoteColumns.WIDGET_ID,
        NoteColumns.WIDGET_TYPE,
    };

    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;
    private long mParentId;
    private String mSnippet;
    private int mType;
    private int mWidgetId;
    private int mWidgetType;
    private String mName;
    private String mPhoneNumber;

    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;
    private boolean mIsMultiNotesFollowingFolder;

    /**
     * 便签列表单行数据的 UI 模型。
     *
     * 该类负责把 Cursor 当前行映射为强类型字段，
     * 并计算列表渲染需要的附加显示状态。
     */
    public NoteItemData(Context context, Cursor cursor) {
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        // 列表展示前，移除清单模式的勾选标记文本。
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        mPhoneNumber = "";
        // 通话记录文件夹下的条目，根据号码解析联系人展示名。
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        checkPostion(cursor);
    }

    // 计算列表首尾/邻接状态，用于背景和分隔样式渲染。
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 判断当前便签是否紧跟在文件夹或系统分组条目之后。
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    public boolean isOneFollowingFolder() {
        // 当前条目是否为“文件夹/系统分组后仅有的一条便签”。
        return mIsOneNoteFollowingFolder;
    }

    public boolean isMultiFollowingFolder() {
        // 当前条目是否为“文件夹/系统分组后多条便签中的第一条”。
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        // 当前条目是否位于列表末尾。
        return mIsLastItem;
    }

    public String getCallName() {
        // 获取通话记录条目对应的联系人名称（无则为空串）。
        return mName;
    }

    public boolean isFirst() {
        // 当前条目是否位于列表开头。
        return mIsFirstItem;
    }

    public boolean isSingle() {
        // 当前列表是否仅有一个条目。
        return mIsOnlyOneItem;
    }

    public long getId() {
        // 获取便签主键 ID。
        return mId;
    }

    public long getAlertDate() {
        // 获取提醒时间戳（毫秒）。
        return mAlertDate;
    }

    public long getCreatedDate() {
        // 获取创建时间戳（毫秒）。
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        // 是否包含附件。
        return mHasAttachment;
    }

    public long getModifiedDate() {
        // 获取最后修改时间戳（毫秒）。
        return mModifiedDate;
    }

    public int getBgColorId() {
        // 获取便签背景颜色 ID。
        return mBgColorId;
    }

    public long getParentId() {
        // 获取父级文件夹 ID。
        return mParentId;
    }

    public int getNotesCount() {
        // 获取文件夹下便签数量（当前条目为文件夹时有意义）。
        return mNotesCount;
    }

    public long getFolderId () {
        // 兼容调用方命名，实际返回父级文件夹 ID。
        return mParentId;
    }

    public int getType() {
        // 获取条目类型（便签/文件夹/系统分组）。
        return mType;
    }

    public int getWidgetType() {
        // 获取关联小组件类型。
        return mWidgetType;
    }

    public int getWidgetId() {
        // 获取关联小组件实例 ID。
        return mWidgetId;
    }

    public String getSnippet() {
        // 获取用于列表展示的摘要文本。
        return mSnippet;
    }

    public boolean hasAlert() {
        // 是否已设置提醒时间。
        return (mAlertDate > 0);
    }

    public boolean isCallRecord() {
        // 是否为通话记录文件夹下且号码有效的条目。
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    public static int getNoteType(Cursor cursor) {
        // 从 Cursor 当前行直接读取条目类型，供适配器快速判断。
        return cursor.getInt(TYPE_COLUMN);
    }
}
