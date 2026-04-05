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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;


import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/*
 * 作用：日期时间组合选择控件，支持最近一周日期、时分选择及 12/24 小时制切换。
 * 实现方法：通过 NumberPicker 组合实现日期/小时/分钟/上午下午四个维度；
 * 使用 setCurrentDate、setCurrentHour、setCurrentMinute 等方法同步内部 Calendar；
 * 在各 OnValueChangeListener 中处理跨天与 AM/PM 切换，并通过 onDateTimeChanged 回调外部。
 * 逻辑示意：DateTimePicker(context, date, is24HourView) -> updateDateControl()/updateHourControl()/updateAmPmControl()
 * -> mOnDateChangedListener/mOnHourChangedListener/mOnMinuteChangedListener/mOnAmPmChangedListener
 * -> setCurrentYear/month/day/hour/minute -> onDateTimeChanged()
 */
public class DateTimePicker extends FrameLayout {

    private static final boolean DEFAULT_ENABLE_STATE = true;

    private static final int HOURS_IN_HALF_DAY = 12;
    private static final int HOURS_IN_ALL_DAY = 24;
    private static final int DAYS_IN_ALL_WEEK = 7;
    private static final int DATE_SPINNER_MIN_VAL = 0;
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;
    private static final int MINUT_SPINNER_MIN_VAL = 0;
    private static final int MINUT_SPINNER_MAX_VAL = 59;
    private static final int AMPM_SPINNER_MIN_VAL = 0;
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    private final NumberPicker mDateSpinner;
    private final NumberPicker mHourSpinner;
    private final NumberPicker mMinuteSpinner;
    private final NumberPicker mAmPmSpinner;
    private Calendar mDate;

    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    private boolean mIsAm;

