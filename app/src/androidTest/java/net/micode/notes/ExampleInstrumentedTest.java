package net.micode.notes;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * ExampleInstrumentedTest 类是一个Android仪器化测试示例。
 * 该类演示了如何在Android设备或模拟器上运行测试，
 * 验证应用的上下文和包名等基本功能。
 * 
 * 仪器化测试的特点：
 * - 在真实的Android设备或模拟器上运行
 * - 可以访问应用的完整Android框架功能
 * - 能够测试需要设备特定功能的代码
 * - 比单元测试更接近真实使用环境
 * 
 * 测试框架：
 * - 使用AndroidJUnit4测试运行器
 * - 集成AndroidX测试库
 * - 支持断言验证测试结果
 * 
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    /**
     * useAppContext 测试方法验证应用的包名是否正确。
     * 该方法获取应用的上下文，并断言包名等于期望值"net.micode.notes"。
     * 
     * 测试步骤：
     * 1. 通过InstrumentationRegistry获取测试环境的Instrumentation对象
     * 2. 获取目标应用的Context（被测试应用）
     * 3. 断言包名是否等于"net.micode.notes"
     * 
     * 这个测试确保应用的基本配置正确，是Android项目模板的标准测试。
     */
    @Test
    public void useAppContext() {
        // 获取被测试应用的上下文对象
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // 断言应用的包名是否等于"net.micode.notes"
        // 这是验证应用基本配置是否正确的标准测试
        assertEquals("net.micode.notes", appContext.getPackageName());
    }
}