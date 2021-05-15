package com.secusoft.image.model;

/**
 * @author lijiannan
 * @version 1.0
 * @date 2021/4/29 15:38
 */
public class AsyncImageDto {

    /**
     * 图⽚流Id，即图⽚卡⼝设备的deivceId
     */
    private String channelId;

    /**
     * 图⽚流时间戳(long类型)，为1970年1⽉ 1⽇ 0:0:0⾄今的
     * 毫秒
     */
    private Long timestamp;

//    /**
//     * 图⽚流计算任务所处理的算法类型。⽀持的类型如下
//     * orig_all: 原图crop_person：⾏⼈⼩图crop_face：⼈脸
//     * ⼩图crop_bicycle：⾮机动⻋⼩图crop_vehicle：机动⻋
//     * ⼩图
//     */
    private String algorithmType;

    private String rawDataType;

    /**
     * 调⽤时传输的原始数据， json格式，⽀持⾃定义输⼊扩
     * 展
     */
    private String rawData;

    public String getRawDataType() {
        return rawDataType;
    }

    public void setRawDataType(String rawDataType) {
        this.rawDataType = rawDataType;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    public String getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(String algorithmType) {
        this.algorithmType = algorithmType;
        }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    @Override
    public String toString() {
        return "AsyncImageDto{" +
                "channelId='" + channelId + '\'' +
                ", timestamp=" + timestamp +
//                ", algorithmType='" + algorithmType + '\'' +
                ", rawDataType='" + rawDataType + '\'' +
                ", rawData=" + rawData +
                '}';
    }
}
