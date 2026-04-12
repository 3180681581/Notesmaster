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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * Contact 类是一个工具类，用于根据电话号码查询联系人信息。
 * 该类提供了获取联系人姓名的功能，并使用内存缓存来提高查询性能。
 * 主要用于通话记录笔记中显示联系人姓名而不是电话号码。
 * 
 * 主要功能：
 * - 根据电话号码查询联系人姓名
 * - 使用HashMap缓存查询结果，避免重复查询
 * - 处理电话号码格式化和匹配逻辑
 * - 集成Android联系人数据库查询
 */
public class Contact {
    /**
     * 联系人缓存，使用HashMap存储电话号码到姓名的映射关系。
     * 用于缓存查询结果，提高重复查询的性能，避免每次都访问联系人数据库。
     * 键为电话号码，值为对应的联系人姓名。
     */
    private static HashMap<String, String> sContactCache;

    /**
     * 日志标签，用于调试和错误日志记录。
     * (Log tag for debugging and error logging)
     */
    private static final String TAG = "Contact";

    /**
     * 来电显示查询选择条件，用于在联系人数据库中查找匹配的电话号码。
     * 该查询使用PHONE_NUMBERS_EQUAL函数进行电话号码匹配，
     * 并通过phone_lookup表进行最小匹配优化。
     * 
     * 查询条件包括：
     * - 电话号码相等匹配
     * - MIME类型为电话号码
     * - 通过phone_lookup表进行最小匹配查找
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名的静态方法。
     * 该方法首先检查缓存，如果缓存中存在则直接返回，
     * 否则查询Android联系人数据库获取联系人姓名并缓存结果。
     * 
     * @param context Android上下文，用于访问ContentResolver
     * @param phoneNumber 要查询的电话号码字符串
     * @return 联系人姓名，如果未找到则返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 初始化联系人缓存，如果还未创建则创建新的HashMap实例
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 检查缓存中是否已存在该电话号码的查询结果
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 构建查询选择条件，将占位符+替换为实际的电话号码最小匹配格式
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        
        // 执行联系人数据库查询，获取显示名称
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 从查询结果中获取联系人姓名
                String name = cursor.getString(0);
                // 将查询结果存入缓存
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 处理游标访问越界异常
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 确保游标被关闭，释放资源
                cursor.close();
            }
        } else {
            // 未找到匹配的联系人，记录调试日志
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
