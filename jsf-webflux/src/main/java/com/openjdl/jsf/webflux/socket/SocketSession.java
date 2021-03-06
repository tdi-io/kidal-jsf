package com.openjdl.jsf.webflux.socket;

import com.google.common.collect.Maps;
import com.google.protobuf.MessageLite;
import com.openjdl.jsf.core.cipher.UserIdentificationNumber;
import com.openjdl.jsf.core.utils.DateUtils;
import com.openjdl.jsf.webflux.socket.exception.SocketPayloadTypeNotFoundException;
import com.openjdl.jsf.webflux.socket.payload.SocketPayload;
import com.openjdl.jsf.webflux.socket.payload.SocketPayloadHeader;
import io.netty.channel.Channel;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created at 2020-12-23 17:39:29
 *
 * @author zink
 * @since 0.0.1
 */
public class SocketSession {
  /**
   * 日志
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * 管理器
   */
  @NotNull
  private final SocketSessionManager sessionManager;

  /**
   * Netty 会话
   */
  @NotNull
  private final Channel channel;

  /**
   * 创建于
   */
  @NotNull
  private final Date createdAt = new Date(System.currentTimeMillis());

  /**
   * 上下文
   */
  @NotNull
  private final ConcurrentMap<String, Object> context = Maps.newConcurrentMap();

  /**
   * 用户身份识别码
   */
  @Nullable
  private UserIdentificationNumber uin;

  /**
   * 认证于
   */
  @Nullable
  private Date signInAt;

  /**
   * 关闭于
   */
  @Nullable
  private Date closedAt;

  /**
   *
   */
  private final ReentrantLock idLock = new ReentrantLock(true);
  private int id = 1;
  private final ConcurrentMap<Long, Pair<Object, SocketPayload>> rpcWaitingMap = new ConcurrentHashMap<>();

  /**
   *
   */
  public SocketSession(@NotNull SocketSessionManager sessionManager, @NotNull Channel channel) {
    this.sessionManager = sessionManager;
    this.channel = channel;
  }

  /**
   * 是否已认证
   */
  public boolean isSignedIn() {
    return uin != null;
  }

  /**
   * 是否已关闭
   */
  public boolean isClosed() {
    return closedAt != null;
  }

  /**
   * 设置UIN
   */
  private void setUin(@Nullable UserIdentificationNumber uin) {
    this.uin = uin;
    this.signInAt = uin != null ? new Date(System.currentTimeMillis()) : null;
  }

  /**
   * 用户登录
   */
  public void signIn(@NotNull UserIdentificationNumber uin) {
    // 登出-登录的其他账号
    if (isSignedIn()) {
      if (uin.equals(this.uin)) {
        return;
      }
      signOut(SocketSignOutReason.SWITCH);
    }

    // 登出其他人登录的该账号
    SocketSession prevSession = sessionManager.getAuthenticatedSessionByUin(uin);
    if (prevSession != null) {
      prevSession.signOut(SocketSignOutReason.ELSEWHERE);
    }

    // 登录
    setUin(uin);
    sessionManager.onSignIn(this);

    // log
    if (log.isDebugEnabled()) {
      log.debug("{} sign in uin={}", this, uin);
    }
  }

  /**
   * 用户登出
   */
  public void signOut(@NotNull SocketSignOutReason reason) {
    if (!isSignedIn()) {
      return;
    }

    UserIdentificationNumber uin = this.getUin();
    sessionManager.onSignOut(this);
    setUin(uin);

    // log
    if (log.isDebugEnabled()) {
      log.debug("{} sign out uin={}", this, uin);
    }
  }


  /**
   * 关闭
   */
  public void close() {
    UserIdentificationNumber uin = this.uin;
    signOut(SocketSignOutReason.CLOSE);
    channel.close();
    closedAt = new Date(System.currentTimeMillis());

    if (log.isDebugEnabled()) {
      log.debug("{} close uin={}", this, uin);
    }
  }

