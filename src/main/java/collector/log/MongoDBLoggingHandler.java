package collector.log;

import com.allanbank.mongodb.*;
import com.allanbank.mongodb.bson.builder.BuilderFactory;
import com.allanbank.mongodb.bson.builder.DocumentBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.util.CharsetUtil;

public class MongoDBLoggingHandler extends ChannelDuplexHandler implements ChannelInboundByteHandler, ChannelOutboundByteHandler {

    private static final MongoCollection chunks;
    private static final MongoCollection closed;

    private final Layer layer;
    private final String requestId;
    private int inboundCount;
    private int outboundCount;

    static {
        MongoClientConfiguration config = new MongoClientConfiguration();
        config.addServer("localhost:27017");
        MongoClient mongoClient = MongoFactory.createClient(config);
        MongoDatabase database = mongoClient.getDatabase("requests");
        chunks = database.getCollection("chunks");
        closed = database.getCollection("closed");
    }

    public MongoDBLoggingHandler(Layer layer, String requestId) {
        this.layer = layer;
        this.requestId = requestId;
        this.inboundCount = 0;
        this.outboundCount = 0;
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

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise future) throws Exception {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone()) {
                    DocumentBuilder document = BuilderFactory.start();
                    document.add("timestamp", System.currentTimeMillis());
                    document.add("request", requestId);
                    document.add("layer", layer.name());
                    closed.insertAsync(document);
                }
            }
        });
        super.close(ctx, future);
    }

    private void store(Direction direction, int index, ByteBuf buf) {
        DocumentBuilder document = BuilderFactory.start();
        document.add("timestamp", System.currentTimeMillis());
        document.add("request", requestId);
        document.add("layer", layer.name());
        document.add("direction", direction.name());
        document.add("index", index);
        document.add("content", buf.toString(CharsetUtil.UTF_8)/*toByteArray(buf)*/);
        //save protocols used (SSL, GZIP)
        //save port used
        chunks.insertAsync(document);
    }

    private byte[] toByteArray(ByteBuf buf) {
        ByteBuf copy = buf.copy();
        byte[] b = new byte[copy.readableBytes()];
        copy.getBytes(0, b);
        return b;
    }

    public static enum Layer { FRONTEND, BACKEND }
    private static enum Direction { INBOUND, OUTBOUND }
}
