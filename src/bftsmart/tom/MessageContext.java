/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom;

import bftsmart.tom.core.messages.TOMMessage;

/**
 * This class represents the whole context of a request ordered in the system.
 * It stores all informations regarding the message sent and the consensus
 * execution that ordered it.
 * 
 * @author alysson
 */
public class MessageContext {
    private long timestamp;
    private byte[] nonces;
    private int regency;
    private int consensusId;
    private int sender;
    private TOMMessage firstInBatch; //to be replaced by a statistics class
    private int batchSize; // Used to inform the size of the batch in which the request was decided. Used for state logging.

    public MessageContext(long timestamp, byte[] nonces, int regency, int consensusId, int sender, TOMMessage firstInBatch) {
        this.timestamp = timestamp;
        this.nonces = nonces;
        this.regency = regency;
        this.consensusId = consensusId;
        this.sender = sender;
        this.firstInBatch = firstInBatch;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return the nonces
     */
    public byte[] getNonces() {
        return nonces;
    }

    /**
     * @return the consensusId
     */
    public int getConsensusId() {
        return consensusId;
    }

    /**
     * @return the regency
     */
    public int getRegency() {
        return regency;
    }

    /**
     * @return the sender
     */
    public int getSender() {
        return sender;
    }

    /**
     * @param sender the sender to set
     */
    public void setSender(int sender) {
        this.sender = sender;
    }
    
    /**
     * @return the first message in the ordered batch
     */
    public TOMMessage getFirstInBatch() {
        return firstInBatch;
    }

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

}
