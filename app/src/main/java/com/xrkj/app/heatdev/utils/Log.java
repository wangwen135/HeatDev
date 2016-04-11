package com.xrkj.app.heatdev.utils;

/**
 * Created by wwh on 2016/3/4.
 */
public class Log {
    public static int logLevel = android.util.Log.ERROR;

    public static void v(String tag, String msg) {
        if (logLevel <= android.util.Log.VERBOSE)
            android.util.Log.v(tag, msg);
    }

    public static void d(String tag, String msg) {
        if (logLevel <= android.util.Log.DEBUG)
            android.util.Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        if (logLevel <= android.util.Log.INFO)
            android.util.Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (logLevel <= android.util.Log.WARN)
            android.util.Log.w(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (logLevel <= android.util.Log.ERROR)
            android.util.Log.e(tag, msg);
    }

    public static void e(String tag,String msg,Throwable e){
        if (logLevel <= android.util.Log.ERROR)
            android.util.Log.e(tag, msg,e);
    }

}
