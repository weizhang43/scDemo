package com.example.scorder;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class RocketMqTest {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    private static final String ORDER_TOPIC = "order_topic";

    @Test
    public void sendMessage(){
        SendResult sendResult = rocketMQTemplate.syncSend(ORDER_TOPIC,"测试发送消息");
        log.info("消息发送成功，sendStatus={}，msgId={}",sendResult.getSendStatus(),sendResult.getMsgId());

    }
}
