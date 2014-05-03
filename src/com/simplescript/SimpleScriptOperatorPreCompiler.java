package com.simplescript;

import java.util.ArrayList;

/**
 * Created by Alex on 4/27/14.
 */
public class SimpleScriptOperatorPreCompiler {

	//

	public void correct(ArrayList<String> argsList) {
		for (int i = 1 ; i < argsList.size() - 1 ; i ++) {
			if (argsList.get(i).equals("*")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("/")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("%")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("+")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("-")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("<")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals(">")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("<=")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals(">=")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("==")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("!=")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("&&")) {
				fix(argsList, i --);
			} else if (argsList.get(i).equals("||")) {
				fix(argsList, i --);
			}
		}
	}

	private void fix(ArrayList<String> argsList, int i) {
		String newArg = '('+argsList.get(i)+' '+argsList.get(i+1)+' '+argsList.get(i-1)+')';
		argsList.add(i-1, newArg);
		argsList.remove(i);
		argsList.remove(i);
		argsList.remove(i);
	}
}
