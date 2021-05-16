package com.lidaxia.image.component;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RocketMqProducer {
    @Value("${rocketmq.name-server}")
    private String addr;
    private String producerGroup = "pay_producer_group";
    private DefaultMQProducer producer;

    public RocketMqProducer() {
        this.producer = new DefaultMQProducer(this.producerGroup);
        this.producer.setNamesrvAddr(this.addr);
        this.start();
    }

    public DefaultMQProducer getProducer() {
        return this.producer;
    }

    public void start() {
        try {
            this.producer.start();
        } catch (MQClientException var2) {
            var2.printStackTrace();
        }

    }

    public void shutdown() {
        this.producer.shutdown();
    }
}
