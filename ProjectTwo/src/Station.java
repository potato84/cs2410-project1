
/**
 * @author Computer Architecture Simulator Project Group
 *
 */

/*
 * Station, a class represents a single station entity of reservation station.
 * Each reservation station has seven fields:
   ■ Op—The operation to perform on source operands S1 and S2.
   ■ Qj, Qk—The reservation stations that will produce the corresponding source
     operand; a value of zero indicates that the source operand is already available
     in Vj or Vk, or is unnecessary.
   ■ Vj, Vk—The value of the source operands. Note that only one of the V
     fields or the Q field is valid for each operand. For loads, the Vk field is used
     to hold the offset field.
   ■ A—Used to hold information for the memory address calculation for a load
     or store. Initially, the immediate field of the instruction is stored here; after
     the address calculation, the effective address is stored here.
   ■ Busy—Indicates that this reservation station and its accompanying functional
     unit are occupied.
     The register file has a field, Qi:
   ■ Qi—The number of the reservation station that contains the operation whose
    result should be stored into this register. If the value of Qi is blank (or 0), no
    currently active instruction is computing a result destined for this register,
    meaning that the value is simply the register contents.
    The load and store buffers each have a field, A, which holds the result of the
    effective address once the first step of execution has been completed.
    In the next section, we will first consider some examples that show how these
    mechanisms work and then examine the detailed algorithm.
 */
public class Station {
	/*
	 * The station's name.
	 */
	String name;
	/*
	 * Busy—Indicates that this reservation station and its accompanying functional
       unit are occupied.
	 */
	boolean Busy = false;
	/*
	 * The operation to perform on source operands S1 and S2.
	 */
	String Op = "";
	
	/*
	 * Vj, Vk—The value of the source operands. Note that only one of the V
       fields or the Q field is valid for each operand. For loads, the Vk field is used
       to hold the offset field.
	 */
	float Vj, Vk;
	
	/*
	 * Qj, Qk—The reservation stations that will produce the corresponding source
       operand; a value of zero indicates that the source operand is already available
       in Vj or Vk, or is unnecessary.
	 */
	int Qj, Qk;
	
	/*
	 * records the destination register's reorder number, if exists 
	 */
	int Dest;
	
	/*
	 * A—Used to hold information for the memory address calculation for a load
       or store. Initially, the immediate field of the instruction is stored here; after
       the address calculation, the effective address is stored here.
	 */
	int A;
	
	int latency = 0;
	
	float result = 0;
	
	int loadFlag = 0;
	
	boolean done = false;
	boolean wbDone = false;
	String status = "";
	String text = "";
	boolean newIssued = true;
	boolean newWB = false;
	int afterDiv = 0;

}
