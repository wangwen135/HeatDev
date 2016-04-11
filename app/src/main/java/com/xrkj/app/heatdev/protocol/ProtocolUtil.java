package com.xrkj.app.heatdev.protocol;

import com.xrkj.app.heatdev.exception.ProtocolException;

import java.util.Arrays;

/**
 * Created by wwh on 2016/3/2.
 */
public class ProtocolUtil {
    private ProtocolUtil() {

    }

    /**
     * 计算校验和
     * 包头+数据长度+功能码+数据
     *
     * @param entity
     * @return
     */
    public static ProtocolEntity calcVerify(ProtocolEntity entity) {
        int verify = 0;
        verify += entity.getHead();
        verify += entity.getLength();
        verify += entity.getFunction();
        byte[] data = entity.getData();
        if (data != null) {
            for (byte b : data) {
                verify += b;
            }
        }

        verify %= 255;

        entity.setVerify((byte) verify);

        return entity;
    }

    /**
     * 计算并填充0xFF 到allData
     *
     * @param entity
     * @return
     */
    public static byte[] calcFill(ProtocolEntity entity) {
        byte[] allData = new byte[ProtocolEntity.PROTOCOL_LENGTH];
        int index = 0;
        allData[index++] = entity.getHead();
        allData[index++] = entity.getLength();
        allData[index++] = entity.getFunction();
        byte[] data = entity.getData();

        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                allData[index++] = data[i];
            }
        }
        if (entity.getVerify() < 0) {
            calcVerify(entity);
        }

        allData[index++] = entity.getVerify();

        for (; index < allData.length; ) {
            allData[index++] = (byte) 0xFF;
        }

        entity.setAllData(allData);

        return allData;
    }

    /**
     * 将数据解析成对象
     * 如果解析失败将抛出 ProtocolException
     *
     * @param bytes
     * @return
     */
    public static ProtocolEntity resolve(byte[] bytes) {
        if (bytes.length != ProtocolEntity.PROTOCOL_LENGTH) {
            throw new ProtocolException();
        }

        byte head = bytes[0];
        if (head != ProtocolEntity.PROTOCOL_HEAD) {
            throw new ProtocolException();
        }

        int length = bytes[1];

        byte function = bytes[2];

        byte[] data = null;
        if (length > 0) {
            data = Arrays.copyOfRange(bytes, 3, length + 3);
        }

        byte verify = bytes[length + 3];

        ProtocolEntity protocolEntity = new ProtocolEntity(function, data);

        //对比校验位是否正确，如果不正确抛出异常？
        calcVerify(protocolEntity);
        if (verify != protocolEntity.getVerify()) {
            throw new ProtocolException();
        }

        return protocolEntity;

    }
}
