/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.getHost;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HexDumpProxyFrontendHandler extends ChannelInboundMessageHandlerAdapter<Object> {

    private volatile Channel outboundChannel;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            if (is100ContinueExpected(req)) {
                send100Continue(ctx);
            }

            URI backendUri = createBackendUriFromFrontendReq(req);

            final Channel inboundChannel = ctx.channel();

            // Start the connection attempt.
            Bootstrap b = new Bootstrap();
            b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new HexDumpProxyBackendHandler(inboundChannel))
                .option(ChannelOption.AUTO_READ, false);
            ChannelFuture f = b.connect(backendUri.getHost(), backendUri.getPort());
            outboundChannel = f.channel();
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // connection complete start to read first data
                        inboundChannel.read();
                    } else {
                        // Close the connection if the connection attempt has failed.
                        inboundChannel.close();
                    }
                }
            });



        }

        if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;

            if (msg instanceof LastHttpContent) {
                LastHttpContent trailer = (LastHttpContent) msg;
            }
        }
    }

    private static URI createBackendUriFromFrontendReq(HttpRequest req) throws Exception {
        Pattern pattern = Pattern.compile("(.*)\\.local(-secure)?");
        Matcher matcher = pattern.matcher(getHost(req));
        matcher.find();
        String destHost = matcher.group(1);
        String destScheme = matcher.group(2) != null && !matcher.group(2).isEmpty() ? "https://" : "http://";
        int destPort = 80; //TODO get local SERVER_PORT from req
        String destPathAndQueryString = req.getUri();

        String destUriAsString = destScheme + destHost + ":" + destPort + destPathAndQueryString;
        URI destUri = new URI(destUriAsString);

        return destUri;
    }

    private static HttpRequest createBackendReqFromFrontendReq(URI destUri, HttpRequest inboundHeader) {
        HttpRequest outboundRequest = new DefaultHttpRequest(
                inboundHeader.getProtocolVersion(), inboundHeader.getMethod(), destUri.getRawPath());
        for (Map.Entry<String, String> h: inboundHeader.headers()) {
            outboundRequest.headers().add(h.getKey(), h.getValue());
        }
        outboundRequest.headers().set(HttpHeaders.Names.HOST, destUri.getHost());
        return outboundRequest;
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.nextOutboundMessageBuffer().add(response);
    }

    @Override
    public void endMessageReceived(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    /*

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
            .channel(ctx.channel().getClass())
            .handler(new HexDumpProxyBackendHandler(inboundChannel))
            .option(ChannelOption.AUTO_READ, false);
        ChannelFuture f = b.connect(remoteHost, remotePort);
        outboundChannel = f.channel();
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    // connection complete start to read first data
                    inboundChannel.read();
                } else {
                    // Close the connection if the connection attempt has failed.
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    */
    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.flush().addListener(ChannelFutureListener.CLOSE);
        }
    }
}
