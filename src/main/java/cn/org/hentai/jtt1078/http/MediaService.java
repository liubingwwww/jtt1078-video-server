package cn.org.hentai.jtt1078.http;

import cn.org.hentai.jtt1078.app.MediaConstant;
import cn.org.hentai.jtt1078.entity.ConnectType;
import cn.org.hentai.jtt1078.entity.Media;
import cn.org.hentai.jtt1078.publisher.PublishManager;
import cn.org.hentai.jtt1078.subscriber.Subscriber;
import cn.org.hentai.jtt1078.util.Configs;
import cn.org.hentai.jtt1078.util.FileUtils;
import cn.org.hentai.jtt1078.util.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.util.List;
import java.util.Map;

public class MediaService {

    static final String HEADER_ENCODING = "ISO-8859-1";

    /**
     * http-flv播放
     * @param ctx
     */
    public static void playForHttp(ChannelHandlerContext ctx,String uri) throws Exception {

        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        Map<String, List<String>> parameters = decoder.parameters();
        List<String> typeList = parameters.get("type");
        String typeStr=null;
        if(typeList != null){
            typeStr=typeList.get(0);
        }
        String app= Configs.get("server.context-path");
        // http-flv 请求
        if (uri.startsWith(app) && typeStr == null)
        {
            sendHtmlReqHeader(ctx);
            String tag = uri.substring(uri.lastIndexOf("/")+1);
            if(tag.indexOf("?") != -1){
                tag=tag.substring(0,tag.lastIndexOf("?"));
            }
            // 订阅视频数据
            Subscriber subscriber= PublishManager.getInstance().subscribe(tag, Media.Type.Video,ConnectType.HTTP, ctx);
            subscriber.start();//开启监听，读取队列
        }
        else if (uri.indexOf("/test/multimedia") != -1 )
        {
            responseHTMLFile("/multimedia.html", ctx);
        }
        else if (uri.indexOf("/test/websocket") != -1 )
        {
            responseHTMLFile("/websocket.html", ctx);
        }
        else{
            //websocket 第一次请求，返回头
            sendFlvReqHeader(ctx);
        }
    }

    /**
     * ws-flv播放
     * @param ctx
     */
    public static void playForWs(ChannelHandlerContext ctx,String uri) {

        String tag = uri.substring(uri.lastIndexOf("/")+1);
        if(tag.indexOf("?") != -1){
            tag=tag.substring(0,tag.lastIndexOf("?"));
        }
        // 订阅视频数据
        PublishManager.getInstance().subscribe(tag, Media.Type.Video, ConnectType.WEBSOCKET, ctx);
    }

    private static void sendHtmlReqHeader(ChannelHandlerContext ctx) throws Exception {
        Packet resp = Packet.create(1024);
        resp.addBytes("HTTP/1.1 200 OK\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("Connection: keep-alive\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("Content-Type: video/x-flv\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("Transfer-Encoding: chunked\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("Cache-Control: no-cache\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("Access-Control-Allow-Origin: *\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("Access-Control-Allow-Credentials: true\r\n".getBytes(HEADER_ENCODING));
        resp.addBytes("\r\n".getBytes(HEADER_ENCODING));
        ctx.writeAndFlush(resp.getBytes()).await();
    }
    /**
     * 发送req header，告知浏览器是flv格式
     *
     * @param ctx
     */
    private static void sendFlvReqHeader(ChannelHandlerContext ctx) {
        HttpResponse rsp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        rsp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                .set(HttpHeaderNames.CONTENT_TYPE, "video/x-flv").set(HttpHeaderNames.ACCEPT_RANGES, "bytes")
                .set(HttpHeaderNames.PRAGMA, "no-cache").set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED).set(HttpHeaderNames.SERVER, MediaConstant.serverName);
        ctx.writeAndFlush(rsp);
    }
    // 响应静态文件内容
    private static void responseHTMLFile(String htmlFilePath, ChannelHandlerContext ctx)
    {
        byte[] fileData = FileUtils.read(NettyHttpServerHandler.class.getResourceAsStream(htmlFilePath));
        ByteBuf body = Unpooled.buffer(fileData.length);
        body.writeBytes(fileData);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(200), body);
        response.headers().add("Content-Length", fileData.length);
        ctx.write(response);
        ctx.flush();
    }


}
