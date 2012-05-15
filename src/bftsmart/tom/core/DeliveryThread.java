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
package bftsmart.tom.core;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.paxosatwar.Consensus;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.statemanagment.ApplicationState;
import bftsmart.tom.MessageContext;
import bftsmart.tom.TOMReceiver;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.util.BatchReader;
import bftsmart.tom.util.Logger;

/**
 * This class implements a thread which will deliver totally ordered requests to the application
 * 
 */
public final class DeliveryThread extends Thread {

    private LinkedBlockingQueue<Consensus> decided = new LinkedBlockingQueue<Consensus>(); // decided consensus
    private TOMLayer tomLayer; // TOM layer
    private TOMReceiver receiver; // Object that receives requests from clients
    private Recoverable recoverer; // Object that uses state transfer
    private ServerViewManager manager;

    /**
     * Creates a new instance of DeliveryThread
     * @param tomLayer TOM layer
     * @param receiver Object that receives requests from clients
     * @param conf TOM configuration
     */
    public DeliveryThread(TOMLayer tomLayer, TOMReceiver receiver, Recoverable recoverer, ServerViewManager manager) {
        super("Delivery Thread");

        this.tomLayer = tomLayer;
        this.receiver = receiver;
        this.recoverer = recoverer;
        //******* EDUARDO BEGIN **************//
        this.manager = manager;
        //******* EDUARDO END **************//
    }

    
    public TOMReceiver getReceiver() {
        return receiver;
    }
    
   public Recoverable getRecoverer() {
        return recoverer;
    }
   
