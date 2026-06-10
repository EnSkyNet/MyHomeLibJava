package org.myhomelib.model;

public enum CollectionType {
    PRIVATE_FB(0x00000000),
    PRIVATE_NON_FB(0x00000001),
    EXTERNAL_LOCAL_FB(0x00010000),
    EXTERNAL_ONLINE_FB(0x08010000),
    EXTERNAL_LOCAL_NON_FB(0x00010001),
    EXTERNAL_ONLINE_NON_FB(0x08010001);

    private static final int CONTENT_MASK = 0x00000001;
    private static final int LOCATION_MASK = 0x08000000;
    private static final int TYPE_MASK = 0x08030000;
    private static final int LOCATION_ONLINE = 0x08000000;
    private static final int LIBRARY_PRIVATE = 0x00000000;

    private final int code;

    CollectionType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isPrivateCollection() {
        return (code & TYPE_MASK) == LIBRARY_PRIVATE;
    }

    public boolean isExternalCollection() {
        return !isPrivateCollection();
    }

    public boolean isOnlineCollection() {
        return (code & LOCATION_MASK) == LOCATION_ONLINE;
    }

    public boolean isLocalCollection() {
        return !isOnlineCollection();
    }

    public boolean isFb2Collection() {
        return (code & CONTENT_MASK) == 0;
    }

    public static CollectionType fromCode(int code) {
        for (CollectionType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return PRIVATE_FB;
    }
}
