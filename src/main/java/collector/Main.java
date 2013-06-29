package collector;

import collector.data.RequestData;
import collector.data.UserRegistry;
import collector.server.Server;
import collector.server.ServerHandlerFactory;
import static java.lang.Integer.parseInt;

public class Main {

    public static void main(String[] args) {
        String mongoHost = env("MONGO_HOST", "localhost");
        String redisHost = env("REDIS_HOST", "localhost");
        int mongoPort = parseInt(env("MONGO_PORT", "27017"));
        int redisPort = parseInt(env("REDIS_PORT", "6379"));
        int port = parseInt(env("HC_PORT", "8080"));

        try(
            RequestData requests = RequestData.connect(mongoHost, mongoPort);
            UserRegistry users = UserRegistry.connect(redisHost, redisPort);
        ) {

            ServerHandlerFactory handler = new ServerHandlerFactory(users, requests);
            Server s = new Server(port, handler);
            s.run();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }

    private static String env(String flag, String orElse) {
        String value = System.getenv(flag);
        return (value != null) ? value : orElse;
    }
}
