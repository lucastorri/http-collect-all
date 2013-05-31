package collector;

import collector.http.HttpFrontendHandler;
import collector.log.MongoDBLoggingHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.ByteLoggingHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProtocolDefinerHandler extends ChannelInboundByteHandlerAdapter {

    private final boolean detectGzip;
    private final boolean detectSsl;
    private final List<Protocol> protocols;

    public ProtocolDefinerHandler() {
        this(true, true, Collections.<Protocol>emptyList());
    }

    private ProtocolDefinerHandler(boolean detectSsl, boolean detectGzip, List<Protocol> protocols) {
        this.detectSsl = detectSsl;
        this.detectGzip = detectGzip;
        this.protocols = protocols;
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // Will use the first five bytes to detect a protocol.
        if (in.readableBytes() < 5) {
            return;
        }

        if (isSsl(in)) {
            enableSsl(ctx);
        } else {
            final int magic1 = in.getUnsignedByte(in.readerIndex());
            final int magic2 = in.getUnsignedByte(in.readerIndex() + 1);
            if (isGzip(magic1, magic2)) {
                enableGzip(ctx);
            } else if (isHttp(magic1, magic2)) {
                switchToHttp(ctx);
            } else {
                // Unknown protocol; discard everything and close the connection.
                in.clear();
                ctx.close();
            }
        }
    }

    private boolean isSsl(ByteBuf buf) {
        if (detectSsl) {
            return SslHandler.isEncrypted(buf);
        }
        return false;
    }

    private boolean isGzip(int magic1, int magic2) {
        if (detectGzip) {
            return magic1 == 31 && magic2 == 139;
        }
        return false;
    }

    private static boolean isHttp(int magic1, int magic2) {
        return
            magic1 == 'G' && magic2 == 'E' || // GET
            magic1 == 'P' && magic2 == 'O' || // POST
            magic1 == 'P' && magic2 == 'U' || // PUT
            magic1 == 'H' && magic2 == 'E' || // HEAD
            magic1 == 'O' && magic2 == 'P' || // OPTIONS
            magic1 == 'P' && magic2 == 'A' || // PATCH
            magic1 == 'D' && magic2 == 'E' || // DELETE
            magic1 == 'T' && magic2 == 'R' || // TRACE
            magic1 == 'C' && magic2 == 'O';   // CONNECT
    }

    private void enableSsl(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();

        SSLEngine engine = //TODO create real ssl certificate
                SecureChatSslContextFactory.getServerContext().createSSLEngine();
        engine.setUseClientMode(false);

        p.addLast("ssl", new SslHandler(engine));
        p.addLast("unificationA", new ProtocolDefinerHandler(false, detectGzip, protocolsWith(Protocol.SSL)));
        p.remove(this);
    }

    private void enableGzip(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        p.addLast("gzipdeflater", ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
        p.addLast("gzipinflater", ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
        p.addLast("unificationB", new ProtocolDefinerHandler(detectSsl, false, protocolsWith(Protocol.GZIP)));
        p.remove(this);
    }

    private void switchToHttp(ChannelHandlerContext ctx) {
        String requestId = Long.toString(System.nanoTime());

        ChannelPipeline p = ctx.pipeline();
        int port = ((InetSocketAddress) ctx.channel().localAddress()).getPort();
        p.addLast("store", new MongoDBLoggingHandler(MongoDBLoggingHandler.Layer.FRONTEND, requestId).logMetadata(protocols, port));
        p.addLast("logging", new ByteLoggingHandler(LogLevel.INFO));
        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("deflater", new HttpContentCompressor());
        p.addLast("handler", new HttpFrontendHandler(protocols, requestId));
        p.remove(this);
    }

    private List<Protocol> protocolsWith(Protocol p) {
        List<Protocol> newProtocols = new ArrayList<>(protocols.size() + 1);
        newProtocols.add(p);
        return newProtocols;
    }

    public static enum Protocol {
        GZIP, SSL;
    }

}
