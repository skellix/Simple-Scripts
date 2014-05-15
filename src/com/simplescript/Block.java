package com.simplescript;

import org.objectweb.asm.Label;

/**
 * Created by Alex on 5/14/14.
 */
public class Block {

	public Label label;
	public int index;

	Block(Label label) {
		this.label = label;
	}

	Block(Label label, int index) {
		this.label = label;
		this.index = index;
	}
}
