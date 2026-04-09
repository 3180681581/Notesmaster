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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/*
 * 作用：
 * 便签列表主页面，负责列表展示和大部分入口交互。
 * 实现方法：
 * 由 onCreate/initResources 初始化列表与交互组件，onStart/startAsyncNotesListQuery 驱动异步加载；
 * 通过 onClick、onItemLongClick、onOptionsItemSelected、onContextItemSelected 分发用户操作，
 * 并由 openNode/openFolder、batchDelete/deleteFolder、exportNoteToText、startQueryDestinationFolders 等方法执行具体业务，
 * 采用 mState 与 mCurrentFolderId 的状态驱动逻辑决定界面与数据行为。
 * 逻辑示意：onCreate(savedInstanceState) -> initResources() -> onStart() -> startAsyncNotesListQuery()
 * -> BackgroundQueryHandler.onQueryComplete(token, cookie, cursor) -> 列表交互分发(onClick/onItemLongClick/onOptionsItemSelected)
 * -> openNode(data)/openFolder(data)/batchDelete()/deleteFolder(folderId)/exportNoteToText()
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    // 异步查询 token：查询“当前文件夹下便签列表”
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;

    // 异步查询 token：查询“可移动到的目标文件夹列表”
    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;

    // 文件夹长按菜单 id：删除文件夹
    private static final int MENU_FOLDER_DELETE = 0;

    // 文件夹长按菜单 id：查看/进入文件夹
    private static final int MENU_FOLDER_VIEW = 1;

    // 文件夹长按菜单 id：重命名文件夹
    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    // SharedPreferences 键：是否已经插入过“首次使用介绍”便签
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    // 当前列表界面所处状态
    private enum ListEditState {
        // 根目录便签列表
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    };

    // 当前页面状态（根目录/子文件夹/通话记录文件夹）
    private ListEditState mState;

    // 异步数据库查询处理器
    private BackgroundQueryHandler mBackgroundQueryHandler;

    // 列表适配器：把数据库数据绑定到 ListView
    private NotesListAdapter mNotesListAdapter;

    // 列表控件
    private ListView mNotesListView;

    // “新建便签”按钮
    private Button mAddNewNote;

    // 是否正在把按钮触摸事件转发给下层 ListView
    private boolean mDispatch;

    // 触摸事件初始 y 坐标（用于计算移动量）
    private int mOriginY;

    // 转发给 ListView 的 y 坐标
    private int mDispatchY;

    // 顶部标题栏（进入子文件夹时显示）
    private TextView mTitleBar;

    // 当前查看的文件夹 id
    private long mCurrentFolderId;

    // 内容解析器：通过 ContentProvider 读写数据
    private ContentResolver mContentResolver;

    // 多选模式回调（ActionMode）
    private ModeCallback mModeCallBack;

    // 日志 tag
    private static final String TAG = "NotesListActivity";

    // 列表滚动速率常量（历史代码保留）
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;

    // 当前长按聚焦的数据项（便签或文件夹）
    private NoteItemData mFocusNoteDataItem;

    // 普通查询条件：父文件夹等于当前文件夹
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";

    // 根目录查询条件：
    // 1) 非系统项且父目录为根目录
    // 2) 或者“通话记录”文件夹且有内容
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    // startActivityForResult 请求码：打开已有便签
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    // startActivityForResult 请求码：新建便签
    private final static int REQUEST_CODE_NEW_NODE  = 103;

    @Override
    /*
        * 作用：
        * Activity 创建入口，完成页面初始搭建。
        * 实现方法：
        * 设置布局后调用 initResources 初始化控件与监听，再调用 setAppInfoFromRawRes 处理首次介绍便签。
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();

        /*
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes();
    }

    @Override
    /*
        * 作用：
        * 处理从编辑页返回后的刷新逻辑。
        * 实现方法：
        * 判断 requestCode/resultCode，只有便签打开或新建成功时才刷新列表游标。
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 只有编辑页“保存成功返回”时才刷新列表；其他情况保持默认处理
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /*
        * 作用：
        * 首次启动时插入“应用介绍”便签。
        * 实现方法：
        * 读取 raw/introduction 文本，创建 WorkingNote 并保存，最后用 SharedPreferences 记录已完成标记。
     */
    private void setAppInfoFromRawRes() {
        // 默认配置存储，用来记录“是否已经插入过介绍便签”
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        // 如果已经插入过介绍便签，则直接跳过
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            // 拼接读取到的介绍文本
            StringBuilder sb = new StringBuilder();
            // 原始资源输入流
            InputStream in = null;
            try {
                // 打开 raw/introduction 文件
                in = getResources().openRawResource(R.raw.introduction);
                // 读取成功才继续处理；否则直接报错返回
                if (in != null) {
                    // 字符流读取器
                    InputStreamReader isr = new InputStreamReader(in);
                    // 带缓冲的读取器，提升读取效率
                    BufferedReader br = new BufferedReader(isr);
                    // 临时缓冲区
                    char [] buf = new char[1024];
                    // 每次实际读取长度
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

                // 创建一条空白工作便签，再写入介绍文本
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            // 介绍便签保存成功后，记录“已插入”标记
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    @Override
    /*
        * 作用：
        * 页面进入可见状态时刷新列表。
        * 实现方法：
        * 在 onStart 中调用 startAsyncNotesListQuery 发起异步查询。
     */
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }

    /*
        * 作用：
        * 初始化页面依赖对象和交互监听。
        * 实现方法：
        * 初始化 ContentResolver、QueryHandler、ListView、Adapter、按钮监听和状态变量。
     */
    private void initResources() {
        // 数据访问入口
        mContentResolver = this.getContentResolver();
        // 异步查询器，避免主线程阻塞
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        // 默认进入根目录
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        // 列表控件
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        // 添加底部占位/装饰视图
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        // 点击与长按监听
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        // 适配器绑定
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        // 新建按钮及监听
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        // 触摸转发状态初始化
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        // 顶部标题栏
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        // 初始状态为根目录列表
        mState = ListEditState.NOTE_LIST;
        // 多选模式控制器
        mModeCallBack = new ModeCallback();
    }

    /*
        * 作用：
        * 封装列表多选模式（ActionMode）的行为。
        * 实现方法：
        * 在回调中管理菜单初始化、勾选状态、删除/移动动作以及多选模式进入退出。
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        // 顶部下拉菜单（显示已选数量、全选/取消全选）
        private DropdownMenu mDropDownMenu;
        // 当前 ActionMode 实例
        private ActionMode mActionMode;
        // “移动到文件夹”菜单项
        private MenuItem mMoveMenu;

        /*
         * 作用：
         * 进入多选模式时初始化菜单 UI。
         * 实现方法：
         * 加载菜单资源，按条件显示移动菜单，并配置下拉选择菜单与自定义视图。
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);
            // 通话记录目录或没有可用文件夹时，隐藏“移动”按钮
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);

            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }

            });
            return true;
        }

        /*
         * 作用：
         * 更新多选菜单标题和全选按钮状态。
         * 实现方法：
         * 根据适配器当前选中数量与是否全选，动态设置文案和勾选状态。
         */
        private void updateMenu() {
            // 当前选中数量
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // Update dropdown menu
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            // 只有找得到菜单项才更新其勾选状态与文案
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            return false;
        }

        /*
         * 作用：
         * 退出多选模式后恢复普通列表交互。
         * 实现方法：
         * 关闭 choiceMode，恢复列表长按能力，并重新显示“新建便签”按钮。
         */
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        /*
         * 作用：
         * 提供外部主动结束多选模式的入口。
         * 实现方法：
         * 直接调用 ActionMode.finish()。
         */
        public void finishActionMode() {
            mActionMode.finish();
        }

        /*
         * 作用：
         * 响应列表项勾选变化。
         * 实现方法：
         * 将勾选状态同步到适配器后刷新菜单显示。
         */
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        /*
         * 作用：
         * 处理多选菜单点击事件。
         * 实现方法：
         * 先校验是否有选中项，再按菜单 id 分发删除或移动流程。
         */
        public boolean onMenuItemClick(MenuItem item) {
            // 没有选中任何条目时，不执行删除/移动
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

//            switch (item.getItemId()) {
//                case R.id.delete:
//                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
//                    builder.setTitle(getString(R.string.alert_title_delete));
//                    builder.setIcon(android.R.drawable.ic_dialog_alert);
//                    builder.setMessage(getString(R.string.alert_message_delete_notes,
//                                             mNotesListAdapter.getSelectedCount()));
//                    builder.setPositiveButton(android.R.string.ok,
//                                             new DialogInterface.OnClickListener() {
//                                                 public void onClick(DialogInterface dialog,
//                                                         int which) {
//                                                     batchDelete();
//                                                 }
//                                             });
//                    builder.setNegativeButton(android.R.string.cancel, null);
//                    builder.show();
//                    break;
//                case R.id.move:
//                    startQueryDestinationFolders();
//                    break;
//                default:
//                    return false;
//            }
            int itemId = item.getItemId();

            // 处理“删除”菜单
            if (itemId == R.id.delete) {
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes,
                        mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                batchDelete();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                return true;
            // 处理“移动”菜单
            } else if (itemId == R.id.move) {
                startQueryDestinationFolders();
                return true;
            } else {
                return false;
            }
            //return true;
        }
    }

    /*
        * 作用：
        * 处理“新建便签”按钮触摸细节。
        * 实现方法：
        * 对透明区域触摸进行坐标换算并透传到 ListView，保证底层列表可以继续滚动。
     */
    private class NewNoteOnTouchListener implements OnTouchListener {

        /*
         * 作用：
         * 分发并转发触摸事件。
         * 实现方法：
         * 在 ACTION_DOWN/ACTION_MOVE/ACTION_UP(默认分支)中维护转发状态和坐标。
         */
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    // 屏幕高度
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    // 新建按钮高度
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    // 按钮在屏幕中的起始 y
                    int start = screenHeight - newNoteViewHeight;
                    // 触摸点在屏幕中的 y
                    int eventY = start + (int) event.getY();
                    /*
                     * Minus TitleBar's height
                     */
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }
                    /*
                     * HACKME:When click the transparent part of "New Note" button, dispatch
                     * the event to the list view behind this button. The transparent part of
                     * "New Note" button could be expressed by formula y=-0.12x+94（Unit:pixel）
                     * and the line top of the button. The coordinate based on left of the "New
                     * Note" button. The 94 represents maximum height of the transparent part.
                     * Notice that, if the background of the button changes, the formula should
                     * also change. This is very bad, just for the UI designer's strong requirement.
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        // 获取底部可见子项（不算 footer）
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        // 只有按钮覆盖到列表可见区域时才转发触摸
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            // 记录初始触摸信息，开始把事件转发给 ListView
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDispatch) {
                        // 根据移动增量更新转发坐标
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
                default: {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            return false;
        }

    };

    /*
        * 作用：
        * 异步查询当前目录的列表数据。
        * 实现方法：
        * 根据 mCurrentFolderId 选择查询条件并启动 AsyncQueryHandler 查询。
     */
    private void startAsyncNotesListQuery() {
        // 根目录与子目录使用不同查询条件
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[] {
                    String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /*
        * 作用：
        * 承接异步查询返回结果。
        * 实现方法：
        * 按 token 区分“列表查询”和“文件夹查询”，分别刷新列表或弹出文件夹菜单。
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        /*
         * 作用：
         * 初始化异步查询处理器。
         * 实现方法：
         * 通过 super(contentResolver) 交给 AsyncQueryHandler 使用。
         */
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        /*
         * 作用：
         * 处理查询完成回调。
         * 实现方法：
         * switch(token) 分发处理，不同 token 执行不同 UI 更新。
         */
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    // 便签列表查询完成：直接刷新适配器数据
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    // 目标文件夹查询完成：有数据才弹出选择菜单
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
                    return;
            }
        }
    }

    /*
        * 作用：
        * 展示目标文件夹选择弹窗。
        * 实现方法：
        * 用 FoldersListAdapter 绑定对话框列表，点击后调用批量移动并退出多选模式。
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        // 文件夹列表适配器
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    /*
        * 作用：
        * 打开“新建便签”编辑页。
        * 实现方法：
        * 构造 Intent(ACTION_INSERT_OR_EDIT) 并携带当前 folderId，使用 startActivityForResult 启动。
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /*
        * 作用：
        * 批量处理已选便签的删除逻辑。
        * 实现方法：
        * 在后台线程中按同步模式执行“直接删除”或“移入回收站”，前台线程刷新受影响小组件并结束多选。
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                // 先记录受影响的小组件，后续用于刷新
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                // 未开启同步账号：直接删除本地数据
                if (!isSyncMode()) {
                    // if not synced, delete notes directly
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // in sync mode, we'll move the deleted note into the trash
                    // folder
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                // 删除后刷新相关桌面小组件
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        // 过滤掉无效 widget，避免发送无意义更新
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    /*
        * 作用：
        * 删除单个文件夹并处理联动更新。
        * 实现方法：
        * 先保护根目录，再按同步模式删除/移入回收站，最后刷新关联 widget。
     */
    private void deleteFolder(long folderId) {
        // 根目录是系统目录，禁止删除
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        // 组装待处理 id 集合（工具方法使用批处理接口）
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        // 找到该文件夹相关的小组件
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        // 与批量删除一致：同步模式下改为移入回收站
        if (!isSyncMode()) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                // 仅刷新有效的小组件实例
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    /*
        * 作用：
        * 打开某条便签详情页。
        * 实现方法：
        * 通过 ACTION_VIEW 携带便签 id，启动 NoteEditActivity。
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /*
        * 作用：
        * 进入指定文件夹并切换页面状态。
        * 实现方法：
        * 更新当前 folderId 后重查列表，再根据文件夹类型调整标题与“新建”按钮可见性。
     */
    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        // 通话记录目录不可新建普通便签，因此隐藏新建按钮
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        // 标题栏显示当前目录名称
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    /*
        * 作用：
        * 处理页面点击事件入口。
        * 实现方法：
        * 通过 view id 分发动作，目前主要处理“新建便签”。
     */
    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.btn_new_note:
//                createNewNote();
//                break;
//            default:
//                break;
//        }
        int id = v.getId();
        // 点击“新建便签”按钮
        if (id == R.id.btn_new_note) {
            createNewNote();
        }
// 如果有其他按钮，继续添加 else if
    }

    /*
        * 作用：
        * 主动显示软键盘。
        * 实现方法：
        * 获取 InputMethodManager 后调用 toggleSoftInput。
     */
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /*
        * 作用：
        * 隐藏软键盘。
        * 实现方法：
        * 根据目标 view 的 windowToken 调用 hideSoftInputFromWindow。
     */
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /*
        * 作用：
        * 弹出“新建文件夹/重命名文件夹”对话框并处理提交。
        * 实现方法：
        * 根据 create 决定初始化内容，点击确定时校验名称并执行 insert/update。
     * @param create true 表示新建；false 表示重命名
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        // 文件夹名称输入框
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        // create=false 时走“重命名”，需要先填充旧名称
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        // “确定”按钮
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
            // 用户输入的新名称
                String name = etName.getText().toString();
                // 同级目录名称不能重复
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                // 重命名：更新已有文件夹记录
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                // 新建：插入一条文件夹记录
                } else if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        // 输入框为空时禁用“确定”，避免创建空名称文件夹
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        /*
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 实时校验：有内容才允许点击“确定”
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    /*
        * 作用：
        * 自定义返回键行为。
        * 实现方法：
        * 根据 mState 决定是返回根目录并刷新列表，还是调用系统默认返回。
     */
    public void onBackPressed() {
        switch (mState) {
            case SUB_FOLDER:
                // 子目录返回：回到根目录列表，不退出 Activity
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                // 通话记录目录返回：回根目录并恢复新建按钮
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                // 根目录返回：执行系统默认返回行为（通常是退出页面）
                super.onBackPressed();
                break;
            default:
                break;
        }
    }

    /*
        * 作用：
        * 刷新指定桌面小组件实例。
        * 实现方法：
        * 根据 widget 类型设置 Provider，发送 APPWIDGET_UPDATE 广播并回传结果。
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        // 根据 widget 尺寸类型选择对应 Provider
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            appWidgetId
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /*
        * 作用：
        * 为文件夹长按创建上下文菜单。
        * 实现方法：
        * 在监听器中基于当前焦点项动态添加“查看/删除/重命名”菜单项。
     */
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            // 只有有焦点数据项时才创建菜单
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    /*
        * 作用：
        * 上下文菜单关闭后的清理。
        * 实现方法：
        * 置空 OnCreateContextMenuListener，避免影响后续长按流程。
     */
    public void onContextMenuClosed(Menu menu) {
        // 菜单关闭后解除监听，避免影响后续普通长按
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    /*
        * 作用：
        * 处理文件夹上下文菜单项点击。
        * 实现方法：
        * 先校验焦点项，再按菜单 id 分发到查看、删除确认、重命名。
     */
    public boolean onContextItemSelected(MenuItem item) {
        // 没有焦点项时无法执行文件夹菜单动作
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_folder));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFolder(mFocusNoteDataItem.getId());
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    /*
        * 作用：
        * 根据页面状态动态准备顶部菜单。
        * 实现方法：
        * 每次先清空菜单，再按 mState inflate 对应菜单资源并更新同步文案。
     */
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        // 根目录显示完整菜单，并根据同步状态更新“同步/取消同步”文案
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        // 子目录使用精简菜单
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        // 通话记录目录使用专用菜单
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case R.id.menu_new_folder: {
//                showCreateOrModifyFolderDialog(true);
//                break;
//            }
//            case R.id.menu_export_text: {
//                exportNoteToText();
//                break;
//            }
//            case R.id.menu_sync: {
//                if (isSyncMode()) {
//                    if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
//                        GTaskSyncService.startSync(this);
//                    } else {
//                        GTaskSyncService.cancelSync(this);
//                    }
//                } else {
//                    startPreferenceActivity();
//                }
//                break;
//            }
//            case R.id.menu_setting: {
//                startPreferenceActivity();
//                break;
//            }
//            case R.id.menu_new_note: {
//                createNewNote();
//                break;
//            }
//            case R.id.menu_search:
//                onSearchRequested();
//                break;
//            default:
//                break;
//        }
//        return true;
//    }
@Override
/*
 * 作用：
 * 处理顶部菜单点击事件。
 * 实现方法：
 * 通过 itemId 分发到新建文件夹、导出、同步、设置、新建便签、搜索等动作。
 */
public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();

    // 新建文件夹
    if (itemId == R.id.menu_new_folder) {
        showCreateOrModifyFolderDialog(true);
        return true;
    }

    // 导出便签到文本文件
    if (itemId == R.id.menu_export_text) {
        exportNoteToText();
        return true;
    }

    // 同步按钮：已配置账号则开始/取消同步；未配置则打开设置
    if (itemId == R.id.menu_sync) {
        if (isSyncMode()) {
            if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                GTaskSyncService.startSync(this);
            } else {
                GTaskSyncService.cancelSync(this);
            }
        } else {
            startPreferenceActivity();
        }
        return true;
    }

    // 打开设置
    if (itemId == R.id.menu_setting) {
        startPreferenceActivity();
        return true;
    }

    // 新建便签
    if (itemId == R.id.menu_new_note) {
        createNewNote();
        return true;
    }

    // 打开搜索
    if (itemId == R.id.menu_search) {
        onSearchRequested();
        return true;
    }

    // 理论上不会执行到这里，但保留以保证语法正确
    return true;
}

    @Override
    /*
        * 作用：
        * 发起系统搜索入口。
        * 实现方法：
        * 调用 startSearch 并返回 true 表示事件已处理。
     */
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    /*
        * 作用：
        * 导出便签到文本文件。
        * 实现方法：
        * 使用 AsyncTask 后台调用 BackupUtils.exportToText，前台根据状态码弹出结果对话框。
     */
    private void exportNoteToText() {
        // 备份/导出工具类
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                // 返回导出状态码
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                // SD 卡不可用：提示失败原因
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                // 导出成功：展示文件名与路径
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                // 其他系统错误：统一提示导出失败
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }

        }.execute();
    }

    /*
        * 作用：
        * 判断是否可进入同步模式。
        * 实现方法：
        * 读取同步账号名，trim 后长度大于 0 视为已配置。
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /*
        * 作用：
        * 打开设置页面。
        * 实现方法：
        * 优先使用父 Activity 作为启动上下文，否则使用当前 Activity。
     */
    private void startPreferenceActivity() {
        // 如果有父 Activity，则从父 Activity 打开；否则当前页面打开
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    /*
        * 作用：
        * 封装列表项点击处理逻辑。
        * 实现方法：
        * 在回调中区分多选状态与页面状态，决定是勾选、打开便签还是进入文件夹。
     */
    private class OnListItemClickListener implements OnItemClickListener {

        /*
         * 作用：
         * 处理单个列表项点击。
         * 实现方法：
         * 先判断是否为 NotesListItem，再按“多选模式/普通模式”和条目类型分发行为。
         */
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // 只处理便签列表项视图，跳过 footer/header 等其他视图
            if (view instanceof NotesListItem) {
                // 当前点击的数据项
                NoteItemData item = ((NotesListItem) view).getItemData();
                // 多选模式下：点击仅切换勾选，不打开详情
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        // 根目录：可进入文件夹，也可打开便签
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        // 子目录：这里只应该出现便签项
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    /*
        * 作用：
        * 查询“移动便签”可选目标文件夹。
        * 实现方法：
        * 构造 selection（排除回收站与当前目录，必要时包含根目录）并异步查询。
     */
    private void startQueryDestinationFolders() {
        // 基础条件：普通文件夹、非回收站、排除当前文件夹
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        // 在子目录中允许移动到根目录，因此额外拼一个 OR 条件
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
            "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    /*
        * 作用：
        * 处理列表项长按行为。
        * 实现方法：
        * 长按便签进入 ActionMode 多选，长按文件夹则注册并显示上下文菜单。
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // 只处理便签行视图
        if (view instanceof NotesListItem) {
            // 记录当前长按项，供后续菜单操作使用
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            // 长按便签：进入多选模式
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            // 长按文件夹：显示上下文菜单
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}
