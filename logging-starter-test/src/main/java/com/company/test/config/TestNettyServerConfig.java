package com.company.test.config;

import com.company.logging.netty.handler.NettyTraceDuplexHandler;
import com.company.test.mapper.TestMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Configuration
public class TestNettyServerConfig {

    private final NettyTraceDuplexHandler nettyTraceDuplexHandler;
    private final TestMapper testMapper;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private int port;

    public TestNettyServerConfig(NettyTraceDuplexHandler nettyTraceDuplexHandler, TestMapper testMapper) {
        this.nettyTraceDuplexHandler = nettyTraceDuplexHandler;
        this.testMapper = testMapper;
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
                        ch.pipeline().addLast("logging_head", nettyTraceDuplexHandler);
                        ch.pipeline().addLast(new StringDecoder());
                        ch.pipeline().addLast(new StringEncoder());
                        ch.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {
                            private StringBuilder accum = new StringBuilder();
                            @Override
                            public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
                                // Restore ThreadLocal for this handler execution
                                String traceId = ctx.channel().attr(io.netty.util.AttributeKey.<String>valueOf("TRACE_ID")).get();
                                String spanId = ctx.channel().attr(io.netty.util.AttributeKey.<String>valueOf("SPAN_ID")).get();
                                com.company.logging.core.sql.SqlTraceContext sqlCtx = ctx.channel().attr(io.netty.util.AttributeKey.<com.company.logging.core.sql.SqlTraceContext>valueOf("SQL_CONTEXT")).get();
                                
                                if (traceId != null) {
                                    com.company.logging.core.context.TraceContextHolder.init(traceId, spanId, com.company.logging.core.enums.TraceLevel.TRACE, true);
                                    com.company.logging.core.sql.SqlTraceContextHolder.set(sqlCtx);
                                }

                                try {
                                    String s = (String) msg;
                                    accum.append(s);
                                    if (s.contains("\n")) {
                                        String finalMsg = accum.toString().trim();
                                        if (!finalMsg.isEmpty()) {
                                            if ("OOM_TEST".equals(finalMsg)) {
                                                // Trigger many SQLs to test OOM prevention (limit is 10 in properties)
                                                for (int i = 0; i < 20; i++) {
                                                    testMapper.selectOne();
                                                }
                                                ctx.writeAndFlush("OOM_TEST_DONE\n");
                                            } else if ("ERROR_TEST".equals(finalMsg)) {
                                                // Trigger SQL then Exception
                                                testMapper.selectWithParam("Before Error");
                                                throw new SQLException("Netty Test Exception");

                                            } else {
                                                // Simulating long processing and SQL execution
                                                testMapper.selectWithParam("Netty-" + finalMsg);
                                                Thread.sleep(200); // Wait to test concurrency
                                                
                                                ctx.writeAndFlush("Echo: " + finalMsg + "\n");
                                            }
                                        }
                                        accum.setLength(0);
                                    }
                                } finally {
                                    com.company.logging.core.context.TraceContextHolder.clear();
                                    com.company.logging.core.sql.SqlTraceContextHolder.clear();
                                }
                            }
                        });
                        ch.pipeline().addLast("logging_tail", nettyTraceDuplexHandler);
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
