package shtel.noc.asr.adapter.onlinehttp.handlers.common.exception;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 并发异常
 */
public class ConcurrencyException extends RuntimeException{
    public ConcurrencyException(String msg) {
        super(msg, new Throwable(msg));
    }

    public ConcurrencyException(String msg, Throwable thrown) {
        super(msg, thrown);
    }
}
