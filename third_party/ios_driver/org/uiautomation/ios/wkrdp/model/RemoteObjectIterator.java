/*
 * Copyright 2012-2013 eBay Software Foundation and ios-driver committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.uiautomation.ios.wkrdp.model;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** An iterator over webpage objects. */
public final class RemoteObjectIterator implements Iterator<Object> {
  private static final Logger log = Logger.getLogger(RemoteObjectIterator.class.getName());

  private final RemoteObject underlyingObject;
  private final int size;

  private int index = 0;

  RemoteObjectIterator(RemoteObject uro, int size) {
    this.underlyingObject = uro;
    this.size = size;
  }

  @Override
  public boolean hasNext() {
    return index < size;
  }

  @Override
  public Object next() {
    Object res;
    try {
      res = underlyingObject.call("[" + index + "]");
      index++;
      return res;
    } catch (Exception e) {
      log.log(Level.SEVERE, "error", e);
    }
    return null;
  }

  @Override
  public void remove() {
    throw new IllegalAccessError("NI");
  }
}
