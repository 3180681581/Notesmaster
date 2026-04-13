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

import android.net.Uri;

/**
 * Notes 类是笔记应用的核心数据定义类。
 * 该类定义了笔记应用中所有数据相关的常量、接口和内部类，
 * 包括笔记类型、系统文件夹ID、数据库列名、URI定义等。
 * 它为整个应用提供了统一的数据结构和访问接口。
 */
public class Notes {
    // 内容提供者权限字符串，用于构建URI
    public static final String AUTHORITY = "micode_notes";

    // 日志标签，用于调试输出
    public static final String TAG = "Notes";

    // 笔记类型常量：普通笔记
    public static final int TYPE_NOTE = 0;

    // 笔记类型常量：文件夹
    public static final int TYPE_FOLDER = 1;

    // 笔记类型常量：系统文件夹（不可删除的特殊文件夹）
    public static final int TYPE_SYSTEM = 2;

    /**
     * 以下ID是系统文件夹的标识符
     * {@link Notes#ID_ROOT_FOLDER} 是默认文件夹
     * {@link Notes#ID_TEMPARAY_FOLDER} 用于没有文件夹的笔记
     * {@link Notes#ID_CALL_RECORD_FOLDER} 用于存储通话记录
     * (Following IDs are system folders' identifiers)
     */
    // 根文件夹ID，所有笔记的默认父文件夹
    public static final int ID_ROOT_FOLDER = 0;

    // 临时文件夹ID，用于移动笔记时的中间存储
    public static final int ID_TEMPARAY_FOLDER = -1;

    // 通话记录文件夹ID，专门存储通话相关的笔记
    public static final int ID_CALL_RECORD_FOLDER = -2;

    // 回收站文件夹ID，用于存放已删除的笔记
    public static final int ID_TRASH_FOLER = -3;

    // Intent额外参数：提醒日期，用于传递笔记的提醒时间
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";

    // Intent额外参数：背景颜色ID，用于传递笔记的背景颜色
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";

    // Intent额外参数：小部件ID，用于传递笔记小部件的ID
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";

    // Intent额外参数：小部件类型，用于传递笔记小部件的类型
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";

    // Intent额外参数：文件夹ID，用于传递目标文件夹的ID
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";

    // Intent额外参数：通话日期，用于传递通话记录的日期
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 小部件类型：无效的小部件
    public static final int TYPE_WIDGET_INVALIDE = -1;

    // 小部件类型：2x2的小部件
    public static final int TYPE_WIDGET_2X = 0;

    // 小部件类型：4x4的小部件
    public static final int TYPE_WIDGET_4X = 1;

    /**
     * DataConstants 类定义了数据类型的常量。
     * 用于标识不同类型的数据项，如普通笔记、通话笔记等。
     */
    public static class DataConstants {
        // 普通笔记数据类型
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;

        // 通话笔记数据类型
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
        // 图片附件在 data 表中的 MIME 标识
        public static final String IMAGE_NOTE = ImageNote.CONTENT_ITEM_TYPE;
    }

    /**
     * 用于查询所有笔记和文件夹的URI
     * (Uri to query all notes and folders)
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 用于查询数据的URI
     * (Uri to query data)
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * NoteColumns 接口定义了笔记表的所有列名常量。
     * 该接口提供了笔记和文件夹数据结构的标准字段定义，
     * 包括ID、时间戳、内容、类型等各种属性。
     * 所有字段都有明确的类型说明和用途描述。
     */
    public interface NoteColumns {
        /**
         * 行的唯一ID
         * <P> 类型: INTEGER (long) </P>
         * (The unique ID for a row)
         */
        public static final String ID = "_id";

        /**
         * 笔记或文件夹的父ID
         * <P> 类型: INTEGER (long) </P>
         * (The parent's id for note or folder)
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 笔记或文件夹的创建日期
         * <P> 类型: INTEGER (long) </P>
         * (Created data for note or folder)
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最新修改日期
         * <P> 类型: INTEGER (long) </P>
         * (Latest modified date)
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 提醒日期
         * <P> 类型: INTEGER (long) </P>
         * (Alert date)
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 文件夹名称或笔记的文本内容
         * <P> 类型: TEXT </P>
         * (Folder's name or text content of note)
         */
        public static final String SNIPPET = "snippet";

        /**
         * 笔记的小部件ID
         * <P> 类型: INTEGER (long) </P>
         * (Note's widget id)
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 笔记的小部件类型
         * <P> 类型: INTEGER (long) </P>
         * (Note's widget type)
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 笔记背景颜色的ID
         * <P> 类型: INTEGER (long) </P>
         * (Note's background color's id)
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 对于文本笔记，没有附件；对于多媒体笔记，至少有一个附件
         * <P> 类型: INTEGER </P>
         * (For text note, it doesn't has attachment, for multi-media note, it has at least one attachment)
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 文件夹中的笔记数量
         * <P> 类型: INTEGER (long) </P>
         * (Folder's count of notes)
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 文件类型：文件夹或笔记
         * <P> 类型: INTEGER </P>
         * (The file type: folder or note)
         */
        public static final String TYPE = "type";

