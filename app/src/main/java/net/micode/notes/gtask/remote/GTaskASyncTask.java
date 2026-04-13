
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;


/**
 * Google Task 异步同步任务类
 * 功能：在后台线程中执行与Google Task的同步操作，避免阻塞主线程
 * 
 * AsyncTask泛型参数说明：
 * - Void：不需要输入参数
 * - String：进度更新消息
 * - Integer：最终返回结果（同步状态码）
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    // 用于标识同步通知的唯一ID
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成监听器接口
     * 当后台同步任务完成时，通过此接口通知主程序
     */
    public interface OnCompleteListener {
    /** 同步完成时调用此方法 */
    void onComplete();
}

// ========== 类成员变量 ==========

/** 上下文对象，用于访问应用资源和系统服务 */
private Context mContext;

/** 通知管理器，用于显示同步进度和结果的系统通知 */
private NotificationManager mNotifiManager;

/** Google Task管理器，负责处理实际的同步逻辑 */
private GTaskManager mTaskManager;

/** 同步完成监听器，同步任务结束时回调 */
private OnCompleteListener mOnCompleteListener;

// ========== 构造方法 ==========

/**
 * 构造方法
 * @param context 上下文对象
 * @param listener 同步完成监听器
 */
public GTaskASyncTask(Context context, OnCompleteListener listener) {
    mContext = context;
    mOnCompleteListener = listener;
    // 获取系统的通知管理器服务
    mNotifiManager = (NotificationManager) mContext
            .getSystemService(Context.NOTIFICATION_SERVICE);
    // 获取Google Task管理器的单例实例
    mTaskManager = GTaskManager.getInstance();
}

// ========== 公开方法 ==========

/**
 * 取消同步操作
 * 调用此方法会停止正在进行的后台同步任务
 */
public void cancelSync() {
    mTaskManager.cancelSync();
}

/**
 * 发送进度更新消息
 * @param message 要显示的进度消息文本
 */
public void publishProgess(String message) {
    publishProgress(new String[] {
        message
    });
}

// ========== 私有方法 ==========

//    private void showNotification(int tickerId, String content) {
//        Notification notification = new Notification(R.drawable.notification, mContext
//                .getString(tickerId), System.currentTimeMillis());
//        notification.defaults = Notification.DEFAULT_LIGHTS;
//        notification.flags = Notification.FLAG_AUTO_CANCEL;
//        PendingIntent pendingIntent;
//        if (tickerId != R.string.ticker_success) {
//            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
//                    NotesPreferenceActivity.class), 0);
//
//        } else {
//            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
//                    NotesListActivity.class), 0);
//        }
//        notification.setLatestEventInfo(mContext, mContext.getString(R.string.app_name), content,
//                pendingIntent);
//        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
//    }

/**
 * 显示系统通知
 * 根据不同的同步状态显示不同的通知内容和点击行为
 * 
 * @param tickerId 通知的标题ID资源文件引用
 * @param content 通知的详细内容文本
 */
private void showNotification(int tickerId, String content) {
    // 根据通知类型决定点击通知后跳转的页面
    PendingIntent pendingIntent;
    // 如果不是成功通知，则跳转到设置页面
    if (tickerId != R.string.ticker_success) {
        pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                NotesPreferenceActivity.class), PendingIntent.FLAG_IMMUTABLE);
    } else {
        // 成功通知，跳转到笔记列表页面
        pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                NotesListActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }
    
    // 使用Notification.Builder来构建现代化的通知
    Notification.Builder builder = new Notification.Builder(mContext)
            .setAutoCancel(true)  // 点击通知后自动关闭
            .setContentTitle(mContext.getString(R.string.app_name))  // 设置通知标题
            .setContentText(content)  // 设置通知内容
            .setContentIntent(pendingIntent)  // 设置点击通知的响应
            .setWhen(System.currentTimeMillis())  // 设置通知时间
            .setOngoing(true);  // 设置为持续通知
    
    // 构建通知对象
    Notification notification = builder.getNotification();
    // 显示通知
    mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
}


    @Override
    /**
     * 后台线程执行的主要方法
     * 这个方法运行在工作线程中，不会阻塞UI线程
     * 
     * @param unused 未使用的参数（由AsyncTask的execute()方法传入）
     * @return 同步结果的状态码
     */
    protected Integer doInBackground(Void... unused) {
        // 发布初始化进度消息
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));
        // 执行实际的同步操作并返回结果状态
        return mTaskManager.sync(mContext, this);
    }

    @Override
    /**
     * 进度更新时调用（在主线程执行）
     * 当doInBackground()中调用publishProgress()时，此方法会被触发
     * 
     * @param progress 进度消息数组
     */
    protected void onProgressUpdate(String... progress) {
        // 显示进度通知
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果当前上下文是同步服务，则广播进度消息
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    @Override
    /**
     * 后台任务完成时调用（在主线程执行）
     * 处理同步结果，显示相应的通知消息
     * 
     * @param result doInBackground()方法的返回值（同步状态码）
     */
    protected void onPostExecute(Integer result) {
        // 根据不同的结果状态显示不同的通知
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            // 保存最后同步时间
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 同步被取消
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }
        
        // 如果设置了完成监听器，则回调通知任务完成
        if (mOnCompleteListener != null) {
            // 在新线程中执行回调，避免阻塞主线程
            new Thread(new Runnable() {

                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}
