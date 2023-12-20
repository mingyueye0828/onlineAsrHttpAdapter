package shtel.noc.asr.adapter.onlinehttp.handlers.common.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.HealthyCheck;


/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation alive探针
 */
public class AliveHandler implements Handler<RoutingContext> {
    private static final String MESSAGE = "alive";

    @Override
    public void handle(RoutingContext routingContext) {
        if (HealthyCheck.isHealthy()) {
            routingContext.response().end(MESSAGE);
        }
    }
}
