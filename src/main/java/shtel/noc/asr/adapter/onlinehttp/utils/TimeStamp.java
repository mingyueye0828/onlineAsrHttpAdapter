package shtel.noc.asr.adapter.onlinehttp.utils;

import java.text.SimpleDateFormat;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 获取时间戳
 */
public class TimeStamp {
    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳
     */
    public static String currentTimeStamp() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return df.format(SystemClock.millisClock().now());
    }

    /**
     * 获取指定时间之前的时间戳
     *
     * @param timeDiff 指定的时间差，单位为秒
     * @return 指定时间差之前的时间戳
     */
    public static String beforeTimeStamp(long timeDiff) {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return df.format(SystemClock.millisClock().now() - timeDiff);
    }
}
