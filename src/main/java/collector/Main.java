package collector;

import collector.server.Server;
import collector.conf.ServerConf;

public class Main {

    public static void main(String[] args) {

        try(ServerConf conf = ServerConf.instance) {

            Server s = new Server(conf);
            s.run();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }
}
