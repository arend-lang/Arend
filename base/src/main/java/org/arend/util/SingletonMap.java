package org.arend.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SingletonMap<K,V> extends AbstractMap<K,V> implements Map.Entry<K,V> {
  private final K key;
  private V value;

  private transient Set<K> keySet;
  private transient Set<Map.Entry<K,V>> entrySet;
  private transient Collection<V> values;

  public SingletonMap(K key, V value) {
    this.key = key;
    this.value = value;
  }

  public int size() {
    return 1;
  }

  public boolean isEmpty() {
    return false;
  }

  public boolean containsKey(Object key) {
    return Objects.equals(key, this.key);
  }

  public boolean containsValue(Object value) {
    return Objects.equals(value, this.value);
  }

  public V get(Object key) {
    return Objects.equals(key, this.key) ? this.value : null;
  }

  public @NotNull Set<K> keySet() {
    if (keySet == null) keySet = Collections.singleton(key);
    return keySet;
  }

  public @NotNull Set<Map.Entry<K,V>> entrySet() {
    if (entrySet == null) entrySet = Collections.singleton(this);
    return entrySet;
  }

  public @NotNull Collection<V> values() {
    if (values == null) values = Collections.singleton(value);
    return values;
  }

  @Override
  public K getKey() {
    return key;
  }

  @Override
  public V getValue() {
    return value;
  }

  @Override
  public V setValue(V value) {
    V old = this.value;
    this.value = value;
    return old;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SingletonMap<?, ?> that = (SingletonMap<?, ?>) o;
    return Objects.equals(key, that.key) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }

  @Override
  public String toString() {
    return key + " = " + value;
  }

  // Override default methods in Map
  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return Objects.equals(key, this.key) ? this.value : defaultValue;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    action.accept(key, value);
  }

  @Override
  public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    value = function.apply(key, value);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    if (Objects.equals(key, this.key) && Objects.equals(oldValue, this.value)) {
      this.value = newValue;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public V replace(K key, V value) {
    if (Objects.equals(key, this.key)) {
      V old = this.value;
      this.value = value;
      return old;
    } else {
      return null;
    }
  }

  @Override
  public V computeIfAbsent(K key, @NotNull Function<? super K, ? extends V> mappingFunction) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V computeIfPresent(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V compute(K key, @NotNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V merge(K key, @NotNull V value, @NotNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    throw new UnsupportedOperationException();
  }
}
