package collector.http;

import collector.log.MongoDBLoggingHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.LastHttpContent;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

public class HttpBackendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private final Channel frontendChannel;
    private MongoDBLoggingHandler frontendLogger;
    private MongoDBLoggingHandler backendLogger;
    private boolean keepAlive;

    public HttpBackendHandler(Channel frontendChannel, MongoDBLoggingHandler frontendLogger, MongoDBLoggingHandler backendLogger) {
        this.frontendChannel = frontendChannel;
        this.frontendLogger = frontendLogger;
        this.backendLogger = backendLogger;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            keepAlive = isKeepAlive((HttpMessage) msg);
        }

        if (msg instanceof HttpContent) {
            msg = ((HttpContent) msg).copy();
        }
        frontendChannel.write(msg);
        frontendChannel.flush();

        if (msg instanceof LastHttpContent) {
            backendLogger.saveClosed();
            backendLogger.nextRequest();
            frontendLogger.nextRequest();
        }
    }

    @Override
    public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        if (!keepAlive) {
            ctx.flush().addListener(ChannelFutureListener.CLOSE);
            frontendChannel.flush().addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
        frontendChannel.close();
    }
}
