package shtel.noc.asr.adapter.onlinehttp.handlers.common.exception;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation Redis锁获取异常类
 */
public class RedisException extends RuntimeException{
    public RedisException(String msg) {
        super(msg, new Throwable(msg));
    }

    public RedisException(String msg, Throwable thrown) {
        super(msg, thrown);
    }
}
