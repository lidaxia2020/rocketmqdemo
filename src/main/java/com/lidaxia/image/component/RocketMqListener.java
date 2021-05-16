package com.lidaxia.image.component;

import org.apache.rocketmq.client.consumer.*;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;

@Component
public class RocketMqListener implements InitializingBean {
    private final Logger logger = LoggerFactory.getLogger(RocketMqListener.class);

    @Value("${redis.key.origimage:origimage}")
    private String IMAGE_KEY;

    @Value("${redis.offset.new:offset_new}")
    private String OFFSET_NEW;

    private MQPullConsumerScheduleService consumer;



    @Value("${rocketmq.name-server}")
    private String addr;

    @Value("${rocketmq.topic.name}")
    private String topic;


    @Value("${algorithm.url}")
    private String algorithmUrl;


    @Value("${rocketmq.consumer:true}")
    private Boolean auto;

    @Value("${rocketmq.consumer.name}")
    private String consumerGroup;

    @Value("${rocketmq.algorithm.type}")
    private String algorithmType;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RedisTemplate redisTemplate;

    public static final String WHITE_CAMERAID = "white_cameraId_";

    public RocketMqListener() {
    }

    @PostConstruct
    private void init() throws MQClientException {
        BoundListOperations boundListOperations = this.redisTemplate.boundListOps(IMAGE_KEY);
        Executors.newFixedThreadPool(1).submit(() -> {
            while (true) {
                String rightPop = (String) boundListOperations.rightPop();
                if (rightPop == null) {
                    Thread.sleep(1000L);
                } else {
                    this.threadPoolTaskExecutor.execute(() -> {
                        try {
                            this.handle(rightPop);
                        } catch (Exception var3) {
                            this.logger.error("redis handle exception: {}", var3.getMessage());
                        }

                    });
                }
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                logger.info(" threadPoolTaskExecutor shutdown");
                threadPoolTaskExecutor.shutdown();
            }
        });

        //  mqInit();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("addr = {},consumerGroup={}, topic={}", addr, consumerGroup, topic);

        this.consumer = new MQPullConsumerScheduleService(this.consumerGroup);
        DefaultMQPullConsumer defaultMQPullConsumer = this.consumer.getDefaultMQPullConsumer();
        defaultMQPullConsumer.setNamesrvAddr(this.addr);
        this.consumer.setMessageModel(MessageModel.CLUSTERING);
        this.consumer.registerPullTaskCallback(this.topic, new PullTaskCallback() {
            public void doPullTask(MessageQueue mq, PullTaskContext pullTaskContext) {
                MQPullConsumer consumer = pullTaskContext.getPullConsumer();
                ValueOperations valueOperations = RocketMqListener.this.redisTemplate.opsForValue();

                try {
                    long offset = consumer.fetchConsumeOffset(mq, false);
                    if (offset < 0L) {
                        offset = 0L;
                    }

                    PullResult pullResult = consumer.pull(mq, "*", offset, 32);
                    if (!RocketMqListener.this.redisTemplate.hasKey(OFFSET_NEW)) {
                        consumer.updateConsumeOffset(mq, pullResult.getMaxOffset());
                        valueOperations.set(OFFSET_NEW, OFFSET_NEW);
                        return;
                    }

                    switch (pullResult.getPullStatus()) {
                        case FOUND:
                            List<MessageExt> messageExtList = pullResult.getMsgFoundList();
                            Iterator var9 = messageExtList.iterator();

                            while (var9.hasNext()) {
                                MessageExt m = (MessageExt) var9.next();
                                RocketMqListener.this.receive(new String(m.getBody(), "utf-8"));
                            }
                            break;
                        case NO_MATCHED_MSG:
                        case NO_NEW_MSG:
                        case OFFSET_ILLEGAL:
                            break;
                    }

                    long nextBeginOffset = pullResult.getNextBeginOffset();
                    consumer.updateConsumeOffset(mq, nextBeginOffset);
                    pullTaskContext.setPullNextDelayTimeMillis(100);
                } catch (Exception var11) {
                    RocketMqListener.this.logger.error("rocket consumption exception: {}", var11.getMessage());
                }

            }
        });
        if (this.auto) {
            this.consumer.start();
            this.logger.info("rocketmq listen start");
        } else {
            this.logger.info("rocketmq do not start");
        }

    }

    private void receive(String body) throws Exception {
        this.logger.info("mq data received：{}", body);
        BoundListOperations boundListOperations = this.redisTemplate.boundListOps(IMAGE_KEY);

        if (boundListOperations.size() > 1000L) {
            throw new Exception("Redis data cache over 1000 ");
        }

        boundListOperations.leftPush(body);
    }

    private void handle(String body) {
        logger.info("handle redis data：{}", body);
    }

}
