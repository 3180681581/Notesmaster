
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
 * GTaskASyncTask 类负责在后台执行 Google 任务同步操作。
 * 该类继承自 AsyncTask，通过异步执行完成同步流程，
 * 并在同步过程中显示系统通知、发送进度广播、以及回调同步完成事件。
 * 
 * 主要职责：
 * - 在后台线程中执行同步操作
 * - 将同步进度发布到通知栏和服务
 * - 根据同步结果显示不同提示
 * - 支持取消同步操作
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    /**
     * 同步通知ID，用于在NotificationManager中唯一标识当前同步通知。
     */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成监听器接口，外部可以注册该接口获取同步完成回调。
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    /**
     * 当前的上下文对象，用于访问系统服务和资源。
     */
    private Context mContext;

    /**
     * 通知管理器，用于发出和更新通知栏提示。
     */
    private NotificationManager mNotifiManager;

    /**
     * Google 任务管理器，用于执行实际的同步逻辑。
     */
    private GTaskManager mTaskManager;

    /**
     * 同步完成回调接口实例，当同步结束时会触发该回调。
     */
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造函数，初始化同步任务对象并获取所需的系统服务。
     * 
     * @param context 当前上下文，通常是服务或Activity
     * @param listener 同步完成后回调的监听器
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 请求取消当前正在执行的同步操作。
     * 该方法会通知任务管理器停止同步流程。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布同步进度消息。
     * 该方法将进度信息发送到AsyncTask的publishProgress，
     * 触发onProgressUpdate回调。
     * 
     * @param message 要显示的进度文本
     */
    public void publishProgess(String message) {
        publishProgress(new String[] {
            message
        });
    }

    /**
     * 根据传入的tickerId和内容显示同步通知。
     * 该方法会创建PendingIntent，点击通知后跳转到相应页面，
     * 并使用Notification.Builder构建通知对象。
     * 
     * @param tickerId 通知栏ticker文本对应的资源ID
     * @param content 通知正文内容
     */
    private void showNotification(int tickerId, String content) {
        PendingIntent pendingIntent;
        if (tickerId != R.string.ticker_success) {
            // 同步失败或正在同步时，点击通知进入同步设置页面
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), PendingIntent.FLAG_IMMUTABLE);
        } else {
            // 同步成功时，点击通知进入笔记列表页面
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), PendingIntent.FLAG_IMMUTABLE);
        }
        Notification.Builder builder = new Notification.Builder(mContext)
                .setAutoCancel(true)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true);
        Notification notification = builder.getNotification();
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 在后台线程中执行同步操作。
     * 该方法首先发布登录进度，然后调用GTaskManager进行实际同步。
     * 
     * @param unused 不使用的参数，保留为AsyncTask定义格式
     * @return 同步结果状态码
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 更新同步进度时调用，该方法运行在UI线程。
     * 它会显示通知并将进度广播给GTaskSyncService。
     * 
     * @param progress 进度文本数组，通常只包含一条消息
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步完成后调用，该方法运行在UI线程。
     * 根据返回结果显示不同的通知，并在成功时记录最后同步时间。
     * 如果设置了完成监听器，还会异步触发回调。
     * 
     * @param result 同步结果状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}
