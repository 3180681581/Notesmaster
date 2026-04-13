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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * AlarmInitReceiver类 - 闹钟初始化广播接收器
 * 
 * 这个类的作用：
 * 1. 在系统启动完成后被调用
 * 2. 查询数据库中所有设置了提醒的笔记
 * 3. 重新设置这些笔记的闹钟（因为系统重启会清除所有闹钟）
 * 
 * 为什么需要这个类：
 * - Android系统启动时会清空所有的闹钟
 * - 用户手机重启后，之前设置的笔记提醒就会失效
 * - 这个接收器在系统启动完成后被激活，重新注册所有的闹钟
 * - 确保笔记提醒功能不会因为系统重启而失效
 * 
 * 工作流程：
 * 1. Android系统启动完成
 * 2. 系统发送BOOT_COMPLETED广播
 * 3. AlarmInitReceiver接收到这个广播
 * 4. 从数据库查询出所有需要提醒的笔记
 * 5. 遍历这些笔记，为每个笔记设置闹钟
 * 6. AlarmReceiver会在指定时间被触发
 * 7. AlarmReceiver启动提醒界面
 * 
 * 关键概念：
 * 
 * AlarmManager（闹钟管理器）：
 * - Android系统的闹钟服务
 * - 用于在指定的未来时间执行某项操作
 * - 即使应用关闭，闹钟也仍然有效
 * - 重启后所有闹钟都会被清空，需要重新设置
 * 
 * PendingIntent（待定意图）：
 * - 代表一个将在未来某个时间执行的意图
 * - 传递给AlarmManager，告诉它"到时间后去执行这个操作"
 * - 实际执行的操作在PendingIntent中定义
 * - 在这里是启动AlarmReceiver来处理闹钟事件
 * 
 * ContentUris.withAppendedId()：
 * - 为一个内容URI附加一个ID
 * - 用法：ContentUris.withAppendedId(baseUri, id)
 * - 结果：基础URI/id（例如：content://notes/1）
 * - 这样可以指向数据库中的特定记录
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * PROJECTION - 数据库查询投影，定义从数据库Note表中查询哪些列
     * 
     * 包含的列：
     * - NoteColumns.ID: 笔记的ID，用于后续的操作
     * - NoteColumns.ALERTED_DATE: 笔记的提醒时间戳，用于设置闹钟
     * 
     * 为什么需要这个数组：
     * 1. 只查询需要的列，提高查询效率
     * 2. 在后面用列索引来快速获取数据
     */
    private static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE
    };

    /**
     * COLUMN_ID - PROJECTION数组中ID列的位置
     * 
     * 值为0表示ID是第一列，在使用Cursor获取数据时使用：
     * cursor.getLong(COLUMN_ID) 获取笔记ID
     * 
     * 优点：使用常量代替魔法数字，代码更易维护和理解
     */
    private static final int COLUMN_ID                = 0;

    /**
     * COLUMN_ALERTED_DATE - PROJECTION数组中提醒日期列的位置
     * 
     * 值为1表示ALERTED_DATE是第二列，在使用Cursor获取数据时使用：
     * cursor.getLong(COLUMN_ALERTED_DATE) 获取提醒时间戳
     */
    private static final int COLUMN_ALERTED_DATE      = 1;

    /**
     * onReceive方法 - 接收系统启动完成的广播
     * 
     * 被调用时机：
     * - 系统启动完成后，系统会发送一个BOOT_COMPLETED广播
     * - 只要应用在AndroidManifest.xml中注册了这个接收器
     * - 系统就会自动调用这个onReceive()方法
     * 
     * 方法职责：
     * 1. 获取当前系统时间
     * 2. 查询数据库中所有提醒时间在当前时间之后的笔记
     * 3. 为这些笔记重新设置闹钟
     * 
     * @param context Android上下文，用于数据库和闹钟操作
     * @param intent 包含广播信息的意图（这里是BOOT_COMPLETED）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        /**
         * 获取当前系统时间戳（毫秒）
         * 
         * System.currentTimeMillis()的用法：
         * - 返回自1970年1月1日0:00:00 UTC以来的毫秒数
         * - 用于时间比较：哪些笔记的提醒时间已经过了，哪些还没有
         * 
         * 为什么需要这个时间：
         * - 在SQL查询中使用，查找提醒时间>当前时间的笔记
         * - 只有这样的笔记才需要设置闹钟
         */
        long currentDate = System.currentTimeMillis();
        
        /**
         * 查询数据库中所有设置了提醒的笔记
         * 
         * 查询参数说明：
         * - Notes.CONTENT_NOTE_URI: 查询笔记表的内容URI
         * - PROJECTION: 指定查询的列（ID和提醒时间）
         * - WHERE子句: 两个条件
         *   1. NoteColumns.ALERTED_DATE + ">?" : 提醒时间 > 当前时间
         *   2. NoteColumns.TYPE + "=" + Notes.TYPE_NOTE: 笔记类型是普通笔记
         * - new String[]{...}: WHERE子句中?占位符的替换值（当前时间）
         * - null: 无排序要求
         * 
         * SQL效果（伪代码）：
         * SELECT ID, ALERTED_DATE FROM Notes 
         * WHERE ALERTED_DATE > 当前时间 AND TYPE = 笔记类型
         * 
         * 结果：Cursor对象，包含所有需要提醒的笔记
         */
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null);

        /**
         * 检查查询是否成功，以及是否有结果
         */
        if (c != null) {
            /**
             * 移动游标到第一条记录
             * moveToFirst()的作用：
             * - 如果有数据，移动到第一条并返回true
             * - 如果没有数据，返回false
             * 
             * 为什么需要这一步：
             * - Cursor刚创建时，指向数据之前的位置
             * - 必须调用moveToFirst()才能访问第一条数据
             */
            if (c.moveToFirst()) {
                /**
                 * do-while循环遍历所有满足条件的笔记
                 * 
                 * do-while循环的流程：
                 * 1. 先执行循环体（处理当前记录）
                 * 2. 然后执行moveToNext()移到下一条
                 * 3. 如果还有下一条就继续，没有就退出
                 */
                do {
                    /**
                     * 获取当前笔记的提醒时间戳
                     * 
                     * c.getLong(COLUMN_ALERTED_DATE):
                     * - c: Cursor游标，代表查询结果的当前行
                     * - COLUMN_ALERTED_DATE: 列的位置（第1列）
                     * - getLong(): 以长整数形式获取该列的值
                     * - 结果：笔记设定的提醒时间戳
                     */
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    
                    /**
                     * 创建一个新的Intent，用于传递给AlarmManager
                     * 
                     * new Intent(context, AlarmReceiver.class)的含义：
                     * - 创建一个指向AlarmReceiver的意图
                     * - 这个意图会被传给闹钟管理器
                     * - 时间到达时，系统会发送这个意图给AlarmReceiver
                     * - AlarmReceiver的onReceive()方法会被调用
                     */
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    
                    /**
                     * 为Intent设置数据URI
                     * 
                     * sender.setData()的作用：
                     * - 为Intent附加一个数据URI
                     * - 这个URI指向特定的笔记
                     * 
                     * ContentUris.withAppendedId()的作用：
                     * - 基础URI：Notes.CONTENT_NOTE_URI (content://notes)
                     * - 附加笔记ID：c.getLong(COLUMN_ID)
                     * - 结果：content://notes/123（指向ID为123的笔记）
                     * 
                     * 为什么需要这样做：
                     * - AlarmReceiver收到广播时，需要知道是哪个笔记的提醒
                     * - 通过URI来传递笔记ID信息
                     * - AlarmReceiver可以解析这个URI获得笔记ID
                     */
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));
                    
                    /**
                     * 创建PendingIntent（待定意图）
                     * 
                     * PendingIntent.getBroadcast()的参数：
                     * - context: 应用上下文
                     * - 0: 请求代码（用于识别不同的PendingIntent，这里为0）
                     * - sender: 要发送的Intent
                     * - 0: 标志（这里为0，表示默认行为）
                     * 
                     * 返回值：
                     * - 一个PendingIntent对象
                     * - 代表"在未来某个时间发送sender这个广播"的意图
                     * 
                     * 为什么需要PendingIntent：
                     * 1. AlarmManager需要一个PendingIntent来指定要执行什么操作
                     * 2. 时间到达时，系统会通过这个PendingIntent发送广播
                     * 3. AlarmReceiver会接收到这个广播
                     */
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);
                    
                    /**
                     * 获取AlarmManager服务
                     * 
                     * getSystemService(Context.ALARM_SERVICE)的作用：
                     * - 获取Android系统的闹钟服务
                     * - 这个服务负责管理所有的系统闹钟
                     * 
                     * 类型转换：
                     * - getSystemService()返回Object类型
                     * - 需要强制转换为(AlarmManager)
                     */
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    
                    /**
                     * 设置闹钟
                     * 
                     * alermManager.set()的参数：
                     * 1. AlarmManager.RTC_WAKEUP:
                     *    - 闹钟类型
                     *    - RTC_WAKEUP表示"实时时间，唤醒设备"
                     *    - 意思是：在指定的毫秒时间点，唤醒设备并执行操作
                     *    - 即使屏幕关闭或设备休眠，闹钟到达也会唤醒设备
                     *    - 另一种常见类型是RTC：不唤醒设备，只在设备已开启时执行
                     * 
                     * 2. alertDate:
                     *    - 闹钟触发的时间戳（毫秒）
                     *    - 这是笔记设定的提醒时间
                     * 
                     * 3. pendingIntent:
                     *    - 闹钟到达时要执行的操作
                     *    - 时间到了，系统会执行这个PendingIntent
                     *    - 即发送Intent广播给AlarmReceiver
                     * 
                     * 执行效果：
                     * - 系统会在alertDate指定的时间记住这个闹钟
                     * - 到达指定时间时，系统会唤醒设备（如果关闭了）
                     * - 然后发送PendingIntent中的广播
                     * - AlarmReceiver接收到广播并处理
                     */
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                    
                } while (c.moveToNext()); // 继续移动到下一条记录
            }
            /**
             * 关闭游标，释放数据库资源
             * 
             * 为什么要关闭Cursor：
             * 1. Cursor占用数据库连接资源
             * 2. 不关闭会导致资源泄漏
             * 3. 最后一定要调用c.close()释放资源
             */
            c.close();
        }
    }
}
