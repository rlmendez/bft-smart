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
package bftsmart.tom;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.communication.client.ReplyListener;
import bftsmart.reconfiguration.ReconfigureReply;
import bftsmart.reconfiguration.StatusReply;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;


/**
 * This class implements a TOMSender and represents a proxy to be used on the
 * client side of the replicated system.
 * It sends a request to the replicas, receives the reply, and delivers it to
 * the application.
 */
public class ServiceProxy extends TOMSender {

    // Locks for send requests and receive replies
    private ReentrantLock canReceiveLock = new ReentrantLock();
    private ReentrantLock canSendLock = new ReentrantLock();
    private Semaphore sm = new Semaphore(0);
    private int reqId = -1; // request id
    private TOMMessageType requestType; 
    private int replyQuorum = 0; // size of the reply quorum
    private TOMMessage replies[] = null; // Replies from replicas are stored here
    private int receivedReplies = 0; // Number of received replies
    private TOMMessage response = null; // Reply delivered to the application
    private int invokeTimeout = 40;
    private Comparator<byte[]> comparator;
    private Extractor extractor;
    private ReplyListener replyListener = null;

    /**
     * Constructor
     *
     * @see bellow
     */
    public ServiceProxy(int processId) {
        this(processId, null, null, null);
    }

    /**
     * Constructor
     *
     * @see bellow
     */
    public ServiceProxy(int processId, String configHome) {
        this(processId, configHome, null, null);
    }

    /**
     * Constructor
     *
     * @param processId Process id for this client (should be different from replicas)
     * @param configHome Configuration directory for BFT-SMART
     * @param replyComparator used for comparing replies from different servers
     *                        to extract one returned by f+1
     * @param replyExtractor used for extracting the response from the matching
     *                       quorum of replies
     */
    public ServiceProxy(int processId, String configHome,
            Comparator<byte[]> replyComparator, Extractor replyExtractor) {
        if (configHome == null) {
            init(processId);
        } else {
            init(processId, configHome);
        }

        replyQuorum = (int) Math.ceil((getViewManager().getCurrentViewN()
                + getViewManager().getCurrentViewF()) / 2) + 1;

        replies = new TOMMessage[getViewManager().getCurrentViewN()];

        comparator = (replyComparator != null) ? replyComparator : new Comparator<byte[]>() {
            @Override
            public int compare(byte[] o1, byte[] o2) {
                return Arrays.equals(o1, o2) ? 0 : -1;
            }
        };

        extractor = (replyExtractor != null) ? replyExtractor : new Extractor() {

            @Override
            public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {
                return replies[lastReceived];
            }
        };
        replyListener = null;
    }

    /**
     * Get the amount of time (in seconds) that this proxy will wait for
     * servers replies before returning null.
     *
     * @return the invokeTimeout
     */
    public int getInvokeTimeout() {
        return invokeTimeout;
    }

    /**
     * Set the amount of time (in seconds) that this proxy will wait for
     * servers replies before returning null.
     *
     * @param invokeTimeout the invokeTimeout to set
     */
    public void setInvokeTimeout(int invokeTimeout) {
        this.invokeTimeout = invokeTimeout;
    }

    public byte[] invokeOrdered(byte[] request) {
        return invoke(request, TOMMessageType.ORDERED_REQUEST);
    }

    public byte[] invokeUnordered(byte[] request) {
        return invoke(request, TOMMessageType.UNORDERED_REQUEST);
    }

    public void invokeAsynchronous(byte[] request, ReplyListener listener, int[] targets) {
        reqId = generateRequestId(TOMMessageType.UNORDERED_REQUEST);
        this.replyListener = listener;
    	if(this.getViewManager().getStaticConf().isTheTTP()) {
    		requestType = TOMMessageType.STATUS_REPLY;
	        try {
	        	sendMessageToTargets(request, reqId, targets);
	        } catch(RuntimeException re) {
	        	if(re.getMessage().equals("Server not connected")) {
	        		TOMMessage tm = new TOMMessage(targets[0], 0, reqId, TOMUtil.getBytes(StatusReply.OFFLINE.toString()), 0, requestType);
	        		listener.replyReceived(tm);
	        	}
	        }
    	} else
        	sendMessageToTargets(request, reqId, targets);
    }

