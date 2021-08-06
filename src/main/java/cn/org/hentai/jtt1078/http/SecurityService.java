package cn.org.hentai.jtt1078.http;

import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityService {
    static Logger logger = LoggerFactory.getLogger(SecurityService.class);

    /**
     * 对设备的合法性进行校验
     * 可以添加自己的逻辑
     * @param tag
     * @param ctx
     * @return
     */
    public static boolean tagCheck(String tag, ChannelHandlerContext ctx) {
        if (StringUtils.isEmpty(tag)) {
            logger.error("设备编号不能为空");
            return false;
        }
        return true;
    }
}
