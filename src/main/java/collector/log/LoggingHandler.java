package collector.log;

import collector.server.ProtocolHandler;
import collector.data.RequestData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.util.Set;

@ChannelHandler.Sharable
public class LoggingHandler extends ChannelDuplexHandler implements ChannelInboundByteHandler, ChannelOutboundByteHandler {

    private final RequestData requests;

    private final Layer layer;
    private final String requestId;
    private int requestNumber;
    private int inboundCount;
    private int outboundCount;

    public LoggingHandler(RequestData requests, Layer layer, String requestId) {
        this.requests = requests;
        this.layer = layer;
        this.requestId = requestId;
        this.requestNumber = this.inboundCount = this.outboundCount = 0;
    }

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelHandlerUtil.allocate(ctx);
    }

    @Override
    public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundByteBuffer().discardSomeReadBytes();
    }

    @Override
    public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelHandlerUtil.allocate(ctx);
    }

    @Override
    public void discardOutboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundByteBuffer().discardSomeReadBytes();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        ByteBuf buf = ctx.inboundByteBuffer();
        inbound(buf);
        ctx.nextInboundByteBuffer().writeBytes(buf);
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ByteBuf buf = ctx.outboundByteBuffer();
        outbound(buf);
        ctx.nextOutboundByteBuffer().writeBytes(buf);
        ctx.flush(promise);
    }

    private void inbound(ByteBuf buf) {
        store(Direction.INBOUND, inboundCount++, buf);
    }
    
    private void outbound(ByteBuf buf) {
        store(Direction.OUTBOUND, outboundCount++, buf);
    }

    private void store(Direction direction, int index, ByteBuf buf) {
        requests.chunk(id(), layer, direction, index, buf);
    }

    private String id() {
        return requestId + "-" + requestNumber;
    }

    public void metadata(Set<ProtocolHandler.Protocol> protocols, int port, String user, String bucket) {
        requests.metadata(id(), user, bucket, port, protocols);
    }

    public void next() {
        inboundCount = outboundCount = 0;
        requestNumber++;
    }

    public void closed() {
        requests.closed(id(), layer);
    }

    public void error(Throwable cause) {
        requests.error(id(), layer, cause);
    }

    public LoggingHandler backend() {
        return new LoggingHandler(requests, LoggingHandler.Layer.BACKEND, requestId);
    }

    public static enum Layer { FRONTEND, BACKEND }
    public static enum Direction { INBOUND, OUTBOUND }
}
