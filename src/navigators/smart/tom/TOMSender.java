/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.communication.client.ReplyReceiver;
import navigators.smart.reconfiguration.ViewManager;
import navigators.smart.tom.core.messages.TOMMessage;



/**
 * This class is used to
 * multicast data to a group of replicas
 */
public abstract class TOMSender implements ReplyReceiver {

    private int me; // process id
    
    //******* EDUARDO BEGIN **************//
    //private int[] group; // group of replicas
    private ViewManager viewManager;
    //******* EDUARDO END **************//
    
    private int sequence = 0; // sequence number
    private CommunicationSystemClientSide cs; // Client side comunication system
    private Lock lock = new ReentrantLock(); // lock to manage concurrent access to this object by other threads
    private boolean useSignatures = false;

    
    
    
    
    /**
     * Creates a new instance of TOMulticastSender
     *
     * TODO: Isto pode mesmo estar vazio?
     */
    public TOMSender() {
    }

    public void close(){
        cs.close();
    }

    public CommunicationSystemClientSide getCommunicationSystem() {
        return this.cs;
    }
    
    
    //******* EDUARDO BEGIN **************//
    public ViewManager getViewManager(){
        return this.viewManager;
    }
    //******* EDUARDO END **************//
    
    /**
     * This method initializes the object
     * TODO: Perguntar se este metodo n pode antes ser protected (compila como protected, mas mesmo assim...)
     *
     * @param cs Client side comunication system
     * @param conf Client side comunication system configuration
     * @param sequence Initial sequence number for data multicast
     *
     * 
     */
    public void init(int processId, int sequence) {
        this.init(processId);
        this.sequence = sequence;
    }
    
    public void init(int processId, String configHome, int sequence) {
        this.init(processId, configHome);
        this.sequence = sequence;
    }

    
    //******* EDUARDO BEGIN **************//
    /**
     * This method initializes the object
     * TODO: Perguntar se este metodo n pode antes ser protected (compila como protected, mas mesmo assim...)
     *
     * @param cs Client side comunication system
     * @param conf Total order messaging configuration
     */
    public void init(int processId) {
        this.viewManager = new ViewManager(processId);
        startsCS();
    }
    
    public void init(int processId, String configHome) {
        this.viewManager = new ViewManager(processId,configHome);
        startsCS();
    }

    private void startsCS() {
        this.cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(this.viewManager);
        this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
        this.me = this.viewManager.getStaticConf().getProcessId();
        this.useSignatures = this.viewManager.getStaticConf().getUseSignatures()==1?true:false;
    }
    //******* EDUARDO END **************//
    
    
    // Get next sequence number to a soon to be multicasted message
    private int getNextSequenceNumber() {
        lock.lock();
        int id = sequence++;
        lock.unlock();
        return id;

    }

    /**
     * Get last sequence number of an already multicasted message
     *
     * @return Last sequence number of an already multicasted message
     *
     * TODO: Isto nao devia ter tambem um semaforo a controlar a leitura deste atributo?
     */
    public int getLastSequenceNumber() {
        return sequence - 1;
    }

    //******* EDUARDO BEGIN **************//
    /**
     * Multicast data to the group of replicas
     *
     * @param m Data to be multicast
     */
    public void TOMulticast(byte[] m) {
        cs.send(useSignatures, this.viewManager.getCurrentViewProcesses(), 
                new TOMMessage(me, getNextSequenceNumber(), m,
                this.viewManager.getCurrentViewId()), false);
    }

    /**
     * Multicast data to the group of replicas
     *
     * @param m Data to be multicast
     * @param readOnly it is a readonly request
     */
    public void doTOMulticast(byte[] m, int reqType, boolean readOnly) {
        cs.send(useSignatures, this.viewManager.getCurrentViewProcesses(), 
                new TOMMessage(me, getNextSequenceNumber(), m,
                this.viewManager.getCurrentViewId(), reqType, readOnly), false);
    }
    
    /**
     * Multicast a TOMMessage to the group of replicas
     *
     * @param m Data to be multicast
     */
    public void TOMulticast(TOMMessage sm) {
        cs.send(useSignatures, this.viewManager.getCurrentViewProcesses(), 
                sm, false);
    }

    /**
     * Create TOMMessage and sign it
     *
     * @param m Data to be included in TOMMessage
     *
     * @return TOMMessage with serializedMsg and serializedMsgSignature fields filled
     */
    public TOMMessage sign(byte[] m) {
        TOMMessage tm = new TOMMessage(me, getNextSequenceNumber(), m, 
                this.viewManager.getCurrentViewId());
        cs.sign(tm);
        return tm;
    }
    
    //******* EDUARDO END **************//
}
