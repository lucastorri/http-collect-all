package collector.server;

import collector.log.LoggingHandler;
import collector.log.RequestLogger;
import collector.server.ProtocolHandler.Protocol;
import io.netty.channel.ChannelHandler;

import java.util.Collections;
import java.util.Set;

public class RequestConf {

    private final Set<Protocol> protocols;
    private final LoggingHandler frontendHandler;
    private final LoggingHandler backendHandler;
    private int port;

    public RequestConf(ServerConf conf, String requestId, int port, Set<Protocol> protocols) {
        this.port = port;
        this.protocols = Collections.unmodifiableSet(protocols);

        frontendHandler = new LoggingHandler(conf.requests(), LoggingHandler.Layer.FRONTEND, requestId);
        backendHandler = frontendHandler.backend();
    }

    public Set<Protocol> protocols() {
        return protocols;
    }

    public ChannelHandler frontendHandler() {
        return frontendHandler;
    }

    public ChannelHandler backendHandler() {
        return backendHandler;
    }

    public RequestLogger frontendLogger() {
        return frontendHandler;
    }

    public RequestLogger backendLogger() {
        return backendHandler;
    }

    public boolean hasProtocol(Protocol protocol) {
        return protocols().contains(protocol);
    }

    public boolean isHttps() {
        return hasProtocol(Protocol.SSL);
    }

    public int port() {
        return this.port;
    }

    public void metadata(String user, String bucket) {
        frontendLogger().metadata(protocols, port, user, bucket);
    }

    public void next() {
        backendHandler.closed();
        backendHandler.next();
        frontendHandler.next();
    }
}
