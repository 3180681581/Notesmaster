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
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    /** 任务的完成状态，true表示已完成，false表示未完成 */
    private boolean mCompleted;

    /** 任务的备注信息，存储任务的详细描述 */
    private String mNotes;

    /** 任务的元信息，存储对应的本地笔记 JSON 数据 */
    private JSONObject mMetaInfo;

    /** 前一个兄弟任务节点，用于维护任务在列表中的顺序 */
    private Task mPriorSibling;

    /** 父任务列表，该任务所属的任务列表 */
    private TaskList mParent;

    /**
     * 构造函数，创建一个新的任务对象。
     * 初始化所有字段为默认值。
     */
    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * Helper method to handle JSONException
     */
    private void handleJsonException(JSONException e, String action) {
        // Security fix: removed Log.e() and e.printStackTrace() to prevent information disclosure
        throw new ActionFailureException("fail to generate task-" + action + " jsonobject");
    }

    /**
     * 生成创建任务的 JSON 操作指令。
     * 创建包含任务所有必要信息的 JSONObject，用于发送给 Google Tasks API。
     *
     * @param actionId 操作 ID，用于标识这个操作
     * @return 包含创建任务操作的 JSONObject
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

            // 在父列表中的位置索引
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 实体变更数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // parent_id
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // dest_parent_type
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // list_id
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // prior_sibling_id
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            handleJsonException(e, "create");
        }

        return js;
    }

    /**
     * 生成更新任务的 JSON 操作指令。
     * 创建包含任务更新信息的 JSONObject，用于发送给 Google Tasks API。
     *
     * @param actionId 操作 ID，用于标识这个操作
     * @return 包含更新任务操作的 JSONObject
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

            // 任务 ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体变更数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            handleJsonException(e, "update");
        }

        return js;
    }

    /**
     * 从远程 Google Tasks JSON 数据设置任务内容。
     * 解析 Google Tasks API 返回的 JSON 数据，设置任务的各个属性。
     *
     * @param js 包含任务信息的 JSONObject，从 Google Tasks API 获取
     * @throws ActionFailureException JSON 解析失败时抛出异常
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 设置任务 ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 设置最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 设置任务名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // 设置任务备注
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // 设置删除标记
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // 设置完成状态
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                // Security fix: removed Log.e() and e.printStackTrace() to prevent information disclosure
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 从本地笔记 JSON 数据设置任务内容。
     * 解析本地笔记数据，将笔记内容设置为任务的名称。
     *
     * @param js 包含本地笔记信息的 JSONObject
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return;
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                // Security fix: removed Log.e() to prevent information disclosure on invalid data
                return;
            }

            // 从数据项中找到笔记内容作为任务名称
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            // Security fix: removed Log.e() and e.printStackTrace() to prevent information disclosure
        }
    }

    /**
     * 将任务内容转换为本地笔记 JSON 格式。
     * 根据任务信息生成对应的笔记 JSON 数据，用于本地存储。
     *
     * @return 包含笔记信息的 JSONObject，转换失败时返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // new task created from web
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // synced task
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

        note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        return mMetaInfo;
    }

    /**
     * 设置任务的元信息。
     * 从 MetaData 对象中提取笔记信息并存储为 JSONObject。
     *
     * @param metaData 包含任务元信息的 MetaData 对象
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    /**
     * 获取同步操作类型。
     * 分析本地和远程数据的差异，确定需要执行的同步操作。
     *
     * @param c 包含本地笔记信息的数据库游标
     * @return 同步操作类型（无操作、本地更新、远程更新、冲突、错误）
     */
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = getNoteInfo();

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 验证笔记 ID 是否匹配
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // there is no local update
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side
                    return SYNC_ACTION_NONE;
                } else {
                    // apply remote to local
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // validate gtask id
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断任务是否值得保存。
     * 检查任务是否有有效的元信息、名称或备注内容。
     *
     * @return true 如果任务包含有效内容值得保存，false 否则
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && !getName().trim().isEmpty())
                || (getNotes() != null && !getNotes().trim().isEmpty());
    }

    /**
     * 设置任务的完成状态。
     *
     * @param completed 完成状态，true 表示已完成，false 表示未完成
     */
    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    /**
     * 设置任务的备注信息。
     *
     * @param notes 任务备注内容
     */
    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    /**
     * 设置前一个兄弟任务节点。
     *
     * @param priorSibling 前一个兄弟任务节点
     */
    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    /**
     * 设置父任务列表。
     *
     * @param parent 父任务列表
     */
    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    /**
     * 获取任务的完成状态。
     *
     * @return 完成状态，true 表示已完成，false 表示未完成
     */
    public boolean getCompleted() {
        return this.mCompleted;
    }

    /**
     * 获取任务的备注信息。
     *
     * @return 任务备注内容
     */
    public String getNotes() {
        return this.mNotes;
    }

    /**
     * 获取前一个兄弟任务节点。
     *
     * @return 前一个兄弟任务节点
     */
    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    /**
     * 获取父任务列表。
     *
     * @return 父任务列表
     */
    public TaskList getParent() {
        return this.mParent;
    }

}
