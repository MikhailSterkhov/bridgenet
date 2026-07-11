package me.moonways.rmap.codec;

/** Байтовые теги TLV-кодека и дискриминаторы classRef (спека §5.2, §5.2a). */
public final class Tags {

    public static final int NULL = 0x00;
    public static final int TRUE = 0x01;
    public static final int FALSE = 0x02;
    public static final int BYTE = 0x03;
    public static final int SHORT = 0x04;
    public static final int INT = 0x05;
    public static final int LONG = 0x06;
    public static final int FLOAT = 0x07;
    public static final int DOUBLE = 0x08;
    public static final int CHAR = 0x09;
    public static final int STRING = 0x0A;
    public static final int UUID = 0x0B;
    public static final int ENUM = 0x0C;
    public static final int LIST = 0x0D;
    public static final int SET = 0x0E;
    public static final int MAP = 0x0F;
    public static final int ARRAY = 0x10;
    public static final int OBJECT = 0x11;
    public static final int BACK_REF = 0x12;
    public static final int VALUE_CODEC = 0x13;
    public static final int REMOTE_REF = 0x14;
    public static final int EXCEPTION = 0x15;
    public static final int BYTES = 0x16;

    /** classRef: первое вхождение класса — 0x00, str FQN (§5.2a). */
    public static final int CLASSREF_DEF = 0x00;
    /** classRef: повторное — 0x01, int32 classId (§5.2a). */
    public static final int CLASSREF_USE = 0x01;

    private Tags() {
    }
}
