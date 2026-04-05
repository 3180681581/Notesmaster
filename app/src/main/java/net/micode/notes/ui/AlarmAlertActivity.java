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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;


/*
 * 作用：闹钟提醒弹窗页面，负责提醒展示、铃声播放与用户跳转处理。
 * 实现方法：由 onCreate 完成窗口与数据初始化并触发 showActionDialog、playAlarmSound；
 * 通过 isScreenOn 控制亮屏与按钮分支；在 onClick 处理中转到 NoteEditActivity；
 * 在 onDismiss 中调用 stopAlarmSound 释放音频资源并结束页面。
 * 逻辑示意：onCreate(intent, savedInstanceState) -> isScreenOn() -> showActionDialog() -> playAlarmSound()
 * -> onClick(dialog, which)/onDismiss(dialog) -> stopAlarmSound() -> finish().
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    /*
     * 作用：保存当前提醒对应的便签 id。
     * 实现方法：在 onCreate 中从 Intent 的 Uri 路径段解析并赋值。
     */
    private long mNoteId;
    /*
     * 作用：保存提醒对话框展示的便签摘要文本。
     * 实现方法：从数据库查询后按最大长度截断并追加省略提示。
     */
    private String mSnippet;
    /*
     * 作用：定义摘要预览最大长度。
     * 实现方法：在读取便签摘要后用于长度判断与截断。
     */
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    /*
     * 作用：控制闹铃音频播放。
     * 实现方法：在页面初始化时创建，播放时配置数据源并循环，结束时停止并释放。
     */
    MediaPlayer mPlayer;

    @Override
    /*
     * 作用：初始化提醒页面并触发提醒流程。
     * 实现方法：配置锁屏显示窗口参数，解析提醒便签信息，校验便签有效性后弹窗并播放铃声。
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 作用：在灭屏场景下点亮并保持屏幕，以便用户直接看到提醒。
        // 实现方法：仅当当前屏幕关闭时添加一组点亮与锁屏显示窗口标记。
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        try {
            // 作用：从提醒 Intent 中提取便签 id 并构建展示摘要。
            // 实现方法：读取 Uri 路径段获取 id，查询摘要后按最大长度截断。
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        mPlayer = new MediaPlayer();
        // 作用：保证只对可见且有效的便签执行提醒。
        // 实现方法：查询数据库可见性，通过后弹出操作对话框并播放闹铃，否则直接结束页面。
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog();
            playAlarmSound();
        } else {
            finish();
        }
    }

    /*
     * 作用：判断当前设备屏幕是否点亮。
     * 实现方法：通过 PowerManager 获取当前屏幕电源状态。
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /*
     * 作用：播放提醒闹铃声音。
     * 实现方法：读取系统默认闹钟铃声，设置音频流类型，配置 MediaPlayer 数据源后循环播放。
     */
    private void playAlarmSound() {
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 作用：根据系统静音影响位选择音频流类型。
        // 实现方法：若闹钟流受静音策略影响则使用对应流标识，否则固定使用闹钟流。
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            mPlayer.setLooping(true);
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /*
     * 作用：展示提醒操作对话框。
     * 实现方法：构建包含摘要、确认按钮和可选“进入便签”按钮的对话框，并监听关闭事件。
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);
        dialog.setMessage(mSnippet);
        dialog.setPositiveButton(R.string.notealert_ok, this);
        // 作用：仅在屏幕已点亮时允许直接跳转到编辑页。
        // 实现方法：屏幕点亮条件下添加“进入便签”负按钮。
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        dialog.show().setOnDismissListener(this);
    }

    /*
     * 作用：处理提醒对话框按钮点击事件。
     * 实现方法：点击“进入便签”时启动 NoteEditActivity 查看当前便签，其他按钮仅关闭流程。
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    /*
     * 作用：处理提醒对话框关闭事件。
     * 实现方法：在对话框消失后统一停止闹铃并结束当前页面。
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /*
     * 作用：停止并释放闹铃播放器资源。
     * 实现方法：判空后依次执行 stop、release 并清空引用。
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}
