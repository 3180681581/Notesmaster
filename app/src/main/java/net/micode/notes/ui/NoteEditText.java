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
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/*
 * 作用：便签编辑输入框组件，扩展键盘事件与上下文菜单能力
 * 实现方法：通过 onTouchEvent 精确定位光标；通过 onKeyDown/onKeyUp 处理回车拆分与退格删除；
 * 通过 onFocusChanged 通知文本状态；通过 onCreateContextMenu 为链接生成快捷操作菜单
 * 逻辑示意：onTouchEvent(event) -> Selection.setSelection(text, off) -> onKeyDown(keyCode, event)
 * -> onKeyUp(keyCode, event) -> OnTextViewChangeListener.onEditTextDelete/onEditTextEnter
 * -> onFocusChanged(focused, direction, rect) -> onCreateContextMenu(menu)
 */
public class NoteEditText extends EditText {
    /*
     * 作用：日志标签
     * 实现方法：在监听器未设置等分支中用于输出调试日志
     */
    private static final String TAG = "NoteEditText";
    /*
     * 作用：记录当前编辑框在列表中的位置
     * 实现方法：由 setIndex 赋值，供删除/新增回调定位条目
     */
    private int mIndex;
    /*
     * 作用：记录退格前的光标起始位置
     * 实现方法：在 onKeyDown(KEYCODE_DEL) 时缓存，用于 onKeyUp 判断是否触发整项删除
     */
    private int mSelectionStartBeforeDelete;

    /*
     * 作用：电话链接协议前缀
     * 实现方法：在上下文菜单生成时用于匹配 URLSpan 类型
     */
    private static final String SCHEME_TEL = "tel:" ;
    /*
     * 作用：网页链接协议前缀
     * 实现方法：在上下文菜单生成时用于匹配 URLSpan 类型
     */
    private static final String SCHEME_HTTP = "http:" ;
    /*
     * 作用：邮件链接协议前缀
     * 实现方法：在上下文菜单生成时用于匹配 URLSpan 类型
     */
    private static final String SCHEME_EMAIL = "mailto:" ;

    /*
     * 作用：协议与菜单文案资源映射表
     * 实现方法：在静态代码块中初始化，供链接菜单标题动态选择
     */
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    /*
     * 作用：初始化协议映射配置
     * 实现方法：将 tel/http/mailto 分别映射到对应字符串资源 id
     */
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /*
     * 作用：定义编辑框与外部页面之间的文本变化回调协议
     * 实现方法：由 NoteEditActivity 实现该接口，接收删除、新增和文本状态变化通知
     */
    public interface OnTextViewChangeListener {
        /*
         * 作用：在退格触发整项删除时回调外部
         * 实现方法：onKeyUp(KEYCODE_DEL) 满足光标在首位且非首项时调用
         */
        void onEditTextDelete(int index, String text);

        /*
         * 作用：在回车触发换行拆分时回调外部新增输入框
         * 实现方法：onKeyUp(KEYCODE_ENTER) 拆分当前文本后调用
         */
        void onEditTextEnter(int index, String text);

        /*
         * 作用：通知外部当前输入框是否含有有效文本
         * 实现方法：在焦点变化时根据文本是否为空调用
         */
        void onTextChange(int index, boolean hasText);
    }

    /*
     * 作用：保存外部回调监听器引用
     * 实现方法：通过 setOnTextViewChangeListener 注入并在键盘/焦点事件中触发
     */
    private OnTextViewChangeListener mOnTextViewChangeListener;

    /*
     * 作用：通过最简构造初始化编辑框
     * 实现方法：调用父类构造并初始化索引为 0
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /*
     * 作用：设置当前编辑框索引
     * 实现方法：将传入索引写入 mIndex
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /*
     * 作用：设置文本变化回调监听器
     * 实现方法：保存外部 listener 供键盘与焦点事件回调使用
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /*
     * 作用：支持 XML 属性构造初始化
     * 实现方法：调用父类 EditText 标准样式构造
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    /*
     * 作用：支持完整参数构造初始化
     * 实现方法：直接委托父类构造完成样式与属性应用
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    @Override
    /*
     * 作用：处理触摸事件并修正光标定位
     * 实现方法：在 ACTION_DOWN 时换算点击坐标到文本布局坐标，再通过 Selection 设置光标
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:

                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                Selection.setSelection(getText(), off);
                break;
        }

        return super.onTouchEvent(event);
    }

    @Override
    /*
     * 作用：处理按键按下阶段的预处理
     * 实现方法：回车时交由后续逻辑处理；退格时缓存删除前光标位置
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    /*
     * 作用：处理按键抬起阶段的核心编辑逻辑
     * 实现方法：退格触发首位删除回调；回车拆分文本并触发新增回调；未设置监听器时输出日志
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart();
                    String text = getText().subSequence(selectionStart, length()).toString();
                    setText(getText().subSequence(0, selectionStart));
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    /*
     * 作用：在焦点变化时同步文本存在状态
     * 实现方法：失焦且文本为空通知 false，其余情况通知 true
     */
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    /*
     * 作用：为选中链接动态创建上下文菜单项
     * 实现方法：识别选中区间 URLSpan 类型，按协议匹配菜单文案并注册点击后跳转动作
     */
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            /*
                             * 作用：响应链接菜单点击并执行跳转
                             * 实现方法：直接调用 URLSpan.onClick 使用系统 intent 打开目标链接
                             */
                            public boolean onMenuItemClick(MenuItem item) {
                                // goto a new intent
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}
