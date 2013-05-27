package collector.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;

public class HttpBackendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private Channel frontendChannel;

    public HttpBackendHandler(Channel frontendChannel) {
        this.frontendChannel = frontendChannel;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
            msg = ((HttpContent) msg).copy();
        }
        frontendChannel.write(msg);
    }

    @Override
    public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        frontendChannel.flush().addListener(ChannelFutureListener.CLOSE);
        ctx.flush().addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