    private boolean mIs24HourView;

    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    private boolean mInitialising;

    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    /*
     * 作用：监听日期滚轮变化。
     * 实现方法：按滚轮偏移调整 Calendar 的天数，并刷新日期显示后触发变更回调。
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            updateDateControl();
            onDateTimeChanged();
        }
    };

    /*
     * 作用：监听小时滚轮变化并处理跨天/AMPM翻转。
     * 实现方法：根据 12/24 小时制判断边界值回绕场景，必要时调整日期并更新 AMPM 状态。
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();
            if (!mIs24HourView) {
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                }
            } else {
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /*
     * 作用：监听分钟滚轮变化并处理小时进位/借位。
     * 实现方法：在 59->0 或 0->59 时调整小时与日期显示，并同步 AMPM 状态。
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());
                updateDateControl();
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                    updateAmPmControl();
                } else {
                    mIsAm = true;
                    updateAmPmControl();
                }
            }
            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /*
     * 作用：监听 AM/PM 滚轮变化。
     * 实现方法：切换 mIsAm 并对 Calendar 小时加减 12，随后刷新控件与回调。
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    /*
     * 作用：定义日期时间变化回调接口。
     * 实现方法：对外暴露 onDateTimeChanged，传出年/月/日/时/分完整结果。
     */
    public interface OnDateTimeChangedListener {
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                int dayOfMonth, int hourOfDay, int minute);
    }

    /*
     * 作用：使用当前系统时间初始化控件。
     * 实现方法：委托到带时间戳构造函数。
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /*
     * 作用：使用指定时间戳初始化控件。
     * 实现方法：自动读取系统 24 小时制设置并委托主构造函数。
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /*
     * 作用：执行完整初始化流程。
     * 实现方法：初始化 Calendar 与四个 NumberPicker，绑定监听器，设置显示制式并写入初始时间。
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;
        inflate(context, R.layout.datetime_picker, this);

        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);
        mMinuteSpinner =  (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // update controls to initial state
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        set24HourView(is24HourView);

        // set to current time
        setCurrentDate(date);

        setEnabled(isEnabled());

        // set the content descriptions
        mInitialising = false;
    }

    @Override
    /*
     * 作用：启用或禁用整个日期时间控件。
     * 实现方法：同步父类状态并批量设置各 NumberPicker 的 enabled 状态。
     */
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    /*
     * 作用：获取当前启用状态。
     * 实现方法：返回内部维护的 mIsEnabled。
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /*
     * 作用：获取当前选择时间的毫秒值。
     * 实现方法：直接返回内部 Calendar 的时间戳。
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /*
     * 作用：按毫秒值设置当前日期时间。
     * 实现方法：先转为 Calendar，再委托到分量设置方法。
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /*
     * 作用：按年月日时分设置当前日期时间。
     * 实现方法：顺序调用 setCurrentYear/setCurrentMonth/setCurrentDay/setCurrentHour/setCurrentMinute。
     */
    public void setCurrentDate(int year, int month,
            int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    /*
     * 作用：获取当前年份。
     * 实现方法：从内部 Calendar 读取 YEAR 字段。
     */
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    /*
     * 作用：设置当前年份。
     * 实现方法：去重判断后写入 Calendar，并刷新日期控件与回调。
     */
    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    /*
     * 作用：获取当前月份。
     * 实现方法：从内部 Calendar 读取 MONTH 字段。
     */
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    /*
     * 作用：设置当前月份。
     * 实现方法：去重判断后写入 Calendar，并刷新日期控件与回调。
     */
    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    /*
     * 作用：获取当前日。
     * 实现方法：从内部 Calendar 读取 DAY_OF_MONTH 字段。
     */
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    /*
     * 作用：设置当前日。
     * 实现方法：去重判断后写入 Calendar，并刷新日期控件与回调。
     */
    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    /*
     * 作用：获取 24 小时制下当前小时。
     * 实现方法：从内部 Calendar 读取 HOUR_OF_DAY 字段。
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /*
     * 作用：获取当前用于小时滚轮显示的小时值。
     * 实现方法：24 小时制直接返回；12 小时制按 AM/PM 规则转换为 1~12。
     */
    private int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    /*
     * 作用：设置当前小时。
     * 实现方法：写入 Calendar 后按制式修正 AM/PM 与滚轮值，并触发回调。
     */
    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
        if (!mIs24HourView) {
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    /*
     * 作用：获取当前分钟。
     * 实现方法：从内部 Calendar 读取 MINUTE 字段。
     */
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    /*
     * 作用：设置当前分钟。
     * 实现方法：去重判断后更新分钟滚轮与 Calendar 字段并触发回调。
     */
    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    /*
     * 作用：获取是否为 24 小时制显示。
     * 实现方法：返回 mIs24HourView。
     */
    public boolean is24HourView () {
        return mIs24HourView;
    }

    /*
     * 作用：切换 24 小时制与 AM/PM 显示模式。
     * 实现方法：更新制式标志，调整 AMPM 控件可见性与小时范围，并回写当前小时显示。
     */
    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        int hour = getCurrentHourOfDay();
        updateHourControl();
        setCurrentHour(hour);
        updateAmPmControl();
    }

    /*
     * 作用：刷新日期滚轮显示内容。
     * 实现方法：以当前日期为中心生成前后 7 天展示文案并重置滚轮到中位。
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);
        mDateSpinner.setDisplayedValues(null);
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        mDateSpinner.invalidate();
    }

    /*
     * 作用：刷新 AM/PM 控件状态。
     * 实现方法：24 小时制隐藏控件；12 小时制按 mIsAm 设置值并显示。
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /*
     * 作用：刷新小时滚轮取值范围。
     * 实现方法：根据当前制式设置 24 小时范围(0~23)或 12 小时范围(1~12)。
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /*
     * 作用：设置日期时间变更回调监听器。
     * 实现方法：保存 callback 引用，后续由 onDateTimeChanged 统一触发。
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /*
     * 作用：向外分发日期时间变化事件。
     * 实现方法：监听器非空时回调当前年/月/日/时/分。
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}
