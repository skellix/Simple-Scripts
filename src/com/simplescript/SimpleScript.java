package com.simplescript;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Alex on 4/6/14.
 */
public class SimpleScript {

	public static long runOnCompileIndex = 0;

	public SimpleScript(String source) {
		byte[] bytes = new SimpleScriptCompiler().compile(source, this);
		Class c = new SimpleScriptClassLoader().load(bytes);
		c = loadClass(Main.name, bytes);
		try {
			Class cl = Class.forName(Main.name, true, Thread.currentThread().getContextClassLoader());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		/*new ErrOutputGrabber(bytes) {
			@Override
			public void run() throws Exception {
				Class cl = Class.forName(Main.name, true, Thread.currentThread().getContextClassLoader());
			}
		};//*/
		//System.out.println("Script created: " + c.getCanonicalName());
		if (c.getCanonicalName().startsWith("__RunOnCompile")) {
			try {
				c.getDeclaredConstructors()[0].newInstance();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
	}
	public static Class loadClass(String className, byte[] b) {
		//override classDefine (as it is protected) and define the class.
		Class clazz = null;
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			Class cls = Class.forName("java.lang.ClassLoader");
			java.lang.reflect.Method method =
					cls.getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, int.class, int.class });

			// protected method invocaton
			method.setAccessible(true);
			try {
				Object[] args = new Object[] { className, b, new Integer(0), new Integer(b.length)};
				clazz = (Class) method.invoke(loader, args);
			} finally {
				method.setAccessible(false);
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (! Main.debug)
				System.exit(1);
		}
		return clazz;
	}
	public boolean extraCommands(ClassWriter classWriter, MethodVisitor methodVisitor, String className, LinkedHashMap<String, Integer> variables, LinkedHashMap<String, String> types, AtomicInteger index, String source) {
		return false;
	}
}
