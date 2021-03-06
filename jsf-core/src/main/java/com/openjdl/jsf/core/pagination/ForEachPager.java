package com.openjdl.jsf.core.pagination;

import com.openjdl.jsf.core.utils.callback.Action1;
import org.jetbrains.annotations.NotNull;


/**
 * Created at 2020-08-04 17:30:00
 *
 * @author kidal
 * @since 0.1.0
 */
public class ForEachPager<T> {
  /**
   * 起始分页参数
   */
  private final PageArgs beginPageArgs;

  /**
   * 数据加载器
   */
  private final Action1<Page<T>, PageArgs> loader;

  /**
   *
   */
  public ForEachPager(@NotNull PageArgs beginPageArgs, @NotNull Action1<Page<T>, PageArgs> loader) {
    this.beginPageArgs = beginPageArgs;
    this.loader = loader;
  }

  public void forEach() {

  }
}
