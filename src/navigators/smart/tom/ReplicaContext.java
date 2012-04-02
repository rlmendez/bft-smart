/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.reconfiguration.views.View;
import navigators.smart.reconfiguration.util.TOMConfiguration;

/**
 *
 * @author alysson
 */
public class ReplicaContext {
    
    private ServerCommunicationSystem cs; // Server side comunication system
    private ServerViewManager SVManager;

    public ReplicaContext(ServerCommunicationSystem cs, 
                                 ServerViewManager SVManager) {
        this.cs = cs;
        this.SVManager = SVManager;
    }
    
    //TODO: implement a method that allow the replica to send a message with
    //total order to all other replicas
       
    /**
     * Returns the static configuration of this replica.
     * 
     * @return the static configuration of this replica
     */
    public TOMConfiguration getStaticConfiguration() {
        return SVManager.getStaticConf();
    }
    
    /**
     * Returns the current view of the replica group.
     * 
     * @return the current view of the replica group.
     */
    public View getCurrentView() {
        return SVManager.getCurrentView();
    }
}
