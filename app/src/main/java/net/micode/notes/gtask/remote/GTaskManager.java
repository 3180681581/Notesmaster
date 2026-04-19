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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;


/**
 * Google Task 管理器类
 * 
 * 功能说明：
 * 1. 管理本地笔记与 Google Task 的同步
 * 2. 处理文件夹和任务的创建、更新、删除操作
 * 3. 维护本地数据库与远程 Google Task 之间的映射关系
 * 
 * 同步流程：
 * - 登录 Google 账号
 * - 初始化远程任务列表
 * - 比较本地和远程数据的差异
 * - 执行相应的同步操作（增删改）
 * - 更新本地同步ID
 */
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    // ========== 同步状态常量 ==========
    
    /** 同步成功 */
    public static final int STATE_SUCCESS = 0;

    /** 网络错误（无法连接Google服务器） */
    public static final int STATE_NETWORK_ERROR = 1;

    /** 内部错误（程序逻辑错误） */
    public static final int STATE_INTERNAL_ERROR = 2;

    /** 同步进行中（已有其他同步任务在执行） */
    public static final int STATE_SYNC_IN_PROGRESS = 3;

    /** 同步被取消（用户主动停止同步） */
    public static final int STATE_SYNC_CANCELLED = 4;

    // ========== 单例实例 ==========
    
    /** 单例实例 */
    private static GTaskManager mInstance = null;

    // ========== 上下文和系统服务 ==========
    
    /** Activity 对象，用于获取谷歌账户认证信息 */
    private Activity mActivity;

    /** 应用上下文对象 */
    private Context mContext;

    /** 内容解析器，用于数据库操作 */
    private ContentResolver mContentResolver;

    // ========== 同步状态标志 ==========
    
    /** 是否正在同步中 */
    private boolean mSyncing;

    /** 是否被取消同步 */
    private boolean mCancelled;

    // ========== 数据映射集合 ==========
    
    /** 远程任务列表哈希表：Google ID -> TaskList对象 */
    private HashMap<String, TaskList> mGTaskListHashMap;

    /** 远程任务/节点哈希表：Google ID -> Node对象（Task或TaskList） */
    private HashMap<String, Node> mGTaskHashMap;

    /** 元数据映射表：Google ID -> MetaData对象（存储笔记元信息） */
    private HashMap<String, MetaData> mMetaHashMap;

    /** 元数据列表（特殊的远程任务列表） */
    private TaskList mMetaList;

    /** 本地删除ID集合：存储待删除的本地笔记ID */
    private HashSet<Long> mLocalDeleteIdMap;

    /** Google ID 到 本地Note ID 的映射：用于查找本地笔记 */
    private HashMap<String, Long> mGidToNid;

    /** 本地Note ID 到 Google ID 的映射：用于查找远程任务 */
    private HashMap<Long, String> mNidToGid;

    // ========== 构造方法 ==========

    /**
     * 私有构造方法（单例模式）
     * 初始化所有数据结构为空状态
     */
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    // ========== 单例访问方法 ==========

    /**
     * 获取单例实例（线程安全）
     * 
     * @return GTaskManager 的唯一实例
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    // ========== 公开方法 ==========

    /**
     * 设置 Activity 上下文
     * 用于获取 Google 账户的认证令牌
     * 
     * @param activity 当前的 Activity 对象
     */
    public synchronized void setActivityContext(Activity activity) {
        // used for getting authtoken
        mActivity = activity;
    }

    /**
     * 执行主同步操作（核心方法）
     * 这是整个同步流程的入口点
     * 
     * 同步步骤：
     * 1. 检查是否已有同步任务在进行
     * 2. 登录 Google 账号获取认证
     * 3. 初始化远程任务列表
     * 4. 执行本地和远程数据的同步
     * 5. 更新本地同步ID
     * 
     * @param context 应用上下文
     * @param asyncTask 异步任务对象（用于发布进度更新）
     * @return 同步状态码（STATE_SUCCESS、STATE_NETWORK_ERROR等）
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 检查是否已有同步任务在执行
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        
        // 初始化上下文和内容解析器
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        
        // 清空所有数据映射集合
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // 第1步：登录 Google Task
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // 第2步：从 Google 获取任务列表
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // 第3步：执行本地和远程内容的同步
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 清空所有临时数据
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        // 返回最终状态
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 初始化远程 Google Task 列表
     * 将远程的所有任务和文件夹下载到本地缓存中
     * 
     * 功能步骤：
     * 1. 获取所有远程任务列表
     * 2. 初始化元数据列表（用于存储笔记的附加信息）
     * 3. 加载普通任务列表及其包含的任务
     * 
     * @throws NetworkFailureException 网络连接失败
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            // 获取所有远程任务列表
            JSONArray jsTaskLists = client.getTaskLists();

            // ========== 第1步：初始化元数据列表 ==========
            // 元数据列表用于存储笔记的元信息
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 查找是否存在元数据列表
                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // 加载元数据列表中的所有元数据对象
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        // 只保存有价值的元数据
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // 如果远程不存在元数据列表，则创建一个
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // ========== 第2步：初始化普通任务列表 ==========
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                // 跳过元数据列表，只处理普通任务列表
                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载该任务列表下的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        // 只保存有价值的任务
                        if (task.isWorthSaving()) {
                            // 关联元数据
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 同步内容（笔记）
     * 处理所有被删除、修改或新增的笔记与远程 Google Task 的同步
     * 
     * 同步流程：
     * 1. 处理本地删除的笔记
     * 2. 同步文件夹结构
     * 3. 同步数据库中的笔记
     * 4. 处理远程新增的笔记
     * 5. 清理数据库中的已删除记录
     * 6. 提交所有更新
     * 7. 刷新本地同步ID
     * 
     * @throws NetworkFailureException 网络连接失败
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // ========== 第1步：处理本地删除的笔记（垃圾箱中的笔记） ==========
        try {
            // 查询垃圾箱中的笔记（非系统文件夹）
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 远程存在此笔记，需要删除
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }

                    // 标记本地笔记为待删除
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ========== 第2步：同步文件夹结构 ==========
        syncFolder();

        // ========== 第3步：同步数据库中的笔记（不在垃圾箱中的笔记） ==========
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 远程已存在此笔记
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        // 获取需要执行的同步操作类型
                        syncType = node.getSyncAction(c);
                    } else {
                        // 远程不存在此笔记
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增的笔记，需要上传
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程已删除，本地需要删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ========== 第4步：处理远程新增的笔记 ==========
        // 遍历所有未处理的远程笔记（仍然在 mGTaskHashMap 中的笔记）
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            // 远程新增的笔记，需要添加到本地
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // ========== 第5步：清理数据库中的已删除记录 ==========
        // 检查是否被取消，因为 mCancelled 可能在其他线程中被修改
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // ========== 第6步：提交所有更新和刷新本地同步ID ==========
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }

    }

    /**
     * 同步文件夹结构
     * 处理文件夹（笔记本）的创建、更新、删除
     * 
     * 同步步骤：
     * 1. 同步根文件夹
     * 2. 同步通话记录文件夹
     * 3. 同步本地文件夹
     * 4. 处理远程新增的文件夹
     * 
     * @throws NetworkFailureException 网络连接失败
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // ========== 第1步：同步根文件夹 ==========
        try {
            // 根文件夹ID为 Notes.ID_ROOT_FOLDER
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 系统文件夹仅在必要时更新远程名称
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ========== 第2步：同步通话记录文件夹 ==========
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // 系统文件夹仅在必要时更新远程名称
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ========== 第3步：同步本地自定义文件夹 ==========
        try {
            // 查询不在垃圾箱中的文件夹
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增的文件夹
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程已删除文件夹
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // ========== 第4步：处理远程新增的文件夹 ==========
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                // 远程新增的文件夹，需要添加到本地
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 提交文件夹相关的更新
        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 执行同步操作（分发器方法）
     * 根据同步类型调用相应的处理方法
     * 
     * @param syncType 同步操作类型（增加本地、增加远程、删除本地等）
     * @param node 远程节点对象（Task 或 TaskList）
     * @param c 本地数据库游标（查询结果）
     * @throws NetworkFailureException 网络连接失败
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            // 将远程任务添加到本地
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            // 将本地笔记上传到远程
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            // 删除本地笔记
            case Node.SYNC_ACTION_DEL_LOCAL:
                if (c != null) {
                    meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                    if (meta != null) {
                        GTaskClient.getInstance().deleteNode(meta);
                    }
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
                break;
            // 删除远程任务
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            // 更新本地笔记内容
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            // 更新远程任务内容
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            // 冲突解决：优先使用本地修改
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // 在本地和远程都有修改时，简单地使用本地更新覆盖
                updateRemoteNode(node, c);
                break;
            // 无需同步
            case Node.SYNC_ACTION_NONE:
                break;
            // 错误状态
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 将远程任务添加到本地数据库
     * 根据任务类型（TaskList 或 Task）创建相应的本地笔记或文件夹
     * 
     * @param node 远程节点对象（Task 或 TaskList）
     * @throws NetworkFailureException 网络连接失败
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 判断远程节点的类型
        if (node instanceof TaskList) {
            // 如果是文件夹，需要检查是否是系统文件夹
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                // 根文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                // 通话记录文件夹
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                // 自定义文件夹
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 如果是任务，需要转换为本地笔记
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 检查并处理笔记ID的冲突
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // 该ID已被使用，需要创建新ID
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                // 检查并处理数据ID的冲突
                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // 该数据ID已被使用，需要创建新ID
                                data.remove(DataColumns.ID);
                            }
                        }
                    }

                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            // 获取父文件夹的本地ID
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        // 保存本地笔记并设置Google Task ID
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // 更新Google ID 与 本地ID 的映射关系
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // 更新远程元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 使用远程任务数据更新本地笔记
     * 
     * @param node 远程节点对象
     * @param c 本地数据库游标
     * @throws NetworkFailureException 网络连接失败
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        // 使用远程数据更新本地笔记
        sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 获取父文件夹ID
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        // 更新远程元数据
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 将本地笔记上传到远程 Google Task
     * 
     * @param node 远程节点对象
     * @param c 本地数据库游标
     * @throws NetworkFailureException 网络连接失败
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // 根据笔记类型处理
        if (sqlNote.isNoteType()) {
            // ========== 处理普通笔记 ==========
            // 创建远程Task任务
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            // 获取父文件夹的 Google ID
            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            // 将任务添加到远程文件夹
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            // 在远程创建该任务
            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            // 添加元数据
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            // ========== 处理文件夹 ==========
            TaskList tasklist = null;

            // 检查是否已存在相同名称的远程文件夹（避免重复创建）
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            // 查找是否存在相同名称的远程文件夹
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            // 如果不存在相同名称的文件夹，则创建新的
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // 更新本地笔记的 Google Task ID
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        // 清除本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // 更新ID映射关系
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 使用本地笔记数据更新远程任务
     * 并在必要时移动任务到新的文件夹
     * 
     * @param node 远程节点对象
     * @param c 本地数据库游标
     * @throws NetworkFailureException 网络连接失败
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // 使用本地数据更新远程内容
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        // 更新元数据
        updateRemoteMeta(node.getGid(), sqlNote);

        // 如果笔记的父文件夹有变化，则需要移动任务
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            // 获取当前的父文件夹
            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            // 如果父文件夹改变了，则移动任务
            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // 清除本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 更新或创建远程元数据
     * 元数据用于存储笔记的附加信息
     * 
     * @param gid 远程任务的 Google ID
     * @param sqlNote 本地笔记对象
     * @throws NetworkFailureException 网络连接失败
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        // 只为普通笔记（不是文件夹）创建/更新元数据
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                // 元数据已存在，更新内容
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                // 创建新的元数据
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 刷新本地同步ID
     * 将远程任务的最后修改时间同步到本地数据库
     * 用于下次同步时判断是否需要更新
     * 
     * @throws NetworkFailureException 网络连接失败
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 重新获取最新的 Google Task 列表
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            // 查询所有需要同步的本地笔记和文件夹
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        // 更新本地的同步ID为远程的最后修改时间
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /**
     * 获取当前同步的 Google 账号名称
     * 
     * @return 账号名称
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消当前的同步操作
     * 此方法是线程安全的，可以从其他线程调用
     */
    public void cancelSync() {
        mCancelled = true;
    }
}
