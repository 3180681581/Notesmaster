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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;


/*
 * 作用：为文件夹列表提供基于 Cursor 的数据适配与视图绑定。
 * 实现方法：继承 CursorAdapter，创建条目视图并在绑定阶段将数据库字段映射到界面。
 */
public class FoldersListAdapter extends CursorAdapter {
    /*
     * 作用：定义查询文件夹列表时需要返回的字段集合。
     * 实现方法：通过固定字段数组，约束 Cursor 的列顺序与内容。
     */
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET
    };

    /*
     * 作用：标识文件夹 ID 在 Cursor 中的列索引。
     * 实现方法：使用常量与 PROJECTION 的字段顺序保持一致。
     */
    public static final int ID_COLUMN   = 0;
    /*
     * 作用：标识文件夹名称在 Cursor 中的列索引。
     * 实现方法：使用常量与 PROJECTION 的字段顺序保持一致。
     */
    public static final int NAME_COLUMN = 1;

    /*
     * 作用：初始化文件夹列表适配器。
     * 实现方法：将上下文与初始 Cursor 交给父类 CursorAdapter 进行管理。
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    /*
     * 作用：创建列表中每一行对应的视图对象。
     * 实现方法：返回自定义的 FolderListItem 作为单行布局容器。
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /*
     * 作用：将当前 Cursor 行的数据绑定到条目视图。
     * 实现方法：判断是否为根目录 ID，是则使用“上级目录”文案，否则使用数据库中的文件夹名称。
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            // 作用：统一处理根目录与普通目录名称展示。
            // 实现方法：根据 ID_COLUMN 与根目录常量比较后选择对应文案。
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /*
     * 作用：按列表位置获取用于展示的文件夹名称。
     * 实现方法：读取对应位置的 Cursor，并复用根目录与普通目录的名称选择逻辑。
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    /*
     * 作用：封装单个文件夹条目的视图与数据绑定逻辑。
     * 实现方法：继承 LinearLayout 并持有名称文本控件以更新显示内容。
     */
    private class FolderListItem extends LinearLayout {
        /*
         * 作用：持有文件夹名称显示控件。
         * 实现方法：在构造阶段通过布局中的 ID 查找并缓存 TextView 引用。
         */
        private TextView mName;

        /*
         * 作用：初始化单条文件夹视图结构。
         * 实现方法：加载 folder_list_item 布局并绑定名称文本控件。
         */
        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /*
         * 作用：设置并刷新当前条目的文件夹名称。
         * 实现方法：将传入字符串直接写入名称 TextView。
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
