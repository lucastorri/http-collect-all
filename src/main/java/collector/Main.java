package collector;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.logging.ByteLoggingHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;


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

        {
            System.out.println(FrontendHandler.class);
        }

        private static URI createBackendUriFromFrontendReq(HttpRequest req) throws Exception {
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

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) msg;

                URI backendUri = createBackendUriFromFrontendReq(req);
                final HttpRequest backendReq = createBackendReqFromFrontendReq(backendUri, req);
                final Channel frontendChannel = ctx.channel();

                System.out.println(req);

                Bootstrap b = new Bootstrap();
                b.group(frontendChannel.eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addFirst(new ByteLoggingHandler(LogLevel.INFO));
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
                        System.out.println("operationComplete: " + future);
                        System.out.println("isActive(): " + future.channel().isActive());
                        System.out.println("isOpen(): " + future.channel().isOpen());
                        future.channel().outboundMessageBuffer().add(backendReq);
                    }
                });

                if (is100ContinueExpected(req)) {
                    send100Continue(ctx);
                }

            }

            if (msg instanceof HttpContent) {
                final HttpContent httpContent = (HttpContent) msg;

                backendFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        System.out.println("adding content");
                        future.channel().outboundMessageBuffer().add(httpContent.content());
                    }
                });

                if (msg instanceof LastHttpContent) {
                    LastHttpContent trailer = (LastHttpContent) msg;
                    backendFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            System.out.println("reading backend");
                            //future.channel().read();
                        }
                    });
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
                    System.out.println("flushing");
                    future.channel().flush();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
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
}
