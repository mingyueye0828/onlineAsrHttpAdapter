package shtel.noc.asr.adapter.onlinehttp.handlers.common.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation ping探针
 */
public class PingHandler implements Handler<RoutingContext> {
    private static final String MESSAGE = "This is asr online http adapter!";
    @Override
    public void handle(RoutingContext context) {
        context.response().end(MESSAGE);
    }
}