    /**
     * Invoked by the TOM layer, to deliver a decide consensus
     * @param cons Consensus established as being decided
     */
    public void delivery(Consensus cons) {
        if (!containsGoodReconfig(cons)) {

            Logger.println("(DeliveryThread.delivery) Consensus ID " + cons.getId() + " does not contain good reconfiguration");
            //set this consensus as the last executed
            tomLayer.setLastExec(cons.getId());
            //define that end of this execution
            tomLayer.setInExec(-1);
        }
        try {
            decided.put(cons);
            Logger.println("(DeliveryThread.delivery) Consensus " + cons.getId() + " finished. Decided size=" + decided.size());
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private boolean containsGoodReconfig(Consensus cons) {
        TOMMessage[] decidedMessages = cons.getDeserializedDecision();

        for (TOMMessage decidedMessage : decidedMessages) {
            if (decidedMessage.getReqType() == TOMMessageType.RECONFIG
                    && decidedMessage.getViewID() == manager.getCurrentViewId()) {
                return true;
            }
        }

        return false;
    }

    /** THIS IS JOAO'S CODE, TO HANDLE STATE TRANSFER */
    private ReentrantLock deliverLock = new ReentrantLock();
    private Condition canDeliver = deliverLock.newCondition();

    public void deliverLock() {
        deliverLock.lock();
        //Logger.println("(DeliveryThread.deliverLock) Deliver lock obtained");
    }

    public void deliverUnlock() {
        deliverLock.unlock();
        //Logger.println("(DeliveryThread.deliverUnlock) Deliver Released");
    }

    public void canDeliver() {
        canDeliver.signalAll();
    }

    public void update(ApplicationState state) {
       
        int lastEid =  recoverer.setState(state);

        //set this consensus as the last executed
        System.out.println("Setting last EID to " + lastEid);
        tomLayer.setLastExec(lastEid);

        //define the last stable consensus... the stable consensus can
        //be removed from the leaderManager and the executionManager
        if (lastEid > 2) {
            int stableConsensus = lastEid - 3;

            //tomLayer.lm.removeStableMultipleConsenusInfos(lastCheckpointEid, stableConsensus);
            tomLayer.execManager.removeOutOfContexts(stableConsensus);
        }

        //define that end of this execution
        //stateManager.setWaiting(-1);
        tomLayer.setNoExec();

        decided.clear();

        Logger.println("(DeliveryThread.update) All finished from up to " + lastEid);
    }

    /**
     * This is the code for the thread. It delivers decided consensus to the TOM
     * request receiver object (which is the application)
     */
    @Override
    public void run() {
        while (true) {
            /** THIS IS JOAO'S CODE, TO HANDLE STATE TRANSFER */
            deliverLock();
            while (tomLayer.isRetrievingState()) {
                Logger.println("(DeliveryThread.run) Retrieving State.");
                canDeliver.awaitUninterruptibly();
            }
            /******************************************************************/
            try { //no exception should stop the batch delivery thread
                // take a decided consensus
                Consensus cons = decided.poll(1500, TimeUnit.MILLISECONDS);
                if (cons == null) {
                    deliverUnlock();
                    continue; //go back to the start of the loop
                }
                Logger.println("(DeliveryThread.run) Consensus " + cons.getId() + " was delivered.");

                TOMMessage[] requests = extractMessagesFromDecision(cons);
                
                if (requests != null && requests.length > 0) {
                    //cons.firstMessageProposed contains the performance counters
                    if (requests[0].equals(cons.firstMessageProposed)) {
                        requests[0] = cons.firstMessageProposed;
                    }

                    //clean the ordered messages from the pending buffer
                    tomLayer.clientsManager.requestsOrdered(requests);

                    deliverMessages(cons.getId(), tomLayer.getLCManager().getLastReg(), true, requests, cons.getDecision());

                    //******* EDUARDO BEGIN **************//
                    if (manager.hasUpdates()) {
                        processReconfigMessages(cons.getId(), cons.getDecisionRound().getNumber());
                        //set this consensus as the last executed
                        tomLayer.setLastExec(cons.getId());
                        //define that end of this execution
                        tomLayer.setInExec(-1);
                    }
                    //******* EDUARDO END **************//
                }
                
                /** THIS IS JOAO'S CODE, TO HANDLE CHECKPOINTS */
                //logDecision(cons);
                /********************************************************/
                //define the last stable consensus... the stable consensus can
                //be removed from the leaderManager and the executionManager
                //TODO: Is this part necessary? If it is, can we put it inside setLastExec
                if (cons.getId() > 2) {
                    int stableConsensus = cons.getId() - 3;

                    tomLayer.lm.removeStableConsenusInfos(stableConsensus);
                    tomLayer.execManager.removeExecution(stableConsensus);
                }

            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

            /** THIS IS JOAO'S CODE, TO HANDLE STATE TRANSFER */
            deliverUnlock();
            /******************************************************************/
        }
    }

    private TOMMessage[] extractMessagesFromDecision(Consensus cons) {
        TOMMessage[] requests = (TOMMessage[]) cons.getDeserializedDecision();

        if (requests == null) {
            //there are no cached deserialized requests
            //this may happen if this batch proposal was not verified
            //TODO: this condition is possible?

            Logger.println("(DeliveryThread.run) interpreting and verifying batched requests.");

            // obtain an array of requests from the taken consensus
            BatchReader batchReader = new BatchReader(cons.getDecision(),
                    manager.getStaticConf().getUseSignatures() == 1);
            requests = batchReader.deserialiseRequests(manager);
        } else {
            Logger.println("(DeliveryThread.run) using cached requests from the propose.");
        }

        return requests;
    }

    public void deliverUnordered(TOMMessage request, int regency) {
        MessageContext msgCtx = new MessageContext(System.currentTimeMillis(),
                new byte[0], regency, -1, request.getSender(), null);
        receiver.receiveReadonlyMessage(request, msgCtx);
    }

    private void deliverMessages(int consId, int regency, boolean fromConsensus, TOMMessage[] requests, byte[] decision) {
        receiver.receiveMessages(consId, regency, fromConsensus, requests, decision);
    }

    private void processReconfigMessages(int consId, int decisionRoundNumber) {
        byte[] response = manager.executeUpdates(consId, decisionRoundNumber);
        TOMMessage[] dests = manager.clearUpdates();

        for (int i = 0; i < dests.length; i++) {
            tomLayer.getCommunication().send(new int[]{dests[i].getSender()},
                    new TOMMessage(manager.getStaticConf().getProcessId(),
                    dests[i].getSession(), dests[i].getSequence(), response,
                    manager.getCurrentViewId()));
        }

        tomLayer.getCommunication().updateServersConnections();
    }

    private void logDecision(Consensus cons) {
        if (manager.getStaticConf().getCheckpointPeriod() > 0) {
            if ((cons.getId() > 0) && ((cons.getId() % manager.getStaticConf().getCheckpointPeriod()) == 0)) {
                Logger.println("(DeliveryThread.run) Performing checkpoint for consensus " + cons.getId());
                //byte[] state = receiver.getState();
                //tomLayer.getStateManager().saveState(state, cons.getId(), cons.getDecisionRound().getNumber(), tomLayer.lm.getCurrentLeader()/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
            } else {
                Logger.println("(DeliveryThread.run) Storing message batch in the state log for consensus " + cons.getId());
                //tomLayer.getStateManager().saveBatch(cons.getDecision(), cons.getId(), cons.getDecisionRound().getNumber(), tomLayer.lm.getCurrentLeader()/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
            }
        }
    }
}
