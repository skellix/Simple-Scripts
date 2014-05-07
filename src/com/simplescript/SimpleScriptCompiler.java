package com.simplescript;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Alex on 4/6/14.
 */
public class SimpleScriptCompiler implements Opcodes {

	private int tabSize = 0;
	private SimpleScript caller = null;

	/**
	 * Used for creating the main class.
	 * @return the Frosty class dump
	 */
	public byte[] compileFrostyMethods() {
		try {
			return FrostyClassDump.dump();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Used for compiling Simple script source code.
	 * @param source the source code
	 * @param simpleScript the caller class
	 * @return java bytecode for the compiled script.
	 */
	public byte[] compile(String source, SimpleScript simpleScript) {
		caller = simpleScript;
		AtomicInteger index = new AtomicInteger(0);
		String name = "__RunOnCompile"+(SimpleScript.runOnCompileIndex ++);
		String args = "";
		//source = source.replaceAll("(\\+|\\-|\\/|\\*|\\%|\\=\\=|\\!\\=|\\<\\<|\\<\\=|\\<|\\>\\=|\\>)", " $1 ");
		if (source.matches("^\\s*[^\\s]+\\s*\\([^\\(\\)]*\\)\\s*\\{(?:\\n|.)*")) {
			for (; (""+source.charAt(index.get())).matches("\\s") ; index.getAndIncrement()); // skip all leading whitespace
			name = getUntil("\\s|\\(", source, index);
			for (; ! (""+source.charAt(index.get())).matches("\\(") ; index.getAndIncrement());
			index.getAndIncrement();
			args = getUntil("\\)", source, index);
			for (; ! (""+source.charAt(index.get())).matches("\\{") ; index.getAndIncrement());
			index.getAndIncrement();
		}
		ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		classWriter.visit(V1_7, ACC_PUBLIC, name, null, "java/lang/Object", null);
		MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "("+args+")V", null, null);
		methodVisitor.visitCode();
		methodVisitor.visitVarInsn(ALOAD, 0);
		methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		LinkedHashMap<String, String> imports = new DefaultFrostyImports().getImports();
		LinkedHashMap<String, Integer> variables = new LinkedHashMap<String, Integer>();
		LinkedHashMap<String, String> types = new LinkedHashMap<String, String>();
		Stack<String> stack = new Stack<String>();
		LinkedHashMap<String, Label> labels = new LinkedHashMap<String, Label>();
		Stack<Label> blocks = new Stack<Label>();
		if (Main.debug) {
			System.out.println(
					"class "+name+" {"
			);
			tabSize ++;
		}
		getbody(classWriter, methodVisitor, name, imports, variables, types, stack, labels, blocks, index, source);
		if (Main.debug) {
			System.out.println(
					"}"
			);
			tabSize --;
		}
		methodVisitor.visitInsn(RETURN);
		methodVisitor.visitMaxs(0, 0);
		methodVisitor.visitEnd();
		Main.name = name;
		return classWriter.toByteArray();
	}

	/**
	 * Compiles the source code body of a function or class.
	 * @param classWriter
	 * @param methodVisitor
	 * @param className name of the class
	 * @param variables current variables in the scope
	 * @param types current variable types in the scope
	 * @param index current source code index
	 * @param source the source code
	 */
	private void getbody(ClassWriter classWriter, MethodVisitor methodVisitor, String className, LinkedHashMap<String, String> imports, LinkedHashMap<String, Integer> variables, LinkedHashMap<String, String> types, Stack<String> stack, LinkedHashMap<String, Label> labels, Stack<Label> blocks, AtomicInteger index, String source) {
		while (index.get() < source.length()) {
			String line = getUntil("\n|\\}", source, index);
			index.getAndIncrement();
			if (! preArgsCheck(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, line, index, source)) {
				if (index.get() < source.length() && source.charAt(index.get()) == '}') {
					line = "} " + line;
					index.getAndIncrement();
				}
				String[] args = argsCheck(classWriter, methodVisitor, className, variables, types, stack, labels, blocks, line, index, source);
				if (Main.debug && args.length > 0 && Main.debugLevel > 0) {
					String list = Arrays.asList(args).toString();
					System.out.println(times(".   ", tabSize)+"-----Line:<"+list.substring(1, list.length()-1)+">");
				}
				for (int i = args.length-1 ; i >= 0 ; i --) {
					String arg = args[i];
					if (Main.debug && ! arg.equals("")) {
						if (! arg.equals("}")) {
							System.out.print(times(".   ", tabSize)+arg+" = ");
						}
					}
					try {
						if (arg.equals("")) {
							//
						} else if (arg.equals("{")) {
							blocks.push(new Label());
						} else if (arg.equals("}")) {
							return;
						} else if (arg.startsWith("(") && arg.endsWith(")")) {
							String newSrc = arg.substring(1, arg.length()-1);
							tabSize ++;
							if (Main.debug && ! arg.equals("")) {
								System.out.println("{");
							}
							getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), newSrc);
							System.out.print(times(".   ", tabSize - 1) + "} = ");
							tabSize --;
						} else if (arg.equals("endl")) {
							methodVisitor.visitLdcInsn("\n");
							stack.push("Ljava/lang/String;");
						} else if (arg.equals("true")) {
							methodVisitor.visitLdcInsn(true);
							stack.push("Z");
						} else if (arg.equals("false")) {
							methodVisitor.visitLdcInsn(false);
							stack.push("Z");
						} else if (arg.charAt(0) == '\"' && arg.charAt(arg.length()-1) == '\"') {
							final String var = arg.substring(1, arg.length() - 1);
							methodVisitor.visitLdcInsn(var);
							stack.push("Ljava/lang/String;");
						} else if (arg.charAt(0) == '\'' && arg.charAt(arg.length()-1) == '\'') { // system process
							final String cmdArgs = arg.substring(1, arg.length() - 1);
							methodVisitor.visitLdcInsn(cmdArgs);
							methodVisitor.visitMethodInsn(INVOKESTATIC, "Frosty", "buildProcess", "(Ljava/lang/String;)Ljava/lang/String;");
							stack.push("Ljava/lang/String;");
						} else if (arg.matches("^\\d+$")) {
							methodVisitor.visitLdcInsn(Integer.parseInt(arg));
							stack.push("I");
						} else if (arg.matches("^\\d+\\.\\d+$")) {
							methodVisitor.visitLdcInsn(Double.parseDouble(arg));
							stack.push("D");
						} else if (arg.contains("//")) {
							break;
						} else if (arg.startsWith("this")) {
							String[] reflectArgs = arg.split("\\.");
						} else if (arg.startsWith("$")) {
							String varName = arg.split("\\.")[0];
							int var = getVar(varName, variables, types, "", source, index);
							methodVisitor.visitVarInsn(
									getLoad(varName, variables, types, index),
									var
							);
							if (arg.contains(".")) {
								String varArgs = arg.substring(varName.length()+1);
								arg = getType(varName, variables, types);
								if (arg.length() > 1) {
									arg = arg.substring(1, arg.length()-1);
								}
								if (Main.debug) {
									System.out.println("{");
									tabSize ++;
								}
								if (varArgs.contains("(") && varArgs.contains(")")) {
									getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), varName);
									String[] varTypes = varArgs.substring(varArgs.indexOf('(')+1, varArgs.lastIndexOf(')')).split("\\,\\s*");
									ArrayList<String> argTypes = new ArrayList<String>();
									for (int j = varTypes.length-1 ; j >= 0 ; j --) {
										if (! varTypes[j].equals("")) {
											getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), varTypes[j]);
											argTypes.add(stack.peek());
										}
									}
									String methodName = varArgs.substring(0, varArgs.indexOf('('));
									try {
										Class c = Class.forName(arg.replace('/', '.'));
										String argsString = "";
										String returnType = "";
										String invokeType = "invokeVirtual";
										int numberOfParams = -1;
										if (varArgs.startsWith("<init>")) {
											returnType = "V";
											invokeType = "invokeSpecial";
											for (Constructor constructor : c.getConstructors()) {
												if (constructor.getParameterTypes().length == argTypes.size()) {
													String thisArgsString = "";
													int thisNumberOfParams = 0;
													if (argTypes.size() == 0) {
														//
													} else {
														Class<?>[] parameterTypes = constructor.getParameterTypes();
														for (int j = 0 ; j < parameterTypes.length && j < argTypes.size() ; j ++) {
															Class c1 = null;
															String argTypeName = argTypes.get(j);
															//doTypeCheck(argTypeName, c1);
															c1 = getClassForType(argTypeName);
															Class c2 = null;
															String typeCanonicalName = parameterTypes[j].getCanonicalName();
															typeCanonicalName = doTypeCheck(typeCanonicalName, imports);
															c2 = getClassForType(typeCanonicalName);
															if (c2.isAssignableFrom(c1)) {
																thisArgsString += typeCanonicalName;
																thisNumberOfParams ++;
															}
														}
													}
													if (thisNumberOfParams > numberOfParams) {
														numberOfParams = thisNumberOfParams;
														argsString = thisArgsString;
													}
												}
											}
										} else {
											Method[] methods = c.getMethods();
											ArrayList<Method> matches = new ArrayList<Method>();
											for (Method method : methods) {
												if (method.getName().equals(methodName)) {
													matches.add(method);
												}
											}
											System.out.print("");
											for (Method method : methods) {
												if (method.getName().equals(methodName)) {
													if (method.getParameterTypes().length == argTypes.size()) {
														String thisArgsString = "";
														int thisNumberOfParams = 0;
														if (argTypes.size() == 0) {
															returnType = method.getReturnType().getCanonicalName().replace('.','/');
															if (returnType.equals("void")) {
																returnType = "V";
															} else {
																returnType = 'L'+returnType+';';
															}
														} else {
															Class<?>[] parameterTypes = method.getParameterTypes();
															for (int j = 0 ; j < parameterTypes.length && j < argTypes.size() ; j ++) {
																Class c1 = null;
																String argTypeName = argTypes.get(j);
														/*if (argTypeName.startsWith("L")) {
															argTypeName = argTypeName.substring(1, argTypeName.length()-1);
															argTypeName = argTypeName.replace('/', '.');
														}//*/
																//doTypeCheck(argTypeName, c1);
																c1 = getClassForType(argTypeName);
																Class c2 = null;
																String typeCanonicalName = parameterTypes[j].getCanonicalName();
																typeCanonicalName = doTypeCheck(typeCanonicalName, imports);
																c2 = getClassForType(typeCanonicalName);
																if (c2.isAssignableFrom(c1)) {
																	thisArgsString += typeCanonicalName;
																	thisNumberOfParams ++;
																	returnType = method.getReturnType().getCanonicalName();//.replace('.','/');
																	/*if (returnType.equals("void")) {
																		returnType = "V";
																	} else {
																		returnType = 'L'+returnType+';';
																	}*/
																	returnType = doTypeCheck(returnType, imports);
																}
															}
														}
														if (thisNumberOfParams > numberOfParams) {
															numberOfParams = thisNumberOfParams;
															argsString = thisArgsString;
														}
													}
												}
											}
										}
										if (numberOfParams != -1) {
											getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0),
													invokeType+':'+arg+'.'+methodName+'('+argsString+')'+returnType
											);
										} else {
											Main.error("Class '"+arg+"' does not contain a method named '"+methodName+"'");
										}
									} catch (ClassNotFoundException e) {
										e.printStackTrace();
									}
								} else {
									getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0),
											arg + '.' + varArgs
									);
								}
								if (Main.debug) {
									tabSize --;
									System.out.print(times(".   ", tabSize)+"} = ");
								}
							} else {
								stack.push(getType(varName, variables, types));
							}
						} else if (arg.endsWith("$")) {
							String type = tryPop(stack, index);
							methodVisitor.visitVarInsn(
									getInst(type, "STORE", variables, types, index),
									getVar(arg, variables, types, type, source, index)
							);
						} else if (arg.endsWith(":")) {
							Label label = new Label();
							labels.put(arg.substring(0, arg.length() - 1), label);
							methodVisitor.visitLabel(label);
						} else if (arg.startsWith(":") && ! arg.startsWith("::")) {
							methodVisitor.visitJumpInsn(
									GOTO,
									labels.get(arg.substring(1))
							);
						} else if (arg.equals("new")) {
							methodVisitor.visitTypeInsn(NEW, args[i+1]);
							methodVisitor.visitInsn(DUP);
							methodVisitor.visitMethodInsn(INVOKESPECIAL, args[i + 1], "<init>", "()V");
							stack.push(args[i+1]);
						} else if (arg.equals("+")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitInsn(getInst(type, "ADD", variables, types, index));
							stack.push(type);
						} else if (arg.equals("-")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitInsn(getInst(type, "SUB", variables, types, index));
							stack.push(type);
						} else if (arg.equals("/")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitInsn(getInst(type, "DIV", variables, types, index));
							stack.push(type);
						} else if (arg.equals("*")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitInsn(getInst(type, "MUL", variables, types, index));
							stack.push(type);
						} else if (arg.equals("%")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitInsn(getInst(type, "REM", variables, types, index));
							stack.push(type);
						} else if (arg.equals("==")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitJumpInsn(IF_ICMPNE, blocks.peek());
						} else if (arg.equals("!=")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitJumpInsn(IF_ICMPEQ, blocks.peek());
						} else if (arg.equals("<")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitJumpInsn(IF_ICMPGE, blocks.peek());
						} else if (arg.equals("<=")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitJumpInsn(IF_ICMPGT, blocks.peek());
						} else if (arg.equals(">")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitJumpInsn(IF_ICMPLE, blocks.peek());
						} else if (arg.equals(">=")) {
							String type = tryPop(stack, index);
							stack.pop();
							methodVisitor.visitJumpInsn(IF_ICMPLT, blocks.peek());
						} else if (arg.equals("if")) {
							if (Main.debug) {
								System.out.println("\n"+line);
								tabSize ++;
							}
							//index.getAndIncrement();
							Label block = blocks.pop();
							getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, index, source);
							methodVisitor.visitLabel(block);
							break;
						} else if (arg.equals("get")) {
							String listType = stack.pop();
							stack.pop();
							if (listType.startsWith("[")) {
								methodVisitor.visitInsn(SWAP);
								methodVisitor.visitInsn(AALOAD);
								stack.push(listType.substring(1));
							} else {
								Main.error("Command get requires a list as input at line: "+getLineIndex(source, index));
							}
						} else if (arg.equals("#StringBuilderStart#")) {
							methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
							methodVisitor.visitInsn(DUP);
							methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V");
							stack.push("Ljava/lang/StringBuilder;");
						} else if (arg.equals("<<")) {
							String type = tryPop(stack, index);
							testPop(stack, "Ljava/lang/StringBuilder;", index);
							methodVisitor.visitMethodInsn(
									INVOKEVIRTUAL,
									"java/lang/StringBuilder",
									"append",
									"(" + type + ")Ljava/lang/StringBuilder;"
							);
							stack.push("Ljava/lang/StringBuilder;");
						} else if (arg.equals("#StringBuilderEnd#")) {
							String type = stack.pop();
							methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
							stack.push("Ljava/lang/String;");
						} else if (arg.equals("join")) {
							stack.pop();
							stack.pop();
							methodVisitor.visitMethodInsn(INVOKESTATIC, "Frosty", "join", "([Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
							stack.push("Ljava/lang/String;");
						} else if (arg.equals("toString")) {
							stack.pop();
							methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
							stack.push("Ljava/lang/String;");
						} else if (arg.equals("print")) {
							methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
							methodVisitor.visitInsn(SWAP);
							methodVisitor.visitMethodInsn(
									INVOKEVIRTUAL,
									"java/io/PrintStream", "print", "(" +
									tryPop(stack, index) +
									")V"
							);
						} else if (arg.equals("read")) {
							methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/lang/InputStream;");
							methodVisitor.visitMethodInsn(
									INVOKEVIRTUAL,
									"java/lang/InputStream", "read", "()I"
							);
							stack.push("I");
						} else if (arg.equals("SWAP")) {
							String temp1 = stack.pop();
							String temp2 = stack.pop();
							methodVisitor.visitInsn(SWAP);
							stack.push(temp1);
							stack.push(temp2);
						}  else if (arg.equals("DUP")) {
							String temp1 = stack.pop();
							methodVisitor.visitInsn(DUP);
							stack.push(temp1);
							stack.push(temp1);
						} else if (arg.startsWith("new:")) {
							String type = arg.split("\\:")[1];
							methodVisitor.visitTypeInsn(NEW, type);
							stack.push('L'+type+';');
						} else if (arg.startsWith("::")) {
							String type = arg.substring(2);
							if (type.contains(".")) {
								String method = type.substring(type.indexOf('.')+1);
								type = type.substring(0,type.indexOf('.'));
								if (imports.containsKey(type)) {
									type = imports.get(type);
								}
								String dup = "";
								if (method.startsWith("<init>")) {
									dup = "DUP";
								}
								if (Main.debug) {
									System.out.println("{");
									tabSize ++;
								}
								String data = type+'.'+method+' '+dup+" ::"+type;
								getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0),
										type+'.'+method+' '+dup+" ::"+type
								);
								if (Main.debug) {
									tabSize --;
									System.out.print(times(".   ", tabSize)+"} = ");
								}
							} else {
								try {
									if (type.equals("Z")) {
										methodVisitor.visitLdcInsn(boolean.class.newInstance());
										stack.push(type);
									} else if (type.equals("C")) {
										methodVisitor.visitLdcInsn(char.class.newInstance());
										stack.push(type);
									} else if (type.equals("B")) {
										methodVisitor.visitLdcInsn(byte.class.newInstance());
										stack.push(type);
									} else if (type.equals("S")) {
										methodVisitor.visitLdcInsn(short.class.newInstance());
										stack.push(type);
									} else if (type.equals("I")) {
										methodVisitor.visitLdcInsn(int.class.newInstance());
										stack.push(type);
									} else if (type.equals("F")) {
										methodVisitor.visitLdcInsn(float.class.newInstance());
										stack.push(type);
									} else if (type.equals("J")) {
										methodVisitor.visitLdcInsn(long.class.newInstance());
										stack.push(type);
									} else if (type.equals("D")) {
										methodVisitor.visitLdcInsn(double.class.newInstance());
										stack.push(type);
									} else if (type.startsWith("[")) {
										methodVisitor.visitInsn(ACONST_NULL);
										stack.push(type);
									} else {
										methodVisitor.visitTypeInsn(NEW, type);
										stack.push('L'+type+';');
									}
								} catch (IllegalAccessException e) {
									e.printStackTrace();
								} catch (InstantiationException e) {
									e.printStackTrace();
								}
							}
						} else if (arg.startsWith("invoke")) {
							Matcher matcher = Pattern.compile("(invokeSpecial|invokeStatic|invokeVirtual)\\:([^\\.]+)\\.([^\\(]+)(.*)").matcher(arg);
							if (matcher.find()) {
								if (! matcher.group(1).equals("invokeStatic")) {
									stack.pop();
								}
								methodVisitor.visitMethodInsn(
										matcher.group(1).equals("invokeSpecial")? INVOKESPECIAL:
												matcher.group(1).equals("invokeStatic")? INVOKESTATIC:
														INVOKEVIRTUAL,
										matcher.group(2),
										matcher.group(3),
										matcher.group(4)
								);
								String returnVal = matcher.group(4).split("\\)")[1];
								String param = matcher.group(4).substring(0, matcher.group(4).length()-returnVal.length());
								LinkedHashMap<String, Integer> paramArgs = new LinkedHashMap<String, Integer>();
								addParamsAsVariables(param, paramArgs, new LinkedHashMap<String, String>(), source, index);
								for (String key : paramArgs.keySet().toArray(new String[0])) {
									stack.pop();
								}
								stack.push(returnVal);
							} else {
								Main.error("invlid invoke at line: "+getLineIndex(source, index));
							}
						} else if (arg.contains("(") && arg.contains(")")) {
							String varName = arg.split("\\.")[0];
							if (arg.contains(".")) {
								String varArgs = arg.substring(varName.length()+1);
								arg = varName;
								if (Main.debug) {
									System.out.println("{");
									tabSize ++;
								}
								if (varArgs.contains("(") && varArgs.contains(")")) {
									String[] varTypes = varArgs.substring(varArgs.indexOf('(')+1, varArgs.lastIndexOf(')')).split("\\,\\s*");
									ArrayList<String> argTypes = new ArrayList<String>();
									for (int j = varTypes.length-1 ; j >= 0 ; j --) {
										if (! varTypes[j].equals("")) {
											getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), varTypes[j]);
											argTypes.add(stack.peek());
										}
									}
									String methodName = varArgs.substring(0, varArgs.indexOf('('));
									try {
										Class c = Class.forName(arg.replace('/', '.'));
										String argsString = "";
										String returnType = "V";
										String invokeType = "invokeVirtual";
										int numberOfParams = -1;
										if (varArgs.startsWith("<init>")) {
											invokeType = "invokeSpecial";
											returnType = "V";
											for (Constructor constructor : c.getConstructors()) {
												String thisArgsString = "";
												int thisNumberOfParams = 0;
												if (argTypes.size() == 0) {
													//
												} else {
													Class<?>[] parameterTypes = constructor.getParameterTypes();
													for (int j = 0 ; j < parameterTypes.length && j < argTypes.size() ; j ++) {
														String argTypeName = argTypes.get(j);
														if (argTypeName.startsWith("L")) {
															argTypeName = argTypeName.substring(1, argTypeName.length()-1);
															argTypeName = argTypeName.replace('/', '.');
														} else if (argTypeName.equals("I")) {
															argTypeName = "Integer";
														}
														Class c1 = Class.forName(argTypeName);
														Class c2 = null;
														String typeCanonicalName = parameterTypes[j].getCanonicalName();
														if (typeCanonicalName.equals("boolean")) {
															c2 = boolean.class;
														} else if (typeCanonicalName.equals("char")) {
															c2 = char.class;
														} else if (typeCanonicalName.equals("byte")) {
															c2 = byte.class;
														} else if (typeCanonicalName.equals("short")) {
															c2 = short.class;
														} else if (typeCanonicalName.equals("int")) {
															c2 = int.class;
														} else if (typeCanonicalName.equals("float")) {
															c2 = float.class;
														} else if (typeCanonicalName.equals("long")) {
															c2 = long.class;
														} else if (typeCanonicalName.equals("double")) {
															c2 = double.class;
														} else {
															c2 = Class.forName(typeCanonicalName);
														}
														//doTypeCheck(argTypeName, c1);
														//c1 = getClassForType(argTypeName);
														//typeCanonicalName = doTypeCheck(typeCanonicalName);
														//c2 = getClassForType(typeCanonicalName);
														if (c2.isAssignableFrom(c1)) {
															thisArgsString += 'L'+typeCanonicalName.replace('.', '/')+';';
															thisNumberOfParams ++;
														}
													}
												}
												if (thisNumberOfParams > numberOfParams) {
													numberOfParams = thisNumberOfParams;
													argsString = thisArgsString;
												}
											}
										} else {
											for (Method method : c.getMethods()) {
												if (method.getName().equals(methodName)) {
													try {
														new FileOutputStream(new File("")).write(new byte[0]);
													} catch (IOException e) {
														e.printStackTrace();
													}
													String thisArgsString = "";
													int thisNumberOfParams = 0;
													if (argTypes.size() == 0) {
														returnType = method.getReturnType().getCanonicalName();
														if (returnType.equals("void")) {
															returnType = "V";
														} else {
															returnType = 'L'+returnType.replace('.', '/')+';';
														}
													} else {
														Class<?>[] parameterTypes = method.getParameterTypes();
														for (int j = 0 ; j < parameterTypes.length && j < argTypes.size() ; j ++) {
															Class c1 = null;
															String argTypeName = argTypes.get(j);
															if (argTypeName.startsWith("L")) {
																argTypeName = argTypeName.substring(1, argTypeName.length()-1);
																argTypeName = argTypeName.replace('/', '.');
																c1 = Class.forName(argTypeName);
															} else if (argTypeName.equals("I")) {
																c1 = int.class;
															} else if (argTypeName.equals("Z")) {
																c1 = boolean.class;
															}
															Class c2 = null;
															String typeCanonicalName = parameterTypes[j].getCanonicalName();
															if (typeCanonicalName.equals("boolean")) {
																c2 = boolean.class;
															} else if (typeCanonicalName.equals("char")) {
																c2 = char.class;
															} else if (typeCanonicalName.equals("byte")) {
																c2 = byte.class;
															} else if (typeCanonicalName.equals("short")) {
																c2 = short.class;
															} else if (typeCanonicalName.equals("int")) {
																c2 = int.class;
															} else if (typeCanonicalName.equals("float")) {
																c2 = float.class;
															} else if (typeCanonicalName.equals("long")) {
																c2 = long.class;
															} else if (typeCanonicalName.equals("double")) {
																c2 = double.class;
															} else {
																c2 = Class.forName(typeCanonicalName);
															}
															//doTypeCheck(argTypeName, c1);
															//c1 = getClassForType(argTypeName);
															//typeCanonicalName = doTypeCheck(typeCanonicalName);
															//c2 = getClassForType(typeCanonicalName);
															if (c2.isAssignableFrom(c1)) {
																thisArgsString += 'L'+typeCanonicalName.replace('.', '/')+';';
																thisNumberOfParams ++;
																returnType = method.getReturnType().getCanonicalName();
																/*if (returnType.equals("void")) {
																	returnType = "V";
																} else {
																	returnType = 'L'+returnType.replace('.', '/')+';';
																}*/
																returnType = doTypeCheck(returnType, imports);
															}
														}
													}
													if (thisNumberOfParams > numberOfParams) {
														numberOfParams = thisNumberOfParams;
														argsString = thisArgsString;
													}
												}
											}
										}
										if (numberOfParams != -1) {
											getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0),
													invokeType+':'+arg+'.'+methodName+'('+argsString+')'+returnType
											);
										} else {
											Main.error("Class '"+arg+"' does not contain a method named '"+methodName+"'");
										}
									} catch (ClassNotFoundException e) {
										e.printStackTrace();
									}
								} else {
									getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0),
											arg + '.' + varArgs
									);
								}
								if (Main.debug) {
									tabSize --;
									System.out.print(times(".   ", tabSize)+"} = ");
								}
							} else {
								getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0),
										className+'.'+arg
								);
							}
						} else if (arg.startsWith("get")) {
							Matcher matcher = Pattern.compile("(getStatic|getField)\\:([^\\.]+)\\.([^\\>]+)\\>(.*)").matcher(arg);
							if (matcher.find()) {
								if (matcher.group(1).equals("getField")) {
									stack.pop();
								}
								methodVisitor.visitFieldInsn(
										matcher.group(1).equals("getStatic")? GETSTATIC:
												GETFIELD,
										matcher.group(2),
										matcher.group(3),
										matcher.group(4)
								);
								stack.push(matcher.group(4));
							} else {
								Main.error("invlid get at line: "+getLineIndex(source, index));
							}
						} else if (arg.contains(".")) {
							String name = arg.substring(arg.indexOf('.')+1);
							String type = arg.substring(0, arg.indexOf('.'));
							if (imports.containsKey(type)) {
								type = imports.get(type);
							}
							try {
								Class c = Class.forName(type.replace('/', '.'));
								Field field = c.getField(name);
								String returnType = field.getType().getCanonicalName().replace('.', '/');
								if (returnType.equals("int")) {
									returnType = "I";
								} else if (returnType.equals("float")) {
									returnType = "F";
								} else if (returnType.equals("boolean")) {
									returnType = "Z";
								} else if (returnType.equals("double")) {
									returnType = "D";
								}
								if (returnType.length() > 1) {
									returnType = 'L' + returnType + ';';
								}//*/
								//String returnType = field.getType().getCanonicalName();
								//returnType = doTypeCheck(returnType);
								methodVisitor.visitFieldInsn(
										Modifier.isStatic(field.getModifiers())? GETSTATIC:
												GETFIELD,
										type,
										name,
										returnType
								);
								if (! Modifier.isStatic(field.getModifiers())) {
									stack.pop();
								}
								stack.push(returnType);
							} catch (ClassNotFoundException e) {
								e.printStackTrace();
							}
						} else if (arg.equals("threadEnv")) {
							methodVisitor.visitMethodInsn(INVOKESTATIC, "Frosty", "getClassLoaderList", "()[Ljava/lang/String;");
							stack.push("[Ljava/lang/String;");
						} else if (arg.equals("getFuncs")) {
							stack.pop();
							methodVisitor.visitMethodInsn(INVOKESTATIC, "Frosty", "getDeclaredMethods", "(Ljava/lang/Object;)[Ljava/lang/String;");
							stack.push("[Ljava/lang/String;");
						} else if (arg.equals("getName")) {
							stack.pop();
							methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;");
							methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;");
							stack.push("Ljava/lang/String;");
						} else if (caller == null || ! caller.extraCommands(classWriter, methodVisitor, className, variables, types, index, source)) {
							Main.error("invalid command at line: "+getLineIndex(source, index));
							Field f;
							if ((f = Opcodes.class.getDeclaredField(arg.toUpperCase())) != null) {
								final Field field = f;
								try {
									methodVisitor.visitInsn((Integer) field.get(this));
									//commands.add("#field# "+field.getName());
								} catch (IllegalAccessException e) {
									e.printStackTrace();
								}
							}
						}
					} catch (NoSuchFieldException e) {
						e.printStackTrace();
					}
					if (! stack.empty() && stack.peek().equals("V")) {
						stack.pop();
					}
					if (Main.debug && ! arg.equals("")) {
						if (! arg.equals("}")) {
							System.out.println(stack.toString());
						}
					}
				}
			}
		}
	}

	private Class getClassForType(String typeCanonicalName) {
		Class c2 = null;
		try {
			if (typeCanonicalName.equals("Z")) {
				c2 = boolean.class;
			} else if (typeCanonicalName.equals("C")) {
				c2 = char.class;
			} else if (typeCanonicalName.equals("B")) {
				c2 = byte.class;
			} else if (typeCanonicalName.equals("S")) {
				c2 = short.class;
			} else if (typeCanonicalName.equals("I")) {
				c2 = int.class;
			} else if (typeCanonicalName.equals("F")) {
				c2 = float.class;
			} else if (typeCanonicalName.equals("J")) {
				c2 = long.class;
			} else if (typeCanonicalName.equals("D")) {
				c2 = double.class;
			} else if (typeCanonicalName.equals("[Z")) {
				c2 = boolean[].class;
			} else if (typeCanonicalName.equals("[C")) {
				c2 = char[].class;
			} else if (typeCanonicalName.equals("[B")) {
				c2 = byte[].class;
			} else if (typeCanonicalName.equals("[S")) {
				c2 = short[].class;
			} else if (typeCanonicalName.equals("[I")) {
				c2 = int[].class;
			} else if (typeCanonicalName.equals("[F")) {
				c2 = float[].class;
			} else if (typeCanonicalName.equals("[J")) {
				c2 = long[].class;
			} else if (typeCanonicalName.equals("[D")) {
				c2 = double[].class;
			} else if (typeCanonicalName.startsWith("[")) {
				c2 = Class.forName(typeCanonicalName.substring(2,typeCanonicalName.length()-1).replace('/', '.'));
			} else {
				c2 = Class.forName(typeCanonicalName.substring(1,typeCanonicalName.length()-1).replace('/', '.'));
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return c2;
	}

	private String doTypeCheck(String typeCanonicalName, LinkedHashMap<String, String> imports) {
		if (typeCanonicalName.equals("void")) {
			typeCanonicalName = "V";
		} else if (typeCanonicalName.equals("boolean")) {
			typeCanonicalName = "Z";
		} else if (typeCanonicalName.equals("char")) {
			typeCanonicalName = "C";
		} else if (typeCanonicalName.equals("byte")) {
			typeCanonicalName = "B";
		} else if (typeCanonicalName.equals("short")) {
			typeCanonicalName = "S";
		} else if (typeCanonicalName.equals("int")) {
			typeCanonicalName = "I";
		} else if (typeCanonicalName.equals("float")) {
			typeCanonicalName = "F";
		} else if (typeCanonicalName.equals("long")) {
			typeCanonicalName = "J";
		} else if (typeCanonicalName.equals("double")) {
			typeCanonicalName = "D";
		} else if (typeCanonicalName.equals("boolean[]")) {
			typeCanonicalName = "[Z";
		} else if (typeCanonicalName.equals("char[]")) {
			typeCanonicalName = "[C";
		} else if (typeCanonicalName.equals("byte[]")) {
			typeCanonicalName = "[B";
		} else if (typeCanonicalName.equals("short[]")) {
			typeCanonicalName = "[S";
		} else if (typeCanonicalName.equals("int[]")) {
			typeCanonicalName = "[I";
		} else if (typeCanonicalName.equals("float[]")) {
			typeCanonicalName = "[F";
		} else if (typeCanonicalName.equals("long[]")) {
			typeCanonicalName = "[J";
		} else if (typeCanonicalName.equals("double[]")) {
			typeCanonicalName = "[D";
		} else if (typeCanonicalName.endsWith("]")) {
			String modName = typeCanonicalName.substring(0,
					typeCanonicalName.indexOf(' ') != -1? typeCanonicalName.indexOf(' '):
							typeCanonicalName.indexOf('[')
			);
			if (imports.containsKey(modName)) {
				typeCanonicalName = "[L"+imports.get(modName)+';';
			} else {
				typeCanonicalName = "[L"+typeCanonicalName.replace('.', '/').substring(0, typeCanonicalName.indexOf('['))+';';
			}
		} else {
			if (imports.containsKey(typeCanonicalName)) {
				typeCanonicalName = 'L'+imports.get(typeCanonicalName)+';';
			} else {
				typeCanonicalName = 'L'+typeCanonicalName.replace('.', '/')+';';
			}
		}
		return typeCanonicalName;
	}

	/**
	 * this function will split the current line into a list of arguments
	 * @param classWriter
	 * @param methodVisitor
	 * @param className
	 * @param variables
	 * @param types
	 * @param stack
	 * @param labels
	 * @param blocks
	 * @param line
	 * @param index
	 * @param source
	 * @return
	 */
	private String[] argsCheck(ClassWriter classWriter, MethodVisitor methodVisitor, String className, LinkedHashMap<String, Integer> variables, LinkedHashMap<String, String> types, Stack<String> stack, LinkedHashMap<String, Label> labels, Stack<Label> blocks, String line, AtomicInteger index, String source) {
		ArrayList<String> argsList = new ArrayList<String>();
		int start = 0;
		int end = 0;
		while (start < line.length()) {
			for (;
			     start < line.length() && (""+line.charAt(start)).matches("\\s");
			     start ++
					);
			if (start < line.length()) {
				if (line.charAt(start) == '$') {
					for ( end = start + 1 ; end < line.length() ; end ++) {
						if (line.charAt(end) == ' ') {
							break;
						}
						if (line.charAt(end) == '(') {
							Stack<Character> pairs = new Stack<Character>();
							pairs.push('(');
							end ++;
							while (end < line.length() && pairs.size() > 0) {
								if (line.charAt(end) == '(') {
									pairs.push('(');
								} else if (line.charAt(end) == ')') {
									pairs.pop();
								}
								end ++;
							}
							break;
						}
					}
					end --;
				} else if (line.charAt(start) == '\"') {
					for (
							end = start + 1;
							end < line.length() && ! ((""+line.charAt(end)).matches("\"") && (end == line.length() || line.charAt(end-1) != '\\'));
							end ++
							);
				} else if (line.charAt(start) == '\'') {
					for (
							end = start + 1;
							end < line.length() && ! ((""+line.charAt(end)).matches("\'") && (end == line.length() || line.charAt(end-1) != '\\'));
							end ++
							);
				} else if (line.charAt(start) == '(') {
					Stack<Character> pairs = new Stack<Character>();
					pairs.push('(');
					end = start + 1;
					while (end < line.length() && pairs.size() > 0) {
						if (line.charAt(end) == '(') {
							pairs.push('(');
						} else if (line.charAt(end) == ')') {
							pairs.pop();
						}
						end ++;
					}
					end --;
				} else {
					for (
							end = start + 1;
							end < line.length() && ! (""+line.charAt(end)).matches("\\s");
							end ++
							);
					end --;
				}
				//if (end == line.length()-1) end --;
				argsList.add(line.substring(start, end+1).trim());
				start = end + 1;
			}
		}
		new SimpleScriptOperatorPreCompiler().correct(
				argsList
		);
		if (argsList.contains("<<")) {
			System.out.print("");
			start = -1;
			for (int i = 0 ; i < argsList.size()-1 ; i ++) {
				if (argsList.get(i+1).equals("<<")) {
					start = i;
					for (i ++ ; i < argsList.size() ; i += 2) {
						if (i + 2 >= argsList.size() || ! argsList.get(i+2).equals("<<")) {
							end = i+1;
							ArrayList<String> temp = new ArrayList<String>();
							for (int j = end ; j >= start ; j --) {
								String element = argsList.remove(j);
								if (element.equals("<<")) {
									temp.add("SWAP");
								} else {
									temp.add(0, "<<");
									temp.add(0, "SWAP");
									temp.add(element);
								}
							}
							temp.remove(0);
							temp.add(0, "#StringBuilderEnd#");
							temp.set(temp.size()-2, "#StringBuilderStart#");
							//temp.add("#StringBuilderStart#");
							i = start + temp.size();
							while (temp.size() > 0) {
								argsList.add(start,temp.remove(temp.size()-1));
							}
							break;
						}
					}
				}
			}
		}
		return argsList.toArray(new String[0]);
	}

	/**
	 * this function will check if the current line represents a function , loop, import, etc declaration.
	 * @param classWriter
	 * @param methodVisitor
	 * @param className
	 * @param variables
	 * @param types
	 * @param stack
	 * @param labels
	 * @param blocks
	 * @param line
	 * @param index
	 * @param source
	 * @return
	 */
	private boolean preArgsCheck(ClassWriter classWriter, MethodVisitor methodVisitor, String className, LinkedHashMap<String, String> imports, LinkedHashMap<String, Integer> variables, LinkedHashMap<String, String> types, Stack<String> stack, LinkedHashMap<String, Label> labels, Stack<Label> blocks, String line, AtomicInteger index, String source) {
		Matcher matcher = Pattern.compile(
				"^\\s*import\\s+([^\\s]+)"
		).matcher(line);
		if (matcher.find()) {
			String name = matcher.group(1);
			try {
				Class c = Class.forName(name, true, Thread.currentThread().getContextClassLoader());
			} catch (ClassNotFoundException e) {
				File frostyFile = new File(name+".frosty");
				if (frostyFile.exists()) {
					new SimpleScript(
							name + "() {\n" +
									new SimpleScriptUtils().read(
											frostyFile.getName()
									) + "\n}"
					);
				} else {
					try {
						Class c = Class.forName(name.replace('/', '.'), true, Thread.currentThread().getContextClassLoader());
					} catch (ClassNotFoundException e1) {
						Main.error("Import file '" + frostyFile.getAbsolutePath() + "' could not be found!");
					}
				}
			}
			String shortName;
			try {
				shortName = name.substring(name.lastIndexOf('/')+1);
			} catch (Exception e) {
				shortName = name;
			}
			imports.put(shortName, name);
			return true;
		} else {
			matcher = Pattern.compile(
					"^\\s*import\\s+([^\\s]+)"
			).matcher(line);
			if (matcher.find()) {
				return true;
			} else {
				matcher = Pattern.compile(
						"^\\s*for\\s*\\(([^\\;]+)\\;([^\\;]+)\\;([^\\;]+)\\)\\s*\\{"
				).matcher(line);
				if (matcher.find()) {
					String firstPart = matcher.group(1);
					String forCheck = matcher.group(2);
					String lastPart = matcher.group(3);
					Label forStartLabel = new Label();
					Label forEndLabel = new Label();
					blocks.push(forEndLabel);
					tabSize ++;
					getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), firstPart);
					methodVisitor.visitLabel(forStartLabel);
					getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), forCheck);
					getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, index, source);
					getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, new AtomicInteger(0), lastPart);
					methodVisitor.visitJumpInsn(GOTO, forStartLabel);
					methodVisitor.visitLabel(forEndLabel);
					tabSize --;
					return true;
				} else {
					matcher = Pattern.compile(
							"^\\s*func\\s+(public|private|protected|)\\s*(static|)\\s*(|[^\\s]+)\\s*([^\\s\\(]+)\\s*(\\([^\\)]*\\))\\s*\\{"
					).matcher(line);
					if (matcher.find()) {
						String permission = matcher.group(1);
						String visibility = matcher.group(2);
						String returnType = doTypeCheck(matcher.group(3), imports);
						String name = matcher.group(4);
						String params = doParamTypeCheck(matcher.group(5), imports);
						index.set(
								(index.get()-line.length())+matcher.regionStart()+(matcher.group(0).length())-1
						);
						makeFunc(classWriter, className, permission, visibility, returnType, name, params, imports, variables, index, source);
						return true;
					} else {
						return false;
					}
				}
			//if (index.get() >= source.length() || source.charAt(index.get()) == '}') {
				//break;
			//}
			}
		}
	}

	private String doParamTypeCheck(String params, LinkedHashMap<String, String> imports) {
		String out = "";
		String[] types = params.substring(1, params.length()-1).split("\\s+,\\s+");
		for (String type : types) {
			if (! type.equals("")) {
				out += doTypeCheck(type, imports);
			}
		}
		return '('+out+')';
	}

	/**
	 * Creates a new method in a class.
	 * @param classWriter
	 * @param className
	 * @param permission
	 * @param visibility
	 * @param returnType
	 * @param name
	 * @param params
	 * @param classVariables
	 * @param index
	 * @param source
	 */
	private void makeFunc(ClassWriter classWriter, String className, String permission, String visibility, String returnType, String name, String params, LinkedHashMap<String, String> imports, LinkedHashMap<String, Integer> classVariables, AtomicInteger index, String source) {
		MethodVisitor methodVisitor = classWriter.visitMethod(
				(
						permission.equals("public")? ACC_PUBLIC:
								permission.equals("private")? ACC_PRIVATE:
										permission.equals("protected")? ACC_PROTECTED:
												ACC_PUBLIC
				) + (
						visibility.equals("static")? ACC_STATIC:
								0
				),
				name,
				params + (
						returnType.equals("")? "V":
								returnType
				),
				null,// generics
				null // exceptions
		);
		methodVisitor.visitCode();
		LinkedHashMap<String, Integer> variables = new LinkedHashMap<String, Integer>();
		LinkedHashMap<String, String> types = new LinkedHashMap<String, String>();
		addParamsAsVariables(params, variables, types, source, index);
		Stack<String> stack = new Stack<String>();
		LinkedHashMap<String, Label> labels = new LinkedHashMap<String, Label>();
		Stack<Label> blocks = new Stack<Label>();
		if (Main.debug) {
			System.out.println(
					times(".   ", tabSize)+"func "+name+" {"
			);
			tabSize ++;
		}
		getbody(classWriter, methodVisitor, className, imports, variables, types, stack, labels, blocks, index, source);
		if (Main.debug) {
			tabSize --;
			System.out.println(
					times(".   ", tabSize)+"}"
			);
		}
		methodVisitor.visitInsn(RETURN);
		methodVisitor.visitMaxs(0, 0);
		methodVisitor.visitEnd();
	}

	private void addParamsAsVariables(String params, LinkedHashMap<String, Integer> variables, LinkedHashMap<String, String> types, String source, AtomicInteger index) {
		params = params.substring(1, params.length()-1);
		for (int i = 0 ; i < params.length() ; i ++) {
			String before = "";
			if (params.charAt(i) == '[') {
				int start = i;
				int end = i+1;
				for (;end < params.length() && params.charAt(end) == ';'; end ++);
				if (end < params.length()) {
					before = ""+params.substring(start, end);
					i = end;
				} else {
					Main.error("No end found for L type parameter in function signature at line: "+getLineIndex(source, index));
				}
			}
			switch (params.charAt(i)) {
				case 'Z' :
				case 'C' :
				case 'B' :
				case 'S' :
				case 'I' :
				case 'F' :
				case 'J' :
				case 'D' : {
					int n = variables.size();
					variables.put(""+n, n);
					types.put(""+n, ""+params.charAt(i));
					break;
				}
				case '[' : {

					break;
				}
				case 'L' : {
					int n = variables.size();
					variables.put(""+n, n);
					int start = i;
					int end = i+1;
					for (;end < params.length() && params.charAt(end) != ';'; end ++);
					if (end < params.length()) {
						types.put(""+n, ""+params.substring(start, end+1));
						i = end;
					} else {
						Main.error("No end found for L type parameter in function signature at line: "+getLineIndex(source, index));
					}
					break;
				}
				default : {
					Main.error("Invalid parameter in function signature at line: "+getLineIndex(source, index));
				}
			}
			if (before.length() > 0) {
				Object[] keys = variables.keySet().toArray();
				String key = (String) keys[keys.length-1];
				types.put(key, before+types.get(key));
			}
		}
	}

	private int getLineIndex(String source, AtomicInteger index) {
		int count = 0;
		for (int i = 0 ; i < source.length() && i < index.get() ; i ++) {
			if (source.charAt(i) == '\n') {
				count ++;
			}
		}
		return count;
	}

	private String times(String s, int n) {
		String out = "";
		for (int i = 0 ; i < n ; i ++) {
			out += s;
		}
		return out;
	}

	private void testPop(Stack<String> stack, String required, AtomicInteger index) {
		String type = stack.pop();
		if (! type.equals(required)) {
			Main.error("Invalid stack arg '"+type+"' found '"+required+"' expected at line: "+(index.get()+1));
		}
		return;
	}

	private String tryPop(Stack<String> stack, AtomicInteger index) {
		if (stack.size() > 0) {
			return stack.pop();
		}
		Main.error("More args required at line: "+(index.get()+1));
		return null;
	}

	private String getPopType(Stack<String> commands, HashMap<String, Integer> variables, HashMap<String, String> types, AtomicInteger index) {
		try {
			return getType(commands.pop(), variables, types, index);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private int getInst(String type, String instName, HashMap<String, Integer> variables, HashMap<String, String> types, AtomicInteger index) {
		if (type.length() != 1) {
			type = "A";
		}
		try {
			return (Integer) Opcodes.class.getDeclaredField(type+instName).get(this);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return RETURN;
	}

	/**
	 * Gets the load type for a variable
	 * @param var
	 * @param variables
	 * @param types
	 * @param index
	 * @return the load type
	 */
	private int getLoad(String var, HashMap<String, Integer> variables, HashMap<String, String> types, AtomicInteger index) {
		if (var.startsWith("$")) {
			var = var.substring(1);
		}
		if (types.containsKey(var)) {
			String type = types.get(var);
			if (type.length() > 1) {
				return ALOAD;
			}
			try {
				return (Integer) Opcodes.class.getDeclaredField(type+"LOAD").get(this);
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		} else {
			Main.error("Variable '"+var+"' used before assignment at line: "+(index.get()+1));
		}
		return ALOAD;
	}

	private int getStore(String var, HashMap<String, Integer> variables, HashMap<String, String> types, String lastCommand, AtomicInteger index) {
		if (var.endsWith("$")) {
			var = var.substring(0, var.length()-1);
		} else {
			Main.error("Variable name must contain '$' in assagnment or use at line: "+(index.get()+1));
		}
		if (! variables.containsKey(var)) {
			int n = variables.size();
			variables.put(var, n);
		}
		String type = "";
		if (! types.containsKey(var)) {
			types.put(var, getType(lastCommand, variables, types, index));
		}
		type = types.get(var);
		if (type.length() > 1) {
			return ASTORE;
		}
		try {
			return (Integer) Opcodes.class.getDeclaredField(type+"STORE").get(this);
		} catch (NoSuchFieldException e) {
			System.out.print("type: \""+type+"\"");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return ASTORE;
	}

	private String getType(String command, HashMap<String, Integer> variables, HashMap<String, String> types, AtomicInteger index) {
		try {
			return tryGetType(command, variables, types, index);
		} catch (Exception e) {
			Main.error("Invalid type used at line: " + (index.get() + 1));
		}
		return null;
	}

	private String tryGetType(String command, HashMap<String, Integer> variables, HashMap<String, String> types, AtomicInteger index) throws Exception {
		if (command.startsWith("#get# ")) {
			String var = command.substring("#get# ".length());
			if (var.startsWith("$")) {
				var = var.substring(1);
			}
			if (types.containsKey(var)) {
				return types.get(var);
			} else {
				Main.error("Variable '"+var+"' used before assignment at line: "+(index.get()+1));
			}
		}
		if (command.startsWith("#String#")) {
			return "Ljava/lang/String;";
		} else if (command.startsWith("#boolean#")) {
			return "Z";
		} else if (command.startsWith("#char#")) {
			return "C";
		} else if (command.startsWith("#byte#")) {
			return "B";
		} else if (command.startsWith("#short#")) {
			return "S";
		} else if (command.startsWith("#int#")) {
			return "I";
		} else if (command.startsWith("#float#")) {
			return "F";
		} else if (command.startsWith("#long#")) {
			return "J";
		} else if (command.startsWith("#double#")) {
			return "D";
		} else if (command.startsWith("[")) {
			return "["+getType(command.substring(1), variables, types, index);
		} else {
			throw new Exception("Invalid type");
		}
	}

	/**
	 * Responsible for checking or creating variables.
	 * @param var variables name
	 * @param variables current variable map
	 * @param types variable type map
	 * @param type last type stored in the variable
	 * @param index compiler source index
	 * @return variable index
	 */
	private int getVar(String var, HashMap<String, Integer> variables, HashMap<String, String> types, String type, String source, AtomicInteger index) {
		if (var.endsWith("$")) {
			var = var.substring(0, var.length()-1);
		} else if (var.startsWith("$")) {
			var = var.substring(1);
			if (! types.containsKey(var)) {
				Main.error("Variable '"+var+"' used before assignment at line: "+getLineIndex(source, index));
			}
		} else {
			Main.error("Variable name must contain '$' in assagnment or use at line: "+getLineIndex(source, index));
		}
		if (variables.containsKey(var)) {
			return variables.get(var);
		} else {
			int n = variables.size();
			variables.put(var, n);
			types.put(var, type);
			return n;
		}
	}

	private int storeType(String type) {
		return 0;
	}

	private String getType(String var, HashMap<String, Integer> variables, HashMap<String, String> types) {
		if (var.startsWith("$")) {
			var = var.substring(1);
		}
		if (types.containsKey(var)) {
			return types.get(var);
		} else {
			//
			return "";
		}
	}

	private String getUntil(String regex, String source, AtomicInteger index) {
		int start = index.get();
		for (; index.get() < source.length() && ! (""+source.charAt(index.get())).matches(regex) ; index.getAndIncrement());
		return source.substring(start, index.get());
	}
}
