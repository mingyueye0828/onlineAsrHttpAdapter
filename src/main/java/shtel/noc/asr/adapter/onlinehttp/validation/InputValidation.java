package shtel.noc.asr.adapter.onlinehttp.validation;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.json.schema.Schema;
import io.vertx.json.schema.SchemaParser;
import io.vertx.json.schema.SchemaRouter;
import io.vertx.json.schema.SchemaRouterOptions;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.json.schema.common.dsl.Schemas.objectSchema;
import static io.vertx.json.schema.common.dsl.Schemas.stringSchema;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 入参校验，查看发送的参数是否包含下面的参数
 */
@Slf4j
public class InputValidation implements Handler<RoutingContext> {
    private final Schema schema;

    //uid(1) auf(0) audioStatus(1) audioData(1) modelId(1) appId(0) callInfo(0) params(0)
    public InputValidation(Vertx vertx) {
        SchemaRouter schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
        SchemaParser schemaParser = SchemaParser.createDraft201909SchemaParser(schemaRouter);
        this.schema = objectSchema()
                .requiredProperty("uid", stringSchema())
                .requiredProperty("audioStatus", stringSchema())
                .requiredProperty("audioData", stringSchema())
                .requiredProperty("modelId", stringSchema())
                .build(schemaParser);
    }

    @Override
    public void handle(RoutingContext context) {
        schema.validateAsync(context.getBodyAsJson())
                //// NOTE: 2021/7/21 .onFailure(routingContext::fail) 这儿直接就返回400了，不进FailureHandler处理
                //这里可以自动返回缺少那些参数，或者信息
                .onFailure(rf -> context.response().setStatusCode(400).end(rf.getMessage()))
                .onSuccess(res -> context.next());
    }
}
