package cs.architecture;

import java.io.*;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Computer Architecture Simulator Project Group
 *
 */

/*
 * Simulator, the simulating procedure runner.
 * 
 */
public class Simulator {
	BU buUnit;// BU unit instance;
	Bus bus;//Bus unit instance;
	FPU fpuUnit;// FPU unit instance;
	INT0 int0Unit;//INT0 unit instance
	INT1 int1Unit;//INT1 unit instance
	IssueQueue issueQueue;//Issuing queue instance
	LoadStore loadStoreUnit;// Load and store unit instance
	MULT multUnit;// MULT unit instance
	ROB renamingBuffer;//ROB instance
	
	
	
	boolean finishedFlag = true;//flag for whether this simulation comes to its end.
	int NF ; // The maximum number of instructions can be fetched in one cycle
	int NQ ; // The length of the instruction queue
	int NI ; // The maximum number of instructions can be decoded in one cycle
	int ND ; // The length of the Decoded instruction queue
	int NW = 4;//The maximum number of instructions can be issued every clock cycle to reservation stations. 
	
	
//	int pc = 0;
	
	public Simulator(String instructionFile, Memory main){
		//Initiate all the units
		buUnit = BU.getInstance();
		bus = Bus.getInstance();
		fpuUnit = FPU.getInstance();
		int0Unit = INT0.getInstance();
		int1Unit = INT1.getInstance();
		issueQueue = IssueQueue.getInstance();
		loadStoreUnit = LoadStore.getInstance();
		multUnit = MULT.getInstance();
		renamingBuffer = ROB.getInstance();
		
		
//		fetch all the instruction from instruction file.
		String line = null;
		try {
			FileReader filereader = new FileReader (instructionFile);
			BufferedReader bufferedreader = new BufferedReader (filereader);
			boolean flag = false; // indicate when the data is start loading
			while ((line = bufferedreader.readLine()) != null){
				if(flag){
					main.loadData(line);
				}else{
					if(line.contains("DATA")){
						flag = true;
					}else{
						Instructions instr = new Instructions();
						instr = instr.loadInstrs(line);
						main.loadInstruction(instr);
					}
				}
			}
		
//			System.out.println(main.getData().size());
			bufferedreader.close();
		}
		catch (FileNotFoundException ex){
			System.out.println("Unable to open file '" + instructionFile + "'");
		}
		catch (IOException ex){
			System.out.println("Error reading file '" + instructionFile + "'");
		}
		
	}
	/*
	 * Start this simulation with a loop representing clock cycles.
	 */
	public void startSimulation(Memory main, int NF, int NQ, int NI, int ND){
		this.NF = NF;
		this.NQ = NQ;
		this.NI = NI;
		this.ND = ND;
		
		finishedFlag = false;
		int clock_cycle = 0;
		int pc = 0; //initialize the program counter 
		BranchTargetBuffer BTBuffer = new BranchTargetBuffer();
		LinkedList<Instruction> FQueue = new LinkedList<Instruction>(); // Fetched Instructions Queue
		LinkedList<Instruction> DQueue = new LinkedList<Instruction>(); // Decoded Instructions Queue (actually, the decode is not needed, only check for branch)
		
		
		while(!finishedFlag){//Clock cycles loop
			
			/**
			 * If the instruction queue is not full, and there are instructions not finished,
			 * Fetch instructions. 
			 * In one clock cycle, the maximum number of fetching is NF. 
			 */
			int fetched = 0;
			while((FQueue.size() < NQ) && (fetched < NF) &&(pc < main.getInstrs().size())){
				main.getInstrs().get(pc).UpdatePC(pc); // Instruction needs to have a feature called pc, so that can check whether the BTBuffer prediction is wrong.
				FQueue.add(main.getInstrs().get(pc));
				if(BTBuffer.Getbuffer()[pc][0] != -1){
					pc = BTBuffer.Getbuffer()[pc%32][0]; // if there is an entry in BTBuffer, use the predicted pc, otherwise, pc ++
				}else{
					pc++;
				}
				fetched++;
			}

			
			/**
			 * Decode the instruction
			 * If the instruction is not a branch, but find in BTBuffer, need to deleted the following instructions in the iqueue, and refetch. 
			 */
			int decoded = 0;
			while((DQueue.size() <= NI) && (decoded < ND)&&(!FQueue.isEmpty()) ){
				Instruction next = FQueue.poll();
				DQueue.add(next);
				if ((next.opco != "BEQZ") &&(next.opco != "BNEZ")&&(next.opco != "BEQ")&&(BTBuffer.Getbuffer()[next.pc%32][0] != -1)){
					// If the instruction is not a branch, but has entry in BTBuffer
					FQueue.clear();
					pc = next.pc++;
					if(BTBuffer.Getbuffer()[next.pc%32][1] == 0) {
						BTBuffer.Getbuffer()[next.pc%32][1] = 1; // allow the first time is wrong
					}else{
						// reset the entry
						BTBuffer.Getbuffer()[next.pc%32][0] = -1;
						BTBuffer.Getbuffer()[next.pc%32][1] = 0;
					}
				}
			}
			
			
			issue(DQueue);
			readOperands();
			execute();
			writeResult();
			
			clock_cycle ++;
			if(pc >= main.getInstrs().size()){
				finishedFlag = true;
			}
		}
		//Destroying all the resources. TODO
	}
	/*
	 * If a functional unit for the instruction is free and no other active
       instruction has the same destination register, the scoreboard issues the
       instruction to the functional unit and updates its internal data structure. This
       step replaces a portion of the ID step in the MIPS pipeline. By ensuring that
       no other active functional unit wants to write its result into the destination
       register, we guarantee that WAW hazards cannot be present. If a structural or
       WAW hazard exists, then the instruction issue stalls, and no further instructions
       will issue until these hazards are cleared. When the issue stage stalls, it
       causes the buffer between instruction fetch and issue to fill; if the buffer is a
       single entry, instruction fetch stalls immediately. If the buffer is a queue with
       multiple instructions, it stalls when the queue fills.

	 */
	public void issue(LinkedList<Instruction> DQueue){
		int decode_count = 0;
		boolean halt = false;
		while((decode_count < this.NW) &&(!halt)){
		//Check no more than NW instructions in the instructions waiting queue
			Instruction instruction = DQueue.poll();
			String unit = Const.unitsForInstruction.get(instruction.opco);
			if(unit == "FPU"){
				if(fpuUnit.insertInstruction(instruction.opco, instruction.rs, instruction.rt, instruction.rd)){
					
				}else{
					halt = true;
				};
			}
			
//			if(DQueue.size()>this.pc){//if instructions waiting queue has an instruction which its index in the queue is 'pc'.
//				Instruction intruction = DQueue.get(this.pc);
//				//check the functional unit for the instruction is free or not.
//				String unit = Const.unitsForInstruction.get(intruction.opco);
//				Station station = Const.reservationStations.get(unit);
//				if(station.Busy){//If the functional unit for the instruction is not free, issue no instruction.
//					break;
//				}else{
//					//Check that not other active instruction has the same destination register.
//					boolean isAvailable = true;
//					if(unit.equals(Const.Unit.INT0)||unit.equals(Const.Unit.INT1)){
//						String rsState = Const.integerRegistersStatus.get(instruction.rs);
//						String rtState = Const.integerRegistersStatus.get(instruction.rt);
//						String rdState = Const.integerRegistersStatus.get(instruction.rd);
//						if((rsState==null) && (rsState==null) && (rdState==null)){//Three registers are not occupied
//							//the scoreboard issues the instruction to the functional unit and updates its internal data structural.
//							station.name = unit;//TODO whether use the unit's name??????
//							station.Busy = true;
//							staion.op = intruction.opco;
//							station.Qj = instruction.Qj;//TODO To check the Qj instruction's state
//							station.Qk = instruction.Qj;//TODO To check the Qj instruction's state
//						}else{
//							break;
//						}
//					}else{
//						String rsState = Const.floatRegistersStatus.get(instruction.rs);
//						String rtState = Const.floatRegistersStatus.get(instruction.rt);
//						String rdState = Const.floatRegistersStatus.get(instruction.rd);
//						if((rsState==null) && (rsState==null) && (rdState==null)){//Three registers are not occupied
//							//the scoreboard issues the instruction to the functional unit and updates its internal data structural.
//							station.name = unit;//TODO whether use the unit's name??????
//							station.Busy = true;
//							staion.op = intruction.opco;
//							station.Qj = instruction.Qj;//TODO To check the Qj instruction's state
//							station.Qk = instruction.Qj;//TODO To check the Qj instruction's state
//						}else{
//							break;
//						}
//					}
//				}
//				
			}
		}

	
	
