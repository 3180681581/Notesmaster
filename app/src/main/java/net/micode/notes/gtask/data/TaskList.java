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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * TaskList 类表示 Google Tasks 中的任务列表（通常代表文件夹）。
 * 该类继承自 Node 类，实现了任务列表的创建、更新、同步等操作，
 * 同时管理该列表下的所有子任务。
 * 
 * 主要功能：
 * - 生成创建和更新任务列表的 JSON 操作指令
 * - 从远程 JSON 数据设置列表内容
 * - 从本地 JSON 数据设置列表内容
 * - 将列表内容转换为本地 JSON 格式
 * - 分析同步动作类型
 * - 管理子任务的添加、删除、移动和查询
 * - 维护任务列表的索引顺序
 * 
 * 任务列表属性：
 * - 索引：任务列表在父节点中的位置
 * - 子任务列表：包含的所有子任务集合
 */
public class TaskList extends Node {
    /** 日志标签，用于调试和错误日志记录 */
    private static final String TAG = TaskList.class.getSimpleName();

    /** 任务列表的索引位置，用于确定在父节点中的顺序 */
    private int mIndex;

    /** 子任务列表，存储该任务列表包含的所有子任务 */
    private ArrayList<Task> mChildren;

    /**
     * 构造函数，创建一个新的任务列表对象。
     * 初始化子任务列表为空，并设置默认索引为 1。
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 生成创建任务列表的 JSON 操作指令。
     * 创建包含任务列表所有必要信息的 JSONObject，用于发送给 Google Tasks API。
     *
     * @param actionId 操作 ID，用于标识这个操作
     * @return 包含创建任务列表操作的 JSONObject
     * @throws ActionFailureException JSON 构建失败时抛出异常
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 操作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 列表的索引位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // 实体变更数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 生成更新任务列表的 JSON 操作指令。
     * 创建包含任务列表更新信息的 JSONObject，用于发送给 Google Tasks API。
     *
     * @param actionId 操作 ID，用于标识这个操作
     * @return 包含更新任务列表操作的 JSONObject
     * @throws ActionFailureException JSON 构建失败时抛出异常
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 操作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务列表 ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体变更数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 从远程 Google Tasks JSON 数据设置任务列表内容。
     * 解析 Google Tasks API 返回的 JSON 数据，设置任务列表的各个属性。
     *
     * @param js 包含任务列表信息的 JSONObject，从 Google Tasks API 获取
     * @throws ActionFailureException JSON 解析失败时抛出异常
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 设置任务列表 ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 设置最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 设置任务列表名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 从本地笔记 JSON 数据设置任务列表内容。
     * 解析本地文件夹数据，将文件夹信息转换为任务列表名称。
     *
     * @param js 包含本地文件夹信息的 JSONObject
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            // 根据本地文件夹类型设置任务列表名称
            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 普通用户创建的文件夹
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹：默认文件夹或通话记录文件夹
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将任务列表内容转换为本地笔记 JSON 格式。
     * 根据任务列表信息生成对应的文件夹 JSON 数据，用于本地存储。
     *
     * @return 包含文件夹信息的 JSONObject，转换失败时返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            // 移除前缀以获得实际的文件夹名称
            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            
            // 设置文件夹名称
            folder.put(NoteColumns.SNIPPET, folderName);
            
            // 根据名称确定是否为系统文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取同步操作类型。
     * 分析本地和远程数据的差异，确定需要执行的同步操作。
     * 对于文件夹冲突，优先应用本地修改。
     *
     * @param c 包含本地文件夹信息的数据库游标
     * @return 同步操作类型（无操作、本地更新、远程更新、错误）
     */
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 没有本地更新
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 两边都没有更新
                    return SYNC_ACTION_NONE;
                } else {
                    // 应用远程更新到本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 验证 Google Task ID
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 只有本地修改
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 对于文件夹冲突，优先应用本地修改
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 获取子任务的数量。
     *
     * @return 子任务列表的大小
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 添加子任务到任务列表末尾。
     * 自动设置任务的前一个兄弟节点和父节点。
     *
     * @param task 要添加的任务对象
     * @return true 如果添加成功，false 如果任务为 null 或已存在
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置任务的前一个兄弟节点和父节点
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定位置添加子任务。
     * 自动更新前后任务的前一个兄弟节点关系。
     *
     * @param task 要添加的任务对象
     * @param index 要插入的位置索引
     * @return true 如果添加成功，false 如果索引无效或任务已存在
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            // 在指定位置添加任务
            mChildren.add(index, task);

            // 更新任务列表中的兄弟节点关系
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            // 设置新任务的前一个兄弟节点
            task.setPriorSibling(preTask);
            // 更新后续任务的前一个兄弟节点
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 从任务列表中移除指定的子任务。
     * 自动清理任务的父节点和兄弟节点关系，并更新后续任务的兄弟节点关系。
     *
     * @param task 要移除的任务对象
     * @return true 如果移除成功，false 如果任务不在列表中
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 清理任务的前一个兄弟节点和父节点
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新后续任务的前一个兄弟节点
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 移动子任务到指定的新位置。
     * 通过先移除再插入的方式实现任务位置的移动。
     *
     * @param task 要移动的任务对象
     * @param index 新的位置索引
     * @return true 如果移动成功，false 如果索引无效或任务不在列表中
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        // 如果位置相同，无需移动
        if (pos == index)
            return true;
        // 先移除再添加到新位置
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据 Google ID 查找子任务。
     *
     * @param gid 要查找的任务的 Google ID
     * @return 找到的任务对象，如果未找到则返回 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取指定任务在列表中的索引位置。
     *
     * @param task 要查找的任务对象
     * @return 任务的索引位置，如果不在列表中则返回 -1
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 根据索引获取子任务。
     *
     * @param index 要获取的任务的索引位置
     * @return 指定索引处的任务对象，如果索引无效则返回 null
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 根据 Google ID 查找子任务（另一个查找方法）。
     * 使用增强型 for 循环遍历查找。
     *
     * @param gid 要查找的任务的 Google ID
     * @return 找到的任务对象，如果未找到则返回 null
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 获取子任务列表。
     *
     * @return 包含所有子任务的 ArrayList
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    /**
     * 设置任务列表的索引位置。
     *
     * @param index 任务列表的新索引位置
     */
    public void setIndex(int index) {
        this.mIndex = index;
    }

    /**
     * 获取任务列表的索引位置。
     *
     * @return 任务列表的索引位置
     */
    public int getIndex() {
        return this.mIndex;
    }
}
