package collector.http;

import collector.server.ProtocolHandler;
import collector.server.ProtocolHandler.Protocol;
import collector.data.UserRegistry;
import collector.log.LoggingHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.ByteLoggingHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.getHost;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpFrontendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private static final String HOSTNAME = "local";
    public static final String HOST_PATTERN = "(.+)\\.(\\w[\\w\\d]*)-?([\\d\\w-]+)?\\." + HOSTNAME;

    private final UserRegistry users;
    private final LoggingHandler frontendLogger;
    private final LoggingHandler backendLogger;
    private final Set<ProtocolHandler.Protocol> frontendProtocols;

    private ChannelFuture backendFuture;
    private String user;
    private String bucket;

    public HttpFrontendHandler(UserRegistry users, Set<Protocol> frontendProtocols, LoggingHandler logger) {
        this.users = users;
        this.frontendProtocols = frontendProtocols;
        this.frontendLogger = logger;
        this.backendLogger = frontendLogger.backend();
    }

    private URI createBackendUriFromFrontendReq(HttpRequest req, ChannelHandlerContext ctx) throws Exception {
        return new URI(scheme() + host(req) + ":" + port(ctx) + req.getUri());
    }

    private String scheme() {
        return isHttps() ? "https://" : "http://";
    }

    private String host(HttpRequest req) {
        Pattern pattern = Pattern.compile(HOST_PATTERN);
        Matcher matcher = pattern.matcher(getHost(req));
        matcher.find();
        String destHost = matcher.group(1);
        user = matcher.group(2);
        bucket = matcher.group(3);
        return destHost;
    }

    private int port(ChannelHandlerContext ctx) {
        return ((InetSocketAddress) ctx.channel().localAddress()).getPort();
    }

    private boolean isHttps() {
        return frontendProtocols.contains(ProtocolHandler.Protocol.SSL);
    }

    private HttpRequest frontendRequestToBackendRequest(URI backendUri, HttpRequest inboundHeader) {
        String queryString = backendUri.getQuery() == null ? "" : "?" + backendUri.getQuery();
        HttpRequest outboundRequest = new DefaultHttpRequest(inboundHeader.getProtocolVersion(), inboundHeader.getMethod(), backendUri.getRawPath() + queryString);
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
            URI backendUri = createBackendUriFromFrontendReq(req, ctx);
            final HttpRequest backendReq = frontendRequestToBackendRequest(backendUri, req);
            final Channel frontendChannel = ctx.channel();
            frontendLogger.metadata(frontendProtocols, port(ctx), user, bucket);

            if (users.isRegistered(user)) {

                Bootstrap b = new Bootstrap();
                b.group(frontendChannel.eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        if (isHttps()) {
                            SSLEngine engine = //TODO create real ssl certificate check
                                SecureChatSslContextFactory.getClientContext().createSSLEngine();
                            engine.setUseClientMode(true);
                            p.addLast("ssl", new SslHandler(engine));
                        }
                        p.addLast("store", backendLogger);
                        p.addLast(new ByteLoggingHandler(LogLevel.INFO));
                        p.addLast("encode", new HttpRequestEncoder());
                        p.addLast("decode", new HttpResponseDecoder());
                        //p.addLast("inflater", new HttpContentDecompressor()); //not needed now, as we don't inspect the message content
                        p.addLast("handler", new HttpBackendHandler(frontendChannel, frontendLogger, backendLogger));
                        }
                    });
                final long timeBeforeConnect = System.currentTimeMillis();
                backendFuture = b.connect(backendUri.getHost(), backendUri.getPort());
                backendFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        long timeToConnect = System.currentTimeMillis() - timeBeforeConnect;
                        future.channel().outboundMessageBuffer().add(backendReq);
                    }
                });

                if (is100ContinueExpected(req)) {
                    send100Continue(ctx);
                }

            } else {
                DefaultHttpResponse forbidden = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN);
                ctx.write(forbidden);
                ctx.close();
            }

        }

        if (msg instanceof HttpContent) {
            final HttpContent httpContent = (HttpContent) msg;

            if (msg instanceof LastHttpContent) {
                LastHttpContent trailer = (LastHttpContent) msg;
                //TODO  trailer.trailingHeaders();
            }

            backendFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    future.channel().outboundMessageBuffer().add(httpContent.content());
                }
            });
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
        frontendLogger.error(cause);
        cause.printStackTrace();
        ctx.close();
    }

}
