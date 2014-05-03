package com.simplescript;

/**
 * Created by Alex on 4/6/14.
 */
public class SimpleScriptClassLoader extends ClassLoader {

	//

	public Class load(byte[] data) {
		try {
			return defineClass(Main.name, data, 0, data.length);
		} catch (ClassFormatError e) {
			System.out.println(
					e.getLocalizedMessage()
			);
		}
		return null;
	}
}
