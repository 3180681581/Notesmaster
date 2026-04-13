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
 * WorkingNote类 - 笔记的工作模型，代表当前正在编辑或查看的笔记
 * 
 * 这个类是应用中最重要的数据模型之一，职责包括：
 * 1. 从数据库加载笔记数据，并提供给UI使用
 * 2. 保存用户对笔记的修改（内容、颜色、模式等）
 * 3. 管理笔记的各种属性（提醒时间、背景色、Widget关联等）
 * 4. 与Note类合作，将修改同步到数据库
 * 5. 通过监听器回调，将笔记变化通知给UI
 * 
 * 关键概念：
 * - WorkingNote代表"正在工作的笔记"，也就是用户当前打开的那一个笔记
 * - 它是内存中的临时对象，加载笔记数据和用户修改
 * - 通过saveNote()方法将修改持久化到数据库
 * - 与Note类的区别：Note是数据模型，WorkingNote是UI工作层
 */
public class WorkingNote {
    // ==================== 笔记核心数据 ====================
    /**
     * mNote - 笔记的数据模型对象
     * 用于管理笔记属性和具体内容的同步
     */
    private Note mNote;
    
    /**
     * mNoteId - 笔记在数据库中的唯一标识符
     * 0表示这是一个新笔记（还未保存到数据库）
     * >0表示这是从数据库加载的已有笔记
     */
    private long mNoteId;
    
    /**
     * mContent - 笔记的文本内容
     * 存储用户输入的笔记正文
     */
    private String mContent;
    
    /**
     * mMode - 笔记的显示模式
     * 用于区分普通模式和清单模式
     */
    private int mMode;

    // ==================== 笔记属性 ====================
    /**
     * mAlertDate - 笔记的提醒时间戳（毫秒）
     * 0表示没有设置提醒
     * >0表示设置了闹钟提醒
     */
    private long mAlertDate;

    /**
     * mModifiedDate - 笔记最后修改的时间戳（毫秒）
     */
    private long mModifiedDate;

    /**
     * mBgColorId - 笔记背景颜色的ID
     * 用来从资源中获取对应的颜色
     */
    private int mBgColorId;

    /**
     * mWidgetId - 笔记关联的Widget的ID
     * 用于桌面快捷方式，INVALID_APPWIDGET_ID表示没有关联Widget
     */
    private int mWidgetId;

    /**
     * mWidgetType - 笔记关联的Widget类型
     * 用于指定Widget的显示样式
     */
    private int mWidgetType;

    /**
     * mFolderId - 笔记所属的文件夹ID
     * 用于笔记分类组织
     */
    private long mFolderId;

    /**
     * mContext - Android上下文对象
     * 用于访问内容提供器、资源等系统服务
     */
    private Context mContext;

    /**
     * TAG - 日志标记
     * 用于在LogCat中过滤查看本类相关的日志
     */
    private static final String TAG = "WorkingNote";

    /**
     * mIsDeleted - 标记笔记是否被删除
     * true表示笔记已删除，false表示笔记正常
     */
    private boolean mIsDeleted;

    /**
     * mNoteSettingStatusListener - 笔记设置变化的监听器
     * 当笔记属性改变时，通过此监听器通知UI更新
     */
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // ==================== 数据库查询投影（指定从数据库获取的列）====================
    /**
     * DATA_PROJECTION - 从Data表查询笔记内容时返回的列
     * 定义了从数据库Data表中要查询哪些列的数据
     * 这样做的好处：
     * 1. 只查询需要的列，减少数据库和内存占用
     * 2. 列的顺序与后面的DATA_*_COLUMN常量对应
     * 
     * 包含的列：
     * - ID: 数据记录的ID
     * - CONTENT: 笔记文本内容
     * - MIME_TYPE: 数据类型（普通笔记或通话笔记）
     * - DATA1/DATA2/DATA3/DATA4: 附加数据字段
     */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    /**
     * NOTE_PROJECTION - 从Note表查询笔记属性时返回的列
     * 定义了从数据库Note表中要查询哪些列的数据
     * 
     * 包含的列：
     * - PARENT_ID: 父文件夹ID
     * - ALERTED_DATE: 提醒时间
     * - BG_COLOR_ID: 背景色ID
     * - WIDGET_ID: 关联的Widget ID
     * - WIDGET_TYPE: Widget类型
     * - MODIFIED_DATE: 修改时间
     */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    // ==================== Data表列索引（用于从Cursor中获取数据）====================
    /**
     * DATA_ID_COLUMN - 在DATA_PROJECTION中，ID字段的位置（第0列）
     * 当查询Data表时，Cursor中数据ID在第0列
     * 使用方式：cursor.getLong(DATA_ID_COLUMN) 获取数据ID
     */
    private static final int DATA_ID_COLUMN = 0;

