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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;


/*
 * 作用：便签列表单行视图组件，负责根据条目类型渲染图标、标题、时间和背景样式。
 * 实现方法：在构造函数中绑定子控件；通过 bind 处理多选态与不同业务类型展示；
 * 通过 setBackground 根据便签位置关系选择不同背景资源；通过 getItemData 暴露当前绑定数据。
 * 逻辑示意：NotesListItem(context) -> bind(context, data, choiceMode, checked)
 * -> setBackground(data) -> getItemData().
 */
public class NotesListItem extends LinearLayout {
    /*
     * 作用：提醒/类型图标控件。
     * 实现方法：在 bind 中按条目类型设置显示状态与图标资源。
     */
    private ImageView mAlert;
    /*
     * 作用：主标题文本控件。
     * 实现方法：在 bind 中显示文件夹标题或便签摘要。
     */
    private TextView mTitle;
    /*
     * 作用：时间文本控件。
     * 实现方法：在 bind 中显示相对修改时间。
     */
    private TextView mTime;
    /*
     * 作用：通话名称文本控件。
     * 实现方法：通话记录子项时显示联系人名称，其余场景隐藏。
     */
    private TextView mCallName;
    /*
     * 作用：保存当前绑定的数据对象。
     * 实现方法：在 bind 时写入，供 getItemData 返回。
     */
    private NoteItemData mItemData;
    /*
     * 作用：多选模式勾选框。
     * 实现方法：仅在多选且条目为便签类型时显示并同步勾选状态。
     */
    private CheckBox mCheckBox;

    /*
     * 作用：初始化列表项视图与子控件引用。
     * 实现方法：加载 note_item 布局并通过 findViewById 绑定各控件。
     */
    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /*
     * 作用：绑定条目数据并刷新界面展示。
     * 实现方法：先处理多选勾选框，再按“通话记录文件夹/通话记录子项/普通项”分支渲染标题、图标与可见性，最后设置时间与背景。
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // 作用：控制多选模式下勾选框显示。
        // 实现方法：仅对便签条目显示勾选框并同步 checked 状态。
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;
        // 作用：渲染“通话记录文件夹”特殊样式。
        // 实现方法：隐藏联系人名称、展示专属图标与文件夹计数文案。
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);
        // 作用：渲染“通话记录子项”样式。
        // 实现方法：显示联系人名称，主标题展示摘要，并按提醒状态显示时钟图标。
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        } else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 作用：区分普通文件夹与普通便签渲染。
            // 实现方法：文件夹展示数量文案；便签展示摘要并按提醒状态显示图标。
            if (data.getType() == Notes.TYPE_FOLDER) {
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                                data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        setBackground(data);
    }

    /*
     * 作用：根据条目类型与位置关系设置背景资源。
     * 实现方法：便签按 single/first/normal/last 等状态选择不同背景；文件夹统一使用文件夹背景。
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /*
     * 作用：返回当前列表项绑定的数据对象。
     * 实现方法：直接返回 mItemData。
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}
