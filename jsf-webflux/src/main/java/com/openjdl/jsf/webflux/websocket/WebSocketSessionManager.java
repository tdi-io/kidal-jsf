package com.openjdl.jsf.webflux.websocket;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.openjdl.jsf.core.JsfMicroServiceListener;
import com.openjdl.jsf.core.exception.JsfException;
import com.openjdl.jsf.core.exception.JsfExceptions;
import com.openjdl.jsf.core.utils.ReflectionUtils;
import com.openjdl.jsf.core.utils.SpringUtils;
import com.openjdl.jsf.core.utils.StringUtils;
import com.openjdl.jsf.webflux.websocket.annotation.WebSocketRequestMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created at 2020-08-12 10:16:47
 *
 * @author kidal
 * @since 0.1.0
 */
public class WebSocketSessionManager implements WebSocketHandler, CorsConfigurationSource, JsfMicroServiceListener {
  /**
   * 日志
   */
  private final static Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

  /**
   *
   */
  @NotNull
  private final SpringUtils springUtils;

  /**
   * 转换服务
   */
  @NotNull
  private final ConversionService conversionService;

  /**
   * 匿名会话
   */
  private final ConcurrentMap<String, WebSocketSession> anonymousSessionMap = Maps.newConcurrentMap();

  /**
   * 已认证会话锁
   */
  private final ReentrantReadWriteLock authenticatedSessionMapLock = new ReentrantReadWriteLock(true);

  /**
   * 已认证会话(SessionId -> Session)
   */
  private final Map<String, WebSocketSession> authenticatedSessionMapById = Maps.newHashMap();

  /**
   * 已认证会话(UIN -> Session)
   */
  private final Map<Object, WebSocketSession> authenticatedSessionMapByUin = Maps.newHashMap();

  /**
   * 消息处理器
   */
  private final Map<String, WebSocketMessageHandler> handlerMap = Maps.newHashMap();

  /**
   *
   */
  public WebSocketSessionManager(@NotNull SpringUtils springUtils,
                                 @NotNull ConversionService conversionService) {
    this.registerSelf();
    this.springUtils = springUtils;
    this.conversionService = conversionService;
  }

  /**
   *
   */
  @Override
  public void onMicroServiceInitialized() {
    springUtils
      .getAllBeans(true)
      .forEach(bean -> {
        WebSocketRequestMapping typeMapping = bean.getClass().getAnnotation(WebSocketRequestMapping.class);
        if (typeMapping == null) {
          return;
        }
        String typeName = StringUtils.isBlank(typeMapping.value())
          ? null
          : typeMapping.value();

        ReflectionUtils.doWithMethods(
          bean.getClass(),
          method -> {
            WebSocketRequestMapping methodMapping = method.getAnnotation(WebSocketRequestMapping.class);

            String methodName = StringUtils.isBlank(methodMapping.value())
              ? StringUtils.uncapitalize(method.getName())
              : methodMapping.value();

            String type = typeName != null
              ? String.format("%s/%s", typeName, methodName)
              : methodName;

            WebSocketMessageHandler prevHandler = handlerMap.put(type, new WebSocketMessageHandler(bean, method));

            if (prevHandler != null) {
              throw new IllegalStateException(
                String.format("WebSocket message handler type=`%s` duplicated: %s.%s or %s.%s",
                  type,
                  prevHandler.getBean().getClass().getSimpleName(),
                  prevHandler.getMethod().getName(),
                  bean.getClass().getSimpleName(),
                  method.getName()
                )
              );
            } else {
              log.debug("Registered WebSocket message handler `{}.{}` for message `{}`",
                bean.getClass().getSimpleName(), method.getName(), type);
            }
          },
          method -> method.isAnnotationPresent(WebSocketRequestMapping.class)
        );
      });
  }

  /**
   *
   */
  @NotNull
  @Override
  public Mono<Void> handle(@NotNull org.springframework.web.reactive.socket.WebSocketSession webSocketSession) {
    // 添加到匿名会话
    WebSocketSession session = new WebSocketSession(this, webSocketSession);
    anonymousSessionMap.put(session.getId(), session);

    // 准备发送流
    Mono<Void> incoming = session
      .getWebSocketSession()
      .receive()
      .doOnNext(message -> {
          String rawPayload = message.getPayloadAsText();
          WebSocketPayload payload = WebSocketPayload.of(rawPayload);
          WebSocketPayload outgoingPayload = handleIncomingPayload(session, payload);

          session.sendPayload(outgoingPayload);
      })
      .doFinally(signalType -> {
        log.warn("doFinally: {}, {}", signalType, session.getId());
        session.close();
      })
      .then();
    Mono<Void> outgoing = session.getWebSocketSession().send(Flux.create(session::setSink));

    // 发送
    return Mono.zip(incoming, outgoing).then();
  }

