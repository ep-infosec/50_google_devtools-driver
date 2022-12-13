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
package org.uiautomation.ios.wkrdp;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openqa.selenium.TimeoutException;
import org.uiautomation.ios.wkrdp.events.ChildIframeInserted;
import org.uiautomation.ios.wkrdp.events.ChildNodeRemoved;
import org.uiautomation.ios.wkrdp.events.Event;
import org.uiautomation.ios.wkrdp.events.EventHistory;
import org.uiautomation.ios.wkrdp.model.NodeId;
import org.uiautomation.ios.wkrdp.model.RemoteWebElement;

public final class DOMContext {
  private static final Logger log = Logger.getLogger(DOMContext.class.getName());

  private volatile boolean isReady = true;
  private NodeId parent;

  private final WebInspectorHelper inspector;

  private RemoteWebElement window;
  private RemoteWebElement document;
  private RemoteWebElement iframe;

  private boolean isOnMainFrame = true;
  private RemoteWebElement mainDocument;
  private RemoteWebElement mainWindow;

  private final EventHistory eventHistory = new EventHistory();

  private final Lock eventsLock = new ReentrantLock();
  private final Condition pageLoadEvent = eventsLock.newCondition();

  DOMContext(WebInspectorHelper inspector) {
    this.inspector = inspector;
  }

  RemoteWebElement getDocument() {
    int cpt = 0;
    while (!isReady) {
      cpt++;
      if (cpt > 20) {
        isReady = true;
        throw new TimeoutException("doc not ready.");
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        log.log(Level.FINE, "", e);
      }
    }
    return document;
  }

  RemoteWebElement getWindow() {
    return window;
  }

  public void newContext() {
    window = null;
    document = null;
    iframe = null;
    mainDocument = null;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append("window " + window);
    b.append("document " + document);
    b.append("iframe " + iframe);
    b.append("mainDocument " + mainDocument);
    return b.toString();
  }

  void reset() {
    RemoteWebElement newDocument = null;
    RemoteWebElement newWindow = null;

    // check is what changed was the context for the current frame.
    if (iframe != null) {
      log.info("iframe was null");
      try {
        newDocument = iframe.getContentDocument();
        newWindow = iframe.getContentWindow();
        setCurrentFrame(iframe, newDocument, newWindow);
        return;
      } catch (Exception e) {
        log.log(Level.SEVERE, "iframe", e);
      }
    }
    newContext();
  }

  // TODO: Cleanup. A reference to the main document of the page needs to be kept. Calling
  // getDocument again after switching to an iframe breaks the nodeId reference.
  public void setCurrentFrame(
      RemoteWebElement iframe, RemoteWebElement document, RemoteWebElement window) {
    this.iframe = iframe;
    this.document = document;
    this.window = window;

    if (iframe != null) {
      isOnMainFrame = false;
    } else {
      isOnMainFrame = true;
    }

    // switchToDefaultContent. revert to main document if it was set.
    if (iframe == null && document == null) {
      this.document = mainDocument;
      this.window = mainWindow;
    }

    // setting the main document for the first time
    if (iframe == null && document != null) {
      mainDocument = document;
      mainWindow = window;
    }
    isReady = true;
  }

  boolean isOnMainFrame() {
    return isOnMainFrame;
  }

  synchronized void domHasChanged(Event e) {
    try {
      if (e instanceof ChildNodeRemoved) {
        ChildNodeRemoved removed = (ChildNodeRemoved) e;
        if (iframe != null ? removed.getNode().equals(iframe.getNodeId()) : false) {
          isReady = false;
          parent = removed.getParent();
          log.fine("current frame " + iframe.getNodeId() + " is gone.Parent = " + parent);
          List<ChildIframeInserted> newOnes = eventHistory.getInsertedFrames(parent);
          if (newOnes.size() == 0) {
            return;
          } else if (newOnes.size() == 1) {
            Event newFrame = newOnes.get(0);
            assignNewFrameFromEvent((ChildIframeInserted) newFrame);
            eventHistory.removeEvent(newFrame);
          } else {
            log.warning(
                "there should be only 1 newly created frame with parent ="
                    + parent
                    + ". Found "
                    + newOnes.size());
          }
        }
        return;
      }

      if (e instanceof ChildIframeInserted) {
        ChildIframeInserted newFrame = (ChildIframeInserted) e;
        // are we waiting for something ?
        if (isReady) {
          eventHistory.add(newFrame);
          return;
        } else {
          // is it the new node we're looking for ?
          if (parent.equals(newFrame.getParent())) {
            if (log.isLoggable(Level.FINE)) log.fine("the new node is here :" + newFrame.getNode());
            assignNewFrameFromEvent(newFrame);
          }
        }
      }
    } catch (Exception ex) {
      log.log(Level.SEVERE, "", e);
    }
  }

  private void assignNewFrameFromEvent(ChildIframeInserted newFrameEvent) throws Exception {
    RemoteWebElement frame = new RemoteWebElement(newFrameEvent.getNode(), inspector);
    RemoteWebElement document = new RemoteWebElement(newFrameEvent.getContentDocument(), inspector);
    RemoteWebElement window = frame.getContentWindow();
    setCurrentFrame(frame, document, window);
    isReady = true;
  }

  void frameDied() {
    // if that's the one we're working on, deselect it.
    if (iframe != null) {
      if (!iframe.exists()) {
        isReady = true;
      }
    }
  }

  public void waitForLoadEvent() {
    try {
      eventsLock.lock();
      pageLoadEvent.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new TimeoutException("timeout waiting for page load event.");
    } finally {
      eventsLock.unlock();
    }
  }

  Lock eventsLock() {
    return eventsLock;
  }

  void signalNewPageLoadReceived() {
    eventsLock.lock();
    try {
      reset();
      pageLoadEvent.signalAll();
    } finally {
      eventsLock.unlock();
    }
  }
}
