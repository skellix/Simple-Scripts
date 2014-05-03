package com.simplescript;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Created by Alex on 5/3/14.
 */
public class SimpleScriptUtils {

	//

	public String read(String fileName) {
		File file = new File(fileName);
		String out = "";
		try {
			Scanner scanner = new Scanner(file);
			while (scanner.hasNextLine()) {
				out += scanner.nextLine()+"\n";
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			Main.error("File '"+file.getAbsolutePath()+"' does not exist!");
		}
		return out;
	}
}
