package cn.org.hentai.jtt1078.server;

import cn.org.hentai.jtt1078.http.SecurityService;
import cn.org.hentai.jtt1078.publisher.PublishManager;
import cn.org.hentai.jtt1078.subscriber.RTMPPublisher;
import cn.org.hentai.jtt1078.util.Configs;
import cn.org.hentai.jtt1078.util.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by matrixy on 2019/4/9.
 */
public class Jtt1078Handler extends SimpleChannelInboundHandler<Packet>
{
    static Logger logger = LoggerFactory.getLogger(Jtt1078Handler.class);
    private static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("session-key");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception
    {
        io.netty.channel.Channel nettyChannel = ctx.channel();

        packet.seek(8);
        String sim = packet.nextBCD() + packet.nextBCD() + packet.nextBCD() + packet.nextBCD() + packet.nextBCD() + packet.nextBCD();
        int channel = packet.nextByte() & 0xff;
        String tag = sim + "-" + channel;


        if (SessionManager.contains(nettyChannel, "tag") == false)
        {
            if(!SecurityService.tagCheck(tag,ctx)) return ;

            if (StringUtils.isEmpty(Configs.get("rtmp.url")) == false)
            {
                RTMPPublisher rtmpPublisher = new RTMPPublisher(tag);
                rtmpPublisher.start();
            }
            SessionManager.set(nettyChannel, "tag", tag);
        }

        //Integer sequence = SessionManager.get(nettyChannel, "video-sequence");
        Integer sequence =0;
        if (sequence == null) sequence = 0;
        // 1. 做好序号
        // 2. 音频需要转码后提供订阅
        int lengthOffset = 28;
        int dataType = (packet.seek(15).nextByte() >> 4) & 0x0f;
        int pkType = packet.seek(15).nextByte() & 0x0f;
        // 透传数据类型：0100，没有后面的时间以及Last I Frame Interval和Last Frame Interval字段
        if (dataType == 0x04) lengthOffset = 28 - 8 - 2 - 2;
        else if (dataType == 0x03) lengthOffset = 28 - 4;

        int pt = packet.seek(5).nextByte() & 0x7f;

        if (dataType == 0x00 || dataType == 0x01 || dataType == 0x02)
        {
            // 碰到结束标记时，序号+1
            if (pkType == 0 || pkType == 2)
            {
                sequence += 1;
                //SessionManager.set(nettyChannel, "video-sequence", sequence);
            }
            long timestamp = packet.seek(16).nextLong();
            PublishManager.getInstance().publishVideo(tag, sequence, timestamp, pt, packet.seek(lengthOffset + 2).nextBytes());
        }
        else if (dataType == 0x03)
        {
            long timestamp = packet.seek(16).nextLong();
            byte[] data = packet.seek(lengthOffset + 2).nextBytes();
            PublishManager.getInstance().publishAudio(tag, sequence, timestamp, pt, data);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        super.channelInactive(ctx);
        release(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        // super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        release(ctx.channel());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (IdleStateEvent.class.isAssignableFrom(evt.getClass())) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                String tag = SessionManager.get(ctx.channel(), "tag");
                logger.info("read timeout: {}",tag);
                release(ctx.channel());
            }
        }
    }

    /**
     * 推流关闭后，相关联的tag的订阅者都关闭
     * @param channel
     */
    private void release(io.netty.channel.Channel channel)
    {
        String tag = SessionManager.get(channel, "tag");
        if (tag != null)
        {
            logger.info("close netty channel: {}", tag);
            PublishManager.getInstance().close(tag);
        }
    }
}
