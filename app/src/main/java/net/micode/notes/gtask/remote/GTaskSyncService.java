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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Task 同步服务类
 * 
 * 功能说明：
 * 1. 后台服务，负责管理同步任务的生命周期
 * 2. 处理启动和取消同步的请求
 * 3. 通过广播通知 UI 层同步进度
 * 4. 在内存不足时自动停止同步
 * 5. 使用单例模式维护全局同步状态
 * 
 * 流程：
 * 1. 通过 Intent 启动此服务
 * 2. Service 创建 AsyncTask 在后台执行同步
 * 3. 同步过程中通过广播发送进度信息
 * 4. 同步完成后停止 Service
 */
public class GTaskSyncService extends Service {
    // ========== 广播和意图常量 ==========
    
    /** Intent 的 Action Bundle 键名 */
    public final static String ACTION_STRING_NAME = "sync_action_type";

    /** 同步操作类型：开始同步 */
    public final static int ACTION_START_SYNC = 0;

    /** 同步操作类型：取消同步 */
    public final static int ACTION_CANCEL_SYNC = 1;

    /** 同步操作类型：无效操作 */
    public final static int ACTION_INVALID = 2;

    // ========== 广播信息常量 ==========
    
    /** 广播的 Action（接收同步状态更新） */
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    /** 广播中的 Extra 键：是否正在同步 */
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    /** 广播中的 Extra 键：同步进度消息 */
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // ========== 同步状态 ==========
    
    /** 当前同步任务实例（全局单例） */
    private static GTaskASyncTask mSyncTask = null;

    /** 同步进度消息 */
    private static String mSyncProgress = "";

    // ========== 私有方法 ==========

    /**
     * 启动后台同步任务
     * 
     * 步骤：
     * 1. 检查是否已有同步任务在执行（防止重复启动）
     * 2. 创建 GTaskASyncTask 实例
     * 3. 设置完成监听器（同步完成后清空任务引用并停止服务）
     * 4. 发送广播通知 UI 层同步已开始
     * 5. 在后台线程执行异步同步任务
     */
    private void startSync() {
        if (mSyncTask == null) {
            // 创建异步同步任务
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                /**
                 * 同步完成回调
                 * 清空任务引用，发送完成广播，停止 Service
                 */
                public void onComplete() {
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();
                }
            });
            // 发送初始广播，通知 UI 同步已开始
            sendBroadcast("");
            // 在后台执行异步同步任务
            mSyncTask.execute();
        }
    }

    /**
     * 取消正在进行的同步操作
     * 
     * 功能：
     * 1. 检查是否有同步任务在运行
     * 2. 如果有，则调用任务的取消方法停止同步
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // ========== Service 生命周期方法 ==========

    /**
     * Service 创建时调用
     * 
     * 初始化：
     * - 清空同步任务引用
     * - 准备接收同步请求
     */
    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    /**
     * Service 启动时调用
     * 这是 Service 处理业务逻辑的主要入口
     * 
     * 步骤：
     * 1. 从 Intent 中提取 Bundle 数据
     * 2. 检查是否包含操作类型 (ACTION_STRING_NAME)
     * 3. 根据操作类型分发请求：
     *    - ACTION_START_SYNC：启动同步
     *    - ACTION_CANCEL_SYNC：取消同步
     *    - 其他：忽略
     * 4. 返回 START_STICKY，表示服务被杀死后会自动重启
     * 
     * @param intent 启动 Service 的 Intent 对象
     * @param flags 启动标志
     * @param startId 启动请求的唯一 ID
     * @return 返回 START_STICKY 实现自动重启机制
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            // 根据操作类型分发请求
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                // 启动同步
                case ACTION_START_SYNC:
                    startSync();
                    break;
                // 取消同步
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                // 未知操作，忽略
                default:
                    break;
            }
            // 返回 START_STICKY 表示服务被 Kill 后会自动重启
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 系统内存不足时调用
     * 
     * 功能：
     * 1. 检查是否有同步任务在运行
     * 2. 如果有，则立即取消同步，释放内存
     * 3. 这是一个优雅的降级策略，避免系统强行杀死 Service
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * Service 绑定回调
     * 
     * 说明：
     * 本服务不支持绑定模式，仅使用 startService 启动方式
     * 因此返回 null
     * 
     * @param intent 绑定 Service 的 Intent
     * @return 返回 null（不支持绑定）
     */
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ========== 公开方法 ==========

    /**
     * 发送同步进度广播
     * 
     * 功能：
     * 1. 更新内部同步进度记录
     * 2. 构建广播 Intent
     * 3. 在 Intent 中包含：
     *    - 是否正在同步的标志
     *    - 当前的进度消息
     * 4. 广播给所有注册的接收器（UI 层会接收并更新显示）
     * 
     * @param msg 同步进度消息（例如："正在初始化任务列表..."）
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        // 添加当前同步状态（是否正在同步）
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        // 添加进度消息
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        // 发送广播
        sendBroadcast(intent);
    }

    /**
     * 静态方法：启动同步服务
     * 
     * 说明：
     * 这是一个便捷方法，供 Activity 调用来启动同步
     * 
     * 步骤：
     * 1. 设置 GTaskManager 的 Activity 上下文（用于获取 Google 账户）
     * 2. 创建启动 Service 的 Intent
     * 3. 在 Intent 中设置 ACTION_START_SYNC 操作
     * 4. 启动服务
     * 
     * @param activity 启动同步的 Activity
     */
    public static void startSync(Activity activity) {
        // 设置 GTaskManager 的上下文（用于获取 Google 账户和显示对话框）
        GTaskManager.getInstance().setActivityContext(activity);
        
        // 创建启动服务的意图
        Intent intent = new Intent(activity, GTaskSyncService.class);
        // 设置操作为 "开始同步"
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        // 启动服务
        activity.startService(intent);
    }

    /**
     * 静态方法：取消同步服务
     * 
     * 说明：
     * 这是一个便捷方法，供 Activity 或其他组件调用来取消同步
     * 
     * 步骤：
     * 1. 创建启动服务的 Intent
     * 2. 在 Intent 中设置 ACTION_CANCEL_SYNC 操作
     * 3. 启动服务（Service 收到后会调用 cancelSync()）
     * 
     * @param context 应用上下文
     */
    public static void cancelSync(Context context) {
        // 创建启动服务的意图
        Intent intent = new Intent(context, GTaskSyncService.class);
        // 设置操作为 "取消同步"
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        // 启动服务
        context.startService(intent);
    }

    /**
     * 检查是否正在同步
     * 
     * @return 如果有同步任务在运行返回 true，否则返回 false
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前的同步进度消息
     * 
     * @return 同步进度文本（例如："已同步100个笔记..."）
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}
