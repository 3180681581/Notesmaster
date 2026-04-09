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
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析器
 * 管理便签应用中各种资源的ID映射，包括：
 * - 便签背景颜色资源
 * - 文字大小样式资源
 * - 小部件背景资源
 * - 列表项背景资源
 */
public class ResourceParser {

    // ==================== 背景颜色常量 ====================
    public static final int YELLOW           = 0;  // 黄色
    public static final int BLUE             = 1;  // 蓝色
    public static final int WHITE            = 2;  // 白色
    public static final int GREEN            = 3;  // 绿色
    public static final int RED              = 4;  // 红色

    /** 默认背景颜色 */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // ==================== 文字大小常量 ====================
    public static final int TEXT_SMALL       = 0;  // 小号字体
    public static final int TEXT_MEDIUM      = 1;  // 中号字体
    public static final int TEXT_LARGE       = 2;  // 大号字体
    public static final int TEXT_SUPER       = 3;  // 超大号字体

    /** 默认文字大小 */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 便签背景资源管理类
     * 管理编辑界面中便签的背景图片资源
     */
    public static class NoteBgResources {
        /** 便签编辑界面背景资源数组 - 索引对应颜色常量 */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_yellow,  // 黄色背景
            R.drawable.edit_blue,    // 蓝色背景
            R.drawable.edit_white,   // 白色背景
            R.drawable.edit_green,   // 绿色背景
            R.drawable.edit_red      // 红色背景
        };

        /** 便签标题栏背景资源数组 */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_title_yellow,  // 黄色标题背景
            R.drawable.edit_title_blue,    // 蓝色标题背景
            R.drawable.edit_title_white,   // 白色标题背景
            R.drawable.edit_title_green,   // 绿色标题背景
            R.drawable.edit_title_red      // 红色标题背景
        };

        /**
         * 获取便签背景资源ID
         * @param id 颜色ID（YELLOW/BLUE/WHITE/GREEN/RED）
         * @return 对应的背景资源ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 获取便签标题背景资源ID
         * @param id 颜色ID
         * @return 对应的标题背景资源ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色ID
     * 根据用户设置决定是否使用随机颜色
     * @param context 上下文对象
     * @return 背景颜色ID
     */
    public static int getDefaultBgId(Context context) {
        // 检查用户是否开启了随机背景色
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 随机返回一个颜色
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 便签列表项背景资源管理类
     * 管理列表中便签条目的背景图片资源
     * 支持不同位置（第一个、中间、最后一个、单独一个）的不同样式
     */
    public static class NoteItemBgResources {
        /** 列表第一项的背景资源 */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.list_yellow_up,   // 黄色-顶部
            R.drawable.list_blue_up,     // 蓝色-顶部
            R.drawable.list_white_up,    // 白色-顶部
            R.drawable.list_green_up,    // 绿色-顶部
            R.drawable.list_red_up       // 红色-顶部
        };

        /** 列表中间项的背景资源 */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.list_yellow_middle,  // 黄色-中间
            R.drawable.list_blue_middle,    // 蓝色-中间
            R.drawable.list_white_middle,   // 白色-中间
            R.drawable.list_green_middle,   // 绿色-中间
            R.drawable.list_red_middle      // 红色-中间
        };

        /** 列表最后一项的背景资源 */
        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.list_yellow_down,  // 黄色-底部
            R.drawable.list_blue_down,    // 蓝色-底部
            R.drawable.list_white_down,   // 白色-底部
            R.drawable.list_green_down,   // 绿色-底部
            R.drawable.list_red_down,     // 红色-底部
        };

        /** 列表中只有单独一项时的背景资源 */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.list_yellow_single,  // 黄色-单独
            R.drawable.list_blue_single,    // 蓝色-单独
            R.drawable.list_white_single,   // 白色-单独
            R.drawable.list_green_single,   // 绿色-单独
            R.drawable.list_red_single      // 红色-单独
        };

        /**
         * 获取列表第一项的背景资源
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        /**
         * 获取列表最后一项的背景资源
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        /**
         * 获取单独一项的背景资源
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        /**
         * 获取列表中间项的背景资源
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取文件夹列表项的背景资源
         * @return 文件夹背景资源ID
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 小部件背景资源管理类
     * 管理桌面小部件的背景图片资源
     */
    public static class WidgetBgResources {
        /** 2x2尺寸小部件的背景资源 */
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_2x_yellow,  // 黄色
            R.drawable.widget_2x_blue,    // 蓝色
            R.drawable.widget_2x_white,   // 白色
            R.drawable.widget_2x_green,   // 绿色
            R.drawable.widget_2x_red,     // 红色
        };

        /**
         * 获取2x2小部件背景资源
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /** 4x4尺寸小部件的背景资源 */
        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_4x_yellow,  // 黄色
            R.drawable.widget_4x_blue,    // 蓝色
            R.drawable.widget_4x_white,   // 白色
            R.drawable.widget_4x_green,   // 绿色
            R.drawable.widget_4x_red      // 红色
        };

        /**
         * 获取4x4小部件背景资源
         * @param id 颜色ID
         * @return 背景资源ID
         */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 文字样式资源管理类
     * 管理便签编辑界面的文字大小样式
     */
    public static class TextAppearanceResources {
        /** 文字样式资源数组 */
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal,  // 普通字体
            R.style.TextAppearanceMedium,  // 中号字体
            R.style.TextAppearanceLarge,   // 大号字体
            R.style.TextAppearanceSuper    // 超大字体
        };

        /**
         * 获取文字样式资源ID
         * @param id 文字大小ID
         * @return 样式资源ID
         * 
         * 注意：修复了SharedPreferences中存储资源ID可能超出数组长度的问题
         * 如果id超出范围，返回默认字体大小
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 修复存储资源ID到SharedPreferences的bug
             * 该ID可能大于资源数组长度，此时返回默认字体大小
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取文字样式资源的数量
         * @return 样式数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}