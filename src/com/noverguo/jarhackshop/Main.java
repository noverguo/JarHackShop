package com.noverguo.jarhackshop;

import org.objectweb.asm.Opcodes;

/**
 * 2016/1/22
 * @author noverguo
 */
public class Main implements Opcodes {
	public static void main(String[] args) throws Exception {
		if(args == null || args.length < 2) {
			System.out.println("useage: <in jar path> <out jar path>");
			return;
		}
		SimpilyMethodTool.run(args[0], args[1]);
	}
}