  /**
   * 延迟关闭
   */
  public void close(Date startTime) {
    sessionManager.getTaskScheduler().schedule(this::close, startTime);
  }

  /**
   * 发送载荷
   */
  public SocketPayload send(@NotNull Object body) throws SocketPayloadTypeNotFoundException {
    // id
    long id;
    idLock.lock();
    try {
      if (this.id >= 10000000) {
        this.id = 1;
      }
      id = this.id++;
    } finally {
      idLock.unlock();
    }

    // 获取消息体对应的类型
    Long type = sessionManager.getSocketPayloadTypeByClass(body.getClass());
    if (type == null) {
      throw new SocketPayloadTypeNotFoundException("Payload type for class " + body.getClass() + " not found");
    }

    SocketPayloadHeader header = new SocketPayloadHeader(id, type);
    SocketPayload payload = new SocketPayload(header, body);

    channel.writeAndFlush(payload);

    return payload;
  }

  /**
   * 发送载荷
   */
  @NotNull
  public SocketPayload rpc(@NotNull Object body, long waitDuration, TimeUnit waitUnit) throws SocketPayloadTypeNotFoundException {
    final Object sync = new Object();
    SocketPayload payload = send(body);

    synchronized (sync) {
      rpcWaitingMap.put(payload.getHeader().getId(), Pair.of(sync, payload));

      try {
        sync.wait(waitUnit.toMillis(waitDuration));
      } catch (InterruptedException ignored) {
      }

      rpcWaitingMap.remove(payload.getHeader().getId());
    }

    return payload;
  }

  /**
   * 发送载荷
   */
  @NotNull
  public SocketPayload rpc(@NotNull Object body) throws SocketPayloadTypeNotFoundException {
    return rpc(body, 5, TimeUnit.SECONDS);
  }

  boolean onResponse(@NotNull SocketPayload payload) {
    Pair<Object, SocketPayload> pair = rpcWaitingMap.get(payload.getHeader().getId());
    if (pair == null) {
      return false;
    }
    Object sync = pair.getKey();
    SocketPayload waiting = pair.getValue();

    synchronized (sync) {
      // TODO: fix
      byte[] respBytes = (byte[])payload.getBody();
      Class<?> type = sessionManager.getSocketPayloadClassByType(payload.getHeader().getType());
      if (type != null) {
        if (MessageLite.class.isAssignableFrom(type)) {
          try {
            Method method = type.getDeclaredMethod("parseFrom", byte[].class);
            Object response = method.invoke(null, respBytes);
            waiting.setResponse(response);
          } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            ExceptionUtils.rethrow(e);
          }
        }
      }
      sync.notify();
    }

    return true;
  }

  @Override
  public String toString() {
    return "SocketSession{" +
      "channel=" + getChannel() +
      ", id=" + getId() +
      ", uin=" + uin +
      ", createdAt=" + DateUtils.toStringSafely(createdAt) +
      ", signInAt=" + DateUtils.toStringSafely(signInAt) +
      ", closedAt=" + DateUtils.toStringSafely(closedAt) +
      '}';
  }

  //--------------------------------------------------------------------------
  // Getters & Setters
  //--------------------------------------------------------------------------
  @NotNull
  public String getId() {
    return channel.id().asShortText();
  }

  @NotNull
  public Channel getChannel() {
    return channel;
  }

  @NotNull
  public Date getCreatedAt() {
    return createdAt;
  }

  @NotNull
  public ConcurrentMap<String, Object> getContext() {
    return context;
  }

  @NotNull
  public UserIdentificationNumber getUin() {
    return Objects.requireNonNull(uin);
  }

  @NotNull
  public Date getSignInAt() {
    return Objects.requireNonNull(signInAt);
  }

  @Nullable
  public Date getClosedAt() {
    return closedAt;
  }
}
