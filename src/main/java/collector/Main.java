package collector;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.ByteLoggingHandler;
import io.netty.handler.logging.LogLevel;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.getHost;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class Main {

    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) throws Exception {
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
                        p.addFirst(new ByteLoggingHandler(LogLevel.INFO));
                        // Uncomment the following line if you want HTTPS
                        //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
                        //engine.setUseClientMode(false);
                        //p.addLast("ssl", new SslHandler(engine));
                        p.addLast("decode", new HttpRequestDecoder());
                        p.addLast("encode", new HttpResponseEncoder());
                        //p.addLast("decode", new HttpResponseDecoder());
                        // Uncomment the following line if you don't want to handle HttpChunks.
                        //p.addLast("aggregator", new HttpObjectAggregator(1048576));
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

        private ChannelFuture backendFuture;

        private static URI createBackendUriFromFrontendReq(HttpRequest req, ChannelHandlerContext ctx) throws Exception {
            //TODO add bucket to pattern (optional)
            Pattern pattern = Pattern.compile("(.*)\\.local(-secure)?"); //TODO use server port for both http and https and learn if destination is secure from that
            Matcher matcher = pattern.matcher(getHost(req));
            matcher.find();
            String destHost = matcher.group(1);
            boolean isHttps = matcher.group(2) != null && !matcher.group(2).isEmpty();
            String destScheme = isHttps ? "https://" : "http://";
            int destPort = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
            String destPathAndQueryString = req.getUri();

            URI destUri = new URI(destScheme + destHost + ":" + destPort + destPathAndQueryString);

            return destUri;
        }

        private static HttpRequest createBackendReqFromFrontendReq(URI backendUri, HttpRequest inboundHeader) {
            HttpRequest outboundRequest = new DefaultHttpRequest(inboundHeader.getProtocolVersion(), inboundHeader.getMethod(), backendUri.getRawPath());
            for (Map.Entry<String, String> h: inboundHeader.headers()) {
                outboundRequest.headers().add(h.getKey(), h.getValue());
            }
            //TODO cookies
            outboundRequest.headers().set(HOST, backendUri.getHost());
            return outboundRequest;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) msg;

                URI backendUri = createBackendUriFromFrontendReq(req, ctx);
                final HttpRequest backendReq = createBackendReqFromFrontendReq(backendUri, req);
                final Channel frontendChannel = ctx.channel();

                final boolean ssl = "https".equals(backendUri.getScheme());

                Bootstrap b = new Bootstrap();
                b.group(frontendChannel.eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addFirst(new ByteLoggingHandler(LogLevel.INFO));

                            if (ssl) {
                                //TODO
                            }

                            p.addLast("encode", new HttpRequestEncoder());
                            p.addLast("decode", new HttpResponseDecoder());
                            p.addLast("inflater", new HttpContentDecompressor());
                            p.addLast("handler", new BackendHandler(frontendChannel));
                        }
                    });
                backendFuture = b.connect(backendUri.getHost(), backendUri.getPort());
                backendFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().outboundMessageBuffer().add(backendReq);
                    }
                });

                if (is100ContinueExpected(req)) {
                    send100Continue(ctx);
                }

                //TODO keepAlive
            }

            if (msg instanceof HttpContent) {
                final HttpContent httpContent = (HttpContent) msg;

                backendFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().outboundMessageBuffer().add(httpContent.content());
                    }
                });

                if (msg instanceof LastHttpContent) {
                    LastHttpContent trailer = (LastHttpContent) msg;
                    //TODO  trailer.trailingHeaders();
                }
            }
        }

        private static void send100Continue(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
            ctx.nextOutboundMessageBuffer().add(response);
        }

        @Override
        public void endMessageReceived(final ChannelHandlerContext ctx) throws Exception {
            backendFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    future.channel().flush();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }

    }

    static class BackendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

        private Channel frontendChannel;

        public BackendHandler(Channel frontendChannel) {
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
}
