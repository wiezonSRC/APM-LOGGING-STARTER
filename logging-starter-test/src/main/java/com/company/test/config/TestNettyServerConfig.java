package com.company.test.config;

import com.company.logging.netty.handler.NettyTraceDuplexHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.netty.channel.ChannelFuture;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestNettyServerConfig {

    private final NettyTraceDuplexHandler nettyTraceDuplexHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private int port;

    public TestNettyServerConfig(NettyTraceDuplexHandler nettyTraceDuplexHandler) {
        this.nettyTraceDuplexHandler = nettyTraceDuplexHandler;
    }

    public int getPort() {
        return port;
    }

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new StringDecoder());
                        ch.pipeline().addLast(new StringEncoder());
                        ch.pipeline().addLast(nettyTraceDuplexHandler);
                        ch.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {
                            private StringBuilder accum = new StringBuilder();
                            @Override
                            public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) {
                                String s = (String) msg;
                                accum.append(s);
                                if (s.contains("\n")) {
                                    ctx.writeAndFlush("Echo: " + accum.toString().trim());
                                    accum.setLength(0);
                                }
                            }
                        });
                    }
                });

        // 0번 포트를 사용하여 빈 포트 자동 할당
        ChannelFuture f = b.bind(0).sync();
        this.port = ((java.net.InetSocketAddress) f.channel().localAddress()).getPort();
    }

    @PreDestroy
    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
