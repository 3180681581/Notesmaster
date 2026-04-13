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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmReceiver类 - 闹钟广播接收器
 * 
 * 这个类的作用：
 * 1. 继承BroadcastReceiver，用于接收系统闹钟事件
 * 2. 当笔记的提醒时间到达时，系统会发送一条广播
 * 3. 这个接收器捕获该广播，然后启动警告提示界面给用户
 * 
 * 重要概念解释：
 * 
 * BroadcastReceiver（广播接收器）：
 * - 是Android中的一种应用组件，用于接收系统或应用发送的广播消息
 * - 类似于收音机，当有人发送广播信号时，收音机就会接收到
 * - 可以接收系统事件（如闹钟、手机启动等）或应用自己发送的事件
 * 
 * 工作流程：
 * 1. Android系统或应用发送一条意图（Intent）广播
 * 2. 系统找到注册了该广播的接收器
 * 3. 系统调用接收器的onReceive()方法
 * 4. onReceive()方法处理这个广播事件
 * 
 * 在这个应用中：
 * 1. 用户在笔记中设置提醒时间
 * 2. AlarmManager（系统服务）在指定时间发送闹钟广播
 * 3. AlarmReceiver接收到广播
 * 4. AlarmReceiver启动AlarmAlertActivity显示提醒界面
 * 5. 用户看到提醒并可以对笔记进行操作
 */
public class AlarmReceiver extends BroadcastReceiver {
    /**
     * onReceive方法 - 接收广播的回调方法
     * 
     * 这个方法在以下情况下会被系统自动调用：
     * - 当注册的广播事件发生时（在这里是闹钟时间到达）
     * 
     * 方法职责：
     * 1. 获取广播中包含的意图（Intent）
     * 2. 将该意图的目标类设置为AlarmAlertActivity
     * 3. 添加必要的标志来启动新的活动窗口
     * 4. 启动AlarmAlertActivity来显示提醒界面
     * 
     * 为什么需要这个方法：
     * - 当系统发送闹钟广播时，需要一个入口来处理这个事件
     * - 这个方法就是那个入口，系统会自动调用它
     * 
     * @param context Android上下文对象
     *        - 用于访问应用资源和启动Activity
     *        - 生命周期受限（只在onReceive执行期间有效）
     * 
     * @param intent 包含广播信息的意图对象
     *        - 包含了广播的类型、附加数据等信息
     *        - 这里用它来承载和传递笔记相关的数据
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        /**
         * 将广播Intent的目标类设置为AlarmAlertActivity
         * 
         * setClass(context, class)的作用：
         * - 指定这个Intent将启动哪个Activity
         * - 在这里表示：启动AlarmAlertActivity来显示提醒
         * 
         * 效果：当这个intent被用来启动Activity时，
         * 系统会启动AlarmAlertActivity类
         */
        intent.setClass(context, AlarmAlertActivity.class);
        
        /**
         * 为Intent添加FLAG_ACTIVITY_NEW_TASK标志
         * 
         * addFlags()的作用：
         * - 为Intent添加额外的处理标志
         * - 标志会影响Activity的启动方式
         * 
         * FLAG_ACTIVITY_NEW_TASK的含义：
         * - 在新的任务栈中启动Activity
         * - 为什么需要这个标志？
         *   1. onReceive()执行在BroadcastReceiver的上下文中
         *   2. 不在任何Activity的任务栈中
         *   3. 必须显式指定在新任务中启动
         *   4. 否则会抛出异常：Cannot start activity from broadcast context
         * 
         * 简单类比：
         * - 没有这个标志，就像在一个没有生根据地的漂流瓶中传递信息
         * - 加上这个标志，就为Activity创建了一个"家"（任务栈）
         */
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        /**
         * 启动Activity来显示提醒界面
         * 
         * startActivity(intent)的作用：
         * - 根据Intent指定的类和标志启动一个Activity
         * - 在这里会启动AlarmAlertActivity
         * 
         * 执行流程：
         * 1. 系统收到这个启动请求
         * 2. 根据intent查找要启动的Activity（AlarmAlertActivity）
         * 3. 创建AlarmAlertActivity的实例
         * 4. 调用其onCreate()、onStart()、onResume()等生命周期方法
         * 5. 将Activity显示在屏幕上
         * 6. 用户看到提醒界面，可以查看提醒的笔记内容
         */
        context.startActivity(intent);
    }
}
