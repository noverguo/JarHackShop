package com.noverguo.jarhackshop;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.noverguo.jarhackshop.visitor.SimpleMethodVisitor;
import com.noverguo.tools.asm.ASMUtils;
import com.noverguo.tools.asm.AsmVerify;
import com.noverguo.tools.asm.JarLoadUtils;
import com.noverguo.tools.common.SizeUtils;

/**
 * 2016/1/22
 * @author noverguo
 */
public class SimpilyMethodTool implements Opcodes {
	public static void run(String jarPath, String outPath) throws Exception {
		HashSet<String> systemSimpilyMethods = new HashSet<String>();
		HashMap<String, MethodNode> localSimpilyMethods = new HashMap<String, MethodNode>();
		appendClass(systemSimpilyMethods, String.class);
		appendClass(systemSimpilyMethods, Integer.class);
		appendClass(systemSimpilyMethods, Boolean.class);
		appendClass(systemSimpilyMethods, Byte.class);
		appendClass(systemSimpilyMethods, Character.class);
		appendClass(systemSimpilyMethods, Short.class);
		appendClass(systemSimpilyMethods, Float.class);
		appendClass(systemSimpilyMethods, Double.class);
		appendClass(systemSimpilyMethods, Long.class);
		appendClass(systemSimpilyMethods, Math.class);
		appendClass(systemSimpilyMethods, Base64.class);
		
		List<ClassNode> cnList = JarLoadUtils.loadJar(jarPath);
		
		boolean change;
		do {
			localSimpilyMethods.clear();
			change = false;
			List<ClassNode> outCnList = initLocalSimpilyMethod(cnList, systemSimpilyMethods, localSimpilyMethods);
			
			File jarFile = new File(jarPath);
			File simpilyJarFile = new File(jarFile.getParent(), "out_" + jarFile.getName());
			String simpilyJarPath = simpilyJarFile.getAbsolutePath();
			JarLoadUtils.saveToJar(simpilyJarPath, outCnList);
			URLClassLoader classLoader = new URLClassLoader(new URL[]{new URL("file:" + simpilyJarPath)}, Thread.currentThread().getContextClassLoader());
			
			change = searchConstMethod(cnList, localSimpilyMethods, classLoader);
			if(change) {
				JarLoadUtils.saveToJar(jarPath, outPath, cnList);
			}
			simpilyJarFile.delete();
		} while(change);
	}
	private static List<ClassNode> initLocalSimpilyMethod(List<ClassNode> cnList, Set<String> systemSimpilyMethods, Map<String, MethodNode> localSimpilyMethods) {
		Map<ClassNode, List<MethodNode>> simpilyMethods = new HashMap<ClassNode, List<MethodNode>>();
		List<ClassNode> outCnList = new ArrayList<ClassNode>();
		boolean found;
		do {
			found = false;
			for(ClassNode cn : cnList) {
				if(SizeUtils.isEmpty(cn.methods)) {
					continue;
				}
				methodLoop:
				for(MethodNode methodNode : cn.methods) {
					if(localSimpilyMethods.containsKey(ASMUtils.toString(cn.name, methodNode.name, methodNode.desc))) {
						continue methodLoop;
					}
					if(ASMUtils.isAbstract(methodNode) || ASMUtils.isNative(methodNode)) {
						continue methodLoop;
					}
					Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
					if(!SizeUtils.isEmpty(argumentTypes)) {
						for(Type type : argumentTypes) {
							if(!ASMUtils.isTypeConst(type)) {
								continue methodLoop;
							}
						}
					}
					Type returnType = Type.getReturnType(methodNode.desc);
					if(!ASMUtils.isTypeConst(returnType)) {
						continue methodLoop;
					}
					SimpleMethodVisitor smv = new SimpleMethodVisitor(systemSimpilyMethods, localSimpilyMethods.keySet());
					methodNode.accept(smv);
					if(!smv.isSimpilyMethod) {
						continue methodLoop;
					}
					localSimpilyMethods.put(ASMUtils.toString(cn.name, methodNode.name, methodNode.desc), methodNode);
					if(!simpilyMethods.containsKey(cn)) {
						simpilyMethods.put(cn, new ArrayList<MethodNode>());
					}
					simpilyMethods.get(cn).add(methodNode);
					found = true;
				}
			}
		} while(found);
		for(ClassNode cn : simpilyMethods.keySet()) {
			List<MethodNode> methods = simpilyMethods.get(cn);
			ClassNode outClassNode = new ClassNode();
			outClassNode = new ClassNode();
			outClassNode.visit(cn.version, cn.access & (~ACC_ABSTRACT) | ACC_PUBLIC, cn.name, cn.signature, "java/lang/Object", new String[0]);
			outCnList.add(outClassNode);
			
			// 空始初化方法，方便调用newInstance()
			MethodVisitor initMethod = outClassNode.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			initMethod.visitCode();
			initMethod.visitVarInsn(ALOAD, 0);
			initMethod.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
			initMethod.visitInsn(RETURN);
			initMethod.visitMaxs(1, 1);
			initMethod.visitEnd();
			
			for(MethodNode mn : methods) {
				mn.accept(outClassNode);
			}
		}
		return outCnList;
	}

