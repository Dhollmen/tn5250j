/*
 * @(#)Session5250.java
 * Copyright:    Copyright (c) 2001
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307 USA
 *
 */
package org.tn5250j;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.tn5250j.interfaces.SessionInterface;
import org.tn5250j.event.SessionListener;
import org.tn5250j.event.SessionChangeEvent;
import org.tn5250j.interfaces.ScanListener;
import org.tn5250j.framework.tn5250.Screen5250;
import org.tn5250j.framework.tn5250.tnvt;
import org.tn5250j.framework.common.SessionManager;

/**
 * A host session
 */
public class Session5250 implements SessionInterface {

   private String configurationResource;
   private String sessionName;
   private int sessionType;
   protected Properties sesProps;
   private String sslType;
   private boolean heartBeat;
   String propFileName;
   protected SessionConfig sesConfig;
   tnvt vt;
   Screen5250 screen;
   SessionGUI guiComponent;
   
   private List<SessionListener> listeners = null;
   private final ReadWriteLock listenerLock = new ReentrantReadWriteLock(); 

   // WVL - LDC : TR.000300 : Callback scenario from 5250
   private boolean scan; // = false;
   private ScanListener scanListener; // = null;

   public Session5250 (Properties props, String configurationResource,
                     String sessionName,
                     SessionConfig config) {

      propFileName = config.getConfigurationResource();

      sesConfig = config;
      this.configurationResource = configurationResource;
      this.sessionName = sessionName;
      sesProps = props;

      if (sesProps.containsKey(TN5250jConstants.SESSION_HEART_BEAT))
         heartBeat = true;

      screen = new Screen5250();

      //screen.setVT(vt);

   }

   public String getConfigurationResource() {

      return configurationResource;

   }

   public SessionConfig getConfiguration() {

      return sesConfig;
   }

   public SessionManager getSessionManager() {
      return SessionManager.instance();
   }

   public boolean isConnected() {

      if (vt == null)
         return false;
      else
         return vt.isConnected();

   }

   public boolean isSendKeepAlive() {
      return heartBeat;
   }

   public Properties getConnectionProperties() {
      return sesProps;
   }

   public void setGUI (SessionGUI gui) {
      guiComponent = gui;
   }

   public SessionGUI getGUI() {
      return guiComponent;
   }
   public String getSessionName() {
      return sessionName;
   }

   public String getAllocDeviceName() {
      if (vt != null)
         return vt.getAllocatedDeviceName();
      else
         return null;
   }

   public int getSessionType() {

      return sessionType;

   }

   public String getHostName() {
      return vt.getHostName();
   }

   public Screen5250 getScreen() {

      return screen;

   }

   public void connect() {

      String proxyPort = "1080"; // default socks proxy port
      boolean enhanced = false;
      boolean support132 = false;
      int port = 23; // default telnet port

      enhanced = sesProps.containsKey(TN5250jConstants.SESSION_TN_ENHANCED);

      if (sesProps.containsKey(TN5250jConstants.SESSION_SCREEN_SIZE))
         if (((String)sesProps.getProperty(TN5250jConstants.SESSION_SCREEN_SIZE)).equals(TN5250jConstants.SCREEN_SIZE_27X132_STR))
            support132 = true;

      final tnvt vt = new tnvt(this,screen,enhanced,support132);
      setVT(vt);

//      vt.setController(this);

      if (sesProps.containsKey(TN5250jConstants.SESSION_PROXY_PORT))
         proxyPort = (String)sesProps.getProperty(TN5250jConstants.SESSION_PROXY_PORT);

      if (sesProps.containsKey(TN5250jConstants.SESSION_PROXY_HOST))
         vt.setProxy((String)sesProps.getProperty(TN5250jConstants.SESSION_PROXY_HOST),
                     proxyPort);

      if (sesProps.containsKey(TN5250jConstants.SSL_TYPE)) {
         sslType = (String)sesProps.getProperty(TN5250jConstants.SSL_TYPE);
      }
      else {
         // set default to none
         sslType = TN5250jConstants.SSL_TYPE_NONE;
      }

      vt.setSSLType(sslType);

      if (sesProps.containsKey(TN5250jConstants.SESSION_CODE_PAGE))
         vt.setCodePage((String)sesProps.getProperty(TN5250jConstants.SESSION_CODE_PAGE));

      if (sesProps.containsKey(TN5250jConstants.SESSION_DEVICE_NAME))
         vt.setDeviceName((String)sesProps.getProperty(TN5250jConstants.SESSION_DEVICE_NAME));

      if (sesProps.containsKey(TN5250jConstants.SESSION_HOST_PORT)) {
         port = Integer.parseInt((String)sesProps.getProperty(TN5250jConstants.SESSION_HOST_PORT));
      }
      else {
         // set to default 23 of telnet
         port = 23;
      }

      final String ses = sesProps.getProperty(TN5250jConstants.SESSION_HOST);
      final int portp = port;

      // lets set this puppy up to connect within its own thread
      Runnable connectIt = new Runnable() {
            public void run() {
               vt.connect(ses,portp);
            }

        };

      // now lets set it to connect within its own daemon thread
      //    this seems to work better and is more responsive than using
      //    swingutilities's invokelater
      Thread ct = new Thread(connectIt);
      ct.setDaemon(true);
      ct.start();

   }

