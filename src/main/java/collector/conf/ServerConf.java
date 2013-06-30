package collector.conf;

import collector.data.RequestData;
import collector.data.UserRegistry;
import collector.server.ProtocolHandler;
import io.netty.channel.ChannelHandler;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.util.Collections.unmodifiableList;

public enum ServerConf implements Closeable {

    instance;

    private final boolean debug;
    private final String host;
    private final List<Integer> ports;
    private final RequestData requests;
    private final UserRegistry users;

    private ServerConf() {
        String mongoHost = env("MONGO_HOST", "localhost");
        int mongoPort = parseInt(env("MONGO_PORT", "27017"));
        requests = RequestData.connect(mongoHost, mongoPort);

        String redisHost = env("REDIS_HOST", "localhost");
        int redisPort = parseInt(env("REDIS_PORT", "6379"));
        users = UserRegistry.connect(redisHost, redisPort);

        host = env("HC_HOST", "local");
        ports = parseInts(env("HC_PORTS", "8080"));

        debug = parseBoolean(env("HC_DEBUG", "false"));
    }

    public UserRegistry users() {
        return users;
    }

    public RequestData requests() {
        return requests;
    }

    public boolean debug() {
        return this.debug;
    }

    public String hostname() {
        return this.host;
    }

    public ChannelHandler handler() {
        return new ProtocolHandler(this);
    }

    public void close() {
        try {
            users.close();
        } catch (Exception e) {
        }
        try {
            requests().close();
        } catch (Exception e) {
        }
    }

    private static String env(String flag, String orElse) {
        String value = System.getenv(flag);
        return (value != null) ? value : orElse;
    }

    private static List<Integer> parseInts(String ints) {
        String[] split = ints.split(",");
        ArrayList parsed = new ArrayList(split.length);
        for (String i : split) {
            parsed.add(parseInt(i.trim()));
        }
        return unmodifiableList(parsed);
    }

    public List<Integer> ports() {
        return ports;
    }
}