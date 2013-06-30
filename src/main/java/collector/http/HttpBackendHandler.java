package collector.http;

import collector.conf.RequestConf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.LastHttpContent;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

public class HttpBackendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private final RequestConf reqConf;
    private final Channel frontend;
    private boolean keepAlive;

    public HttpBackendHandler(RequestConf reqConf, Channel frontend) {
        this.reqConf = reqConf;
        this.frontend = frontend;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            keepAlive = isKeepAlive((HttpMessage) msg);
        }

        if (msg instanceof HttpContent) {
            msg = ((HttpContent) msg).copy();
        }
        frontend.write(msg);
        frontend.flush();

        if (msg instanceof LastHttpContent) {
            reqConf.next();
        }
    }

    @Override
    public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        if (!keepAlive) {
            ctx.flush().addListener(ChannelFutureListener.CLOSE);
            frontend.flush().addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        reqConf.backendLogger().error(cause);
        cause.printStackTrace(System.err);
        ctx.close();
        frontend.close();
    }
}
