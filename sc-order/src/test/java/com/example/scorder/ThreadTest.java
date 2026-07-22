package com.example.scorder;

import com.alibaba.fastjson.parser.Feature;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
@SpringBootTest
public class ThreadTest {
    @Autowired
    @Qualifier(value = "stockRestoreExecutor")
    private ThreadPoolTaskExecutor stockRestoreExecutor;

    @Autowired
    @Qualifier(value = "testRestoreExecutor")
    private ThreadPoolTaskExecutor testRestoreExecutor;


    public boolean flag = true;


    @Test
    public void doTest() throws InterruptedException {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            stockRestoreExecutor.execute(() -> {
                try {
                    fun();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        log.info("方法执行共耗时：{}", System.currentTimeMillis() - start);
    }


    @Test
    public void doTest2() throws InterruptedException {
        //开启新线程执行fun3，进入循环
        testRestoreExecutor.execute(() -> fun3());
        //确保fun3已进入循环
        Thread.sleep(100);
        //开启新线程执行fun2，由fun2修改flag
        stockRestoreExecutor.execute(() -> fun2());
        //等待观察fun3是否感知到flag变化
        Thread.sleep(3000);
        System.out.println("doTest2 结束");
    }


    /**
     * 测试方法
     *
     * @return
     */
    public String fun() {
        try {
            System.out.println("=============方法执行了一次");
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    /**
     *
     */
    public void fun2() {
        Random random = new Random();
        while (true) {
            int val = random.nextInt(100);
            if (val > 95) {
                flag = false;
                System.out.println("fun2 随机出：" + val + "，已把 flag 改为 false");
                break;
            }
        }
    }


    /**
     *
     */
    public void fun3() {
        System.out.println("fun3 进入循环");
        while (flag) {
            // 空循环：不能有println等含synchronized的操作，否则内存屏障会让flag的修改被看到
        }
        System.out.println("fun3 感知到 flag 变化，退出了");
    }


}
