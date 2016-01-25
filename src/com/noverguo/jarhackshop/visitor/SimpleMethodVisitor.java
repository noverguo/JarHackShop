package com.noverguo.jarhackshop.visitor;

import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.noverguo.tools.asm.ASMUtils;

/**
 * 2016/1/22
 * @author noverguo
 */
public class SimpleMethodVisitor extends MethodVisitor {
	private Set<String> systemSimpilyMethods;
	private Set<String> localSimpilyMethods;
	public boolean isSimpilyMethod = true;
	public boolean isHasSystemSimpilyMethod = false;
	public SimpleMethodVisitor(Set<String> systemSimpilyMethods, Set<String> localSimpilyMethods) {
		super(Opcodes.ASM5);
		this.systemSimpilyMethods = systemSimpilyMethods;
		this.localSimpilyMethods = localSimpilyMethods;
	}
	
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		isSimpilyMethod = false;
	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
//		System.out.println(ASMUtils.toString(owner, name, desc));
		String key = ASMUtils.toString(owner, name, desc);
		if(!systemSimpilyMethods.contains(key) && !localSimpilyMethods.contains(key)) {
			isSimpilyMethod = false;
		} else {
			isHasSystemSimpilyMethod = true;
		}
	}

}
