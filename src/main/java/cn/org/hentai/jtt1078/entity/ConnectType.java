package cn.org.hentai.jtt1078.entity;

public enum ConnectType {
    HTTP(0,"http"), WEBSOCKET(1,"websocket");

    int type;
    String name;

    ConnectType(int type, String name){
        this.type=type;
        this.name=name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
