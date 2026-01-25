package org.margin.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.services.WebSocketClientService;

@Slf4j
@Component
public class WebSocketServer {

    @Value("${websocket.port:8081}")
    private int port;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtService jwtService;
    private final WebSocketClientService clientService;
    private final UserService userService;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public WebSocketServer(JwtService jwtService,
                           WebSocketClientService clientService,
                           UserService userService) {
        this.jwtService = jwtService;
        this.clientService = clientService;
        this.userService = userService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread serverThread = new Thread(this::run, "websocket-server");
        serverThread.start();
    }

    private void run() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(16);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(65536))
                                    .addLast(new WebSocketMessageDecoder(objectMapper))
                                    .addLast(createWebSocketHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            serverChannel = bootstrap.bind(port).sync().channel();
            log.info("WebSocket server started on port {}", port);

            serverChannel.closeFuture().sync();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WebSocket server interrupted", e);
        } catch (Exception e) {
            log.error("Failed to start WebSocket server", e);
        } finally {
            shutdown();
        }
    }

    private WebSocketHandler createWebSocketHandler() {
        return new WebSocketHandler(jwtService, clientService, userService);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WebSocket server...");

        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        clientService.clearAllClients();
        log.info("WebSocket server shut down");
    }
}