    /**
     * DATA_CONTENT_COLUMN - 在DATA_PROJECTION中，CONTENT字段的位置（第1列）
     * 使用方式：cursor.getString(DATA_CONTENT_COLUMN) 获取笔记内容
     */
    private static final int DATA_CONTENT_COLUMN = 1;

    /**
     * DATA_MIME_TYPE_COLUMN - 在DATA_PROJECTION中，MIME_TYPE字段的位置（第2列）
     * 使用方式：cursor.getString(DATA_MIME_TYPE_COLUMN) 获取数据类型
     */
    private static final int DATA_MIME_TYPE_COLUMN = 2;

    /**
     * DATA_MODE_COLUMN - 在DATA_PROJECTION中，MODE字段的位置（第3列）
     * 使用方式：cursor.getInt(DATA_MODE_COLUMN) 获取笔记显示模式
     */
    private static final int DATA_MODE_COLUMN = 3;

    // ==================== Note表列索引 ====================
    /**
     * NOTE_PARENT_ID_COLUMN - 在NOTE_PROJECTION中，PARENT_ID字段的位置（第0列）
     */
    private static final int NOTE_PARENT_ID_COLUMN = 0;

    /**
     * NOTE_ALERTED_DATE_COLUMN - 在NOTE_PROJECTION中，ALERTED_DATE字段的位置（第1列）
     */
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;

    /**
     * NOTE_BG_COLOR_ID_COLUMN - 在NOTE_PROJECTION中，BG_COLOR_ID字段的位置（第2列）
     */
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;

    /**
     * NOTE_WIDGET_ID_COLUMN - 在NOTE_PROJECTION中，WIDGET_ID字段的位置（第3列）
     */
    private static final int NOTE_WIDGET_ID_COLUMN = 3;

    /**
     * NOTE_WIDGET_TYPE_COLUMN - 在NOTE_PROJECTION中，WIDGET_TYPE字段的位置（第4列）
     */
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;