  /**
   *
   */
  @NotNull
  WebSocketPayload handleIncomingPayload(@NotNull WebSocketSession session,
                                         @NotNull WebSocketPayload payload) {
    // 获取处理器
    WebSocketMessageHandler handler = handlerMap.get(payload.getType());
    if (handler == null) {
      // 使用内建消息处理
      if ("$heartbeat".equals(payload.getType())) {
        return payload.toResponse((Object) null);
      }

      // 报错
      return payload.toResponse(
        new WebSocketPayload.Error(
          JsfExceptions.BAD_REQUEST.getId(),
          JsfExceptions.BAD_REQUEST.getCode(),
          "There's no handler for this type: " + payload.getType()
        )
      );
    }

    // 处理载荷
    try {
      WebSocketMessageHandlingContext context = new WebSocketMessageHandlingContext(this, session, payload);
      Object responseData = handler.handle(context);
      return payload.toResponse(responseData);
    } catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof JsfException) {
        return payload.toResponse(new WebSocketPayload.Error((JsfException) e.getTargetException()));
      } else {
        log.error("Handle incoming payload {} failed", payload.getType(), e);
        return payload.toResponse(new WebSocketPayload.Error(JsfExceptions.SERVER_INTERNAL_ERROR));
      }
    } catch (JsfException e) {
      return payload.toResponse(new WebSocketPayload.Error(e));
    } catch (Exception e) {
      log.error("Handle incoming payload {} failed", payload.getType(), e);
      return payload.toResponse(new WebSocketPayload.Error(JsfExceptions.SERVER_INTERNAL_ERROR));
    }
  }

  /**
   * 跨域
   */
  @Override
  public CorsConfiguration getCorsConfiguration(@NotNull ServerWebExchange exchange) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.addAllowedOrigin("*");
    return configuration;
  }

  /**
   *
   */
  @NotNull
  public ImmutableList<WebSocketSession> getAnonymousSessions() {
    return ImmutableList.copyOf(anonymousSessionMap.values());
  }

  /**
   *
   */
  @NotNull
  public ImmutableList<WebSocketSession> getAuthenticatedSessions() {
    return ImmutableList.copyOf(authenticatedSessionMapByUin.values());
  }

  /**
   *
   */
  @Nullable
  public WebSocketSession getAnonymousSessionById(@NotNull String id) {
    return anonymousSessionMap.get(id);
  }

  /**
   *
   */
  @Nullable
  public WebSocketSession getAuthenticatedSessionById(@NotNull String id) {
    authenticatedSessionMapLock.readLock().lock();
    try {
      return authenticatedSessionMapById.get(id);
    } finally {
      authenticatedSessionMapLock.readLock().unlock();
    }
  }

  /**
   *
   */
  @Nullable
  public WebSocketSession getAuthenticatedSessionByUin(@NotNull Object uin) {
    authenticatedSessionMapLock.readLock().lock();
    try {
      return authenticatedSessionMapByUin.get(uin);
    } finally {
      authenticatedSessionMapLock.readLock().unlock();
    }
  }

  /**
   * 登录
   */
  void onSignIn(@NotNull WebSocketSession session) {
    anonymousSessionMap.remove(session.getId());

    authenticatedSessionMapLock.writeLock().lock();
    try {
      authenticatedSessionMapByUin.put(session.getUin(), session);
      authenticatedSessionMapById.put(session.getId(), session);
    } finally {
      authenticatedSessionMapLock.writeLock().unlock();
    }
  }

  /**
   * 登出
   */
  void onSignOut(@NotNull WebSocketSession session) {
    authenticatedSessionMapLock.writeLock().lock();
    try {
      if (session.isSignedIn()) {
        authenticatedSessionMapByUin.remove(session.getUin());
      }
      authenticatedSessionMapById.remove(session.getId());
    } finally {
      authenticatedSessionMapLock.writeLock().unlock();
    }

    anonymousSessionMap.put(session.getId(), session);
  }

  //--------------------------------------------------------------------------
  //
  //--------------------------------------------------------------------------

  @NotNull
  public SpringUtils getSpringUtils() {
    return springUtils;
  }

  @NotNull
  public ConversionService getConversionService() {
    return conversionService;
  }
}
