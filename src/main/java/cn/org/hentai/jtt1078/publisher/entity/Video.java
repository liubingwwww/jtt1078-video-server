package cn.org.hentai.jtt1078.publisher.entity;

/**
 * Created by houcheng on 2019-12-11.
 */
public class Video extends Media
{
    public boolean isKeyFrame;
    public Video(boolean isKeyFrame, byte[] data)
    {
        super(Type.video, data);
        this.isKeyFrame = isKeyFrame;
    }
}