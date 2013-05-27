package collector;

import collector.http.HttpFrontendHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.ByteLoggingHandler;
import io.netty.handler.logging.LogLevel;

import static io.netty.handler.codec.http.HttpHeaders.getHost;


public class Main {

    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addFirst(new ByteLoggingHandler(LogLevel.INFO));
                        // Uncomment the following line if you want HTTPS
                        //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
                        //engine.setUseClientMode(false);
                        //p.addLast("ssl", new SslHandler(engine));
                        p.addLast("decode", new HttpRequestDecoder());
                        p.addLast("encode", new HttpResponseEncoder());
                        //p.addLast("decode", new HttpResponseDecoder());
                        // Uncomment the following line if you don't want to handle HttpChunks.
                        //p.addLast("aggregator", new HttpObjectAggregator(1048576));
                        // Remove the following line if you don't want automatic content compression.
                        //p.addLast("deflater", new HttpContentCompressor());
                        p.addLast("handler", new HttpFrontendHandler());
                    }
                });

            Channel ch = b.bind(SERVER_PORT).sync().channel();
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
