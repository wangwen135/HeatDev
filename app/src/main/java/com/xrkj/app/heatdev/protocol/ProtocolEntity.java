package com.xrkj.app.heatdev.protocol;

import com.xrkj.app.heatdev.utils.Converts;

/**
 * Created by wwh on 2016/3/2.
 */
public class ProtocolEntity {

    /**
     * 调整广播周期
     */
    public static final byte FUNCTION_BROADCAST = 0x00;
    /**
     * 控制PWM
     */
    public static final byte FUNCTION_PWM = 0x01;
    /**
     * 控制IO
     */
    public static final byte FUNCTION_IO = 0x02;
    /**
     * 温度上下阀值调节
     */
    public static final byte FUNCTION_TEMP_THRESHOLD = 0x03;

    /**
     * 查询状态
     */
    public static final byte FUNCTION_QUERY_STATUS = 0x04;

    /**
     * 协议长度
     */
    public static final int PROTOCOL_LENGTH = 20;

    /**
     * 固定的协议头
     */
    public static final byte PROTOCOL_HEAD = (byte) 0xFE;

    /**
     * 包头
     */
    private byte head = ProtocolEntity.PROTOCOL_HEAD;

    /**
     * 长度
     */
    private byte length = 0;

    /**
     * 功能
     */
    private byte function;

    /**
     * 数据
     */
    private byte[] data;

    /**
     * 校验和
     */
    private byte verify = -1;

    /**
     * 全部数据，包括补齐字节
     */
    private byte[] allData;

    /**
     * 构造方法
     *
     * @param function 功能代码
     * @param data     数据
     */
    public ProtocolEntity(byte function, byte... data) {
        this.function = function;
        this.data = data;
        if (data == null) {
            length = 0;
        } else {
            length = (byte) data.length;
        }
    }

    /**
     * 构造方法，内容为空
     *
     * @param function 功能代码
     */
    public ProtocolEntity(byte function) {
        this(function, null);
    }

    /**
     * 获取data中的某个字节
     *
     * @param index 从0开始
     * @return
     */
    public byte getData(int index) {
        if (data != null && data.length > index) {
            return data[index];
        }
        return -1;
    }

    public byte getHead() {
        return head;
    }

    public byte getLength() {
        return length;
    }

    public byte getFunction() {
        return function;
    }

    public byte[] getData() {
        return data;
    }

    public byte getVerify() {
        return verify;
    }

    public byte[] getAllData() {
        return allData;
    }

    public void setVerify(byte verify) {
        this.verify = verify;
    }

    public void setAllData(byte[] allData) {
        this.allData = allData;
    }

    @Override
    public String toString() {
        StringBuffer sbuf = new StringBuffer(super.toString());
        sbuf.append("\nhead=");
        sbuf.append(Converts.bytesToHexString(getHead()));
        sbuf.append("\nlength=");
        sbuf.append(Converts.bytesToHexString(getLength()));
        sbuf.append("\nfunction=");
        sbuf.append(Converts.bytesToHexString(getFunction()));
        sbuf.append("\ndata=");
        if (getData() != null)
            sbuf.append(Converts.bytesToHexString(getData()));
        sbuf.append("\nverify=");
        sbuf.append(Converts.bytesToHexString(getVerify()));

        return sbuf.toString();
    }
}
