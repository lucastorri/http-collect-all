package collector.http;

import collector.log.LoggingHandler;
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
    private LoggingHandler frontendLogger;
    private LoggingHandler backendLogger;
    private boolean keepAlive;

    public HttpBackendHandler(Channel frontendChannel, LoggingHandler frontendLogger, LoggingHandler backendLogger) {
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
            backendLogger.closed();
            backendLogger.next();
            frontendLogger.next();
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
        backendLogger.error(cause);
        cause.printStackTrace();
        ctx.close();
        frontendChannel.close();
    }
}
