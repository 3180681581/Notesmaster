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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/*
 * 作用：封装一个带下拉菜单的按钮控件。
 * 实现方法：通过 Button 结合 PopupMenu 提供标题显示、菜单展开和菜单项点击能力。
 */
public class DropdownMenu {
    private Button mButton;
    private PopupMenu mPopupMenu;
    private Menu mMenu;

    /*
     * 作用：初始化下拉菜单组件。
     * 实现方法：为按钮设置下拉图标，创建 PopupMenu，加载菜单资源，并绑定按钮点击事件以显示菜单。
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            /*
             * 作用：响应按钮点击以弹出下拉菜单。
             * 实现方法：在点击回调中直接调用 PopupMenu.show()。
             */
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /*
     * 作用：设置下拉菜单项点击监听器。
     * 实现方法：判断 PopupMenu 是否存在后，将外部监听器注册到 PopupMenu。
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /*
     * 作用：按菜单项 id 查找对应的菜单项。
     * 实现方法：直接委托给内部 Menu 对象进行查找。
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /*
     * 作用：设置下拉按钮显示的标题。
     * 实现方法：将传入标题写入按钮文本。
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
