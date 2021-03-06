package com.sf.jintn3270.tn3270.stream;

import com.sf.jintn3270.tn3270.TerminalModel3278;
import com.sf.jintn3270.tn3270.TNCharacter;
import com.sf.jintn3270.tn3270.TNFieldCharacter;
import com.sf.jintn3270.tn3270.Field;

/**
 * Write 3270 Command
 */
public class Write extends Command {
	/* The following constants are the incoming Orders allowed as part of the
	 * write stream.
	 *
	 * Details provided by 4.3 of IBM Document number GA23-0059-07, 
	 * 3270 Data Stream Programmer's Reference.
	 */
	private static final int START_FIELD = 0x1d;
	private static final int START_FIELD_EXTENDED = 0x29;
	private static final int SET_BUFFER_ADDRESS = 0x11;
	private static final int SET_ATTRIBUTE = 0x28;
	private static final int MODIFY_FIELD = 0x2c;
	private static final int INSERT_CURSOR = 0x13;
	private static final int PROGRAM_TAB = 0x05;
	private static final int REPEAT_TO_ADDRESS = 0x3c;
	private static final int ERASE_UNPROTECTED_TO_ADDRESS = 0x12;
	private static final int GRAPHIC_ESCAPE = 0x08;
	
	public Write() {
		super(new short[] {0xf1, 0x01});
	}
	
