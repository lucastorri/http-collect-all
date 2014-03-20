package collector.log;

import collector.server.ProtocolHandler;

import java.util.Set;

public interface RequestLogger {

    void metadata(Set<ProtocolHandler.Protocol> protocols, int port, String user, String bucket);

    void next();

    void closed();

    void error(Throwable cause);

}
