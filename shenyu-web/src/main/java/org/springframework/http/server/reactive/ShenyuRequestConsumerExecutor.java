package org.springframework.http.server.reactive;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.logging.Log;
import org.apache.shenyu.disruptor.consumer.QueueConsumerExecutor;
import org.apache.shenyu.disruptor.consumer.QueueConsumerFactory;
import org.apache.shenyu.web.server.ShenyuServerExchange;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpMethod;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.URISyntaxException;

public class ShenyuRequestConsumerExecutor<T extends ShenyuServerExchange> extends QueueConsumerExecutor<T> {
    
    private static final Log LOGGER = HttpLogging.forLogName(ShenyuRequestConsumerExecutor.class);
    
    @Override
    public void run() {
        T data = getData();
        ShenyuServerExchange exchange = data;
        
        new Thread(() -> {
            HttpServerRequest reactorRequest = exchange.getReactorRequest();
            HttpServerResponse reactorResponse = exchange.getReactorResponse();
            HttpHandler httpHandler = exchange.getHttpHandler();
            
            NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(reactorResponse.alloc());
            try {
                ReactorServerHttpRequest request = new ReactorServerHttpRequest(reactorRequest, bufferFactory);
                ServerHttpResponse response = new ReactorServerHttpResponse(reactorResponse, bufferFactory);
                
                if (request.getMethod() == HttpMethod.HEAD) {
                    response = new HttpHeadResponseDecorator(response);
                }
                httpHandler.handle(request, response)
                        .doOnError(ex -> LOGGER.trace(request.getLogPrefix() + "Failed to complete: " + ex.getMessage()))
                        .doOnSuccess(aVoid -> LOGGER.trace(request.getLogPrefix() + "Handling completed"))
                        .subscribe();
            } catch (URISyntaxException ex) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Failed to get request URI: " + ex.getMessage());
                }
                reactorResponse.status(HttpResponseStatus.BAD_REQUEST);
            }
        }).start();
    }
    
    public static class ShenyuRequestConsumerExecutorFactory<T extends ShenyuServerExchange> implements QueueConsumerFactory<T> {
        @Override
        public ShenyuRequestConsumerExecutor<T> create() {
            
            return new ShenyuRequestConsumerExecutor<>();
        }
    
        @Override
        public String fixName() {
            return "shenyu_request";
        }
    }
}
