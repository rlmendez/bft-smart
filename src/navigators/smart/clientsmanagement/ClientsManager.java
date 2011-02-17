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

package navigators.smart.clientsmanagement;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.RequestsTimer;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMConfiguration;


/**
 *
 * @author alysson
 */
public class ClientsManager {

    private TOMConfiguration conf;
    private RequestsTimer timer;
    private HashMap<Integer, ClientData> clientsData = new HashMap<Integer, ClientData>();
    private ReentrantLock clientsLock = new ReentrantLock();

   
    public ClientsManager(TOMConfiguration conf, RequestsTimer timer) {
        this.conf = conf;
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
            Logger.println("(ClientsManager.getClientData) Creating new client data, client id="+clientId);
            clientData = new ClientData(clientId);
            clientsData.put(clientId, clientData);
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();

        return clientData;
    }

    /**
     * Get pending requests in a fair way (one request from each client
     * queue until the max number of requests is gotten).
     *
     * @return the set of all pending requests of this system
     */
    public PendingRequests getPendingRequests() {
        PendingRequests allReq = new PendingRequests();

        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/

        Set<Entry<Integer, ClientData>> clientsEntrySet = clientsData.entrySet();

        for (int i = 0; true; i++) {
            Iterator<Entry<Integer, ClientData>> it = clientsEntrySet.iterator();
            int noMoreMessages = 0;

            while (it.hasNext()) {
                ClientData clientData = it.next().getValue();
                PendingRequests clientPendingRequests = clientData.getPendingRequests();


                clientData.clientLock.lock();
                /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
                
                TOMMessage request = (clientPendingRequests.size() > i) ? clientPendingRequests.get(i) : null;

                /******* END CLIENTDATA CRITICAL SECTION ******/
                clientData.clientLock.unlock();

                if (request != null) {
                    //this client have pending message
                    allReq.addLast(request);
                    //I inserted a message on the batch, now I must verify if
                    //the max batch size is reached
                    if (allReq.size() == conf.getMaxBatchSize()) {
                        /******* END CLIENTS CRITICAL SECTION ******/
                        clientsLock.unlock();
                        return allReq;
                    }
                } else {
                    //this client do not have more pending requests
                    noMoreMessages++;

                    //now I have to verify if all clients are empty
                    if (noMoreMessages == clientsEntrySet.size()) {
                        /******* END CLIENTS CRITICAL SECTION ******/
                        clientsLock.unlock();
                        return allReq;
                    }
                }
            }
        }
    }

