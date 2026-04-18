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

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * MetaData 类是Google任务同步中的元数据管理类。
 * 该类继承自Task类，专门用于处理同步过程中的元数据信息，
 * 包括任务的关联ID、同步状态等辅助信息。
 * 
 * 主要功能：
 * - 存储和管理任务的元数据信息
 * - 处理Google任务ID的关联关系
 * - 提供元数据的JSON序列化和反序列化
 * - 管理同步过程中的辅助数据
 * 
 * 元数据特点：
 * - 不直接对应用户可见的任务内容
 * - 主要用于同步逻辑的辅助信息
 * - 包含任务的Google ID关联信息
 * - 支持JSON格式的存储和传输
 */
public class MetaData extends Task {

    /**
     * 日志标签，用于调试和错误日志记录。
     * 使用类名作为标签，便于在日志中识别来源。
     */
    private final static String TAG = MetaData.class.getSimpleName();

    /**
     * 关联的Google任务ID，用于标识该元数据对应的Google任务。
     * 该字段存储从JSON元数据中解析出的Google任务唯一标识符。
     */
    private String mRelatedGid = null;

    /**
     * 设置元数据信息，将Google任务ID和元数据信息组合成JSON格式存储。
     * 该方法将Google任务ID嵌入到元数据JSON对象中，并设置任务名称为元数据标识。
     * 
     * @param gid Google任务的唯一标识符
     * @param metaInfo 包含元数据的JSONObject对象
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将Google任务ID添加到元数据JSON对象中
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            // JSON操作异常，记录错误日志
            Log.e(TAG, "failed to put related gid");
        }
        // 将完整的元数据JSON转换为字符串存储
        setNotes(metaInfo.toString());
        // 设置任务名称为元数据标识
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取关联的Google任务ID。
     * 返回当前元数据对象关联的Google任务唯一标识符。
     * 
     * @return 关联的Google任务ID，如果未设置则返回null
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断该元数据是否值得保存。
     * 只有当元数据包含有效的notes信息时才认为值得保存。
     * 
     * @return 如果notes不为空则返回true，否则返回false
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程JSON数据设置内容，解析并提取元数据信息。
     * 该方法首先调用父类的设置方法，然后从notes中解析JSON格式的元数据，
     * 提取出关联的Google任务ID。
     * 
     * @param js 包含远程任务数据的JSONObject对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        // 调用父类方法设置基本内容
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                // 解析notes中的JSON元数据
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                // 提取关联的Google任务ID
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                // JSON解析异常，记录警告日志并清空关联ID
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 从本地JSON数据设置内容的方法。
     * 该方法不应该被调用，因为MetaData只处理远程数据。
     * 如果被调用，会抛出IllegalAccessError异常。
     * 
     * @param js 本地JSON数据对象
     * @throws IllegalAccessError 总是抛出，因为该方法不应被调用
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 从内容生成本地JSON的方法。
     * 该方法不应该被调用，因为MetaData不生成本地JSON。
     * 如果被调用，会抛出IllegalAccessError异常。
     * 
     * @return 不会返回，总是抛出异常
     * @throws IllegalAccessError 总是抛出，因为该方法不应被调用
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 获取同步动作的方法。
     * 该方法不应该被调用，因为MetaData不参与同步动作判断。
     * 如果被调用，会抛出IllegalAccessError异常。
     * 
     * @param c 数据库游标对象
     * @return 不会返回，总是抛出异常
     * @throws IllegalAccessError 总是抛出，因为该方法不应被调用
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }
}
