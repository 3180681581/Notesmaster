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

package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 备份工具类
 * 负责将便签数据导出为文本文件，支持备份到SD卡
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";  // 日志标签
    
    // 单例模式实例
    private static BackupUtils sInstance;

    /**
     * 获取BackupUtils单例实例
     * @param context 上下文对象
     * @return BackupUtils实例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * 备份或恢复操作的状态码定义
     */
    // SD卡未挂载
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // 备份文件不存在
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // 数据格式被破坏，可能被其他程序修改
    public static final int STATE_DATA_DESTROIED               = 2;
    // 运行时异常导致备份或恢复失败
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // 备份或恢复成功
    public static final int STATE_SUCCESS                      = 4;

    private TextExport mTextExport;  // 文本导出器

    /**
     * 私有构造函数，初始化文本导出器
     * @param context 上下文对象
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检查外部存储（SD卡）是否可用
     * @return true表示已挂载可用，false表示不可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 导出便签为文本文件
     * @return 操作状态码
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文本文件名
     * @return 文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文本文件目录
     * @return 文件目录路径
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 文本导出器内部类
     * 负责实际的文本导出逻辑
     */
    private static class TextExport {
        // 便签查询列投影
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,           // 便签ID
                NoteColumns.MODIFIED_DATE, // 修改日期
                NoteColumns.SNIPPET,       // 摘要/片段
                NoteColumns.TYPE           // 类型（文件夹或便签）
        };

        // 便签列索引常量
        private static final int NOTE_COLUMN_ID = 0;              // ID列索引
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;   // 修改日期列索引
        private static final int NOTE_COLUMN_SNIPPET = 2;         // 摘要列索引

        // 数据查询列投影
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,    // 内容
                DataColumns.MIME_TYPE,  // MIME类型
                DataColumns.DATA1,      // 数据字段1
                DataColumns.DATA2,      // 数据字段2
                DataColumns.DATA3,      // 数据字段3
                DataColumns.DATA4,      // 数据字段4
        };

        // 数据列索引常量
        private static final int DATA_COLUMN_CONTENT = 0;       // 内容列索引
        private static final int DATA_COLUMN_MIME_TYPE = 1;     // MIME类型列索引
        private static final int DATA_COLUMN_CALL_DATE = 2;     // 通话日期列索引
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;  // 电话号码列索引

        private final String [] TEXT_FORMAT;  // 文本格式数组
        // 格式类型索引常量
        private static final int FORMAT_FOLDER_NAME          = 0;  // 文件夹名称格式
        private static final int FORMAT_NOTE_DATE            = 1;  // 便签日期格式
        private static final int FORMAT_NOTE_CONTENT         = 2;  // 便签内容格式

        private Context mContext;      // 上下文对象
        private String mFileName;      // 导出文件名
        private String mFileDirectory; // 导出文件目录

        /**
         * 构造函数
         * @param context 上下文对象
         */
        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 获取指定索引的格式字符串
         * @param id 格式索引
         * @return 格式字符串
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 将指定文件夹导出为文本
         * @param folderId 文件夹ID
         * @param ps 打印流，用于写入文件
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询属于该文件夹的所有便签
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                        folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印便签的最后修改日期
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 查询并打印便签的数据内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 将指定便签导出为文本
         * @param noteId 便签ID
         * @param ps 打印流，用于写入文件
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            // 查询便签关联的所有数据
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                        noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        
                        // 处理通话记录类型的便签
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 打印电话号码
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // 打印通话日期
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // 打印通话附件位置
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } 
                        // 处理普通文本便签
                        else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            
            // 打印便签之间的分隔线
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 导出所有便签为文本文件
         * 这是主要的导出入口方法
         * @return 操作状态码
         */
        public int exportToText() {
            // 检查SD卡是否可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 获取输出打印流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }
            
            // 第一步：导出文件夹及其中的便签
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 打印文件夹名称
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // 第二步：导出根目录下的便签（不属于任何文件夹的便签）
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        // 打印便签日期
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 打印便签内容
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * 获取指向导出文件的打印流
         * 在SD卡上创建导出文件并返回PrintStream
         * @return PrintStream对象，失败返回null
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            PrintStream ps = null;
            try {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    ps = new PrintStream(fos);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在SD卡上生成文本文件用于存储导出的数据
     * @param context 上下文对象
     * @param filePathResId 文件路径资源ID
     * @param fileNameFormatResId 文件名格式资源ID
     * @return 创建的文件对象，失败返回null
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            // 创建目录（如果不存在）
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            // 创建文件（如果不存在）
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}