    /**
     * NOTE_MODIFIED_DATE_COLUMN - 在NOTE_PROJECTION中，MODIFIED_DATE字段的位置（第5列）
     */
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    // ==================== 构造函数 ====================
    /**
     * WorkingNote构造函数（私有）- 创建新笔记的初始化
     * 
     * 使用场景：创建全新的笔记时调用
     * 私有访问控制：防止直接通过new创建，必须通过静态工厂方法createEmptyNote()创建
     * 
     * 初始化规则：
     * - mNoteId: 设置为0，表示还未保存到数据库的新笔记
     * - mModifiedDate: 设置为当前系统时间
     * - mAlertDate: 设置为0，表示没有提醒
     * - mMode: 设置为0，表示普通模式
     * - mWidgetType: 设置为TYPE_WIDGET_INVALIDE，表示没有关联Widget
     * - mIsDeleted: false，笔记未被删除
     * 
     * @param context Android上下文，用于数据库操作
     * @param folderId 新笔记所属的文件夹ID
     */
    private WorkingNote(Context context, long folderId) {
        // 保存上下文，用于后续的数据库操作
        mContext = context;
        // 初始化提醒时间为0，表示无提醒
        mAlertDate = 0;
        // 设置修改时间为当前系统时间戳
        mModifiedDate = System.currentTimeMillis();
        // 设置所属的文件夹
        mFolderId = folderId;
        // 创建新的Note对象用于管理笔记数据
        mNote = new Note();
        // 新笔记的ID为0，表示还未保存到数据库
        mNoteId = 0;
        // 标记为未删除
        mIsDeleted = false;
        // 初始化显示模式为0（普通模式）
        mMode = 0;
        // 初始化Widget类型为无效，表示没有关联桌面快捷方式
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    /**
     * WorkingNote构造函数（私有）- 加载数据库中已存在的笔记
     * 
     * 使用场景：打开已存在的笔记时调用
     * 私有访问控制：防止直接通过new创建，必须通过静态工厂方法load()加载
     * 
     * 初始化规则：
     * - mNoteId: 设置为传入的noteId
     * - 其他数据：通过loadNote()方法从数据库查询获取
     * 
     * @param context Android上下文
     * @param noteId 要加载的笔记ID（数据库中的唯一标识符）
     * @param folderId 笔记所属的文件夹ID
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        // 保存上下文
        mContext = context;
        // 设置要加载的笔记ID
        mNoteId = noteId;
        // 设置文件夹ID
        mFolderId = folderId;
        // 标记为未删除
        mIsDeleted = false;
        // 创建Note对象用于管理笔记数据
        mNote = new Note();
        // 从数据库加载笔记的所有数据
        loadNote();
    }

    /**
     * 从数据库加载笔记的属性数据
     * 
     * 职责：
     * 1. 查询Note表，获取笔记的属性（颜色、提醒时间、Widget等）
     * 2. 将查询结果解析到WorkingNote的成员变量中
     * 3. 然后调用loadNoteData()加载笔记的具体内容
     * 
     * 异常处理：
     * - 如果数据库中找不到该笔记，抛出IllegalArgumentException异常
     * 
     * @throws IllegalArgumentException 如果笔记不存在
     */
    private void loadNote() {
        // 使用ContentResolver查询笔记属性
        // ContentUris.withAppendedId()：构造一个指向特定笔记的Uri
        // NOTE_PROJECTION：指定要查询的列
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        // 判断查询是否成功
        if (cursor != null) {
            // 检查是否至少有一条记录
            if (cursor.moveToFirst()) {
                // 逐一解析Cursor中的数据，填充成员变量
                // 使用前面定义的列索引常量来定位数据位置
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            // 关闭Cursor，释放数据库资源
            cursor.close();
        } else {
            // 如果查询返回null，说明数据库出错
            Log.e(TAG, "No note with id:" + mNoteId);
            // 抛出异常，告诉调用者笔记不存在
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        // 完成属性加载后，继续加载笔记的具体内容（文本、通话记录等）
        loadNoteData();
    }

    /**
     * 从数据库加载笔记的具体内容数据
     * 
     * 职责：
     * 1. 查询Data表，获取笔记中的具体内容（文本、通话记录等）
     * 2. 根据数据类型（MIME_TYPE），分别处理不同类型的数据
     * 3. 保存数据ID和内容到相应的变量中
     * 
     * 异常处理：
     * - 如果数据库中找不到笔记的数据，抛出IllegalArgumentException异常
     * 
     * @throws IllegalArgumentException 如果笔记数据不存在
     */
    private void loadNoteData() {
        // 查询Data表，获取与该笔记相关的所有数据
        // 条件：DataColumns.NOTE_ID = mNoteId（匹配该笔记的所有数据）
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        // 判断查询是否成功
        if (cursor != null) {
            // 检查是否至少有一条数据记录
            if (cursor.moveToFirst()) {
                // 使用do-while循环遍历所有的数据记录
                do {
                    // 获取当前数据的类型（普通笔记或通话笔记）
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    
                    // 判断数据类型，分别处理
                    if (DataConstants.NOTE.equals(type)) {
                        // 这是普通笔记数据（文本内容）
                        // 获取笔记文本内容
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        // 获取笔记的显示模式（普通或清单）
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        // 保存文本数据在数据库中的ID，便于后续更新时使用
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        // 这是通话笔记数据
                        // 保存通话数据在数据库中的ID
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        // 遇到未知类型的数据，记录警告日志
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext()); // 继续获取下一条数据
            }
            // 关闭Cursor，释放数据库资源
            cursor.close();
        } else {
            // 如果查询返回null，说明数据库出错
            Log.e(TAG, "No data with id:" + mNoteId);
            // 抛出异常
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    // ...existing code...

    /**
     * 静态工厂方法 - 创建新的空笔记
     * 
     * 使用工厂方法而不是直接new的好处：
     * 1. 隐藏实现细节，提供清晰的API
     * 2. 可以在方法中进行额外的初始化
     * 3. 为将来的变化留下扩展空间
     * 
     * @param context Android上下文
     * @param folderId 笔记所属的文件夹ID
     * @param widgetId 关联的Widget ID（可选）
     * @param widgetType Widget的类型（可选）
     * @param defaultBgColorId 默认背景颜色ID
     * @return 创建好的新WorkingNote对象
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        // 创建新的WorkingNote对象
        WorkingNote note = new WorkingNote(context, folderId);
        // 设置背景颜色
        note.setBgColorId(defaultBgColorId);
        // 设置关联的Widget ID
        note.setWidgetId(widgetId);
        // 设置Widget类型
        note.setWidgetType(widgetType);
        // 返回初始化后的笔记对象
        return note;
    }

    /**
     * 静态工厂方法 - 从数据库加载笔记
     * 
     * @param context Android上下文
     * @param id 要加载的笔记ID
     * @return 加载后的WorkingNote对象
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 将笔记的所有修改保存到数据库
     * 
     * synchronized关键字的含义：
     * - 保证同一时间只有一个线程能执行此方法
     * - 防止并发修改导致数据损坏或不一致
     * 
     * 执行流程：
     * 1. 检查笔记是否值得保存（isWorthSaving()）
     * 2. 如果笔记还不在数据库中，创建新笔记记录并获取ID
     * 3. 调用Note对象的syncNote()方法同步所有修改
     * 4. 如果有关联的Widget，通知监听器进行更新
     * 5. 返回保存是否成功
     * 
     * @return true表示保存成功或没有需要保存的内容，false表示保存失败
     */
    public synchronized boolean saveNote() {
        // 检查笔记是否值得保存
        if (isWorthSaving()) {
            // 检查笔记是否已存在于数据库中
            if (!existInDatabase()) {
                // 笔记还是新的，需要创建数据库记录并获得ID
                // 如果创建失败，getNewNoteId()会返回0
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    // 创建失败，记录错误并返回false
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 将笔记的所有修改同步到数据库
            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果笔记关联了Widget（桌面快捷方式），需要更新Widget的显示内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                // 通知监听器，Widget相关内容已改变
                mNoteSettingStatusListener.onWidgetChanged();
            }
            // 保存成功
            return true;
        } else {
            // 笔记不值得保存，返回false
            return false;
        }
    }

    /**
     * 检查笔记是否已存在于数据库中
     * 
     * @return true表示笔记已在数据库中，false表示这是新笔记
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 检查笔记是否值得保存
     * 
     * 笔记"不值得保存"的情况：
     * 1. 笔记已被删除（mIsDeleted = true）
     * 2. 这是新笔记且内容为空（空字符串）
     * 3. 这是已有笔记但内容未被修改过
     * 
     * 其他情况笔记都应该被保存
     * 
     * @return true表示笔记值得保存，false表示不需要保存
     */
    private boolean isWorthSaving() {
        // 使用||（或）运算符，满足任意一个条件就不值得保存
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置笔记属性变化的监听器
     * 
     * 监听器的作用：
     * - 当笔记属性改变时（颜色、提醒、模式等），通过回调通知UI层进行更新
     * - 这是观察者（Observer）设计模式的应用
     * 
     * @param l NoteSettingChangedListener监听器实例
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        // 保存监听器引用，后续在属性改变时使用
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置笔记的提醒时间（闹钟）
     * 
     * @param date 提醒时间的时间戳（毫秒），0表示取消提醒
     * @param set true表示设置提醒，false表示取消提醒
     */
    public void setAlertDate(long date, boolean set) {
        // 只有当新时间与旧时间不同时才更新
        if (date != mAlertDate) {
            // 更新内存中的提醒时间
            mAlertDate = date;
            // 将修改标记到Note对象，用于后续同步到数据库
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        // 无论是否有实际变化，都通知监听器（UI可能需要更新显示）
        if (mNoteSettingStatusListener != null) {
            // 调用监听器的回调方法，通知UI提醒时间已改变
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记笔记为删除或恢复
     * 
     * @param mark true表示删除笔记，false表示恢复笔记
     */
    public void markDeleted(boolean mark) {
        // 标记删除状态
        mIsDeleted = mark;
        // 如果笔记关联了Widget，需要通知UI更新Widget显示
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                // 通知监听器，Widget关联的笔记已改变
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置笔记的背景颜色
     * 
     * @param id 颜色的ID号，用于从资源中取得对应的颜色值
     */
    public void setBgColorId(int id) {
        // 只有当新颜色与旧颜色不同时才更新
        if (id != mBgColorId) {
            // 更新内存中的颜色ID
            mBgColorId = id;
            // 通知监听器，背景颜色已改变，UI需要重新绘制
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            // 将修改标记到Note对象，用于后续同步到数据库
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 切换笔记的显示模式（普通模式或清单模式）
     * 
     * @param mode 新的显示模式（0=普通模式，1=清单模式等）
     */
    public void setCheckListMode(int mode) {
        // 只有当模式改变时才更新
        if (mMode != mode) {
            // 通知监听器模式即将改变，传递旧模式和新模式
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            // 更新模式
            mMode = mode;
            // 将修改标记到Note对象，用于后续同步到数据库
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置笔记关联的Widget类型
     * 
     * @param type Widget类型ID
     */
    public void setWidgetType(int type) {
        // 只有当类型改变时才更新
        if (type != mWidgetType) {
            // 更新Widget类型
            mWidgetType = type;
            // 将修改标记到Note对象，用于后续同步到数据库
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置笔记关联的Widget ID
     * 
     * @param id AppWidget的ID
     */
    public void setWidgetId(int id) {
        // 只有当ID改变时才更新
        if (id != mWidgetId) {
            // 更新Widget ID
            mWidgetId = id;
            // 将修改标记到Note对象，用于后续同步到数据库
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置笔记的文本内容
     * 
     * 此方法处理用户的输入，需要比较新旧内容是否不同
     * 只有真正改变才进行更新，减少不必要的数据库操作
     * 
     * @param text 笔记的新文本内容
     */
    public void setWorkingText(String text) {
        // TextUtils.equals()方法会安全地比较两个字符串
        // 相比于==操作符，它能正确处理null值
        if (!TextUtils.equals(mContent, text)) {
            // 内容确实改变了，更新内存中的内容
            mContent = text;
            // 将修改标记到Note对象，用于后续同步到数据库
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将普通笔记转换为通话笔记
     * 
     * 使用场景：
     * - 从来电记录中创建笔记时调用
     * - 将笔记关联到通话记录文件夹
     * - 保存电话号码和通话时间
     * 
     * @param phoneNumber 来电或拨出的电话号码
     * @param callDate 通话的时间戳
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        // 设置通话发生的时间
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        // 设置通话的电话号码
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        // 将笔记移动到通话记录文件夹
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    /**
     * 检查笔记是否设置了提醒闹钟
     * 
     * @return true表示设置了提醒，false表示未设置
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    /**
     * 获取笔记的文本内容
     * 
     * @return 笔记的文本内容
     */
    public String getContent() {
        return mContent;
    }

    /**
     * 获取笔记的提醒时间戳
     * 
     * @return 时间戳（毫秒），0表示没有提醒
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取笔记的修改时间戳
     * 
     * @return 时间戳（毫秒）
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取笔记背景颜色对应的资源ID
     * 
     * 用于在UI中获取实际的色值资源
     * 
     * @return Android资源ID，可用于获取Drawable或Color
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    /**
     * 获取笔记背景颜色的ID（数据库中存储的ID值）
     * 
     * @return 颜色ID
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取笔记标题栏背景颜色对应的资源ID
     * 
     * @return Android资源ID
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    /**
     * 获取笔记的显示模式
     * 
     * @return 模式值（0=普通模式，1=清单模式等）
     */
    public int getCheckListMode() {
        return mMode;
    }

    /**
     * 获取笔记的ID
     * 
     * @return 笔记在数据库中的ID，0表示新笔记
     */
    public long getNoteId() {
        return mNoteId;
    }

    /**
     * 获取笔记所属的文件夹ID
     * 
     * @return 文件夹ID
     */
    public long getFolderId() {
        return mFolderId;
    }

    /**
     * 获取笔记关联的Widget ID
     * 
     * @return Widget ID
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取笔记关联的Widget类型
     * 
     * @return Widget类型
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * NoteSettingChangedListener - 笔记设置变化的监听器接口
     * 
     * 这是一个回调接口，用于实现观察者模式：
     * - 当WorkingNote中的笔记属性改变时
     * - 通过此接口的回调方法通知UI层进行相应的更新
     * 
     * 好处：
     * 1. 解耦：数据模型和UI层不需要直接依赖
     * 2. 灵活：UI层可以自由选择监听哪些属性变化
     * 3. 可维护：属性变化的处理逻辑集中在实现类中
     */
    public interface NoteSettingChangedListener {
        /**
         * 笔记背景颜色改变时调用
         * 
         * UI应该在此方法中重新获取颜色并重新绘制
         */
        void onBackgroundColorChanged();

        /**
         * 笔记的提醒（闹钟）设置改变时调用
         * 
         * @param date 新的提醒时间戳
         * @param set true表示设置提醒，false表示取消提醒
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 笔记关联的Widget改变时调用
         * 
         * 使用场景：
         * - 笔记被删除时
         * - 笔记内容改变时
         * - Widget从笔记分离时
         * 
         * UI应该在此方法中更新Widget的显示内容
         */
        void onWidgetChanged();

        /**
         * 笔记的显示模式（普通/清单）改变时调用
         * 
         * @param oldMode 旧的模式值
         * @param newMode 新的模式值
         * 
         * 使用场景：
         * - 用户切换模式时，UI需要切换不同的编辑界面
         * - 普通模式：显示单行编辑框
         * - 清单模式：显示多个待办项
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
