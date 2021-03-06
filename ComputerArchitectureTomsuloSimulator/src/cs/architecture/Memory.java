package cs.architecture;

import java.util.*;

public class Memory {
	private static List<Instruction> instrs = new ArrayList<Instruction>(); // the array's index is for pc
	private static Hashtable data = new Hashtable(); 
	
	public void loadInstruction(Instruction newInstr){
		instrs.add(newInstr);
	}
	
	public List<Instruction> getInstrs(){
		return instrs;
	}
	
	public void loadData(String line){
		// when parse the string, hard coded... 
		String[] records = line.split("\\t+");
		String[] tmp = records[records.length-1].split("\\)\\s+\\=\\s+");
		double number = Double.parseDouble(tmp[tmp.length-1]);
		String[] tmp2 = tmp[0].split("\\(");
		int index = Integer.parseInt(tmp2[tmp2.length-1]);
		data.put(index, number);
	}
	
	public Hashtable getData(){
		return data;
	}
}
