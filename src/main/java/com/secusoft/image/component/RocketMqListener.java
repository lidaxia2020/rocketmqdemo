package com.secusoft.image.component;

import com.secusoft.image.model.AsyncImageDto;
import com.secusoft.image.util.JacksonUtils;
import com.secusoft.image.util.RESTClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.*;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Component
public class RocketMqListener implements InitializingBean {
    private final Logger logger = LoggerFactory.getLogger(RocketMqListener.class);
    private static final String IMAGE_KEY = "origimage";
    private static final String OFFSET_NEW = "offset_new";
    private MQPullConsumerScheduleService consumer;
    private final String ASYNCIMAGE = "/algorithm/asyncimage";
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
    @Resource(
            name = "taskExecutor"
    )
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RedisTemplate redisTemplate;

    public static final String WHITE_CAMERAID = "white_cameraId_";

    public RocketMqListener() {
    }

    @PostConstruct
    private void init() {
        BoundListOperations boundListOperations = this.redisTemplate.boundListOps("origimage");
        Executors.newFixedThreadPool(1).submit(() -> {
            while(true) {
                String rightPop = (String)boundListOperations.rightPop();
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
                    if (!RocketMqListener.this.redisTemplate.hasKey("offset_new")) {
                        consumer.updateConsumeOffset(mq, pullResult.getMaxOffset());
                        valueOperations.set("offset_new", "offset_new");
                        return;
                    }

                    switch(pullResult.getPullStatus()) {
                        case FOUND:
                            List<MessageExt> messageExtList = pullResult.getMsgFoundList();
                            Iterator var9 = messageExtList.iterator();

                            while(var9.hasNext()) {
                                MessageExt m = (MessageExt)var9.next();
                                RocketMqListener.this.receive(new String(m.getBody(), "utf-8"));
                            }
                        case NO_MATCHED_MSG:
                        case NO_NEW_MSG:
                        case OFFSET_ILLEGAL:
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
        BoundListOperations boundListOperations = this.redisTemplate.boundListOps("origimage");
        boundListOperations.leftPush(body);
        if (boundListOperations.size() > 1000L) {
            throw new Exception("Redis data cache over 1000 ");
        }
    }

    private void handle(String body) {
        this.logger.info("handle redis data：{}", body);
        Map<String, Object> stringObjectMap = JacksonUtils.json2map(body);
        if (stringObjectMap != null) {
            List<Object> objList = (List)stringObjectMap.get("objList");
            if (objList == null || objList.size() == 0) {
                this.logger.error("message  not have objList");
                return;
            }

            String origImageContent = (String)stringObjectMap.get("origImage");
            HashMap map = (HashMap)objList.get(0);
            String cameraId = (String)map.get("cameraId");
            Long timestamp = (Long)map.get("timestamp");
            AsyncImageDto asyncImageDto = new AsyncImageDto();
            asyncImageDto.setTimestamp(timestamp);
            asyncImageDto.setChannelId(cameraId);
            asyncImageDto.setRawDataType("base64");
            asyncImageDto.setAlgorithmType(this.algorithmType);
            asyncImageDto.setRawData(origImageContent);
            Map req = new HashMap();
            req.put("data", asyncImageDto);
            String res = RESTClient.getClientConnectionPool().fetchByPostMethod(this.algorithmUrl + "/algorithm/asyncimage", JacksonUtils.map2Json(req));
            if (StringUtils.isEmpty(res)) {
                this.logger.error("send asyncimage fail, sava redis");
                this.redisTemplate.boundListOps("origimage").leftPush(body);

                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException var12) {
                    var12.printStackTrace();
                }
            }
        }

    }
}
