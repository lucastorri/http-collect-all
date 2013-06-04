package collector.http;

import collector.ProtocolDefinerHandler;
import collector.log.MongoDBLoggingHandler;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.getHost;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpFrontendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private ChannelFuture backendFuture;
    private List<ProtocolDefinerHandler.Protocol> frontendProtocols;
    private String requestId;

    public HttpFrontendHandler(List<ProtocolDefinerHandler.Protocol> frontendProtocols, String requestId) {
        this.frontendProtocols = frontendProtocols;
        this.requestId = requestId;
    }

    private URI createBackendUriFromFrontendReq(HttpRequest req, ChannelHandlerContext ctx) throws Exception {
        //TODO add bucket to pattern (optional)
        Pattern pattern = Pattern.compile("(.*)\\.local"); //TODO use server port for both http and https and learn if destination is secure from that
        Matcher matcher = pattern.matcher(getHost(req));
        matcher.find();
        String destHost = matcher.group(1);
        String destScheme = isHttps() ? "https://" : "http://";
        int destPort = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
        String destPathAndQueryString = req.getUri();

        URI destUri = new URI(destScheme + destHost + ":" + destPort + destPathAndQueryString);

        return destUri;
    }

    private boolean isHttps() {
        return frontendProtocols.contains(ProtocolDefinerHandler.Protocol.SSL);
    }

    private HttpRequest createBackendReqFromFrontendReq(URI backendUri, HttpRequest inboundHeader) {
        String queryString = backendUri.getQuery() == null ? "" : "?" + backendUri.getQuery();
        HttpRequest outboundRequest = new DefaultHttpRequest(inboundHeader.getProtocolVersion(), inboundHeader.getMethod(), backendUri.getRawPath() + queryString);
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
                    p.addLast("store", new MongoDBLoggingHandler(MongoDBLoggingHandler.Layer.BACKEND, requestId));
                    p.addLast(new ByteLoggingHandler(LogLevel.INFO));
                    p.addLast("encode", new HttpRequestEncoder());
                    p.addLast("decode", new HttpResponseDecoder());
                    p.addLast("inflater", new HttpContentDecompressor());
                    p.addLast("handler", new HttpBackendHandler(frontendChannel));
                    }
                });
            final long timeBeforeConnect = System.currentTimeMillis();
            backendFuture = b.connect(backendUri.getHost(), backendUri.getPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    long timeToConnect = System.currentTimeMillis() - timeBeforeConnect;
                }
            });
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