   public void disconnect() {
      vt.disconnect();
   }

   // WVL - LDC : TR.000300 : Callback scenario from 5250
   protected void setVT(tnvt v)
   {
     vt = v;
     screen.setVT(vt);
     if (vt != null)
       vt.setScanningEnabled(this.scan);
   }

   public tnvt getVT() {
      return vt;
   }

   // WVL - LDC : TR.000300 : Callback scenario from 5250
   /**
    * Enables or disables scanning.
    *
    * @param scan enables scanning when true; disables otherwise.
    *
    * @see tnvt#setCommandScanning(boolean);
    * @see tnvt#isCommandScanning();
    * @see tnvt#scan();
    * @see tnvt#parseCommand();
    * @see scanned(String,String)
    */
   public void setScanningEnabled(boolean scan)
   {
     this.scan = scan;

     if (this.vt != null)
       this.vt.setScanningEnabled(scan);
   }

   // WVL - LDC : TR.000300 : Callback scenario from 5250
   /**
    * Checks whether scanning is enabled.
    *
    * @return true if command scanning is enabled; false otherwise.
    *
    * @see tnvt#setCommandScanning(boolean);
    * @see tnvt#isCommandScanning();
    * @see tnvt#scan();
    * @see tnvt#parseCommand();
    * @see scanned(String,String)
    */
   public boolean isScanningEnabled()
   {
     if (this.vt != null)
       return this.vt.isScanningEnabled();

     return this.scan;
   }

   // WVL - LDC : TR.000300 : Callback scenario from 5250
   /**
    * This is the callback method for the TNVT when sensing the action cmd
    * screen pattern (!# at position 0,0).
    *
    * This is <strong>NOT a threadsafe method</strong> and will be called
    * from the TNVT read thread!
    *
    * @param command discovered in the 5250 stream.
    * @param remainder are all the other characters on the screen.
    *
    * @see tnvt#setCommandScanning(boolean);
    * @see tnvt#isCommandScanning();
    * @see tnvt#scan();
    * @see tnvt#parseCommand();
    * @see scanned(String,String)
    */
  public void scanned(String command, String remainder)
  {
    if (scanListener != null)
      scanListener.scanned(command, remainder);
   }

   public void addScanListener(ScanListener listener)
   {
     scanListener = ScanMulticaster.add(scanListener, listener);
   }

   public void removeScanListener(ScanListener listener)
   {
     scanListener = ScanMulticaster.remove(scanListener, listener);
   }

   /**
    * Notify all registered listeners of the onSessionChanged event.
    *
    * @param state  The state change property object.
    */
   public final void fireSessionChanged(int state) {
	   listenerLock.readLock().lock();
	   try {
		   if (this.listeners != null) {
			   for (SessionListener listener : this.listeners) {
				   SessionChangeEvent sce = new SessionChangeEvent(this);
				   sce.setState(state);
				   listener.onSessionChanged(sce);
			   }
		   }
	   } finally {
		   listenerLock.readLock().unlock();
	   }
   }

   /**
    * Add a SessionListener to the listener list.
    *
    * @param listener  The SessionListener to be added
    */
   public final void addSessionListener(SessionListener listener) {
	   listenerLock.writeLock().lock();
	   try {
		   if (listeners == null) {
			   listeners = new ArrayList<SessionListener>(3);
		   }
		   listeners.add(listener);
	   } finally {
		   listenerLock.writeLock().unlock();
	   }
   }

   /**
    * Remove a SessionListener from the listener list.
    *
    * @param listener  The SessionListener to be removed
    */
   public final void removeSessionListener(SessionListener listener) {
	   listenerLock.writeLock().lock();
	   try {
		   if (listeners != null) {
			   listeners.remove(listener);
		   }
	   } finally {
		   listenerLock.writeLock().unlock();
	   }
   }

}