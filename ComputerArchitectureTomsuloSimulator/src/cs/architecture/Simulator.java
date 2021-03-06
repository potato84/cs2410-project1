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
	 * Get an instruction from the instruction queue. Issue the instruction if
       there is an empty reservation station and an empty slot in the ROB; send the
       operands to the reservation station if they are available in either the registers
       or the ROB. Update the control entries to indicate the buffers are in use. The
       number of the ROB entry allocated for the result is also sent to the reservation
       station, so that the number can be used to tag the result when it is placed
       on the CDB. If either all reservations are full or the ROB is full, then instruction
       issue is stalled until both have available entries.

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
			
		}
      }

	
	
	
	/*
	 *If one or more of the operands is not yet available, monitor the
      CDB while waiting for the register to be computed. This step checks for
      RAW hazards. When both operands are available at a reservation station, execute
      the operation. Instructions may take multiple clock cycles in this stage,
      and loads still require two steps in this stage. Stores need only have the base
      register available at this step, since execution for a store at this point is only
      effective address calculation.
	 */
    public void execute(){
		//Iterate resvervation stations table, and execute every station.
    	fpuUnit.execute();
    	int0Unit.execute();
    	buUnit.execute();
	}
    /*
     * When the result is available, write it on the CDB (with the ROB
       tag sent when the instruction issued) and from the CDB into the ROB, as well
       as to any reservation stations waiting for this result. Mark the reservation station
       as available. Special actions are required for store instructions. If the value
       to be stored is available, it is written into the Value field of the ROB entry for
       the store. If the value to be stored is not available yet, the CDB must be monitored
       until that value is broadcast, at which time the Value field of the ROB
       entry of the store is updated. For simplicity we assume that this occurs during
       the write results stage of a store; we discuss relaxing this requirement later.
     */
    public void writeResult(){
    	
    }
    /*
     * This is the final stage of completing an instruction, after which only
       its result remains. (Some processors call this commit phase ��completion�� or
       ��graduation.��) There are three different sequences of actions at commit depending
       on whether the committing instruction is a branch with an incorrect prediction,
       a store, or any other instruction (normal commit). The normal commit case
       occurs when an instruction reaches the head of the ROB and its result is present
       in the buffer; at this point, the processor updates the register with the result and
       removes the instruction from the ROB. Committing a store is similar except
       that memory is updated rather than a result register. When a branch with incorrect
       prediction reaches the head of the ROB, it indicates that the speculation
       3.6 Hardware-Based Speculation �� 187
       was wrong. The ROB is flushed and execution is restarted at the correct successor
       of the branch. If the branch was correctly predicted, the branch is finished.
       Once an instruction commits, its entry in the ROB is reclaimed and the register
       or memory destination is updated, eliminating the need for the ROB entry.
     */
    public void commit(){
    	
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