	/*
	 * The scoreboard monitors the availability of the source operands.
       A source operand is available if no earlier issued active instruction is
       going to write it. When the source operands are available, the scoreboard tells
       the functional unit to proceed to read the operands from the registers and
       begin execution. The scoreboard resolves RAW hazards dynamically in this
       step, and instructions may be sent into execution out of order. This step,
       together with issue, completes the function of the ID step in the simple MIPS
       pipeline.
	 */
	public void readOperands(){
		//Iterate resvervation stations table, and read all the operands for every station
//		Const.reservationStations;
	}
	/*
	 * The functional unit begins execution upon receiving operands.
       When the result is ready, it notifies the scoreboard that it has completed
       execution. This step replaces the EX step in the MIPS pipeline and takes multiple
       cycles in the MIPS FP pipeline.
	 */
    public void execute(){
		//Iterate resvervation stations table, and execute every station
    	
	}
    /*
     * Once the scoreboard is aware that the functional unit has completed
       execution, the scoreboard checks for WAR hazards and stalls the completing
       instruction, if necessary.
     */
    public void writeResult(){
    	
    }
    
	public static void main(String args[]) throws IOException{
		String inputFile = args[0];
		int NF = Integer.parseInt(args[1]); // The maximum number of instructions can be fetched in one cycle
		int NQ = Integer.parseInt(args[2]); // The length of the instruction queue
		int NI =  Integer.parseInt(args[3]); // The maximum number of instructions can be decoded in one cycle
		int ND = Integer.parseInt(args[4]); // The length of the Decoded instruction queue
		Memory main = new Memory(); // store memory data 
		
		Simulator simulator = new Simulator(inputFile, main);
		
		simulator.startSimulation(main, NF, NQ, NI, ND);
	}
	
	
}
