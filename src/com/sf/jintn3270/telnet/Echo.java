package com.sf.jintn3270.telnet;

import java.io.ByteArrayOutputStream;


import com.sf.jintn3270.TerminalModel;

/**
 * RFC 857 - Echo.
 * 
 * If echo is disabled (meaning the remote host will not echo, we enable
 * localEcho on the TerminalModel, so that typed characters will end up
 * in the buffer.
 */
public class Echo extends Option {
	public Echo() {
		super();
	}
	
	public String getName() {
		return "Echo";
	}
	
	public byte getCode() {
		return (byte)1;
	}
	
	public void initiate(TelnetClient client) {
		client.sendDo(getCode());
	}
	
	void setEnabled(boolean b, TelnetClient client) {
		super.setEnabled(b, client);
		client.getTerminalModel().setLocalEcho(!isEnabled());
	}
}