    /**
     * This method sends a request to the replicas, and returns the related reply.
     * If the servers take more than invokeTimeout seconds the method returns null.
     * This method is thread-safe.
     *
     * @param request Request to be sent
     * @param reqType TOM_NORMAL_REQUESTS for service requests, and other for
     *        reconfig requests.
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request, TOMMessageType reqType) {
        canSendLock.lock();

        // Clean all statefull data to prepare for receiving next replies
        Arrays.fill(replies, null);
        receivedReplies = 0;
        response = null;
        replyListener = null;

        // Send the request to the replicas, and get its ID
        reqId = generateRequestId(reqType);
        requestType = reqType; 
        TOMulticast(request, reqId, reqType);

        Logger.println("Sending request (" + reqType + ") with reqId=" + reqId);
        Logger.println("Expected number of matching replies: " + replyQuorum);

        // This instruction blocks the thread, until a response is obtained.
        // The thread will be unblocked when the method replyReceived is invoked
        // by the client side communication system
        try {
            if (!this.sm.tryAcquire(invokeTimeout, TimeUnit.SECONDS)) {
                Logger.println("###################TIMEOUT#######################");
                Logger.println("Reply timeout for reqId=" + reqId);
                canSendLock.unlock();
                
                System.out.print(getProcessId() + " // " + reqId + " // TIMEOUT // ");
                System.out.println("Replies received: " + receivedReplies);
                
                return null;
            }
        } catch (InterruptedException ex) {
        }

        Logger.println("Response extracted = " + response);

        byte[] ret = null;

        if (response == null) {
            //the response can be null if n-f replies are received but there isn't
            //a replyQuorum of matching replies
            Logger.println("Received n-f replies and no response could be extracted.");

            canSendLock.unlock();
            if (reqType == TOMMessageType.UNORDERED_REQUEST) {
                //invoke the operation again, whitout the read-only flag
                Logger.println("###################RETRY#######################");
                return invokeOrdered(request);
            } else {
                throw new RuntimeException("Received n-f replies without f+1 of them matching.");
            }
        } else {
            //normal operation
            //******* EDUARDO BEGIN **************//
            if (reqType == TOMMessageType.ORDERED_REQUEST) {
                //Reply to a normal request!
                if (response.getViewID() == getViewManager().getCurrentViewId()) {
                    ret = response.getContent(); // return the response
                } else {//if(response.getViewID() > getViewManager().getCurrentViewId())
                    //updated view received
                    reconfigureTo((View) TOMUtil.getObject(response.getContent()));

                    canSendLock.unlock();
                    return invoke(request, reqType);
                }
            } else {
                if (response.getViewID() > getViewManager().getCurrentViewId()) {
                    //Reply to a reconfigure request!
                    Logger.println("Reconfiguration request' reply received!");
                    Object r = TOMUtil.getObject(response.getContent());
                    if (r instanceof View) { //did not executed the request because it is using an outdated view
                        reconfigureTo((View) r);

                        canSendLock.unlock();
                        return invoke(request, reqType);
                    } else { //reconfiguration executed!
                        reconfigureTo(((ReconfigureReply) r).getView());
                        ret = response.getContent();
                    }
                } else {
                	// Reply to readonly request
                    ret = response.getContent();
                }
            }
        }
        //******* EDUARDO END **************//

        canSendLock.unlock();
        return ret;
    }

    //******* EDUARDO BEGIN **************//
    private void reconfigureTo(View v) {
        Logger.println("Installing a most up-to-date view with id=" + v.getId());
        getViewManager().reconfigureTo(v);
        replies = new TOMMessage[getViewManager().getCurrentViewN()];
        getCommunicationSystem().updateConnections();
    }
    //******* EDUARDO END **************//

    /**
     * This is the method invoked by the client side comunication system.
     *
     * @param reply The reply delivered by the client side comunication system
     */
    @Override
    public void replyReceived(TOMMessage reply) {
        canReceiveLock.lock();
        if (reqId == -1) {//no message being expected
            Logger.println("throwing out request: sender=" + reply.getSender() + " reqId=" + reply.getSequence());
            canReceiveLock.unlock();
            return;
        }

        int pos = getViewManager().getCurrentViewPos(reply.getSender());

        if (pos < 0) { //ignore messages that don't come from replicas
            canReceiveLock.unlock();
            return;
        }

        if (reply.getSequence() == reqId && reply.getReqType() == requestType) {
        	if(replyListener != null) {
        		replyListener.replyReceived(reply);
                canReceiveLock.unlock();
                return;
        	}
        	
            Logger.println("Receiving reply from " + reply.getSender() +
                    " with reqId:" + reply.getSequence() + ". Putting on pos=" + pos);

            if (replies[pos] == null) {
                receivedReplies++;
            }
            replies[pos] = reply;
            
            // Compare the reply just received, to the others
            int sameContent = 1;
            for (int i = 0; i < replies.length; i++) {
                if (i != pos && replies[i] != null && 
                        (comparator.compare(replies[i].getContent(), reply.getContent()) == 0)) {
                    sameContent++;
                    if (sameContent >= replyQuorum) {
                        response = extractor.extractResponse(replies, sameContent, pos);
                        reqId = -1;
                        this.sm.release(); // resumes the thread that is executing the "invoke" method
                        break;
                    }
                }
            }
            
            if (response == null) {
            	if(requestType.equals(TOMMessageType.ORDERED_REQUEST)) {
            		if(receivedReplies == getViewManager().getCurrentViewN()) {
                        reqId = -1;
                        this.sm.release(); // resumes the thread that is executing the "invoke" method
            		}
            	} else {  // UNORDERED
            		if(receivedReplies != sameContent) {
                        reqId = -1;
                        this.sm.release(); // resumes the thread that is executing the "invoke" method
            		}
            	}
            }
        }

        // Critical section ends here. The semaphore can be released
        canReceiveLock.unlock();
    }
}
