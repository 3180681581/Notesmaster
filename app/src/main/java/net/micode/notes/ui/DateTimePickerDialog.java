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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/*
 * 作用：日期时间选择对话框，封装 DateTimePicker 并回传用户确认结果。
 * 实现方法：在构造函数中初始化 DateTimePicker、按钮与标题；
 * 通过 OnDateTimeChangedListener 实时同步 mDate 并刷新标题；
 * 在 onClick 中调用 OnDateTimeSetListener 将最终毫秒值回传外部。
 * 逻辑示意：DateTimePickerDialog(context, date) -> mDateTimePicker.setOnDateTimeChangedListener(...)
 * -> updateTitle(date) -> setOnDateTimeSetListener(callback) -> onClick(dialog, which)
 * -> OnDateTimeSetListener.OnDateTimeSet(dialog, date)
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    /*
     * 作用：保存当前对话框选择的日期时间。
     * 实现方法：在构造函数初始化，并在日期变化监听中持续同步。
     */
    private Calendar mDate = Calendar.getInstance();
    /*
     * 作用：记录当前标题显示是否按 24 小时制格式化。
     * 实现方法：由 set24HourView 设置，供 updateTitle 拼接格式标志。
     */
    private boolean mIs24HourView;
    /*
     * 作用：保存确定按钮回调。
     * 实现方法：由 setOnDateTimeSetListener 注入，在 onClick 中触发。
     */
    private OnDateTimeSetListener mOnDateTimeSetListener;
    /*
     * 作用：嵌入式日期时间选择控件。
     * 实现方法：在构造函数创建并作为对话框内容视图。
     */
    private DateTimePicker mDateTimePicker;

    /*
     * 作用：定义对话框确认后的时间回调接口。
     * 实现方法：外部实现 OnDateTimeSet 以接收最终时间戳。
     */
    public interface OnDateTimeSetListener {
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /*
     * 作用：初始化日期时间选择对话框。
     * 实现方法：创建并绑定 DateTimePicker，设置确认/取消按钮，初始化 24 小时制与标题。
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            /*
             * 作用：响应内部选择器时间变化。
             * 实现方法：将分量写回 mDate 并调用 updateTitle 实时刷新对话框标题。
             */
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis());
            }
        });
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        updateTitle(mDate.getTimeInMillis());
    }

    /*
     * 作用：设置标题格式所用的 24 小时制标志。
     * 实现方法：更新 mIs24HourView 供 updateTitle 使用。
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /*
     * 作用：设置对话框确认回调。
     * 实现方法：保存外部监听器，供 onClick 调用。
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /*
     * 作用：刷新对话框标题显示。
     * 实现方法：组合日期时间格式标志并使用 DateUtils.formatDateTime 生成标题文本。
     */
    private void updateTitle(long date) {
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /*
     * 作用：处理对话框按钮点击事件。
     * 实现方法：确认监听器存在时回调当前 mDate 时间戳。
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}