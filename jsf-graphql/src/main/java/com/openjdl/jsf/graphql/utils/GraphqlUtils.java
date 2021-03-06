package com.openjdl.jsf.graphql.utils;

import com.google.common.collect.ImmutableMap;
import com.openjdl.jsf.core.pagination.Page;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created at 2020-08-05 23:02:21
 *
 * @author kidal
 * @since 0.1.0
 */
public class GraphqlUtils {
  /**
   * X系列参数前缀
   */
  public static final String X_VARIABLE_PREFIX = "x-jsf-graphql-";

  /**
   *
   */
  public static boolean isXVariable(@NotNull String key) {
    return key.toLowerCase().startsWith(X_VARIABLE_PREFIX);
  }

  /**
   *
   */
  @NotNull
  public static Map<String, String> parseXVariables(@Nullable Map<String, Object> variables) {
    if (variables == null) {
      return Collections.emptyMap();
    }

    return variables
      .entrySet()
      .stream()
      .filter(entry -> isXVariable(entry.getKey()))
      .collect(Collectors.toMap(
        entry -> entry.getKey().substring(X_VARIABLE_PREFIX.length()).toLowerCase(),
        entry -> Objects.toString(entry.getValue()))
      );
  }

  /**
   *
   */
  @NotNull
  public static <T, V> Map<String, Object> toPageResults(@NotNull Page<T> page, @NotNull Function<T, V> transform) {
    return ImmutableMap.of(
      "page", page.getPageArgs().getPage(),
      "limit", page.getPageArgs().getLimit(),
      "totalCount", page.getTotalCount(),
      "nodes", page.getNodes().stream().map(transform).toArray()
    );
  }

  /**
   *
   */
  @NotNull
  public static <T> Map<String, Object> toPageResults(@NotNull Page<T> page) {
    return toPageResults(page, node -> node);
  }
}
