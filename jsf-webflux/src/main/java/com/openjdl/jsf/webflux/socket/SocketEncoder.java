package com.openjdl.jsf.webflux.socket;

import com.openjdl.jsf.webflux.socket.payload.SocketPayload;
import com.openjdl.jsf.webflux.socket.payload.SocketPayloadBody;
import com.openjdl.jsf.webflux.socket.payload.SocketPayloadHeader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created at 2020-12-23 18:25:37
 *
 * @author zink
 * @since 0.0.1
 */
public class SocketEncoder extends MessageToByteEncoder<SocketPayload> {
  /**
   * log
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   *
   */
  @Override
  protected void encode(@NotNull ChannelHandlerContext ctx, @NotNull SocketPayload payload, @NotNull ByteBuf out) throws Exception {
    SocketPayloadHeader header = payload.getHeader();
    SocketPayloadBody body = payload.getBody();
    byte[] bodyData = body.serialize();

    out.writeByte(header.getMask());
    out.writeByte(header.getVersion());
    out.writeInt((int) header.getId());
    out.writeInt((int) header.getType());
    if (bodyData == null) {
      out.writeInt(0);
    } else {
      out.writeInt(bodyData.length);
      out.writeBytes(bodyData);
    }

    if (log.isTraceEnabled()) {
      log.trace("Encode {} to {}", payload, ByteBufUtil.hexDump(out));
    }
  }
}
