package cpf;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public final class CpfServer extends ChannelInitializer<SocketChannel> {

    protected void initChannel(SocketChannel soChannel) throws Exception {
        ChannelPipeline cpl = soChannel.pipeline();

        cpl.addLast(new CpfServerHandler());
    }

    private static final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private static final EventLoopGroup workerGroup = new NioEventLoopGroup();

    public static void run(int port) {
        try{
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(bossGroup, workerGroup);
            boot.channel(NioServerSocketChannel.class);
            boot.childHandler(new CpfServer());
            boot.option(ChannelOption.SO_BACKLOG, 8192);
            ChannelFuture f = boot.bind(port).sync();

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static void stop(int timeout) {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

}
