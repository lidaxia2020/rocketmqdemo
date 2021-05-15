package com.secusoft.image.controller;

import com.secusoft.image.component.RocketMqListener;
import com.secusoft.image.component.RocketMqProducer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * @author lijiannan
 * @version 1.0
 * @date 2021/4/29 13:50
 */
@RestController
public class RocketMqController {

    private final Logger logger = LoggerFactory.getLogger(RocketMqController.class);

    @Value("${rocketmq.topic.name}")
    private String topic;

    @Autowired
    private RocketMqProducer rocketMqProducer;

    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping("/testMq")
    public String testMq(@RequestBody String test) {

        Message message = new Message(topic, test.getBytes());

        SendResult send = null;
        for (int i = 0; i< 20000; i++){
            try {
                // rocketMqProducer.getProducer().createTopic("test",topic, 8);
                send = rocketMqProducer.getProducer().send(message);
            } catch (MQClientException e) {
                logger.error(e.getMessage());
            } catch (RemotingException e) {
                logger.error(e.getMessage());
            } catch (MQBrokerException e) {
                logger.error(e.getMessage());
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }
        }



        System.out.println(send);
        return "success";
    }


    @GetMapping("/getWhiteCametaId")
    public Set getWhiteCametaId(){
        return redisTemplate.keys(RocketMqListener.WHITE_CAMERAID + "*");
    }

    @PostMapping("/delWhiteCametaId")
    public Long delWhiteCametaId(@RequestBody List<String> list){

        return redisTemplate.delete(list);
    }

    @PostMapping("/addWhiteCametaId")
    public String addWhiteCametaId(@RequestBody List<String> list){
        ValueOperations valueOperations = redisTemplate.opsForValue();
        for (String str: list){
            valueOperations.setIfAbsent(RocketMqListener.WHITE_CAMERAID + str, str);
        }

        return "success";
    }


}
