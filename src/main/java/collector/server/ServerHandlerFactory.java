package collector.server;

import collector.data.DataStores;
import collector.data.RequestData;
import collector.data.UserRegistry;

public class ServerHandlerFactory {

    private final DataStores data;

    public ServerHandlerFactory(final UserRegistry users, final RequestData requests) {
        this.data = new DataStores() {
            @Override
            public UserRegistry users() {
                return users;
            }

            @Override
            public RequestData requests() {
                return requests;
            }
        };
    }

    public ProtocolHandler newInstance() {
        return new ProtocolHandler(data);
    }

}
