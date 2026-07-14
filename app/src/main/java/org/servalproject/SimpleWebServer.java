/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

/*
 * Includes code from Paul James Mutton's Mini Web Server / SimpleWebServer.
 * Copyright Paul James Mutton, 2001-2004, http://www.jibble.org/
 * Original project notes described dual GPL/commercial licensing; see the
 * original upstream distribution for full third-party license details.
 */

package org.servalproject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import android.util.Log;

/**
 * Copyright Paul Mutton
 * <https://www.jibble.org/>
 *
 */
public class SimpleWebServer extends Thread {

	private final ServerSocket _serverSocket;
	private boolean _running = true;
	public final int port;

	public SimpleWebServer(int startPort, int endPort) throws IOException {
		int p=startPort;
		ServerSocket s = null;
		while(p<=endPort){
			try {
				s = new ServerSocket(p);
				break;
			}catch (IOException e){

			}
			p++;
		}
		if (s==null)
			throw new IOException("Unable to bind web server port");
		_serverSocket = s;
		port = p;
        start();
    }

    @Override
	public void interrupt() {
    	_running=false;
		try {
			_serverSocket.close();
		} catch (IOException e) {
			Log.e("BatPhone WebServer", e.toString(), e);
		}
		super.interrupt();
	}

	@Override
	public void run() {
        while (_running) {
        	try {
				Socket socket = _serverSocket.accept();
				RequestThread requestThread = new RequestThread(socket);
                requestThread.start();
        	}catch (IOException e) {
            	Log.e("BatPhone WebServer",e.toString(),e);
            }
        }
		try {
			_serverSocket.close();
		} catch (IOException e) {
			Log.e("BatPhone WebServer", e.toString(), e);
		}
    }
}