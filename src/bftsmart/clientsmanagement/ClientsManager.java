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
package bftsmart.clientsmanagement;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.timer.RequestsTimer;
import bftsmart.tom.util.Logger;


/**
 *
 * @author alysson
 */
public class ClientsManager {

    private ServerViewManager manager;
    private RequestsTimer timer;
    private HashMap<Integer, ClientData> clientsData = new HashMap<Integer, ClientData>();
    private ReentrantLock clientsLock = new ReentrantLock();

    public ClientsManager(ServerViewManager manager, RequestsTimer timer) {
        this.manager = manager;
        this.timer = timer;
    }

    /**
     * We are assuming that no more than one thread will access
     * the same clientData during creation.
     *
     *
     * @param clientId
     * @return the ClientData stored on the manager
     */
    public ClientData getClientData(int clientId) {
        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/
        ClientData clientData = clientsData.get(clientId);

        if (clientData == null) {
            Logger.println("(ClientsManager.getClientData) Creating new client data, client id=" + clientId);

            //******* EDUARDO BEGIN **************//
            clientData = new ClientData(clientId,
                    (manager.getStaticConf().getUseSignatures() == 1)
                    ? manager.getStaticConf().getRSAPublicKey(clientId)
                    : null);
            //******* EDUARDO END **************//
            clientsData.put(clientId, clientData);
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();

        return clientData;
    }

    /**
     * Get pending requests in a fair way (one request from each client
     * queue until the max number of requests is obtained).
     *
     * @return the set of all pending requests of this system
     */
    public RequestList getPendingRequests() {
        RequestList allReq = new RequestList();

        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/
        
        Set<Entry<Integer, ClientData>> clientsEntrySet = clientsData.entrySet();
        
        for (int i = 0; true; i++) {
            Iterator<Entry<Integer, ClientData>> it = clientsEntrySet.iterator();
            int noMoreMessages = 0;

            while (it.hasNext()
                    && allReq.size() < manager.getStaticConf().getMaxBatchSize()
                    && noMoreMessages < clientsEntrySet.size()) {

                ClientData clientData = it.next().getValue();
                RequestList clientPendingRequests = clientData.getPendingRequests();

                clientData.clientLock.lock();
                /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
                TOMMessage request = (clientPendingRequests.size() > i) ? clientPendingRequests.get(i) : null;

                /******* END CLIENTDATA CRITICAL SECTION ******/
                clientData.clientLock.unlock();

                if (request != null) {
                    if(!request.alreadyProposed) {
                        //this client have pending message
                        request.alreadyProposed = true;
                        allReq.addLast(request);
                    }
                } else {
                    //this client don't have more pending requests
                    noMoreMessages++;
                }
            }
            
            if(allReq.size() == manager.getStaticConf().getMaxBatchSize() ||
                    noMoreMessages == clientsEntrySet.size()) {
                
                break;
            }
        }
        
        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();
        return allReq;
    }

    /**
     * We've implemented some protection for individual client
     * data, but the clients table can change during the operation.
     *
     * @return true if there are some pending requests and false otherwise
     */
    public boolean havePendingRequests() {
        boolean havePending = false;

        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/        
        
        Iterator<Entry<Integer, ClientData>> it = clientsData.entrySet().iterator();

        while (it.hasNext() && !havePending) {
            ClientData clientData = it.next().getValue();
            
            clientData.clientLock.lock();
            RequestList reqs = clientData.getPendingRequests();
            if (!reqs.isEmpty()) {
                for(TOMMessage msg:reqs) {
                    if(!msg.alreadyProposed) {
                        havePending = true;
                        break;
                    }
                }
            }
            clientData.clientLock.unlock();
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();
        return havePending;
    }

    /**
     * Verifies if some reqId is pending.
     *
     * @param reqId the request identifier
     * @return true if the request is pending
     */
    public boolean isPending(int reqId) {
        return getPending(reqId) != null;
    }

    /**
     * Get some reqId that is pending.
     *
     * @param reqId the request identifier
     * @return the pending request, or null
     */
    public TOMMessage getPending(int reqId) {
        ClientData clientData = getClientData(TOMMessage.getSenderFromId(reqId));

        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        TOMMessage pendingMessage = clientData.getPendingRequests().getById(reqId);

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();

        return pendingMessage;
    }

    public boolean requestReceived(TOMMessage request, boolean fromClient) {
        return requestReceived(request, fromClient, null);
    }

    /**
     * Notifies the ClientsManager that a new request from a client arrived.
     * This method updates the ClientData of the client request.getSender().
     *
     * @param request the received request
     * @param fromClient the message was received from client or not?
     * @param storeMessage the message should be stored or not? (read-only requests are not stored)
     * @param cs server com. system to be able to send replies to already processed requests
     *
     * @return true if the request is ok and is added to the pending messages
     * for this client, false if there is some problem and the message was not
     * accounted
     */
    public boolean requestReceived(TOMMessage request, boolean fromClient, ServerCommunicationSystem cs) {
        
        request.receptionTime = System.nanoTime();

        int clientId = request.getSender();
        boolean accounted = false;

        //Logger.println("(ClientsManager.requestReceived) getting info about client "+clientId);
        ClientData clientData = getClientData(clientId);

        //Logger.println("(ClientsManager.requestReceived) wait for lock for client "+clientData.getClientId());
        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        //Logger.println("(ClientsManager.requestReceived) lock for client "+clientData.getClientId()+" acquired");

        /* ################################################ */
        //pjsousa: simple flow control mechanism to avoid out of memory exception
        if (fromClient && (manager.getStaticConf().getUseControlFlow() != 0)) {
            if (clientData.getPendingRequests().size() > manager.getStaticConf().getUseControlFlow()) {
                //clients should not have more than defined in the config file
                //outstanding messages, otherwise they will be dropped.
                //just account for the message reception
                clientData.setLastMessageReceived(request.getSequence());
                clientData.setLastMessageReceivedTime(request.receptionTime);

                clientData.clientLock.unlock();
                return false;
            }
        }
        /* ################################################ */

        //new session... just reset the client counter
        if (clientData.getSession() != request.getSession()) {
            clientData.setSession(request.getSession());
            clientData.setLastMessageReceived(-1);
        }

        if ((clientData.getLastMessageReceived() == -1) || //first message received or new session (see above)
                (clientData.getLastMessageReceived() + 1 == request.getSequence()) || //message received is the expected
                ((request.getSequence() > clientData.getLastMessageReceived()) && !fromClient)) {

            //it is a new message and I have to verify it's signature
            if (!request.signed
                    || clientData.verifySignature(request.serializedMessage,
                    request.serializedMessageSignature)) {

                //I don't have the message but it is valid, I will
                //insert it in the pending requests of this client

                clientData.getPendingRequests().add(request); 
                clientData.setLastMessageReceived(request.getSequence());
                clientData.setLastMessageReceivedTime(request.receptionTime);

                //create a timer for this message
                if (timer != null) {
                    timer.watch(request);
                }

                accounted = true;
            }
        } else {
            //I will not put this message on the pending requests list
            if (clientData.getLastMessageReceived() >= request.getSequence()) {
                //I already have/had this message

                //send reply if it is available
                TOMMessage reply = clientData.getReply(request.getId());
                if (reply != null && cs != null) {
                    cs.send(new int[]{request.getSender()}, reply);
                }

                accounted = true;
            } else {
                //a too forward message... the client must be malicious
                accounted = false;
            }
        }

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();

        return accounted;
    }

    /**
     * Notifies the ClientsManager that these requests were already executed.
     * 
     * @param requests the array of requests to account as ordered
     */
    public void requestsOrdered(TOMMessage[] requests) {
        clientsLock.lock();
        for (TOMMessage request : requests) {
            requestOrdered(request);
        }
        clientsLock.unlock();
    }

    /**
     * Cleans all state for this request (e.g., removes it from the pending
     * requests queue and stop any timer for it).
     *
     * @param request the request ordered by the consensus
     */
    private void requestOrdered(TOMMessage request) {
        //stops the timer associated with this message
        if (timer != null) {
            timer.unwatch(request);
        }

        ClientData clientData = getClientData(request.getSender());

        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        if (!clientData.removeOrderedRequest(request)) {
            Logger.println("(ClientsManager.requestOrdered) Request "
                    + request + " does not exist in pending requests");
        }
        clientData.setLastMessageExecuted(request.getSequence());

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();
    }

    public ReentrantLock getClientsLock() {
        return clientsLock;
    }
}
