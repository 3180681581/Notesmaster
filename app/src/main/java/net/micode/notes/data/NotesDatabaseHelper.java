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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * NotesDatabaseHelper 类是一个 SQLiteOpenHelper 的子类，用于管理笔记应用的数据库。
 * 该类负责创建和升级数据库表，包括笔记表（note）和数据表（data），以及相关的触发器和索引。
 * 它使用单例模式确保在应用中只有一个数据库帮助器实例。
 * 数据库版本为4，支持笔记的创建、修改、删除以及文件夹管理等功能。
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库名称
    private static final String DB_NAME = "note.db";

    // 数据库版本号，每次数据库结构变更时需要递增
    private static final int DB_VERSION = 4;

    /**
     * TABLE 接口定义了数据库中的表名常量。
     * 包括笔记表（NOTE）和数据表（DATA）。
     */
    public interface TABLE {
        // 笔记表名
        public static final String NOTE = "note";

        // 数据表名
        public static final String DATA = "data";
    }

    // 日志标签，用于调试输出
    private static final String TAG = "NotesDatabaseHelper";

    // 单例实例，确保全局只有一个数据库帮助器
    private static NotesDatabaseHelper mInstance;

    /**
     * 创建笔记表的SQL语句。
     * 该表存储笔记的基本信息，包括ID、父ID、提醒日期、背景颜色、创建时间等。
     * 使用SQLite的时间函数strftime('%s','now') * 1000来设置默认时间戳（毫秒）。
     * 表结构：
     * - ID: 主键，自增整数
     * - PARENT_ID: 父文件夹ID，默认为0（根目录）
     * - ALERTED_DATE: 提醒日期时间戳，默认为0（无提醒）
     * - BG_COLOR_ID: 背景颜色ID，默认为0
     * - CREATED_DATE: 创建时间戳，使用SQLite函数设置默认值
     * - HAS_ATTACHMENT: 是否有附件，0=无，1=有
     * - MODIFIED_DATE: 修改时间戳，使用SQLite函数设置默认值
     * - NOTES_COUNT: 子笔记数量（用于文件夹）
     * - SNIPPET: 笔记摘要文本
     * - TYPE: 笔记类型（普通笔记、文件夹等）
     * - WIDGET_ID: 小部件ID
     * - WIDGET_TYPE: 小部件类型
     * - SYNC_ID: 同步ID
     * - LOCAL_MODIFIED: 本地修改标志
     * - ORIGIN_PARENT_ID: 原始父ID
     * - GTASK_ID: Google Tasks ID
     * - VERSION: 版本号
     */
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +
        ")";

    /**
     * 创建数据表的SQL语句。
     * 该表存储笔记的附加数据，如文本内容、附件等。
     * 每个数据项与一个笔记关联，通过NOTE_ID字段。
     * 表结构：
     * - ID: 主键，自增整数
     * - MIME_TYPE: 数据类型（如"text/plain"表示文本，"image/*"表示图片等）
     * - NOTE_ID: 关联的笔记ID
     * - CREATED_DATE: 创建时间戳，使用SQLite函数设置默认值
     * - MODIFIED_DATE: 修改时间戳，使用SQLite函数设置默认值
     * - CONTENT: 数据内容（文本内容、文件路径等）
     * - DATA1-DATA5: 扩展数据字段，用于存储额外信息（如图片尺寸、地理位置等）
     */
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA1 + " INTEGER," +
            DataColumns.DATA2 + " INTEGER," +
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
        ")";

    /**
     * 创建数据表中NOTE_ID字段的索引SQL语句。
     * 该索引用于加速根据笔记ID查询数据的操作。
     * 索引名称：note_id_index
     * 索引字段：NOTE_ID
     * 作用：当需要查找某个笔记的所有数据项时，可以快速定位
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    /**
     * 触发器：当笔记的父ID更新时，增加新父文件夹的笔记计数。
     * 当笔记从一个文件夹移动到另一个文件夹时，该触发器会自动更新文件夹的笔记数量。
     * (Increase folder's note count when move note to the folder)
     * 触发条件：笔记表中PARENT_ID字段被更新后
     * 执行逻辑：将新父文件夹（new.PARENT_ID）的NOTES_COUNT字段加1
     * 作用：保持文件夹的笔记数量统计准确
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 触发器：当笔记的父ID更新时，减少原父文件夹的笔记计数。
     * 确保原文件夹的笔记数量正确减少，避免计数错误。
     * (Decrease folder's note count when move note from folder)
     * 触发条件：笔记表中PARENT_ID字段被更新后
     * 执行逻辑：将原父文件夹（old.PARENT_ID）的NOTES_COUNT字段减1（如果大于0）
     * 作用：保持原文件夹的笔记数量统计准确
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * 触发器：当插入新笔记时，增加父文件夹的笔记计数。
     * 新建笔记时自动更新文件夹的统计信息。
     * (Increase folder's note count when insert new note to the folder)
     * 触发条件：笔记表中插入新记录后
     * 执行逻辑：将父文件夹（new.PARENT_ID）的NOTES_COUNT字段加1
     * 作用：新建笔记时自动更新文件夹统计
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 触发器：当删除笔记时，减少父文件夹的笔记计数。
     * 删除笔记时保持文件夹统计的准确性。
     * (Decrease folder's note count when delete note from the folder)
     * 触发条件：笔记表中删除记录后
     * 执行逻辑：将父文件夹（old.PARENT_ID）的NOTES_COUNT字段减1（如果大于0）
     * 作用：删除笔记时自动更新文件夹统计
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * 触发器：当插入笔记类型的数据时，更新笔记的摘要内容。
     * 确保笔记的SNIPPET字段反映最新的文本内容。
     * (Update note's content when insert data with type DataConstants.NOTE)
     * 触发条件：数据表中插入新记录后，且MIME_TYPE为NOTE类型
     * 执行逻辑：将笔记表中对应笔记的SNIPPET字段更新为新插入的数据内容
     * 作用：保持笔记摘要与实际内容同步
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 触发器：当更新笔记类型的数据时，更新笔记的摘要内容。
     * 保持笔记摘要与实际内容同步。
     * (Update note's content when data with DataConstants.NOTE type has changed)
     * 触发条件：数据表中更新记录后，且旧记录的MIME_TYPE为NOTE类型
     * 执行逻辑：将笔记表中对应笔记的SNIPPET字段更新为新的数据内容
     * 作用：编辑笔记内容时自动更新摘要
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 触发器：当删除笔记类型的数据时，清空笔记的摘要内容。
     * 防止删除数据后摘要仍显示旧内容。
     * (Update note's content when data with DataConstants.NOTE type has deleted)
     * 触发条件：数据表中删除记录后，且被删除记录的MIME_TYPE为NOTE类型
     * 执行逻辑：将笔记表中对应笔记的SNIPPET字段清空
     * 作用：删除笔记内容时清除摘要，避免显示过时信息
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 触发器：当删除笔记时，同时删除所有关联的数据记录。
     * 维护数据完整性，防止孤立数据。
     * (Delete datas belong to note which has been deleted)
     * 触发条件：笔记表中删除记录后
     * 执行逻辑：删除数据表中所有NOTE_ID等于被删除笔记ID的记录
     * 作用：级联删除，保持数据一致性
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 触发器：当删除文件夹时，同时删除该文件夹下的所有笔记。
     * 级联删除以保持数据库一致性。
     * (Delete notes belong to folder which has been deleted)
     * 触发条件：笔记表中删除记录后
     * 执行逻辑：删除笔记表中所有PARENT_ID等于被删除笔记ID的记录（递归删除子笔记）
     * 作用：删除文件夹时自动清理所有子内容
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 触发器：当文件夹移动到回收站时，将其子笔记也移动到回收站。
     * 确保整个文件夹结构一起进入回收站。
     * (Move notes belong to folder which has been moved to trash folder)
     * 触发条件：笔记表中更新记录后，且新PARENT_ID为回收站ID
     * 执行逻辑：将所有PARENT_ID等于被移动文件夹ID的笔记也移动到回收站
     * 作用：保持文件夹结构的完整性，即使在回收站中
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 构造函数：创建数据库帮助器实例。
     * 调用父类SQLiteOpenHelper的构造函数，指定数据库名、版本等参数。
     * @param context Android应用上下文，用于访问数据库文件。
     */
    public NotesDatabaseHelper(Context context) {
        // 调用父类构造函数，传入上下文、数据库名、游标工厂（null）和数据库版本
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建笔记表及其相关触发器和系统文件夹。
     * 该方法在数据库首次创建时调用，设置完整的笔记表结构。
     * @param db SQLiteDatabase实例，用于执行SQL语句。
     */
    public void createNoteTable(SQLiteDatabase db) {
        // 执行创建笔记表的SQL语句
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        // 重新创建笔记表相关的所有触发器
        reCreateNoteTableTriggers(db);
        // 创建系统文件夹（如根文件夹、回收站等）
        createSystemFolder(db);
        // 记录日志，表示笔记表创建完成
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重新创建笔记表的所有触发器。
     * 首先删除可能存在的旧触发器，然后创建新的触发器。
     * 用于数据库升级或初始化时确保触发器是最新的。
     * @param db SQLiteDatabase实例。
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除可能存在的旧触发器，避免冲突
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新的触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 创建系统文件夹。
     * 初始化数据库时创建必要的系统文件夹，包括通话记录文件夹、根文件夹、临时文件夹和回收站文件夹。
     * @param db SQLiteDatabase实例。
     */
    private void createSystemFolder(SQLiteDatabase db) {
        // 创建ContentValues对象，用于插入数据
        ContentValues values = new ContentValues();

        /**
         * 通话记录文件夹，用于存储通话笔记。
         * (call record folder for call notes)
         */
        // 设置文件夹ID为通话记录文件夹ID
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        // 设置类型为系统文件夹
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        // 插入到笔记表中
        db.insert(TABLE.NOTE, null, values);

        /**
         * 根文件夹，作为默认文件夹。
         * (root folder which is default folder)
         */
        // 清空ContentValues，准备插入下一个文件夹
        values.clear();
        // 设置文件夹ID为根文件夹ID
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        // 设置类型为系统文件夹
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        // 插入到笔记表中
        db.insert(TABLE.NOTE, null, values);

        /**
         * 临时文件夹，用于移动笔记时的中间存储。
         * (temporary folder which is used for moving note)
         */
        // 清空ContentValues
        values.clear();
        // 设置文件夹ID为临时文件夹ID
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        // 设置类型为系统文件夹
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        // 插入到笔记表中
        db.insert(TABLE.NOTE, null, values);

        /**
         * 创建回收站文件夹。
         * (create trash folder)
         */
        // 清空ContentValues
        values.clear();
        // 设置文件夹ID为回收站文件夹ID
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        // 设置类型为系统文件夹
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        // 插入到笔记表中
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建数据表及其相关触发器和索引。
     * 该方法在数据库首次创建时调用，设置完整的数据表结构。
     * @param db SQLiteDatabase实例，用于执行SQL语句。
     */
    public void createDataTable(SQLiteDatabase db) {
        // 执行创建数据表的SQL语句
        db.execSQL(CREATE_DATA_TABLE_SQL);
        // 重新创建数据表相关的触发器
        reCreateDataTableTriggers(db);
        // 创建数据表上的索引，用于加速查询
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        // 记录日志，表示数据表创建完成
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重新创建数据表的所有触发器。
     * 首先删除可能存在的旧触发器，然后创建新的触发器。
     * 用于数据库升级时确保触发器是最新的。
     * @param db SQLiteDatabase实例。
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 删除可能存在的旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        // 创建新的触发器
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取数据库帮助器的单例实例。
     * 使用同步方法确保线程安全，避免创建多个实例。
     * @param context Android应用上下文。
     * @return NotesDatabaseHelper的单例实例。
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        // 检查单例实例是否已创建
        if (mInstance == null) {
            // 如果未创建，则创建新实例
            mInstance = new NotesDatabaseHelper(context);
        }
        // 返回单例实例
        return mInstance;
    }

    /**
     * 当数据库首次创建时调用。
     * 创建所有必要的表、触发器、索引和系统数据。
     * @param db 新创建的SQLiteDatabase实例。
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建笔记表及其相关结构
        createNoteTable(db);
        // 创建数据表及其相关结构
        createDataTable(db);
    }

    /**
     * 当数据库需要升级时调用。
     * 根据旧版本号执行相应的升级逻辑，最终达到新版本。
     * @param db SQLiteDatabase实例。
     * @param oldVersion 旧数据库版本号。
     * @param newVersion 新数据库版本号。
     * @throws IllegalStateException 如果升级失败。
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 标志是否需要重新创建触发器
        boolean reCreateTriggers = false;
        // 标志是否跳过版本2的单独升级（因为版本1升级到版本2时已包含版本2到版本3的逻辑）
        boolean skipV2 = false;

        // 如果旧版本是1，则升级到版本2（此升级包含从版本2到版本3的逻辑）
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // 设置跳过标志，避免重复执行版本2的升级
            oldVersion++; // 版本号递增
        }

        // 如果旧版本是2且未跳过，则执行版本2到版本3的升级
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true; // 标记需要重新创建触发器
            oldVersion++; // 版本号递增
        }

        // 如果旧版本是3，则升级到版本4
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++; // 版本号递增
        }

        // 如果需要重新创建触发器，则执行
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 检查升级是否成功，如果版本号不匹配则抛出异常
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 升级数据库到版本2。
     * 删除旧表并重新创建，相当于完全重建数据库。
     * @param db SQLiteDatabase实例。
     */
    private void upgradeToV2(SQLiteDatabase db) {
        // 删除旧的笔记表（如果存在）
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        // 删除旧的数据表（如果存在）
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        // 重新创建笔记表
        createNoteTable(db);
        // 重新创建数据表
        createDataTable(db);
    }

    /**
     * 升级数据库到版本3。
     * 删除不再使用的触发器，添加Google Tasks ID列，并创建回收站系统文件夹。
     * (Upgrade database to version 3: drop unused triggers, add gtask id column, add trash system folder)
     * @param db SQLiteDatabase实例。
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除不再使用的触发器 (drop unused triggers)
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 添加Google Tasks ID列 (add a column for gtask id)
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 添加回收站系统文件夹 (add a trash system folder)
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级数据库到版本4。
     * 为笔记表添加版本号列，用于跟踪笔记的版本信息。
     * @param db SQLiteDatabase实例。
     */
    private void upgradeToV4(SQLiteDatabase db) {
        // 为笔记表添加VERSION列，默认值为0
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}
