package collector.data;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

public class UserRegistry implements Closeable {

    private final RedisAsyncConnection<String, String> connection;

    private UserRegistry(RedisClient redis) {
        this.connection = redis.connectAsync();
    }

    public boolean isRegistered(String username) {
        Future<String> ud = connection.get(username);
        try {
            return ud.get() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static UserRegistry connect(String host, int port) {
        RedisClient redis = new RedisClient(host, port);
        return new UserRegistry(redis);
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