    /**
     * We've implemented some protection for individual client
     * data, but the clients table can change during the operation.
     *
     * @return true if there are some pending requests and false otherwise
     */
    public boolean havePendingRequests() {
        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/

        Iterator<Entry<Integer, ClientData>> it = clientsData.entrySet().iterator();

        while (it.hasNext()) {
            if (!it.next().getValue().getPendingRequests().isEmpty()) {
                clientsLock.unlock();
                return true;
            }
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();

        return false;
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
        int clientId = TOMMessage.getSenderFromId(reqId);

        if (clientId >= conf.getN()) {
            ClientData clientData = getClientData(clientId);

            clientData.clientLock.lock();
            /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
            TOMMessage pendingMessage = clientData.getPendingRequests().getById(reqId);
            /******* END CLIENTDATA CRITICAL SECTION ******/
            clientData.clientLock.unlock();

            return pendingMessage;
        } else {
            return null;
        }
    }

    public boolean requestReceived(TOMMessage request, boolean fromClient) {
        return requestReceived(request,fromClient,true);
    }

    /**
     * Notifies the ClientsManager that a new request from a client arrived.
     * This method updates the ClientData of the client request.getSender().
     *
     * @param request the received request
     * @param fromClient the message was received from client or not?
     * @param storeMessage the message should be stored or not? (read-only requests are not stored)
     *
     * @return true if the request is ok and is added to the pending messages
     * for this client, false if there is some problem and the message was not
     * accounted
     */
    public boolean requestReceived(TOMMessage request, boolean fromClient, boolean storeMessage) {
        request.receptionTime = System.currentTimeMillis();

        int clientId = request.getSender();
        boolean accounted = false;

        //Logger.println("(ClientsManager.requestReceived) getting info about client "+clientId);
        ClientData clientData = getClientData(clientId);

        //Logger.println("(ClientsManager.requestReceived) wait for lock for client "+clientData.getClientId());
        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        //Logger.println("(ClientsManager.requestReceived) lock for client "+clientData.getClientId()+" acquired");

        /*
        //for dealing with restarted clients
        if ((request.getSequence() == 0) &&
                (request.receptionTime - clientData.getLastMessageReceivedTime() >
                conf.getReplyVerificationTime())) {
            System.out.println("Start accounting messages for client "+clientId);
            clientData.setLastMessageReceived(-1);
        }
        */

/* ################################################ */
        //pjsousa: simple flow control mechanism to avoid out of memory exception
        if (conf.getUseControlFlow()!=0){
            if (fromClient && (clientData.getPendingRequests().size()>conf.getUseControlFlow())){
                //clients should not have more than 1000 outstanding messages, otherwise they will be dropped
                clientData.setLastMessageReceived(request.getSequence());
                clientData.setLastMessageReceivedTime(request.receptionTime);
                clientData.clientLock.unlock();
                accounted = false;
                return accounted;
            }
        }
/* ################################################ */

        if ((clientData.getLastMessageReceived()==-1) || (clientData.getLastMessageReceived() + 1 == request.getSequence()) || ((request.getSequence()>clientData.getLastMessageReceived()) && !fromClient)) {

            //it is a new message and I have to verify it's signature
            if (!request.signed || clientData.verifySignature(
                    request.serializedMessage,
                    request.serializedMessageSignature)) {
                //I don't have the message but it is correctly signed, I will
                //insert it in the pending requests of this client
                if(storeMessage) {
                    clientData.getPendingRequests().add(request);
                    /*
                    if (request.getSequence()%1000 == 0)
                        Logger.println("(ClientsManager.requestReceived) client "+clientId+ " pending requests size: "+clientData.getPendingRequests().size());
                     */
                }
                clientData.setLastMessageReceived(request.getSequence());
                clientData.setLastMessageReceivedTime(request.receptionTime);

                //create a timer for this message
                if (timer != null && storeMessage) {
                    timer.watch(request);
                }

                accounted = true;
            }
        } else {//I will not put this message on the pending requests list

            if (clientData.getLastMessageReceived() >= request.getSequence()) {
                //I already have/had this message
                accounted = true;
            } else {
                //it is an invalid message if it's being sent by a client (sequence number > last received + 1)
                /*
                Logger.println("Ignoring message "+request+" from client "+
                        clientData.getClientId()+"(last received = "+
                        clientData.getLastMessageReceived()+"), msg sent by client? "+fromClient);
                 **/
                accounted = false;
            }
        }

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();

        return accounted;
    }



    /**
     * Notifies the ClientsManager that the request was executed. It cleans all
     * state for this request (e.g., removes it from the pending requests queue
     * and stop any timer for it).
     *
     * @param request the request executed by the application
     * @param reply the resulting reply of the request execution
     */
    public void requestOrdered(TOMMessage request) {
        //stops the timer associated with this message
        if (timer != null) {
            timer.unwatch(request);
        }

        ClientData clientData = getClientData(request.getSender());

        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/

        //Logger.println("(ClientsManager.requestOrdered) Removing request "+request+" from pending requests");
        if (clientData.getPendingRequests().remove(request) == false){
            Logger.println("(ClientsManager.requestOrdered) Request "+request+" does not exist in pending requests");                        
        }
        clientData.setLastMessageExecuted(request.getSequence());

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();
    }

     public ReentrantLock getClientsLock() {
        return clientsLock;
    }

}
