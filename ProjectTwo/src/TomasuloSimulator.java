
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class TomasuloSimulator {
	BU buUnit;// BU unit instance;
	FPU fpuUnit;// FPU unit instance;
	INT0 int0Unit;//INT0 unit instance
	INT1 int1Unit;//INT1 unit instance
	LoadStore loadStoreUnit;// Load and store unit instance
	MULT multUnit;// MULT unit instance
	
	
	
	
	boolean finishedFlag = true;//flag for whether this simulation comes to its end.
	int NF ; // The maximum number of instructions can be fetched in one cycle
	int NQ ; // The length of the instruction queue
	int NI ; // The maximum number of instructions can be decoded in one cycle
	int ND ; // The length of the Decoded instruction queue
	int NW = 4;//The maximum number of instructions can be issued every clock cycle to reservation stations. 
	
//	int pc = 0;
	
	public TomasuloSimulator(String instructionFile, Memory main){
		//Initiate all the units
		buUnit = BU.getInstance();
		fpuUnit = FPU.getInstance();
		int0Unit = INT0.getInstance();
		int1Unit = INT1.getInstance();
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
						if (line.isEmpty() || line.trim().equals("") || line.trim().equals("\n")){
							// skip the empty lines.
						}else{
							Instruction instr = new Instruction();
							instr = instr.loadInstrs(line);
							main.loadInstruction(instr);
						}
					}
				}
			}
		
//			System.out.println(main.getInstrs().size());
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
		LinkedList FQueue = new LinkedList(); // Fetched Instructions Queue
		LinkedList DQueue = new LinkedList(); // Decoded Instructions Queue (actually, the decode is not needed, only check for branch)
		
		
		while(!finishedFlag){//Clock cycles loop
			
			/**
			 * If the instruction queue is not full, and there are instructions not finished,
			 * Fetch instructions. 
			 * In one clock cycle, the maximum number of fetching is NF. 
			 */
			int fetched = 0;
			while((FQueue.size() < NQ) && (fetched < NF) &&(pc < main.getInstrs().size())){
				((Instruction)main.getInstrs().get(pc)).UpdatePC(pc); // Instruction needs to have a feature called pc, so that can check whether the BTBuffer prediction is wrong.
				FQueue.add(main.getInstrs().get(pc));
				if(BTBuffer.Getbuffer()[pc%32][0] != -1){
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
				Instruction next = (Instruction) FQueue.poll();
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
			
			commit(main,BTBuffer);
			execute();
			issue(DQueue);
			
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
	public void issue(LinkedList DQueue){
		int issue_count = 0;
		boolean halt = false;
		while((issue_count < this.NW) &&(!halt)){
			if(Const.ROB.size()>=Const.NR){//If ROB' size equals or is greater than NR , stop issuing instructions.
				halt = true;
				return;
			}
		//Check no more than NW instructions in the instructions waiting queue
			Instruction instruction = (Instruction) DQueue.poll();
			String unit = (String)Const.unitsForInstruction.get(instruction.opco);
			boolean isSuccessful = true;
			if(unit == "FPU"){
				isSuccessful = fpuUnit.insertInstruction(instruction);
			}else if(unit == "INT0"){
				isSuccessful = int0Unit.insertInstruction(instruction);
			}else if(unit == "INT1"){
				isSuccessful = int1Unit.insertInstruction(instruction);
			}else if(unit == "Load/Store"){
				isSuccessful = loadStoreUnit.insertInstruction(instruction);
			}else if(unit == "BU"){
				isSuccessful = buUnit.insertInstruction(instruction);
			}else if(unit == "MULT"){
				isSuccessful = multUnit.insertInstruction(instruction);
				
			}
			if(!isSuccessful){
				halt = true;
				return;
			}
			issue_count++;
			
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
    	int1Unit.execute();
    	loadStoreUnit.execute();
    	multUnit.execute();
    	buUnit.execute();
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
    public void commit(Memory main,BranchTargetBuffer btb){
		int NC = 4;
		int bus_count = 0;
    	if(Const.ROB.size()>0 && bus_count < NC ){
    		int h = 0;  // always commit the first item in ROB
			ROBItem item = (ROBItem)Const.ROB.get(h);
    		if(item.ready){
    			String d = item.destination;
    			if(item.instruction.opco == "BEQZ" || item.instruction.opco == "BNEZ"
    					||item.instruction.opco == "BNE"||item.instruction.opco == "BEQ"){
    				int predicted = btb.Getbuffer ()[item.instruction.pc % 32][0]; // the predicted pc
					if(item.value != predicted){// If branch is mispredicted.
    						Const.ROB.clear();
    						Const.initiateFloatRegistersStatus();
    						Const.initiateIntegerRegistersStatus();
							if(btb.Getbuffer()[item.instruction.pc%32][1] == 1){
								// update the branch-target-buffer
								btb.Getbuffer()[item.instruction.pc%32][0] = (int)item.value;
								btb.Getbuffer()[item.instruction.pc%32][1] = 0;
							}else{
								// allow make mistakes twice.
							}
    				}
    			}else if(item.instruction.opco == "S.D" || item.instruction.opco == "SD"){
    				main.updateData(Integer.parseInt(d), item.value);
    			}else{
					//TODO update the registers
					bus_count++;

    			}
    			item.busy = false;
    			if(((Register)Const.floatRegistersStatus.get(d)).Reorder==h){
    				((Register)Const.floatRegistersStatus.get(d)).busy = false;
    			}
//                if(((Register)Const.integerRegistersStatus.get(d)).Reorder==h){
//                	((Register)Const.integerRegistersStatus.get(d)).busy = false;
//    			}
    		}
    	}
    }
    public static void main(String args[]) throws IOException{
		String inputFile = args[0];
		int NF = Integer.parseInt(args[1]); // The maximum number of instructions can be fetched in one cycle
		int NQ = Integer.parseInt(args[2]); // The length of the instruction queue
		int NI =  Integer.parseInt(args[3]); // The maximum number of instructions can be decoded in one cycle
		int ND = Integer.parseInt(args[4]); // The length of the Decoded instruction queue
		Memory main = new Memory(); // store memory data 
		
		TomasuloSimulator simulator = new TomasuloSimulator(inputFile, main);
		
//		simulator.startSimulation(main, NF, NQ, NI, ND);
	}
	
	
}