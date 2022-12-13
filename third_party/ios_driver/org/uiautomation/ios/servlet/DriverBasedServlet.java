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
package org.uiautomation.ios.servlet;

import javax.servlet.http.HttpServlet;
import org.uiautomation.ios.IOSServer;
import org.uiautomation.ios.IOSServerManager;

@SuppressWarnings("serial")
public abstract class DriverBasedServlet extends HttpServlet {
  private IOSServerManager driver;

  public synchronized IOSServerManager getDriver() {
    if (driver == null) {
      driver = (IOSServerManager) getServletContext().getAttribute(IOSServer.DRIVER);
    }
    return driver;
  }
}
