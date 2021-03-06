import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;


/**
 * @author Computer Architecture Simulator Project Group
 *
 */

/*
 * Unit       Latency for operation         Reservation stations            Instructions executing on the unit
 * 
 * 
 */
public class INT1 {
	private static INT1 instance;
	private INT1 (){}
	public static INT1 getInstance(){
		if(instance==null)
			instance = new INT1();
		return instance;
	}
private static final int LATENCY = 1;
	
	/*
	 * Reservation Stations Table.
	 * Station 1 to 4 are INT0&INT1 stations.
	   Station 5 and 6 are MULT stations.
	   Station 7 to 12 are Load/Store  stations.
	   Station 13 to 17 are FPU  stations.
	   Station 18 and 19 are BU  stations.
	 */
	public boolean insertInstruction(Instruction instruction){
		for(int i = 3;i<=4;i++){
			Station station = (Station) Const.reservationStations.get(i+"");
			if((!station.Busy)){
				int h;
				Register register;
				if (instruction.rs.contains("R")){
					register = (Register) Const.integerRegistersStatus.get(instruction.rs);
				}else{
					register = (Register) Const.floatRegistersStatus.get(instruction.rs);
				}
				
				if(register.busy){
					h = register.Reorder;
					if(((ROBItem)Const.ROB.get(h)).ready){
						station.Vj = ((ROBItem)Const.ROB.get(h)).value;
						station.Qj = 0;
					}else{
						station.Qj = h;
					}
				}else{
					station.Vj = register.value;
					station.Qj = 0;
				}
				
				// The same update for rt 
				if(instruction.immediate){//If the rt is an immediate.
					station.Vk = Float.parseFloat(instruction.rt);
					station.Qk = 0;
				}else{//If rt is a register.
					if (instruction.rt.contains("R")){
						register = (Register) Const.integerRegistersStatus.get(instruction.rt);
					}else{
						register = (Register) Const.floatRegistersStatus.get(instruction.rt);
					}
					System.out.println("instruction.rt->"+instruction.rt);
					if(register.busy){
						h = register.Reorder;
						if(((ROBItem)Const.ROB.get(h)).ready){
							station.Vk = ((ROBItem)Const.ROB.get(h)).value;
							station.Qk = 0;
						}else{
							station.Qk = h;//If the value of the register in the ROB not ready yet. Use the Qk to record the index, then get the value.
						}
					}else{
						station.Vk = register.value ;
						station.Qk = 0;
					}
				}
				
				
				if (instruction.rd.contains("R")){
					register = (Register) Const.integerRegistersStatus.get(instruction.rd);
				}else{
					register = (Register) Const.floatRegistersStatus.get(instruction.rd);
				}
				ROBItem item = new ROBItem();
				item.destination = instruction.rd;
				item.instruction = instruction;
				Const.ROB.add(item);
				int b = Const.ROB.indexOf(item);
				Const.lastOfROB = b + 1;
				register.Reorder = b; 
				register.busy = true;
				station.Dest = b;
				station.Busy = true;
				station.latency = 0;
				station.done = false;
				station.wbDone = false;
				station.Op = instruction.opco;
				station.status = "issued";
				station.text = instruction.text;
				station.newIssued = true;
				return true;
			}
		}
		
		// the issue is not successful, needs to stall for one cycle.
		Const.stallsByRS++;
		return false;
	}
	
	public void execute(){
		boolean isExecute = false;
		boolean isWB = false;
		for(int i = 3;i<=4;i++){
			Station station = (Station) Const.reservationStations.get(i+"");
			
			if(station.Busy && !station.newIssued){
				if (station.latency == 0 && isExecute) {
					continue;
				} else if(station.latency<LATENCY || !station.done){
					if (station.done) {
						station.latency = station.latency +1;
					}
					if((station.Qj==0) && (station.Qk==0) && !station.done){
						float vk = station.Vk;
						float vj = station.Vj;
						if(station.Op.equals("DADD")){
							station.result = vk +vj;
						}else if(station.Op.equals("DSUB")){
							station.result = vj - vk; 
						}else if(station.Op.equals("DADDI")){
							station.result = vj + vk;  
						}else if(station.Op.equals("AND")){
							station.result = (int)vj & (int)vk;
						}else if(station.Op.equals("ANDI")){
							station.result = (int)vj & (int)vk;
						}else if(station.Op.equals("OR")){
							station.result = ((int)vj) | ((int)vk);
						}else if(station.Op.equals("ORI")){
							station.result = ((int)vj) | ((int)vk);
						}else if(station.Op.equals("SLT")){//if $s < $t $d = 1; advance_pc (4); else $d = 0; advance_pc (4);
							boolean result ;
							result = vj < vk ;
							if(result){
								station.result = 1;
							}else{
								station.result = 0;
							}
						}else if(station.Op.equals("SLTI")){
							boolean result ;
							result = vj < vk ;
							if(result){
								station.result = 1;
							}else{
								station.result = 0;
							}
						}
						station.done = true;
						isExecute = true;
						station.status = "executing";
						station.latency = station.latency +1;
					}
				}else if(station.latency>=LATENCY && !station.wbDone && station.done && !isWB && Const.NB > 0 && Const.NC > 0){
					//Write result. 
					int b = station.Dest;
					
					Iterator iterator = Const.reservationStations.entrySet().iterator();
					while(iterator.hasNext()){
					   Map.Entry entry = (Entry) iterator.next();
					   Station s = (Station) entry.getValue();
					   if(s.Qj==b){
						   s.Vj = station.result;s.Qj = 0;
					   }
					   if(s.Qk==b){
						   s.Vk = station.result;s.Qk = 0;
					   }
					}
					((ROBItem)Const.ROB.get(b)).value = station.result;
					((ROBItem)Const.ROB.get(b)).ready = true;
					((ROBItem)Const.ROB.get(b)).newReady = true;
					station.Busy = false;
					isWB = true;
					station.wbDone = true;
					Const.NB--;
					Const.NC--;
					station.status = "WB";
					station.newWB = true;
				}
			}
			
		}
	}
}
