package com.openjdl.jsf.webflux.modbus.dtu.payload;

import com.openjdl.jsf.webflux.modbus.dtu.payload.request.ModbusDtuRequest;
import com.openjdl.jsf.webflux.modbus.dtu.payload.response.ModbusDtuExceptionResponse;
import com.openjdl.jsf.webflux.modbus.dtu.payload.response.ModbusDtuReadCoilsResponse;
import com.openjdl.jsf.webflux.modbus.dtu.payload.response.ModbusDtuResponse;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created at 2020-12-07 19:23:22
 *
 * @author kidal
 * @since 0.5
 */
public class ModbusDtuPayload {
  /**
   * log
   */
  private static final Logger log = LoggerFactory.getLogger(ModbusDtuPayload.class);

  /**
   * 创建
   */
  @NotNull
  public static ModbusDtuPayload of(@NotNull Type type) {
    return of(type, ModbusDtuResponse.ModbusDtuEmptyResponse.EMPTY);
  }

  /**
   * 创建
   */
  @NotNull
  public static ModbusDtuPayload of(@NotNull Type type, @NotNull ModbusDtuResponse body) {
    ModbusDtuPayload payload = new ModbusDtuPayload();
    payload.type = type;
    payload.response = body;
    return payload;
  }

  /**
   * 创建
   */
  @Nullable
  public static ModbusDtuPayload of(@NotNull ByteBuf in) {
    // 保证有足够的长度
    if (in.readableBytes() < 7) {
      log.trace("数据长度不够: {} < {}", in.readableBytes(), 7);
      return null;
    }

    // 创建载荷
    ModbusDtuPayload payload = new ModbusDtuPayload();
    payload.type = Type.MESSAGE;

    // 事务处理标识、协议标识符、长度、单元标识符
    payload.id = in.readUnsignedShort();
    payload.protocol = in.readUnsignedShort();
    payload.length = in.readUnsignedShort();
    payload.unitId = in.readUnsignedByte();

    // log
    if (log.isTraceEnabled()) {
      log.trace("读取到数据头: {}", payload);
    }

    // 保证有足够的长度
    if (in.readableBytes() < payload.length) {
      log.trace("数据长度不够: {} < {}", in.readableBytes(), payload.length);
      return null;
    }

    // 读取功能码
    short fc = in.readUnsignedByte();

    // 读取消息体
    ModbusDtuResponse response = null;

    if (fc > 0x80) {
      response = ModbusDtuExceptionResponse.of(fc, in);
    } else if (fc == 0x01) {
      response = ModbusDtuReadCoilsResponse.of(in);
    }

    if (response == null) {
      return null;
    }

    // 设置消息体
    payload.response = response;

    // done
    return payload;
  }

  /**
   * 类型
   */
  @NotNull
  private Type type = Type.HEARTBEAT;

  /**
   * 事务处理标识: 可以理解为报文的序列号，一般每次通信之后就要加1以区别不同的通信数据报文。
   * 2字节.
   */
  private int id;

  /**
   * 协议标识符: 0x0000表示ModbusTCP协议。
   * 2字节.
   */
  private int protocol;

  /**
   * 长度: 表示接下来的数据长度，单位为字节。
   * 2字节.
   */
  private int length;

  /**
   * 单元标识符: 可以理解为设备地址。
   * 1字节.
   */
  private short unitId;

  /**
   * 请求
   */
  @NotNull
  private ModbusDtuRequest request = ModbusDtuRequest.EMPTY;

  /**
   * 消息体
   */
  @NotNull
  private ModbusDtuResponse response = ModbusDtuResponse.EMPTY;

  //--------------------------------------------------------------------------------------------------------------
  // Object
  //--------------------------------------------------------------------------------------------------------------
  //region

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "ModbusDtuPayload{" +
      "type=" + type
      +
      (
        type.equals(Type.MESSAGE)
          ? ", id=" + id +
          ", protocol=" + protocol +
          ", length=" + length +
          ", unitId=" + unitId
          : ""
      )
      +
      (
        request.equals(ModbusDtuRequest.EMPTY)
          ? ""
          : ", request=" + request
      )
      +
      (
        response.equals(ModbusDtuResponse.EMPTY)
          ? ""
          : ", response=" + response
      )
      +
      '}';
  }

  //endregion

  //--------------------------------------------------------------------------------------------------------------
  // Getters & Setters
  //--------------------------------------------------------------------------------------------------------------
  //region

  @NotNull
  public Type getType() {
    return type;
  }

  public void setType(@NotNull Type type) {
    this.type = type;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getProtocol() {
    return protocol;
  }

  public void setProtocol(int protocol) {
    this.protocol = protocol;
  }

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public short getUnitId() {
    return unitId;
  }

  public void setUnitId(short unitId) {
    this.unitId = unitId;
  }

  @NotNull
  public ModbusDtuRequest getRequest() {
    return request;
  }

  public void setRequest(@NotNull ModbusDtuRequest request) {
    this.request = request;
  }

  @NotNull
  public ModbusDtuResponse getResponse() {
    return response;
  }

  public void setResponse(@NotNull ModbusDtuResponse response) {
    this.response = response;
  }

  //endregion

  //--------------------------------------------------------------------------------------------------------------
  // 协议
  //--------------------------------------------------------------------------------------------------------------
  //region

  public enum Type {
    /**
     * 注册
     */
    REGISTER,

    /**
     * 心跳
     */
    HEARTBEAT,

    /**
     * 小心
     */
    MESSAGE
  }

  //endregion
}
