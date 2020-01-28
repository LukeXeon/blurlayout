package com.luke.android.recycle;

import org.jetbrains.annotations.NotNull;

import java.util.TreeMap;

// Never serialized.
@SuppressWarnings("serial")
class PrettyPrintTreeMap<K, V> extends TreeMap<K, V> {
  @NotNull
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("( ");
    for (Entry<K, V> entry : entrySet()) {
      sb.append('{').append(entry.getKey()).append(':').append(entry.getValue()).append("}, ");
    }
    if (!isEmpty()) {
      sb.replace(sb.length() - 2, sb.length(), "");
    }
    return sb.append(" )").toString();
  }
}
