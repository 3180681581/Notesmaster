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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


/**
 * Google Task HTTP 客户端类
 * 
 * 功能说明：
 * 1. 处理 Google 账户认证和登录
 * 2. 管理 HTTP 连接和 Cookie 维护
 * 3. 与 Google Task 服务器通信（获取、创建、更新、删除任务）
 * 4. 处理 HTTP 响应的压缩解压（GZIP、DEFLATE）
 * 5. 维护操作队列，支持批量更新
 * 
 * 使用单例模式确保全局只有一个 HTTP 客户端实例
 */
public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();

    // ========== Google Task API 地址常量 ==========
    
    /** Google Task 基础地址 */
    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    /** Google Task GET 请求地址（获取任务列表） */
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    /** Google Task POST 请求地址（创建、更新、删除任务） */
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    // ========== 单例实例 ==========
    
    /** 单例实例 */
    // ========== 单例实例 ==========
    
    /** 单例实例 */
    private static GTaskClient mInstance = null;

    // ========== HTTP 连接相关 ==========
    
    /** Apache HTTP 客户端对象，用于发送 HTTP 请求 */
    private DefaultHttpClient mHttpClient;

    /** GET 请求地址（可能根据账户类型动态调整，用于自定义域名） */
    private String mGetUrl;

    /** POST 请求地址（可能根据账户类型动态调整，用于自定义域名） */
    private String mPostUrl;

    // ========== Google Task 服务器信息 ==========
    
    /** 客户端版本号（从 Google Task 服务器获取） */
    private long mClientVersion;

    // ========== 登录状态 ==========
    
    /** 是否已登录标志 */
    private boolean mLoggedin;

    /** 上一次登录的时间戳（用于检测是否需要重新登录） */
    private long mLastLoginTime;

    // ========== 操作管理 ==========
    
    /** 操作 ID 计数器（每次操作递增，用于唯一标识每个操作） */
    private int mActionId;

    // ========== 账户信息 ==========
    
    /** 当前同步的 Google 账户 */
    private Account mAccount;

    // ========== 批量操作队列 ==========
    
    /** 待更新的操作队列（支持批量提交，最多10条） */
    private JSONArray mUpdateArray;

    /**
     * 私有构造方法（单例模式）
     * 初始化所有成员变量为初始状态
     */
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    /**
     * 获取单例实例（线程安全）
     * 
     * @return GTaskClient 的唯一实例
     */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 登录 Google Task 服务
     * 
     * 登录步骤：
     * 1. 检查登录状态是否过期（Cookie 有效期5分钟）
     * 2. 检查账户是否已切换
     * 3. 获取 Google 账户认证令牌
     * 4. 尝试登录自定义域名的 Google Task（如果账户是企业域名）
     * 5. 如果失败，尝试登录官方 Google Task
     * 
     * @param activity 用于获取 Google 账户的 Activity 上下文
     * @return 登录成功返回 true，否则返回 false
     */
    public boolean login(Activity activity) {
        // ========== 第1步：检查登录状态是否过期 ==========
        // 假设 Cookie 有效期为 5 分钟，需要定时重新登录
        final long interval = 1000L * 60 * 5;
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            // 登录状态已过期，需要重新登录
            mLoggedin = false;
        }

        // ========== 第2步：检查账户是否已切换 ==========
        // 如果用户更换了同步账户，也需要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                        .getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        // ========== 第3步：如果已登录则直接返回 ==========
        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        // ========== 第4步：更新登录时间 ==========
        mLastLoginTime = System.currentTimeMillis();
        
        // ========== 第5步：获取 Google 账户的认证令牌 ==========
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // ========== 第6步：处理自定义域名的账户（企业 Google Workspace） ==========
        // 检查账户是否使用自定义域名（非 gmail.com 或 googlemail.com）
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            // 为自定义域名构建特殊的 Google Task URL
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            // 尝试登录自定义域名的 Google Task
            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // ========== 第7步：如果自定义域名登录失败，尝试官方 URL ==========
        if (!mLoggedin) {
            // 重置为官方 Google Task URL
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    /**
     * 登录 Google 账户并获取认证令牌
     * 
     * @param activity 用于显示账户选择对话框的 Activity
     * @param invalidateToken 是否需要重新获取令牌（旧令牌已过期）
     * @return 认证令牌，如果获取失败返回 null
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        // 获取系统的账户管理器
        AccountManager accountManager = AccountManager.get(activity);
        // 获取所有 Google 账户
        Account[] accounts = accountManager.getAccountsByType("com.google");

        // 检查是否有可用的 Google 账户
        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        // 获取用户在设置中选择的同步账户名
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        // 查找与设置中相同名称的账户
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // ========== 获取认证令牌 ==========
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            // 等待获取认证令牌（可能弹出密码输入对话框）
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            
            // 如果需要重新获取令牌，则先清除旧令牌再重新获取
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                // 递归调用，重新获取新令牌
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    /**
     * 尝试登录 Google Task
     * 如果认证失败，则重新获取令牌后再试一次
     * 
     * @param activity 用于获取新令牌的 Activity
     * @param authToken 认证令牌
     * @return 登录成功返回 true
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // 可能令牌已过期，重新获取令牌并尝试
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            // 使用新令牌重新尝试登录
            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    /**
     * 实际执行 Google Task 登录操作
     * 
     * 步骤：
     * 1. 创建 HTTP 连接配置
     * 2. 使用认证令牌访问 Google Task
     * 3. 获取并验证认证 Cookie
     * 4. 解析响应获取客户端版本号
     * 
     * @param authToken 认证令牌
     * @return 登录成功返回 true
     */
    private boolean loginGtask(String authToken) {
        // ========== 第1步：配置 HTTP 连接参数 ==========
        int timeoutConnection = 10000;  // 连接超时时间（毫秒）
        int timeoutSocket = 15000;      // Socket 超时时间（毫秒）
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        mHttpClient = new DefaultHttpClient(httpParameters);
        
        // 创建 Cookie 存储
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // ========== 第2步：使用认证令牌登录 Google Task ==========
        try {
            // 构建登录 URL（带有认证令牌参数）
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // ========== 第3步：获取并验证认证 Cookie ==========
            // 登录成功后，Google 会返回 GTL Cookie
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // ========== 第4步：解析响应获取客户端版本号 ==========
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            // 保存客户端版本号，后续请求需要使用
            mClientVersion = js.getLong("v");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    /**
     * 获取下一个操作 ID
     * 每次调用都会递增计数器，确保每个操作有唯一的 ID
     * 
     * @return 操作 ID
     */
    private int getActionId() {
        return mActionId++;
    }

    /**
     * 创建 HTTP POST 请求对象
     * 设置必要的请求头信息
     * 
     * @return HttpPost 对象
     */
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        // 设置请求内容类型为 form-urlencoded（表单提交格式）
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        // AT 头表示 "Authentication Token"
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    /**
     * 获取 HTTP 响应的内容文本
     * 自动处理 GZIP 和 DEFLATE 压缩
     * 
     * @param entity HTTP 响应体对象
     * @return 解压后的响应内容
     * @throws IOException IO 异常
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        String contentEncoding = null;
        // 获取响应的压缩编码方式
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        // ========== 处理压缩编码 ==========
        InputStream input = entity.getContent();
        // 如果是 GZIP 压缩，则解压
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());
        } 
        // 如果是 DEFLATE 压缩，则解压
        else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
        }

        // ========== 读取响应内容 ==========
        try (InputStreamReader isr = new InputStreamReader(input);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();

            // 逐行读取响应内容
            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    return sb.toString();
                }
                sb = sb.append(buff);
            }
        }
    }

    /**
     * 发送 POST 请求到 Google Task 服务器
     * 
     * 请求流程：
     * 1. 检查登录状态
     * 2. 创建 POST 请求对象
     * 3. 将操作数据编码为 form 格式
     * 4. 执行请求并获取响应
     * 5. 解析 JSON 响应
     * 
     * @param js 包含操作数据的 JSON 对象
     * @return 服务器返回的 JSON 响应
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 请求失败或响应解析失败
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();
        try {
            // ========== 构建请求体 ==========
            // 将 JSON 对象作为 "r" 参数编码为 form 格式
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            // ========== 执行请求 ==========
            HttpResponse response = mHttpClient.execute(httpPost);
            String jsString = getResponseContent(response.getEntity());
            return new JSONObject(jsString);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request");
        }
    }

    /**
     * 在远程 Google Task 中创建任务
     * 
     * 步骤：
     * 1. 提交之前的批量更新（确保操作序列正确）
     * 2. 构建创建任务的 JSON 请求
     * 3. 发送 POST 请求到服务器
     * 4. 获取服务器返回的新任务 ID（Google ID）
     * 5. 更新本地任务对象的 Google ID
     * 
     * @param task 要创建的任务对象
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 创建失败或响应解析失败
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 添加创建任务的操作
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 设置客户端版本号（必需，用于版本验证）
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求并解析响应
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            // 从响应中获取新分配的 Google ID
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 在远程 Google Task 中创建任务列表（文件夹）
     * 
     * 步骤：
     * 1. 提交之前的批量更新
     * 2. 构建创建任务列表的 JSON 请求
     * 3. 发送 POST 请求到服务器
     * 4. 获取服务器返回的新列表 ID
     * 5. 更新本地任务列表对象的 Google ID
     * 
     * @param tasklist 要创建的任务列表对象
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 创建失败或响应解析失败
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 添加创建任务列表的操作
            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 设置客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求并解析响应
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            // 从响应中获取新分配的 Google ID
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    /**
     * 提交批量更新操作
     * 
     * 说明：
     * - Google Task API 支持批量操作，最多包含10个操作
     * - 此方法将缓存中的所有操作一次性提交
     * - 提交后清空缓存，为下一批操作做准备
     * 
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 提交失败
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // 设置操作列表
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // 设置客户端版本号
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                postRequest(jsPost);
                // 清空缓存，准备下一批操作
                mUpdateArray = null;
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    /**
     * 添加更新操作到批量队列
     * 
     * 说明：
     * - 将更新操作缓存到队列中
     * - 当队列中的操作数超过10个时，自动提交一次
     * - 这样可以提高效率，减少网络请求次数
     * 
     * @param node 要更新的节点（Task 或 TaskList）
     * @throws NetworkFailureException 网络连接失败
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // 如果缓存中的操作数已达到最大值（10个），则先提交
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();
            }

            // 初始化缓存数组（如果还未创建）
            if (mUpdateArray == null)
                mUpdateArray = new JSONArray();
            // 将新操作添加到缓存
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * 移动任务到不同的文件夹
     * 
     * 功能：
     * 1. 支持在同一文件夹内移动（调整顺序）
     * 2. 支持在不同文件夹之间移动
     * 3. 处理优先级关系（prior_sibling_id）
     * 
     * @param task 要移动的任务
     * @param preParent 任务原来所在的文件夹
     * @param curParent 任务要移动到的文件夹
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 移动失败
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 构建移动操作
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());
            
            // 如果在同一文件夹内移动且不是第一项，则设置前驱兄弟节点
            if (preParent == curParent && task.getPriorSibling() != null) {
                // put prioring_sibing_id only if moving within the tasklist and
                // it is not the first one
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            // 设置源文件夹（移动前的文件夹）
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            // 设置目标父节点（通常与目标文件夹相同）
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());
            
            // 只在移动到不同文件夹时才设置目标列表
            if (preParent != curParent) {
                // put the dest_list only if moving between tasklists
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 设置客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    /**
     * 删除远程节点
     * 
     * 步骤：
     * 1. 提交之前的批量更新
     * 2. 将节点标记为已删除
     * 3. 发送删除操作给服务器
     * 4. 清空操作缓存
     * 
     * @param node 要删除的节点（Task 或 TaskList）
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 删除失败
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 标记节点为已删除状态
            node.setDeleted(true);
            // 添加删除操作
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 设置客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            // 清空缓存
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    /**
     * 获取所有任务列表（文件夹）
     * 
     * 步骤：
     * 1. 检查登录状态
     * 2. 发送 GET 请求获取列表数据
     * 3. 解析响应中的 JavaScript 对象
     * 4. 提取并返回任务列表数组
     * 
     * @return JSON 数组，包含所有任务列表的信息
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 解析失败
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // ========== 提取并解析响应 ==========
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            // 从响应中提取任务列表数组
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed");
        }
    }

    /**
     * 获取指定任务列表中的所有任务
     * 
     * @param listGid 任务列表的 Google ID
     * @return JSON 数组，包含该列表中所有任务的信息
     * @throws NetworkFailureException 网络连接失败
     * @throws ActionFailureException 获取失败
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 构建获取所有任务的操作
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            // 不获取已删除的任务
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 设置客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            // 从响应中提取任务数组
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    /**
     * 获取当前同步的 Google 账户
     * 
     * @return 账户对象
     */
    public Account getSyncAccount() {
        return mAccount;
    }

    /**
     * 重置操作缓存
     * 清空所有待提交的批量更新操作
     */
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}
