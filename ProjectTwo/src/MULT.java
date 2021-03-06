import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;


/**
 * @author Computer Architecture Simulator Project Group
 *
 */

/*
 * Unit    Latency for operation              Reservation stations      Instructions executing on the unit
 * 
 * MULT    4 (integer multiply)               2                         DMUL
 * 
 */
public class MULT {
	private static MULT instance;
	private MULT (){}
	public static MULT getInstance(){
		if(instance==null){
			instance = new MULT();
		}
		return instance;
	}
private static final int LATENCY = 2;
	
	/*
	 * Reservation Stations Table.
	 * Station 1 to 4 are INT0&INT1 stations.
	   Station 5 and 6 are MULT stations.
	   Station 7 to 12 are Load/Store  stations.
	   Station 13 to 17 are FPU  stations.
	   Station 18 and 19 are BU  stations.
	 */
	public boolean insertInstruction(Instruction instruction){
		for(int i = 5;i<=6;i++){
			Station station = (Station) Const.reservationStations.get(i+"");
			if((!station.Busy)){
				int h;//TODO 这里的h是ROB的head entry？？？
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
				if (instruction.rt.contains("R")){
					register = (Register) Const.integerRegistersStatus.get(instruction.rt);
				}else{
					register = (Register) Const.floatRegistersStatus.get(instruction.rt);
				}
				
				if(register.busy){
					h = register.Reorder;
					if(((ROBItem)Const.ROB.get(h)).ready){
						station.Vk = ((ROBItem)Const.ROB.get(h)).value;
						station.Qk = 0;
					}else{
						station.Qk = h;
					}
				}else{
					station.Vk = register.value ;
					station.Qk = 0;
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
		for(int i = 5;i<=6;i++){
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
						station.result = vj * vk;
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