	private static boolean searchConstMethod(List<ClassNode> cnList, HashMap<String, MethodNode> localSimpilyMethods, URLClassLoader classLoader) throws Exception {
		boolean res = false;
		boolean change;
		do {
			change = false;
			for(ClassNode cn : cnList) {
				if(SizeUtils.isEmpty(cn.methods)) {
					continue;
				}
	//			cn.accept(new TraceClassVisitor(new PrintWriter(System.out)));
				methodLoop:
				for(MethodNode methodNode : cn.methods) {
					boolean changeInsn = false;
					InsnList insnList = methodNode.instructions;
					for(int i=0;i<insnList.size();++i) {
						AbstractInsnNode insnNode = insnList.get(i);
						if(insnNode.getType() != AbstractInsnNode.METHOD_INSN) {
							continue;
						}
						// 找到简单方法的调用位置
						MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
						if(!localSimpilyMethods.containsKey(ASMUtils.toString(methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc))) {
							continue;
						}
						MethodNode invokeMethodNode = localSimpilyMethods.get(ASMUtils.toString(methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc));
						Type[] argumentTypes = Type.getArgumentTypes(methodInsnNode.desc);
						Type returnType = Type.getReturnType(methodInsnNode.desc);
						Object obj = null;
						int argSize = SizeUtils.getSize(argumentTypes);
						Class<?> clazz = classLoader.loadClass(ASMUtils.toClassName(methodInsnNode.owner));
						Object[] argValues = new Object[argSize];
						Method method;
						// 无参数有返回值的方法：直接调用后把返回值缓存
						if(argSize == 0) {
							method = clazz.getDeclaredMethod(methodInsnNode.name, new Class[0]);
						} else {
							// 有参数有返回值的方法：看参数是否都是常量，如果是的话，则直接调用后把返回值缓存
							if(i - argSize < 0) {
								continue;
							}
							Class<?>[] argClasses = new Class[argSize];
							for(int j=0;j<argSize;++j) {
								argClasses[j] = ASMUtils.getTypeClass(argumentTypes[j]);
							}
							method = clazz.getDeclaredMethod(methodInsnNode.name, argClasses);
							
							for(int j=0;j<argSize;++j) {
								AbstractInsnNode argInsnNode = insnList.get(j + i-argSize);
								int opcode = argInsnNode.getOpcode();
								if(opcode > LDC) {
									continue methodLoop;
								}
								argValues[j] = ASMUtils.getConstValue(argInsnNode);
							}
							// 把常量参数删除
							for(int j=0;j<argSize;++j) {
								insnList.remove(insnList.get(i-argSize));
							}
							i -= argSize;
						}
						// 无返回值的方法：	直接删除
						if(returnType.getSort() == Type.VOID) {
							if(ASMUtils.isStatic(invokeMethodNode)) {
								--i;
								insnList.remove(insnNode);
							} else {
								// 非静态方法时，栈中会有this对象，因此需要pop出来
								insnList.set(insnNode, new InsnNode(POP));
							}
							changeInsn = true;
							continue;
						}
						method.setAccessible(true);
						if(ASMUtils.isStatic(invokeMethodNode)) {
							obj = method.invoke(null, argValues);
						} else {
							// 非静态方法时，栈中会有this对象，因此需要pop出来
							insnList.insertBefore(insnNode, new InsnNode(POP));
							obj = method.invoke(clazz.newInstance(), argValues);
						}
						// 把方法调用处替换成常量
						insnList.set(insnNode, ASMUtils.getConstInsnNode(obj));
	//					System.out.println(ASMUtils.toString(cn, methodNode));
	//					System.out.println("\t" + ASMUtils.toString(methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc) + ": " + res);
						changeInsn = true;
					}
					if(changeInsn) {
						change = true;
						res = true;
						AsmVerify.check(cn);
					}
				}
			}
		} while(change);
		return res;
	}
	
	private static void appendClass(HashSet<String> systemSimpilyMethods, Class<?> clazz) {
		appendMethods(systemSimpilyMethods, clazz.getName(), clazz.getDeclaredMethods());
		appendConstructors(systemSimpilyMethods, clazz.getName(), clazz.getDeclaredConstructors());
	}
	
	private static void appendMethods(HashSet<String> systemSimpilyMethods, String className, Method[] methods) {
		if(!SizeUtils.isEmpty(methods)) {
			for(Method method : methods) {
				if(Modifier.isPublic(method.getModifiers())) {
					String desc = "(";
					Parameter[] parameters = method.getParameters();
					if(!SizeUtils.isEmpty(parameters)) {
						for(Parameter parameter : parameters) {
							desc += Type.getDescriptor(parameter.getType());
						}
					}
					desc += ")";
					desc += Type.getDescriptor(method.getReturnType());
					systemSimpilyMethods.add(ASMUtils.toString(ASMUtils.toInternalName(className), method.getName(), desc));
//					System.out.println(ASMUtils.toString(ASMUtils.toInternalName(className), method.getName(), desc));
				}
			}
		}
	}
	
	private static void appendConstructors(HashSet<String> systemSimpilyMethods, String className, Constructor<?>[] constructors) {
		if(!SizeUtils.isEmpty(constructors)) {
			for(Constructor<?> constructor : constructors) {
				if(Modifier.isPublic(constructor.getModifiers())) {
					String desc = "(";
					Parameter[] parameters = constructor.getParameters();
					if(!SizeUtils.isEmpty(parameters)) {
						for(Parameter parameter : parameters) {
							desc += Type.getDescriptor(parameter.getType());
						}
					}
					desc += ")V";
					systemSimpilyMethods.add(ASMUtils.toString(ASMUtils.toInternalName(className), "<init>", desc));
//					System.out.println(ASMUtils.toString(ASMUtils.toInternalName(className), "<init>", desc));
				}
			}
		}
	}
}
