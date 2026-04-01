package org.margin.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.presence.PresenceService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.processors.WebSocketMessageProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class WebSocketServer {
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final ConnectionManager connectionManager;
    private final PresenceService presenceService;
    private final List<WebSocketMessageProcessor<?>> processors;
    private final Executor dbExecutor;
    @Value("${websocket.port:8081}")
    private int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public WebSocketServer(ObjectMapper objectMapper,
                           JwtService jwtService,
                           ConnectionManager connectionManager,
                           PresenceService presenceService,
                           List<WebSocketMessageProcessor<?>> processors,
                           @Qualifier("wsDbExecutor") Executor dbExecutor) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.connectionManager = connectionManager;
        this.presenceService = presenceService;
        this.processors = processors;
        this.dbExecutor = dbExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread serverThread = new Thread(this::run, "websocket-server");
        serverThread.start();
    }

    private void run() {
        bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

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
                                    .addLast(new WebSocketHandler(
                                            dbExecutor,
                                            jwtService,
                                            connectionManager,
                                            presenceService,
                                            processors));
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

        connectionManager.clearAll();
        log.info("WebSocket server shut down");
    }
}