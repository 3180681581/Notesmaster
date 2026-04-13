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
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * Note类 - 笔记数据模型，用于管理笔记内容的创建、修改和同步
 * 
 * 这个类的主要职责：
 * 1. 创建新笔记并获取笔记ID
 * 2. 管理笔记的基本属性（创建时间、修改时间等）
 * 3. 管理笔记中的具体数据（文本内容、通话记录等）
 * 4. 将修改内容同步到本地数据库
 * 
 * 设计特点：
 * - 使用ContentValues来存储需要修改的属性值
 * - 使用内部类NoteData来管理笔记的具体内容
 * - 通过ContentResolver与数据库进行交互
 */
public class Note {
    /**
     * mNoteDiffValues - 存储笔记属性的变化值
     * 只记录被修改过的字段，减少数据库操作的量
     * 例如：修改时间、本地修改标记等
     */
    private ContentValues mNoteDiffValues;

    /**
     * mNoteData - 笔记的具体数据（文本和通话记录）
     * 通过内部类NoteData来管理这些数据
     */
    private NoteData mNoteData;

    /**
     * TAG - 日志标记
     * 用于在Android Studio的LogCat中过滤和查看本类相关的日志信息
     */
    private static final String TAG = "Note";
    /**
     * 创建新笔记 - 在数据库中插入一条新笔记记录并返回其ID
     * 
     * synchronized关键字的含义：
     * - 保证同一时间只有一个线程能执行此方法
     * - 防止多个线程同时创建笔记时产生ID重复或冲突
     * 
     * @param context Android上下文，用于访问内容提供器（数据库）
     * @param folderId 笔记所属文件夹的ID
     * @return 新笔记的ID（数据库中的唯一标识符）
     * @throws IllegalStateException 如果获取到无效的笔记ID（-1）时抛出异常
     * 
     * 执行流程：
     * 1. 创建ContentValues对象来存储笔记的初始属性
     * 2. 设置笔记的创建时间和修改时间为当前系统时间
     * 3. 设置笔记类型、本地修改标记、所属文件夹等
     * 4. 通过ContentResolver插入数据库，获取返回的Uri
     * 5. 从Uri中提取笔记ID（URI的第二个路径段）
     * 6. 验证ID的有效性，无效则返回0
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 创建ContentValues对象来存储新笔记的属性
        ContentValues values = new ContentValues();
        // 获取当前系统时间（毫秒级时间戳）
        long createdTime = System.currentTimeMillis();
        // 设置笔记的创建日期
        values.put(NoteColumns.CREATED_DATE, createdTime);
        // 设置笔记的最后修改日期（初始与创建日期相同）
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        // 设置笔记类型为普通笔记
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        // 设置本地修改标记为1（表示已修改，需要同步）
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        // 设置笔记所属的文件夹ID
        values.put(NoteColumns.PARENT_ID, folderId);
        // 通过内容提供器向数据库插入新笔记，返回指向这条新记录的Uri
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从Uri中提取笔记ID
            // Uri的格式通常为：content://authority/table_name/id
            // getPathSegments()得到路径的各个部分，第1位（下标）是ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            // 如果无法将字符串转换为Long，记录错误日志
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        // 检查ID的有效性，-1通常表示数据库插入失败
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * Note类的构造函数 - 创建一个新的Note对象
     * 
     * 初始化规则：
     * - mNoteDiffValues: 初始化为空的ContentValues对象，用于记录笔记属性的改变
     * - mNoteData: 初始化为新的NoteData对象，用于管理笔记的文本和通话数据
     * 
     * 何时调用：每当需要创建一个新的笔记对象时调用
     */
    public Note() {
        // 创建一个新的ContentValues对象，用于存储笔记属性的变化
        mNoteDiffValues = new ContentValues();
        // 创建一个新的NoteData对象，用于管理笔记的具体内容（文本、通话记录等）
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记的属性值
     * 
     * 此方法做了三件事情：
     * 1. 将新的属性值存储到mNoteDiffValues中
     * 2. 标记笔记已被本地修改（LOCAL_MODIFIED = 1）
     * 3. 更新笔记的修改时间为当前系统时间
     * 
     * @param key 要修改的属性名（例如：NoteColumns.SNIPPET_MODIFIED_DATE）
     * @param value 属性的新值
     */
    public void setNoteValue(String key, String value) {
        // 将新的属性值添加到差异值集合中
        mNoteDiffValues.put(key, value);
        // 标记笔记已在本地被修改，1表示已修改
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        // 更新笔记的修改时间为当前系统时间戳
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置笔记中的文本数据内容
     * 
     * @param key 文本数据的key（例如：TextNote.COLUMN_CONTENT - 笔记内容）
     * @param value 文本数据的值（笔记的具体文本内容）
     */
    public void setTextData(String key, String value) {
        // 将文本数据委托给NoteData对象处理
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置笔记中文本数据在数据库中的ID
     * 
     * 何时使用：
     * - 当从数据库加载已存在的笔记时，需要保存其文本数据的ID
     * - 这样之后更新笔记时才知道要更新数据库中的哪条文本记录
     * 
     * @param id 文本数据在数据库中的唯一标识符
     */
    public void setTextDataId(long id) {
        // 将文本数据ID委托给NoteData对象保存
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取笔记中文本数据在数据库中的ID
     * 
     * @return 文本数据的ID，如果未设置则返回0
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置笔记中通话数据在数据库中的ID
     * 
     * @param id 通话数据在数据库中的唯一标识符
     */
    public void setCallDataId(long id) {
        // 将通话数据ID委托给NoteData对象保存
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置笔记中的通话数据内容
     * 
     * @param key 通话数据的key（例如：CallNote.COLUMN_PHONE_NUMBER - 电话号码）
     * @param value 通话数据的值
     */
    public void setCallData(String key, String value) {
        // 将通话数据委托给NoteData对象处理
        mNoteData.setCallData(key, value);
    }

    /**
     * 检查笔记是否在本地被修改过
     * 
     * 判断逻辑：
     * - 如果笔记属性值集合中有内容，说明笔记属性被修改过
     * - 或者笔记中的具体数据（文本或通话记录）被修改过
     * 
     * @return true表示笔记被修改过，false表示笔记未被修改
     */
    public boolean isLocalModified() {
        // 检查笔记属性和笔记数据是否有任何修改
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将笔记的所有修改内容同步到本地数据库
     * 
     * 执行过程：
     * 1. 首先验证noteId的有效性（必须大于0）
     * 2. 检查是否有内容需要同步，如果没有直接返回true
     * 3. 更新笔记的主属性到数据库（通过mNoteDiffValues）
     * 4. 更新笔记中的具体数据（文本和通话记录）
     * 5. 清空差异值集合，准备下次修改
     * 
     * @param context Android上下文，用于访问内容提供器（数据库）
     * @param noteId 要同步的笔记ID
     * @return true表示同步成功，false表示同步失败
     * @throws IllegalArgumentException 如果noteId无效（≤0）时抛出异常
     */
    public boolean syncNote(Context context, long noteId) {
        // 验证笔记ID的有效性
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 如果笔记没有任何修改，无需同步，直接返回成功
        if (!isLocalModified()) {
            return true;
        }

        /**
         * 理论上，一旦数据改变，笔记应该在LOCAL_MODIFIED和MODIFIED_DATE字段更新。
         * 为了数据安全，即使更新笔记失败，我们仍然继续更新笔记数据信息
         * 防止数据永久丢失
         */
        // 通过ContentResolver更新笔记的属性字段到数据库
        if (context.getContentResolver().update(
                // 根据noteId构造指向特定笔记的Uri
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), 
                // 传入包含要修改的属性值的ContentValues对象
                mNoteDiffValues, 
                null,
                null) == 0) {
            // 如果没有任何记录被更新（返回0），记录错误日志
            Log.e(TAG, "Update note error, should not happen");
            // 但不返回false，继续执行，以确保数据被同步
        }
        // 清空属性差异值集合，以便下次使用
        mNoteDiffValues.clear();

        // 如果笔记的具体数据（文本或通话记录）有修改，将其推送到数据库
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            // 如果推送失败，返回false表示同步失败
            return false;
        }

        // 所有修改都成功同步到数据库，返回true
        return true;
    }

    /**
     * NoteData - 笔记数据管理内部类
     * 
     * 这是一个内部类（在Note类内部定义），专门用于管理笔记中的具体内容数据。
     * 
     * 主要职责：
     * 1. 管理笔记中的文本数据（文本内容、对应的数据库ID等）
     * 2. 管理笔记中的通话数据（电话号码、通话记录等）
     * 3. 将这些数据变化推送到数据库
     * 
     * 为什么使用内部类：
     * - 隐藏复杂的数据管理逻辑，使Note类更清晰
     * - NoteData只服务于Note类，不需要被其他类使用
     * - 能够访问Note类的private成员（如mNoteDiffValues）
     */
    private class NoteData {
        /**
         * mTextDataId - 笔记中文本数据在数据库中的ID
         * 当从数据库加载笔记时设置，用于更新时标识要更新的记录
         */
        private long mTextDataId;

        /**
         * mTextDataValues - 存储文本数据的变化值
         * 只记录被修改的文本数据字段
         */
        private ContentValues mTextDataValues;

        /**
         * mCallDataId - 笔记中通话数据在数据库中的ID
         * 当从数据库加载笔记时设置，用于更新时标识要更新的记录
         */
        private long mCallDataId;

        /**
         * mCallDataValues - 存储通话数据的变化值
         * 只记录被修改的通话数据字段
         */
        private ContentValues mCallDataValues;

        /**
         * TAG - 日志标记，用于NoteData相关的日志记录
         */
        private static final String TAG = "NoteData";

        /**
         * NoteData的构造函数 - 初始化数据集合
         * 
         * 初始化：
         * - mTextDataValues: 空的ContentValues对象
         * - mCallDataValues: 空的ContentValues对象
         * - mTextDataId: 0（表示还没有被设置）
         * - mCallDataId: 0（表示还没有被设置）
         */
        public NoteData() {
            // 初始化文本数据值集合
            mTextDataValues = new ContentValues();
            // 初始化通话数据值集合
            mCallDataValues = new ContentValues();
            // 初始化文本数据ID为0
            mTextDataId = 0;
            // 初始化通话数据ID为0
            mCallDataId = 0;
        }

        /**
         * 检查NoteData是否有本地修改
         * 
         * @return true表示文本数据或通话数据有修改，false表示都没修改
         */
        boolean isLocalModified() {
            // 检查文本或通话数据是否有任何变化
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据在数据库中的ID
         * 
         * @param id 文本数据的数据库ID
         * @throws IllegalArgumentException 如果ID不合法（≤0）时抛出异常
         */
        void setTextDataId(long id) {
            // 验证ID有效性，ID必须大于0
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            // 保存文本数据的ID
            mTextDataId = id;
        }

        /**
         * 设置通话数据在数据库中的ID
         * 
         * @param id 通话数据的数据库ID
         * @throws IllegalArgumentException 如果ID不合法（≤0）时抛出异常
         */
        void setCallDataId(long id) {
            // 验证ID有效性，ID必须大于0
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            // 保存通话数据的ID
            mCallDataId = id;
        }

        /**
         * 设置通话数据内容，并标记笔记为已修改
         * 
         * @param key 通话数据字段名（例如：电话号码字段）
         * @param value 通话数据的值
         */
        void setCallData(String key, String value) {
            // 将通话数据添加到通话数据值集合中
            mCallDataValues.put(key, value);
            // 标记父Note对象中的笔记已被本地修改
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            // 更新父Note对象中的修改时间戳
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据内容，并标记笔记为已修改
         * 
         * @param key 文本数据字段名（例如：CONTENT_ITEM_TYPE）
         * @param value 文本数据的值（笔记内容）
         */
        void setTextData(String key, String value) {
            // 将文本数据添加到文本数据值集合中
            mTextDataValues.put(key, value);
            // 标记父Note对象中的笔记已被本地修改
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            // 更新父Note对象中的修改时间戳
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将NoteData中的数据变化推送到数据库
         * 
         * 执行逻辑：
         * 1. 验证noteId的有效性
         * 2. 如果有文本数据的修改：
         *    - 如果是新数据（mTextDataId==0），执行INSERT操作，保存新ID
         *    - 如果是现有数据，执行UPDATE操作
         * 3. 如果有通话数据的修改，执行相同的INSERT或UPDATE操作
         * 4. 使用applyBatch()批量执行所有操作
         * 
         * @param context Android上下文，用于访问内容提供器
         * @param noteId 笔记的ID，用于关联数据
         * @return 如果成功返回指向笔记的Uri，失败返回null
         * @throws IllegalArgumentException 如果noteId无效（≤0）时抛出异常
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * 安全性检查
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            // 创建一个ContentProviderOperation列表来批量执行数据库操作
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            // 创建一个Builder对象用于构造操作
            ContentProviderOperation.Builder builder = null;

            // ==================== 处理文本数据 ====================
            if(mTextDataValues.size() > 0) {
                // 将笔记ID添加到文本数据值中
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                
                if (mTextDataId == 0) {
                    // 新增操作：如果还没有文本数据记录，则创建新记录
                    // 设置MIME类型，用于标识这是一个文本笔记
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    // 将新文本数据插入到数据库
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        // 从返回的Uri中提取新创建的文本数据ID并保存
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        // 如果无法解析URI，记录错误日志
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        // 清空数据值集合
                        mTextDataValues.clear();
                        // 返回null表示操作失败
                        return null;
                    }
                } else {
                    // 更新操作：如果已经有文本数据记录，则更新现有记录
                    // 创建一个UPDATE操作，指定要更新的数据ID
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    // 设置要更新的值
                    builder.withValues(mTextDataValues);
                    // 将这个操作添加到操作列表中
                    operationList.add(builder.build());
                }
                // 清空文本数据值集合
                mTextDataValues.clear();
            }

            // ==================== 处理通话数据 ====================
            if(mCallDataValues.size() > 0) {
                // 将笔记ID添加到通话数据值中
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                
                if (mCallDataId == 0) {
                    // 新增操作：如果还没有通话数据记录，则创建新记录
                    // 设置MIME类型，用于标识这是一个通话笔记
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    // 将新通话数据插入到数据库
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        // 从返回的Uri中提取新创建的通话数据ID并保存
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        // 如果无法解析URI，记录错误日志
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        // 清空数据值集合
                        mCallDataValues.clear();
                        // 返回null表示操作失败
                        return null;
                    }
                } else {
                    // 更新操作：如果已经有通话数据记录，则更新现有记录
                    // 创建一个UPDATE操作，指定要更新的数据ID
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    // 设置要更新的值
                    builder.withValues(mCallDataValues);
                    // 将这个操作添加到操作列表中
                    operationList.add(builder.build());
                }
                // 清空通话数据值集合
                mCallDataValues.clear();
            }

            // ==================== 批量执行数据库操作 ====================
            if (operationList.size() > 0) {
                try {
                    // 使用applyBatch()方法批量执行所有的UPDATE操作
                    // 这比一个个执行UPDATE要高效得多
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    // 如果操作成功，返回指向笔记的Uri；否则返回null
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    // RemoteException表示与内容提供器通信失败
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    // OperationApplicationException表示批量操作中某个操作失败
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            // 如果操作列表为空，没有更新操作需要执行，返回null
            return null;
        }
    }
}
