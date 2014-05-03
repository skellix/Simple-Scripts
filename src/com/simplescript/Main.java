package com.simplescript;

import java.io.*;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by Alex on 4/6/14.
 */
public class Main {

	public static String name = "";
	public static String input = "";
	public static String output = "";
	public static String source = "";
	public static int execute = 1;
	public static int debugLevel = 0;
	public static boolean debug = false;

	public static void main(String[] args) {
		for (int i = 0 ; i < args.length ; i ++) {
			String arg = args[i];
			if (arg.startsWith("-")) {
				for (int j = 1 ; j < arg.length() ; j ++) {
					if (arg.charAt(j) == 'i') {
						input = args[++ i];
					} else if (arg.charAt(j) == 'o') {
						execute --;
						output = args[++ i];
					} else if (arg.charAt(j) == 'x') {
						execute ++;
					} else if (arg.charAt(j) == 'd') {
						Main.debug = true;
					} else if (arg.charAt(j) == 'D') {
						debugLevel ++;
					} else if (arg.charAt(j) == 's') {
						for (i ++; i < args.length ; i ++) {
							source += args[i] + " ";
						}
						source = source.substring(0, source.length()-1);
					}
				}
			}
		}
		SimpleScriptCompiler simpleScriptCompiler = new SimpleScriptCompiler();
		byte[] data;
		Class c;
		try {
			Class cl = Class.forName("Frosty", true, Thread.currentThread().getContextClassLoader());
		} catch (ClassNotFoundException e) {
			data = simpleScriptCompiler.compileFrostyMethods();
			name = "Frosty";
			//new ManaBar(name, data);
			if (Main.debug && Main.debugLevel > 2) {
				//new ManaBar(Main.name, data);
				System.out.println(
						"===============================\n" +
						"class "+Main.name+" dump {\n" +
							new String(data) +
						"\n}\n" +
						"==============================="
				);//*/
			}
			c = new SimpleScriptClassLoader().load(data);
			c = SimpleScript.loadClass("Frosty", data);
			if (output != "") {
				try {
					FileOutputStream fileOutputStream = new FileOutputStream("Frosty.class");
					fileOutputStream.write(data);
					fileOutputStream.close();
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}

		if (input != "") {
			source = input.substring(input.lastIndexOf(File.separatorChar)+1).split("\\.")[0]+"() {\n" +
						new SimpleScriptUtils().read(input) +
					"\n}";
		}
		data = simpleScriptCompiler.compile(source, null);
		final byte[] dataPacket = data;
		if (Main.debug && Main.debugLevel > 1) {
			File classDump = new File("temp"+File.separatorChar+Main.name+".class.dump");
			new File(classDump.getParent()).mkdirs();
			if (! classDump.exists()) {
				try {
					classDump.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(classDump);
				try {
					fileOutputStream.write(data);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			//*
			System.out.println(
					"===============================\n" +
						"class "+Main.name+" dump {\n" +
							new String(data) +
						"\n}\n" +
					"==============================="
			);//*/
		}
		if (!output.equals("")) {
			try {
				FileOutputStream fileOutputStream = new FileOutputStream(output);
				fileOutputStream.write(data);
				fileOutputStream.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (execute > 0) {
			c = new SimpleScriptClassLoader().load(data);
			c = SimpleScript.loadClass(name, data);
			try {
				c.getDeclaredMethod("main", String[].class).invoke(c, (Object) null);
				//c.getDeclaredConstructors()[0].newInstance();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}
	public static void error(String ... messages) {
		for (String message : messages) {
			System.err.println(message);
		}
	}
}