	protected int preform(TerminalModel3278 model, short[] b, int off, int len) {
		System.out.println("" + getClass().getName() + " off: " + off + " len: " + len + " b.length: " + b.length);
		for (int i = off; i < (len + off); i++) {
			short code = b[i];
			switch (code) {
				case START_FIELD: {
					short attribs = b[++i];
					model.print(new TNFieldCharacter(attribs));
					break;
				}
				case START_FIELD_EXTENDED: {
					short pairs = b[++i];
					System.out.println("SFE with " + pairs + " pairs.");
					
					int fieldAddress = -1; // Buffer Address of the start field character
					int ap = 0;
					short[][] attribs = new short[pairs - 1][2];
					for (int p = 0; p < pairs; p++) {
						short type = b[++i];
						short val = b[++i];
						
						if (type == 0xc0) {
							fieldAddress = model.getActivePartition().getBufferAddress();
							model.print(new TNFieldCharacter(val));
						} else {
							attribs[ap][0] = type;
							attribs[ap][1] = val;
							ap++;
						}
					}
					
					// attribs now holds all the attributes for the field.
					for (int p = 0; p < attribs.length; p++) {
						model.getActivePartition().getCharacter(
								fieldAddress).applyAttribute(
										attribs[p][0], attribs[p][1]);
					}
					
					break;
				}
				case SET_BUFFER_ADDRESS: {
					int address = model.getActivePartition().decodeAddress(b[++i], b[++i]);
					model.getActivePartition().setBufferAddress(address);
					break;
				}
				case SET_ATTRIBUTE: {
					short attribType = b[++i];
					short attribVal = b[++i];
					
					model.getActivePartition().getCharacter().applyAttribute(attribType, attribVal);
					
					break;
				}
				case MODIFY_FIELD: {
					short pairs = b[++i];
					System.out.println("MF with " + pairs + " pairs.");
					for (int p = 0; p < pairs; p++) {
						short attribType = b[++i];
						short attribVal = b[++i];
					}
					break;
				}
				case INSERT_CURSOR: {
					model.getActivePartition().cursor().setPosition(
							model.getActivePartition().getBufferAddress());
					break;
				}
				case PROGRAM_TAB: {
					System.out.println("PROGRAM TAB");
					
					// TODO: Insert NULLS when we're not preceeded by a command, order, or order sequence. What the hell is that?
					// See 4.3.7 of the Stream Programmer's Reference.
					
					boolean found = false;
					while (!found) {
						TNCharacter tc = model.getActivePartition().getCharacter();
						if (tc instanceof TNFieldCharacter) {
							TNFieldCharacter tfc = (TNFieldCharacter)tc;
							if (!tfc.isProtected()) {
								found = true;
							}
						}
						
						if (!found) {
							if (model.getActivePartition().getBufferAddress() == model.getActivePartition().getMaxBufferAddress()) {
								model.getActivePartition().setBufferAddress(0);
								break;
							}
							model.getActivePartition().setBufferAddress(
								model.getActivePartition().getBufferAddress() + 1);
						}
					}
					
					// If we found one, the current buffer position is the Field Attribute character.
					// We need to set the buffer address to the first character of the field.
					if (found) {
						model.getActivePartition().setBufferAddress(
							model.getActivePartition().getBufferAddress() + 1);
					}
					
					break;
				}
				case REPEAT_TO_ADDRESS: {
					int stopAddress = model.getActivePartition().decodeAddress(b[++i], b[++i]);
					short c = b[++i];
					if (c == GRAPHIC_ESCAPE) {
						// TODO: Handle alternate character sets.
						c = b[++i];
					}
					
					if (stopAddress == model.getActivePartition().getBufferAddress()) {
						// Set the start to zero and the stop to the max address...
						model.getActivePartition().setBufferAddress(0);
						while (model.getActivePartition().getBufferAddress() < model.getActivePartition().getMaxBufferAddress()) {
							model.print(c);
						}
						model.getActivePartition().setBufferAddress(stopAddress);
					} else if (stopAddress < model.getActivePartition().getBufferAddress()) {
						// Fill to the end of the buffer so that the 
						// current buffer position wraps to 0.
						while (model.getActivePartition().getBufferAddress() != 0) {
							model.print(c);
						}
					}
					
					// Print the same data until the given address
					while(model.getActivePartition().getBufferAddress() < stopAddress) {
						model.print(c);
					}
					
					break;
				}
				case ERASE_UNPROTECTED_TO_ADDRESS: {
					int stopAddress = model.getActivePartition().decodeAddress(b[++i], b[++i]);
					
					if (stopAddress == model.getActivePartition().getBufferAddress()) {
						// erase -all- unprotected fields.
						model.getActivePartition().setBufferAddress(0);
						while (model.getActivePartition().getBufferAddress() < model.getActivePartition().getMaxBufferAddress()) {
							Field f = model.getActivePartition().getField();
							// Make sure we have an unprotected field and that this is not the SF character...
							if (f != null && !f.getFieldCharacter().isProtected() && model.getActivePartition().getBufferAddress() != f.getStart()) {
								model.print((short)0);
							} else {
								model.getActivePartition().setBufferAddress(
										model.getActivePartition().getBufferAddress() + 1);
							}
						}
						model.getActivePartition().setBufferAddress(stopAddress);
					} else if (stopAddress < model.getActivePartition().getBufferAddress()) {
						// erase all unprotected to the end of the display.
						while (model.getActivePartition().getBufferAddress() != 0) {
							Field f = model.getActivePartition().getField();
							if (f != null && !f.getFieldCharacter().isProtected() && model.getActivePartition().getBufferAddress() != f.getStart()) {
								model.print((short)0);
							} else {
								model.getActivePartition().setBufferAddress(
									model.getActivePartition().getBufferAddress() + 1);
							}
						}
					}
					
					// erase everything between the current buffer position and the stopAddress.
					while (model.getActivePartition().getBufferAddress() < stopAddress) {
						Field f = model.getActivePartition().getField();
						if (f != null && !f.getFieldCharacter().isProtected() && model.getActivePartition().getBufferAddress() != f.getStart()) {
							model.print((short)0);
						} else {
							model.getActivePartition().setBufferAddress(
								model.getActivePartition().getBufferAddress() + 1);
						}
					}
					
					break;
				}
				case GRAPHIC_ESCAPE: {
					++i; // Unhandled at the moment.
					break;
				}
				default: {
					model.print(b[i]);
				}
			}
		}
		
		// If we have an unclosed Field, we need to set it's closed value now.
		if (model.getActivePartition().hasFields()) {
			Field lastField = model.getActivePartition().getFields().getLast();
			if (!lastField.isEndSet()) {
				lastField.setEnd(model.getActivePartition().getMaxBufferAddress());
			}
		}
		
		return len;
	}
}
