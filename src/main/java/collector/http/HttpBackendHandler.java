package collector.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;

public class HttpBackendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private final Channel frontendChannel;
    private boolean keepAlive;

    public HttpBackendHandler(Channel frontendChannel) {
        this.frontendChannel = frontendChannel;
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
