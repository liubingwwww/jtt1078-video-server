package cn.org.hentai.jtt1078.http;

import cn.org.hentai.jtt1078.app.MediaConstant;
import cn.org.hentai.jtt1078.util.Configs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyWebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    static Logger logger = LoggerFactory.getLogger(NettyWebSocketServerHandler.class);

    static final String HEADER_ENCODING = "ISO-8859-1";

    private WebSocketServerHandshaker handshaker;


    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        // 传统的HTTP接入
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        }
        // WebSocket接入
        else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }



    private void handleHttpRequest(ChannelHandlerContext ctx,
                                   FullHttpRequest req) throws Exception {

        if (!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
            // http请求
            MediaService.playForHttp(ctx,req.uri());
        } else {
            String app= Configs.get("server.context-path");
            if(!req.uri().startsWith(app)){
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
                return ;
            }
            // websocket握手，请求升级

            // 参数分别是ws地址，子协议，是否扩展，最大frame长度
            WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                    getWebSocketLocation(req), null, true, 5 * 1024 * 1024);
            handshaker = factory.newHandshaker(req);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            } else {
                HttpResponse rsp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                rsp.headers().set(HttpHeaderNames.SERVER, MediaConstant.serverName);
                DefaultChannelPromise channelPromise = new DefaultChannelPromise(ctx.channel());

                handshaker.handshake(ctx.channel(), req, rsp.headers(), channelPromise);
                MediaService.playForWs(ctx,req.uri());
            }
        }

    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx,
                                      WebSocketFrame frame) {

        // 判断是否是关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(),
                    (CloseWebSocketFrame) frame.retain());
            return;
        }
        // 判断是否是Ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(
                    new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        // 文本
        if (frame instanceof TextWebSocketFrame) {
            // 返回应答消息
            String request = ((TextWebSocketFrame) frame).text();

            ctx.channel().write(
                    new TextWebSocketFrame(request
                            + " , 欢迎使用Netty WebSocket服务，现在时刻："
                            + new java.util.Date().toString()));
            return;
        }

        if (frame instanceof BinaryWebSocketFrame) {
            return;
        }

    }



    private static void sendHttpResponse(
            ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // Generate an error page if response getStatus code is not OK (200).
        int statusCode = res.status().code();
        if (statusCode != OK.code() && res.content().readableBytes() == 0) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        HttpUtil.setContentLength(res, res.content().readableBytes());

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || statusCode != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
    private static String getWebSocketLocation(FullHttpRequest req) {
        String location = req.headers().get(HttpHeaderNames.HOST) + req.uri();
        return "ws://" + location;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
