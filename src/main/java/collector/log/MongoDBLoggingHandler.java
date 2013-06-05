package collector.log;

import collector.ProtocolDefinerHandler;
import com.allanbank.mongodb.*;
import com.allanbank.mongodb.bson.builder.BuilderFactory;
import com.allanbank.mongodb.bson.builder.DocumentBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.util.Set;

@ChannelHandler.Sharable
public class MongoDBLoggingHandler extends ChannelDuplexHandler implements ChannelInboundByteHandler, ChannelOutboundByteHandler {

    private static final MongoCollection chunks;
    private static final MongoCollection closed;
    private static final MongoCollection metadata;

    private final Layer layer;
    private final String requestId;
    private int requestNumber;
    private int inboundCount;
    private int outboundCount;


    static {
        MongoClientConfiguration config = new MongoClientConfiguration();
        config.addServer("localhost:27017");
        MongoClient mongoClient = MongoFactory.createClient(config);
        MongoDatabase database = mongoClient.getDatabase("requests");
        chunks = database.getCollection("chunks");
        closed = database.getCollection("closed");
        metadata = database.getCollection("metadata");
    }

    public MongoDBLoggingHandler(Layer layer, String requestId) {
        this.layer = layer;
        this.requestId = requestId;
        this.requestNumber = 0;
        this.inboundCount = 0;
        this.outboundCount = 0;
    }

    public MongoDBLoggingHandler logMetadata(Set<ProtocolDefinerHandler.Protocol> protocols, int port) {
        DocumentBuilder document = BuilderFactory.start()
            .add("request", id())
            .add("port", port)
            .add("ssl", protocols.contains(ProtocolDefinerHandler.Protocol.SSL))
            .add("gzip", protocols.contains(ProtocolDefinerHandler.Protocol.GZIP));
        metadata.insertAsync(document);
        return this;
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

    protected void inbound(ByteBuf buf) {
        store(Direction.INBOUND, inboundCount++, buf);
    }
    
    protected void outbound(ByteBuf buf) {
        store(Direction.OUTBOUND, outboundCount++, buf);
    }

    private void store(Direction direction, int index, ByteBuf buf) {
        DocumentBuilder document = BuilderFactory.start()
            .add("timestamp", System.currentTimeMillis())
            .add("request", id())
            .add("layer", layer.name())
            .add("direction", direction.name())
            .add("index", index)
            .add("content", toByteArray(buf));
        chunks.insertAsync(document);
    }

    private byte[] toByteArray(ByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.getBytes(0, b);
        return b;
    }

    private String id() {
        return requestId + "-" + requestNumber;
    }

    public void nextRequest() {
        inboundCount = outboundCount = 0;
        requestNumber++;
    }

    public void saveClosed() {
        DocumentBuilder document = BuilderFactory.start()
            .add("timestamp", System.currentTimeMillis())
            .add("request", id())
            .add("layer", layer.name());
        closed.insertAsync(document);
    }

    public static enum Layer { FRONTEND, BACKEND }
    private static enum Direction { INBOUND, OUTBOUND }
}
