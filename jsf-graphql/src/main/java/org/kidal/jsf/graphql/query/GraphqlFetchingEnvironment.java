package org.kidal.jsf.graphql.query;

import graphql.schema.DataFetchingEnvironment;
import org.jetbrains.annotations.NotNull;
import org.kidal.jsf.core.exception.JsfException;
import org.kidal.jsf.core.exception.JsfExceptions;
import org.kidal.jsf.core.pagination.PageArgs;
import org.kidal.jsf.core.pagination.PageSortArg;
import org.kidal.jsf.core.sugar.BeanAccessor;
import org.kidal.jsf.core.sugar.BeanPropertyAccessor;
import org.kidal.jsf.core.sugar.MapBeanPropertyAccessor;
import org.kidal.jsf.core.utils.StringUtils;
import org.springframework.core.convert.ConversionService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created at 2020-08-05 17:42:46
 *
 * @author kidal
 * @since 0.1.0
 */
public class GraphqlFetchingEnvironment implements BeanAccessor {
  /**
   *
   */
  @NotNull
  private final DataFetchingEnvironment environment;

  /**
   *
   */
  @NotNull
  private final GraphqlFetchingContext context;

  /**
   *
   */
  @NotNull
  private final BeanAccessor parameters;

  /**
   *
   */
  public GraphqlFetchingEnvironment(@NotNull DataFetchingEnvironment environment,
                                    @NotNull GraphqlFetchingContext context) {
    this.environment = environment;
    this.context = context;
    this.parameters = new BeanAccessor() {
      @NotNull
      @Override
      public BeanPropertyAccessor getPropertyAccessor() {
        return new MapBeanPropertyAccessor(environment.getArguments());
      }

      @Override
      public ConversionService getConversionService() {
        return context.getConversionService();
      }

      @NotNull
      @Override
      public Supplier<RuntimeException> getExceptionSupplier() {
        return () -> new JsfException(JsfExceptions.BAD_REQUEST);
      }
    };
  }

  /**
   * 获取上层的对象
   */
  @SuppressWarnings("unchecked")
  public <T> T getSourceObject() {
    Object sourceObject = environment.getSource();
    if (sourceObject instanceof Map) {
      return (T) ((Map<String, Object>) sourceObject).getOrDefault(".source", null);
    }
    return (T) sourceObject;
  }

  /**
   * 获取分页参数
   *
   * @return 分页参数
   */
  @NotNull
  public PageArgs getPageArgs() {
    // 页码
    int page = environment.getArgumentOrDefault("page", PageArgs.DEFAULT_PAGE);

    // 每页个数
    int limit = environment.getArgumentOrDefault("limit", PageArgs.DEFAULT_LIMIT);

    // 排序
    List<String> sorts = environment.getArgumentOrDefault("sorts", Collections.emptyList());
    PageSortArg[] pageSortArgs = sorts.stream()
      .map(it -> it.split(" "))
      .filter(it -> it.length == 2 && StringUtils.isNoneBlank(it[0], it[1]))
      .map(pair -> new PageSortArg(pair[0], "desc".equals(pair[1].toLowerCase())))
      .toArray(PageSortArg[]::new);

    // done
    return PageArgs.of(page, limit, pageSortArgs);
  }

  /**
   *
   */
  @NotNull
  public DataFetchingEnvironment getEnvironment() {
    return environment;
  }

  /**
   *
   */
  @NotNull
  public GraphqlFetchingContext getContext() {
    return context;
  }

  /**
   *
   */
  @NotNull
  public BeanAccessor getParameters() {
    return parameters;
  }

  /**
   *
   */
  @NotNull
  @Override
  public BeanPropertyAccessor getPropertyAccessor() {
    return parameters.getPropertyAccessor();
  }

  /**
   *
   */
  @NotNull
  @Override
  public Supplier<RuntimeException> getExceptionSupplier() {
    return parameters.getExceptionSupplier();
  }
}