        /**
         * 最后同步ID
         * <P> 类型: INTEGER (long) </P>
         * (The last sync id)
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 标识是否本地修改
         * <P> 类型: INTEGER </P>
         * (Sign to indicate local modified or not)
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移动到临时文件夹前的原始父ID
         * <P> 类型: INTEGER </P>
         * (Original parent id before moving into temporary folder)
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * Google Tasks ID
         * <P> 类型: TEXT </P>
         * (The gtask id)
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 版本代码
         * <P> 类型: INTEGER (long) </P>
         * (The version code)
         */
        public static final String VERSION = "version";
    }

    /**
     * DataColumns 接口定义了数据表的所有列名常量。
     * 该接口提供了笔记附件数据的标准字段定义，
     * 包括笔记ID、数据类型、内容、摘要等各种属性。
     * 用于存储笔记的各种附件数据，如图片、录音等。
     */
    public interface DataColumns {
        /**
         * 行的唯一ID
         * <P> 类型: INTEGER (long) </P>
         * (The unique ID for a row)
         */
        public static final String ID = "_id";

        /**
         * MIME类型，表示该行所代表项目的MIME类型
         * <P> 类型: TEXT </P>
         * (The MIME type of the item represented by this row)
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 笔记ID，表示该数据属于哪个笔记
         * <P> 类型: INTEGER (long) </P>
         * (The reference id to note that this data belongs to)
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 创建日期
         * <P> 类型: INTEGER (long) </P>
         * (Created data for note or folder)
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最新修改日期
         * <P> 类型: INTEGER (long) </P>
         * (Latest modified date)
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 数据内容
         * <P> 类型: TEXT </P>
         * (Data's content)
         */
        public static final String CONTENT = "content";

        /**
         * 通用数据列，含义由MIME类型决定，用于整数数据类型
         * <P> 类型: INTEGER </P>
         * (Generic data column, the meaning is MIMETYPE specific, used for integer data type)
         */
        public static final String DATA1 = "data1";

        /**
         * 通用数据列，含义由MIME类型决定，用于整数数据类型
         * <P> 类型: INTEGER </P>
         * (Generic data column, the meaning is MIMETYPE specific, used for integer data type)
         */
        public static final String DATA2 = "data2";

        /**
         * 通用数据列，含义由MIME类型决定，用于TEXT数据类型
         * <P> 类型: TEXT </P>
         * (Generic data column, the meaning is MIMETYPE specific, used for TEXT data type)
         */
        public static final String DATA3 = "data3";

        /**
         * 通用数据列，含义由MIME类型决定，用于TEXT数据类型
         * <P> 类型: TEXT </P>
         * (Generic data column, the meaning is MIMETYPE specific, used for TEXT data type)
         */
        public static final String DATA4 = "data4";

        /**
         * 通用数据列，含义由MIME类型决定，用于TEXT数据类型
         * <P> 类型: TEXT </P>
         * (Generic data column, the meaning is MIMETYPE specific, used for TEXT data type)
         */
        public static final String DATA5 = "data5";
    }

    /**
     * TextNote 类定义了文本笔记的数据结构和常量。
     * 该类继承了DataColumns接口，提供了文本笔记特有的字段定义，
     * 包括检查列表模式、内容类型和URI等。
     * 用于处理纯文本笔记的数据操作。
     */
    public static final class TextNote implements DataColumns {
        /**
         * 模式指示符，用于表示文本是否在检查列表模式
         * <P> 类型: INTEGER 1:检查列表模式 0:普通模式 </P>
         * (Mode to indicate the text in check list mode or not)
         */
        public static final String MODE = DATA1;

        /**
         * 检查列表模式的常量值
         * (Constant value for check list mode)
         */
        public static final int MODE_CHECK_LIST = 1;

        /**
         * 文本笔记的内容类型，用于目录查询
         * (Content type for text note directory)
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        /**
         * 文本笔记的单个项目内容类型
         * (Content type for single text note item)
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        /**
         * 文本笔记的URI，用于查询文本笔记数据
         * (URI for querying text note data)
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * CallNote 类定义了通话记录笔记的数据结构和常量。
     * 该类继承了DataColumns接口，提供了通话记录笔记特有的字段定义，
     * 包括通话日期、电话号码、内容类型和URI等。
     * 用于处理通话记录类型的笔记数据操作。
     */
    public static final class CallNote implements DataColumns {
        /**
         * 该记录的通话日期
         * <P> 类型: INTEGER (long) </P>
         * (Call date for this record)
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 该记录的电话号码
         * <P> 类型: TEXT </P>
         * (Phone number for this record)
         */
        public static final String PHONE_NUMBER = DATA3;

        /**
         * 通话记录笔记的内容类型，用于目录查询
         * (Content type for call note directory)
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        /**
         * 通话记录笔记的单个项目内容类型
         * (Content type for single call note item)
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        /**
         * 通话记录笔记的URI，用于查询通话记录笔记数据
         * (URI for querying call note data)
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }

    public static final class ImageNote implements DataColumns {
        // 仅用于标识“图片附件”数据行，不直接暴露独立 Provider 路由
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image_note";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/image_note";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/image_note");
    }
}
