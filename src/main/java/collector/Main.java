package collector;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.example.proxy.HexDumpProxy;
import io.netty.handler.codec.http.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;


public class Main {

    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) throws Exception {
        HexDumpProxy.main(new String[] {"8080", "www.google.com", "80"});
    }

    public static void maina(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        // Uncomment the following line if you want HTTPS
                        //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
                        //engine.setUseClientMode(false);
                        //p.addLast("ssl", new SslHandler(engine));
                        p.addLast("decoder", new HttpRequestDecoder());
                        // Uncomment the following line if you don't want to handle HttpChunks.
                        //p.addLast("aggregator", new HttpObjectAggregator(1048576));
                        p.addLast("encoder", new HttpResponseEncoder());
                        // Remove the following line if you don't want automatic content compression.
                        //p.addLast("deflater", new HttpContentCompressor());
                        p.addLast("handler", new FrontendHandler());
                    }
                });

            Channel ch = b.bind(SERVER_PORT).sync().channel();
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    static class FrontendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

        private HttpRequest request;
        /** Buffer that stores the response content */
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpRequest) {
                HttpRequest request = this.request = (HttpRequest) msg;

                if (is100ContinueExpected(request)) {
                    send100Continue(ctx);
                }

            }

            if (msg instanceof HttpContent) {
                HttpContent httpContent = (HttpContent) msg;

                if (msg instanceof LastHttpContent) {
                    LastHttpContent trailer = (LastHttpContent) msg;
                }
            }
        }

        private static URI createBackendUriFromFrontendReq(HttpRequest req) throws Exception {
            //String rawHttpHeader = req.toString();

            Pattern pattern = Pattern.compile("(.*)\\.local(-secure)?");
            Matcher matcher = pattern.matcher(getHost(req));
            matcher.find();
            String destHost = matcher.group(1);
            String destScheme = matcher.group(2) != null && !matcher.group(2).isEmpty() ? "https://" : "http://";
            int destPort = 80; //TODO get local SERVER_PORT from req
            String destPathAndQueryString = req.getUri();

            URI destUri = new URI(destScheme + destHost + ":" + destPort + destPathAndQueryString);

            return destUri;
        }

        private static HttpRequest createBackendReqFromFrontendReq(URI backendUri, HttpRequest inboundHeader) {
            HttpRequest outboundRequest = new DefaultHttpRequest(inboundHeader.getProtocolVersion(), inboundHeader.getMethod(), backendUri.getRawPath());
            for (Map.Entry<String, String> h: inboundHeader.headers()) {
                outboundRequest.headers().add(h.getKey(), h.getValue());
            }
            outboundRequest.headers().set(HOST, backendUri.getHost());
            return outboundRequest;
        }

        private static void send100Continue(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
            ctx.nextOutboundMessageBuffer().add(response);
        }

        @Override
        public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }

    }
}
