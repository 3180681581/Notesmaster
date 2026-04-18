package net.micode.notes;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ExampleUnitTest 类是一个Android单元测试示例。
 * 该类演示了如何在开发机器的JVM上运行本地单元测试，
 * 验证应用的纯Java逻辑和基本数学运算等功能。
 * 
 * 单元测试的特点：
 * - 在开发机器（主机）的JVM上运行，不需要Android设备
 * - 执行速度快，适合频繁运行
 * - 主要测试纯Java逻辑，不涉及Android框架
 * - 可以测试算法、工具类、数据处理等逻辑
 * 
 * 测试框架：
 * - 使用JUnit 4测试框架
 * - 支持@Test注解标记测试方法
 * - 使用Assert类进行结果验证
 * - 支持多种断言方法（assertEquals、assertTrue等）
 * 
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    /**
     * addition_isCorrect 测试方法验证基本的加法运算是否正确。
     * 该方法测试2+2是否等于4，这是单元测试的最简单示例。
     * 
     * 测试目的：
     * - 验证JUnit测试框架是否正常工作
     * - 演示assertEquals断言的使用方法
     * - 作为Android项目单元测试的入门示例
     * 
     * 测试逻辑：
     * 1. 执行2+2的加法运算
     * 2. 断言结果等于4
     * 3. 如果断言失败，测试将报错
     */
    @Test
    public void addition_isCorrect() {
        // 断言2+2的结果等于4
        // 这是一个简单的数学运算测试，用于验证测试环境是否正常
        assertEquals(4, 2 + 2);
    }
}