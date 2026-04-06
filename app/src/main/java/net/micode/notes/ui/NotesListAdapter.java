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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


/*
 * 作用：便签列表数据适配器，负责将 Cursor 数据绑定到列表项并管理多选状态。
 * 实现方法：通过 newView/bindView 构建与绑定 NotesListItem；
 * 使用 setChoiceMode、setCheckedItem、selectAll、isSelectedItem、getSelectedCount、isAllSelected 管理选择态；
 * 使用 getSelectedItemIds/getSelectedWidget 提供批处理所需数据，并在 onContentChanged/changeCursor/calcNotesCount 中维护便签统计。
 * 逻辑示意：newView(context, cursor, parent) -> bindView(view, context, cursor) -> setChoiceMode(mode)
 * -> setCheckedItem(position, checked)/selectAll(checked) -> getSelectedItemIds()/getSelectedWidget()
 * -> onContentChanged()/changeCursor(cursor) -> calcNotesCount()
 */
public class NotesListAdapter extends CursorAdapter {
    /*
     * 作用：日志输出标签。
     * 实现方法：在异常分支中统一作为 Log 的 tag。
     */
    private static final String TAG = "NotesListAdapter";
    /*
     * 作用：保存上下文引用。
     * 实现方法：在构造函数中赋值，用于创建 NoteItemData 等依赖 Context 的对象。
     */
    private Context mContext;
    /*
     * 作用：记录列表位置与勾选状态映射。
     * 实现方法：以 position 为 key、是否选中为 value 维护多选数据。
     */
    private HashMap<Integer, Boolean> mSelectedIndex;
    /*
     * 作用：记录当前列表中“便签类型”条目的数量。
     * 实现方法：在游标变化后由 calcNotesCount 重新统计。
     */
    private int mNotesCount;
    /*
     * 作用：标记是否处于多选模式。
     * 实现方法：由 setChoiceMode 切换，供 bindView 和交互逻辑判断。
     */
    private boolean mChoiceMode;

    /*
     * 作用：承载小组件刷新所需字段。
     * 实现方法：在 getSelectedWidget 中填充 widgetId/widgetType 并返回集合。
     */
    public static class AppWidgetAttribute {
        /*
         * 作用：小组件实例 id。
         * 实现方法：从便签条目数据读取并写入。
         */
        public int widgetId;
        /*
         * 作用：小组件类型（如 2x/4x）。
         * 实现方法：从便签条目数据读取并写入。
         */
        public int widgetType;
    };

    /*
     * 作用：初始化便签列表适配器。
     * 实现方法：调用父类构造并初始化选择映射、上下文引用与便签计数。
     */
    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    @Override
    /*
     * 作用：创建列表项视图实例。
     * 实现方法：返回自定义 NotesListItem 作为每行展示容器。
     */
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    @Override
    /*
     * 作用：将当前 Cursor 行数据绑定到列表项。
     * 实现方法：构造 NoteItemData 后调用 NotesListItem.bind，同时传入多选模式与选中状态。
     */
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /*
     * 作用：设置指定位置条目的勾选状态。
     * 实现方法：更新 mSelectedIndex 映射并通知适配器刷新界面。
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    /*
     * 作用：判断当前是否处于多选模式。
     * 实现方法：直接返回 mChoiceMode 状态值。
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /*
     * 作用：切换多选模式状态。
     * 实现方法：先清空历史勾选映射，再写入新的模式标志。
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /*
     * 作用：批量设置全部便签条目的勾选状态。
     * 实现方法：遍历游标位置，仅对 TYPE_NOTE 条目调用 setCheckedItem。
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /*
     * 作用：获取当前选中条目的数据库 id 集合。
     * 实现方法：遍历已选映射，读取 getItemId(position) 并过滤根目录 id。
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }

        return itemSet;
    }

    /*
     * 作用：获取选中条目对应的小组件属性集合。
     * 实现方法：遍历选中位置，读取 Cursor 构造 NoteItemData，提取 widgetId/widgetType 后封装返回。
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /*
                     * 作用：强调游标生命周期由适配器统一管理。
                     * 实现方法：此处仅消费游标数据，不手动关闭，避免破坏适配器后续访问。
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /*
     * 作用：统计当前被选中的条目数量。
     * 实现方法：遍历 mSelectedIndex 的 value 集合并累计 true 数量。
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    /*
     * 作用：判断便签条目是否已全选。
     * 实现方法：比较选中数量与 mNotesCount，且要求选中数不为 0。
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    /*
     * 作用：判断指定位置条目是否选中。
     * 实现方法：先判空映射值，不为空时返回对应布尔值。
     */
    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    @Override
    /*
     * 作用：响应数据源内容变化。
     * 实现方法：调用父类处理后，重新计算可选便签数量。
     */
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    @Override
    /*
     * 作用：切换适配器使用的游标对象。
     * 实现方法：调用父类替换游标后，触发便签数量重算。
     */
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /*
     * 作用：计算当前列表中的便签条目总数。
     * 实现方法：遍历全部条目，按 NoteItemData.getNoteType(c) 统计 TYPE_NOTE 数量。
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}
