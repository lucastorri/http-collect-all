package collector.data;

import collector.ProtocolDefinerHandler;
import collector.log.LoggingHandler.Direction;
import collector.log.LoggingHandler.Layer;
import com.allanbank.mongodb.*;
import com.allanbank.mongodb.bson.builder.BuilderFactory;
import com.allanbank.mongodb.bson.builder.DocumentBuilder;
import io.netty.buffer.ByteBuf;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;
import java.util.concurrent.Future;

public class RequestData {

    private final MongoDatabase database;
    private final MongoClient client;

    private final MongoCollection chunks;
    private final MongoCollection closed;
    private final MongoCollection metadata;
    private final MongoCollection error;

    private RequestData(MongoClient client) {
        this.client = client;

        this.database = client.getDatabase("requests");

        this.chunks = database.getCollection("chunks");
        this.closed = database.getCollection("closed");
        this.metadata = database.getCollection("metadata");
        this.error = database.getCollection("error");
    }

    public Future<Integer> closed(String request, Layer layer) {
        DocumentBuilder doc = BuilderFactory.start()
            .add("timestamp", System.currentTimeMillis())
            .add("request", request)
            .add("layer", layer.name());
        return closed.insertAsync(doc);
    }

    public Future<Integer> chunk(String request, Layer layer, Direction direction, int index, ByteBuf buf) {
        DocumentBuilder document = BuilderFactory.start()
            .add("timestamp", System.currentTimeMillis())
            .add("request", request)
            .add("layer", layer.name())
            .add("direction", direction.name())
            .add("index", index)
            .add("content", toByteArray(buf));
        return chunks.insertAsync(document);
    }

    public Future<Integer> error(String request, Layer layer, Throwable cause) {
        ByteArrayOutputStream causeString = new ByteArrayOutputStream();
        cause.printStackTrace(new PrintStream(causeString));
        DocumentBuilder document = BuilderFactory.start()
            .add("timestamp", System.currentTimeMillis())
            .add("request", request)
            .add("layer", layer.name())
            .add("message", cause.getMessage())
            .add("trace", causeString.toString());
        return error.insertAsync(document);
    }

    public Future<Integer> metadata(String request, String user, String bucket, int port, Set<ProtocolDefinerHandler.Protocol> protocols) {
        DocumentBuilder document = BuilderFactory.start()
            .add("request", request)
            .add("user", user)
            .add("bucket", bucket)
            .add("port", port)
            .add("ssl", protocols.contains(ProtocolDefinerHandler.Protocol.SSL))
            .add("gzip", protocols.contains(ProtocolDefinerHandler.Protocol.GZIP));
        return metadata.insertAsync(document);
    }

    private byte[] toByteArray(ByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.getBytes(0, b);
        return b;
    }

    public static RequestData connect(String host, int port) {
        MongoClientConfiguration conf = new MongoClientConfiguration();
        conf.addServer(host + ":" + port);
        return new RequestData(MongoFactory.createClient(conf));
    }
}
