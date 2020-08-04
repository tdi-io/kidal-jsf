package org.kidal.jsf.core.utils.callback;

/**
 * 带返回值的回调
 *
 * @author kidal
 */
public interface Action4<V, A0, A1, A2, A3> {
  /**
   * 回调方法
   *
   * @param arg0 参数0
   * @param arg1 参数1
   * @param arg2 参数2
   * @param arg3 参数3
   * @return 任意返回值
   */
  V call(A0 arg0, A1 arg1, A2 arg2, A3 arg3);
}
