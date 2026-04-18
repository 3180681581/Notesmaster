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

import org.json.JSONObject;

/**
 * Node 类是Google任务同步中节点数据结构的抽象基类。
 * 该类定义了任务树形结构中每个节点的基本属性和行为，
 * 提供了同步动作的常量定义和通用的数据操作方法。
 * 
 * 主要功能：
 * - 定义同步动作类型常量
 * - 管理节点的Google ID、名称、修改时间、删除状态
 * - 提供JSON数据转换的抽象接口
 * - 支持本地和远程数据的同步操作
 * 
 * 节点特点：
 * - 抽象类，需要子类实现具体的数据转换逻辑
 * - 支持任务树形结构的构建和维护
 * - 提供统一的同步动作判断机制
 * - 管理节点的基本生命周期状态
 */
public abstract class Node {

    /**
     * 同步动作常量：无动作
     * 表示该节点不需要进行任何同步操作
     */
    public static final int SYNC_ACTION_NONE = 0;

    /**
     * 同步动作常量：添加远程节点
     * 表示需要在Google Tasks中创建对应的远程任务
     */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /**
     * 同步动作常量：添加本地节点
     * 表示需要在本地数据库中创建对应的本地任务
     */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /**
     * 同步动作常量：删除远程节点
     * 表示需要删除Google Tasks中的远程任务
     */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /**
     * 同步动作常量：删除本地节点
     * 表示需要删除本地数据库中的本地任务
     */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /**
     * 同步动作常量：更新远程节点
     * 表示需要更新Google Tasks中的远程任务数据
     */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /**
     * 同步动作常量：更新本地节点
     * 表示需要更新本地数据库中的本地任务数据
     */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /**
     * 同步动作常量：冲突更新
     * 表示本地和远程数据都发生了变化，需要手动解决冲突
     */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /**
     * 同步动作常量：同步错误
     * 表示同步过程中发生了错误，需要特殊处理
     */
    public static final int SYNC_ACTION_ERROR = 8;

    /**
     * 节点的Google任务ID，用于唯一标识Google Tasks中的任务
     */
    private String mGid;

    /**
     * 节点名称，表示任务或列表的标题
     */
    private String mName;

    /**
     * 最后修改时间戳，记录节点最后一次修改的时间
     */
    private long mLastModified;

    /**
     * 删除标记，表示该节点是否已被删除
     */
    private boolean mDeleted;

    /**
     * 默认构造函数，初始化节点的基本属性为默认值。
     * 创建一个空的节点对象，所有字段都设置为初始状态。
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    /**
     * 抽象方法：获取创建动作的JSON对象。
     * 子类需要实现该方法，返回用于创建远程任务的JSON数据结构。
     * 
     * @param actionId 动作ID，用于标识具体的创建操作类型
     * @return 包含创建动作数据的JSONObject对象
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 抽象方法：获取更新动作的JSON对象。
     * 子类需要实现该方法，返回用于更新远程任务的JSON数据结构。
     * 
     * @param actionId 动作ID，用于标识具体的更新操作类型
     * @return 包含更新动作数据的JSONObject对象
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 抽象方法：从远程JSON数据设置节点内容。
     * 子类需要实现该方法，从Google Tasks API返回的JSON数据中解析并设置节点属性。
     * 
     * @param js 包含远程任务数据的JSONObject对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 抽象方法：从本地JSON数据设置节点内容。
     * 子类需要实现该方法，从本地数据库的JSON数据中解析并设置节点属性。
     * 
     * @param js 包含本地任务数据的JSONObject对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 抽象方法：从节点内容生成本地JSON对象。
     * 子类需要实现该方法，将节点的属性转换为JSON格式，用于本地存储。
     * 
     * @return 包含节点数据的JSONObject对象
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 抽象方法：根据数据库游标获取同步动作。
     * 子类需要实现该方法，通过比较本地和远程数据来判断需要执行的同步操作。
     * 
     * @param c 数据库游标，包含本地任务数据
     * @return 同步动作常量，表示需要执行的操作类型
     */
    public abstract int getSyncAction(Cursor c);

    /**
     * 设置节点的Google任务ID。
     * 
     * @param gid Google任务的唯一标识符
     */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /**
     * 设置节点名称。
     * 
     * @param name 节点的新名称
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * 设置节点的最后修改时间。
     * 
     * @param lastModified 最后修改的时间戳
     */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /**
     * 设置节点的删除状态。
     * 
     * @param deleted true表示已删除，false表示未删除
     */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /**
     * 获取节点的Google任务ID。
     * 
     * @return 节点的Google任务ID，如果未设置则返回null
     */
    public String getGid() {
        return this.mGid;
    }

    /**
     * 获取节点名称。
     * 
     * @return 节点的名称字符串
     */
    public String getName() {
        return this.mName;
    }

    /**
     * 获取节点的最后修改时间。
     * 
     * @return 最后修改的时间戳
     */
    public long getLastModified() {
        return this.mLastModified;
    }

    /**
     * 获取节点的删除状态。
     * 
     * @return true表示已删除，false表示未删除
     */
    public boolean getDeleted() {
        return this.mDeleted;
    }

}
