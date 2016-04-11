package com.xrkj.app.heatdev.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;

/**
 * Created by wwh on 2016/2/26.
 */
public class Converts {


    private Converts() {
    }

    private static byte toByte(char c) {
        byte b = (byte) "0123456789ABCDEF".indexOf(c);
        return b;
    }

    /**
     * 把16进制字符串转换成字节数组
     *
     * @param hex
     * @return
     */
    public static byte[] hexStringToByte(String hex) {
        int len = (hex.length() / 2);
        byte[] result = new byte[len];
        char[] achar = hex.toCharArray();
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            result[i] = (byte) (toByte(achar[pos]) << 4 | toByte(achar[pos + 1]));
        }
        return result;
    }

    /**
     * 把字节数组转换成16进制字符串
     *
     * @param bArray
     * @return
     */
    public static final String bytesToHexString(byte... bArray) {
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < bArray.length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }


    /**
     * 把字节数组转换为对象
     *
     * @param bytes
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static final Object bytesToObject(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        ObjectInputStream oi = new ObjectInputStream(in);
        Object o = oi.readObject();
        oi.close();
        return o;
    }

    public static DecimalFormat TEMPERATURE_FORMAT = new DecimalFormat("#.0");

    /**
     * <pre>
     * 两个字节转换成温度
     * 分两个字节存储，第一个字节前4 bit，第二个字节8bit
     * 将结果 乘以 0.0625°C
     * </pre>
     *
     * @param MSB
     * @param LSB
     * @return 带正负号，保留1位小数
     */
    public static final String temperature(byte MSB, byte LSB) {
        // 先判断是正温度还是负温度
        if ((MSB >> 3) == 0) {// 正温度
            int t = (((MSB & 0xF) << 8) | (LSB & 0xFF));
            float temp = (float) (t * 0.0625);
            return TEMPERATURE_FORMAT.format(temp);
        } else {// 负温度
            int t = (((MSB & 0xF) << 8) | (LSB & 0xFF));
            // 取反，加1
            t = (0xFFF ^ t) + 1;
            float temp = (float) (t * 0.0625);
            return "-" + TEMPERATURE_FORMAT.format(temp);
        }
    }
}
