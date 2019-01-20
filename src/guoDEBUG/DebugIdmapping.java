package guoDEBUG;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import core.DTNHost;
import core.SimScenario;
import movement.ExternalMovement;
import movement.MovementModel;

public class DebugIdmapping {
    public static void getInfoIdMapping() {
        System.out.println("getInfoIdMapping");
        SimScenario scen = SimScenario.getInstance();
        HashMap<String, ExternalMovement> idmapping = (HashMap<String, ExternalMovement>)ExternalMovement.getIdMapping();
        Set<String> keySet = idmapping.keySet();
        
        List<DTNHost> hosts = scen.getHosts();
        for( int i = 0; i < hosts.size(); i++ ) {
            MovementModel movementModel1 = hosts.get(i).getMovement();
            for( String bus_id:keySet) {
                MovementModel movementModel2 = idmapping.get(bus_id); 
                if( movementModel1 == movementModel2 ) {
                    System.out.print(bus_id+"    ");
                    System.out.println(hosts.get(i).getAddress());
                }
            }
        }
    }
}
